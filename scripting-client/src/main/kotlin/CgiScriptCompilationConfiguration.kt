package com.sschr15.scripting

import kotlinx.coroutines.runBlocking
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
    }
}) {
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
}
