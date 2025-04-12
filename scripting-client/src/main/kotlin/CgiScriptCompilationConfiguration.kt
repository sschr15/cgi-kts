package com.sschr15.scripting

import com.sschr15.scripting.api.DependsOnLocal
import com.sschr15.scripting.api.DependsOnMavenCentral
import com.sschr15.scripting.api.DependsOnUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.*
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.*
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.jvmTarget

internal object CgiScriptCompilationConfiguration : ScriptCompilationConfiguration({
    defaultImports("com.sschr15.scripting.api.*")
    defaultImports("kotlinx.html.*")
    defaultImports("io.github.oshai.kotlinlogging.KotlinLogging", "io.github.oshai.kotlinlogging.KLogger")
    defaultImports(Repository::class, DependsOn::class)

    jvm {
        // Use detected version if possible, or Java 21 (the version used for this project) if unknown
        jvmTarget(System.getProperty("java.specification.version") ?: "21")
        dependenciesFromCurrentContext(wholeClasspath = true)
    }

    refineConfiguration {
        onAnnotations(Repository::class, DependsOn::class) {
            CgiScriptCompilationConfiguration.handleMavenAnnotations(it)
        }
        onAnnotations(DependsOnMavenCentral::class) {
            CgiScriptCompilationConfiguration.handleMavenCentral(it)
        }
        onAnnotations(DependsOnLocal::class) {
            CgiScriptCompilationConfiguration.handleFile(it)
        }
        onAnnotations(DependsOnUrl::class) {
            CgiScriptCompilationConfiguration.handleUrl(it)
        }
    }
}) {
    private const val MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2"
    private val artifactRegex = Regex("""(?<group>[^:]*):(?<name>[^:]*):(?<version>[^:]*)(?::(?<classifier>[^:]*))?(?:@(?<extension>[^:]*))?""")

    private val logger = KotlinLogging.logger {}
    private fun readResolve(): Any = CgiScriptCompilationConfiguration
    private val resolver = CompoundDependenciesResolver(FileSystemDependenciesResolver(), MavenDependenciesResolver())

    private fun handleMavenAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.takeIf { it.isNotEmpty() }
            ?: return context.compilationConfiguration.asSuccess()
        return runBlocking {
            resolver.resolveFromScriptSourceAnnotations(annotations)
        }.onSuccess {
            context.compilationConfiguration.with {
                dependencies.append(JvmDependency(it))
            }.asSuccess()
        }
    }

    private fun handleMavenCentral(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val sources = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)
            ?.filter { it.annotation is DependsOnMavenCentral }
            ?: return context.compilationConfiguration.asSuccess()

        var config = context.compilationConfiguration

        for (source in sources) {
            val mvnCentral = source.annotation as DependsOnMavenCentral

            data class Dep(
                val group: String,
                val artifact: String,
                val version: String,
                val classifier: String,
                val extension: String
            )

            val dep = if (mvnCentral.coordinates.isNotBlank()) {
                val match = artifactRegex.matchEntire(mvnCentral.coordinates)
                    ?: return fail("Invalid coordinates: ${mvnCentral.coordinates}", source.location)
                val group = match.groups["group"]!!.value.replace('.', '/')
                val name = match.groups["name"]!!.value
                val version = match.groups["version"]!!.value
                val classifier = match.groups["classifier"]?.value
                val extension = match.groups["extension"]?.value ?: "jar"

                val classifierPart = classifier?.let { "-$it" } ?: ""

                Dep(group, name, version, classifierPart, extension)
            } else {
                if (mvnCentral.group.isBlank()) return fail("No specified MavenCentral group", source.location)
                if (mvnCentral.artifact.isBlank()) return fail(
                    "No specified MavenCentral artifact",
                    source.location
                )
                if (mvnCentral.version.isBlank()) return fail("No specified MavenCentral version", source.location)

                val group = mvnCentral.group.replace('.', '/')
                val name = mvnCentral.artifact
                val version = mvnCentral.version
                val classifier = mvnCentral.classifier.takeIf { it.isNotBlank() }?.let { "-$it" } ?: ""
                val extension = mvnCentral.extension

                Dep(group, name, version, classifier, extension)
            }

            val (group, name, version, classifier, extension) = dep
            val path = "$group/$name/$version/$name-$version$classifier.$extension"
            val url = "$MAVEN_CENTRAL_URL/$path"
            val resultLocation = Path(System.getenv("HOME"), ".m2/repository", path)
            val result = runCatching {
                if (resultLocation.exists()) {
                    val sha1 = MessageDigest.getInstance("SHA-1").apply {
                        resultLocation.inputStream().use {
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var bytesRead = it.read(buffer)
                            while (bytesRead != -1) {
                                update(buffer, 0, bytesRead)
                                bytesRead = it.read(buffer)
                            }
                        }
                    }.digest().toHexString()

                    logger.debug { "sha1 of cached artifact $path: $sha1" }

                    val foundSha1 = URI.create("$url.sha1").toURL().openStream().readAllBytes().decodeToString()
                    if (sha1 == foundSha1) {
                        logger.debug { "Cached artifact $path matches sha1 checksum" }
                        return@runCatching
                    } else {
                        logger.debug { "Cached artifact $path does not match sha1 checksum (MavenCentral reports $foundSha1)" }
                        resultLocation.deleteExisting()
                    }
                }

                resultLocation.parent.createDirectories()
                URI.create(url).toURL().openStream().use {
                    Files.copy(it, resultLocation, StandardCopyOption.REPLACE_EXISTING)
                }
            }

            if (result.isSuccess) {
                config = config.with {
                    dependencies.append(JvmDependency(resultLocation.toFile()))
                }
            } else {
                return fail(
                    "Failed to download artifact from $url",
                    source.location,
                    result.exceptionOrNull()
                        ?: NullPointerException("result.exceptionOrNull() returned null despite the result not being a success")
                )
            }
        }

        return config.asSuccess()
    }

    private fun handleFile(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val sources = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)
            ?.filter { it.annotation is DependsOnMavenCentral }
            ?: return context.compilationConfiguration.asSuccess()

        var config = context.compilationConfiguration
        for (source in sources) {
            val local = source.annotation as DependsOnLocal
            val file = Path(local.path)
            if (!file.exists()) return fail("File does not exist: ${file.toAbsolutePath()}", source.location)
            config = config.with {
                dependencies.append(JvmDependency(file.toFile()))
            }
        }

        return config.asSuccess()
    }

    private fun handleUrl(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val sources = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)
            ?.filter { it.annotation is DependsOnMavenCentral }
            ?: return context.compilationConfiguration.asSuccess()

        var config = context.compilationConfiguration
        for (source in sources) {
            val url = (source.annotation as DependsOnUrl).url.let { URI.create(it).toURL() }
            val resultLocation = Path(System.getenv("HOME"), ".cgi-cache", url.path)
            val result = runCatching {
                resultLocation.parent.createDirectories()
                url.openStream().use {
                    Files.copy(it, resultLocation)
                }
            }
            if (result.isSuccess) {
                config = config.with {
                    dependencies.append(JvmDependency(resultLocation.toFile()))
                }
            } else {
                return fail("Failed to download artifact from $url", source.location, result.exceptionOrNull())
            }
        }

        return config.asSuccess()
    }

    private fun fail(text: String, location: SourceCode.LocationWithId? = null, exception: Throwable? = null): ResultWithDiagnostics.Failure {
        logger.error(exception) { "$text (at $location)" }

        return ResultWithDiagnostics.Failure(
            ScriptDiagnostic(
                ScriptDiagnostic.unspecifiedError,
                text,
                ScriptDiagnostic.Severity.ERROR,
                location,
                exception
            )
        )
    }
}
