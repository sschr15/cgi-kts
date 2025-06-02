package com.sschr15.scripting

import com.sschr15.scripting.api.CgiScript
import com.sschr15.scripting.api.HttpStatusCode.ServerError.Companion.InternalServerError
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate

class ScriptCache {
    private val cacheLocation = System.getProperty("script.cache")?.let(::Path)
    private val cache = SerializableBackedMutableMap<String, CompiledScript>(cacheLocation)

    private val compiler = JvmScriptCompiler()

    fun uncache(path: Path) {
        cache.remove(path.absolutePathString())
    }

    operator fun contains(path: Path): Boolean = path.absolutePathString() in cache

    suspend fun compile(path: Path): List<ScriptDiagnostic> {
        val config = createJvmCompilationConfigurationFromTemplate<CgiScript>()

        val scriptSource = withContext(Dispatchers.IO) {
            path.readText().toScriptSource(path.name.removeSuffix(".cgi.kts"))
        }

        val scriptResult = compiler(scriptSource, config)
        scriptResult.reports.forEach { it.logTo(logger) }

        if (scriptResult is ResultWithDiagnostics.Success) {
            cache[path.absolutePathString()] = scriptResult.value

            if (cacheLocation != null) withContext(Dispatchers.IO) {
                cache.save(cacheLocation)
                logger.info { "Compiled $path and cached to $cacheLocation" }
            }
        }

        return scriptResult.reports
    }

    private fun serverErrorResponse(message: String) = buildString {
        append("HTTP/1.1 ")
        append(InternalServerError)
        append("\r\n\r\n")
        append(message)
        append("\r\n\r\n")
    }.toByteArray()

    suspend fun evaluate(path: Path, envRequest: (String) -> String?): ByteArray {
        val script = cache[path.absolutePathString()]
            ?: return serverErrorResponse("The requested script failed to compile or was not found in the cache.")

        val responseInfo = CgiResponseInfo(envRequest)

        val evalConfig = createJvmEvaluationConfigurationFromTemplate<CgiScript> { 
            constructorArgs(responseInfo)
            scriptExecutionWrapper {
                try {
                    it()
                } catch (e: Throwable) {
                    if (e !is CgiScriptStoppedException) throw e
                    else return@scriptExecutionWrapper
                }
            }
        }

        val result = BasicJvmScriptEvaluator()(script, evalConfig)

        result.onFailure { result ->
            result.reports.forEach { it.logTo(logger) }

            return serverErrorResponse(
                result.reports.joinToString("\n", transform = ScriptDiagnostic::render)
            )
        }

        return try {
            responseInfo.result
        } catch (_: UninitializedPropertyAccessException) {
            serverErrorResponse("The script did not properly respond.")
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
