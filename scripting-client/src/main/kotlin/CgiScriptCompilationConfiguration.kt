package com.sschr15.scripting

import kotlinx.coroutines.runBlocking
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.*
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

internal object CgiScriptCompilationConfiguration : ScriptCompilationConfiguration({
    defaultImports("com.sschr15.scripting.api.*")
    defaultImports("kotlinx.html.*")
    defaultImports(Repository::class, DependsOn::class)

    jvm {
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
