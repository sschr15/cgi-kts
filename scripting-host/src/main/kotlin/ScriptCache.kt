package com.sschr15.scripting

import com.sschr15.scripting.api.CgiScript
import com.sschr15.scripting.api.HttpStatusCode.ServerError.Companion.InternalServerError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate

class ScriptCache {
    private val cache = mutableMapOf<Path, CompiledScript>() //TODO
    private val compiler = JvmScriptCompiler()

    fun uncache(path: Path) {
        cache.remove(path)
    }

    operator fun contains(path: Path): Boolean = path in cache

    suspend fun compile(path: Path): Boolean {
        val config = createJvmCompilationConfigurationFromTemplate<CgiScript>()

        val scriptSource = withContext(Dispatchers.IO) {
            path.readText().toScriptSource(path.name.removeSuffix(".cgi.kts"))
        }

        val scriptResult = compiler(scriptSource, config)
        scriptResult.reports.forEach { println(it.render()) }

        if (scriptResult is ResultWithDiagnostics.Success) {
            cache[path] = scriptResult.value
            return true
        } else {
            return false
        }
    }

    suspend fun evaluate(path: Path, envRequest: (String) -> String?): String {
        val script = cache[path] ?: return buildString {
            append("HTTP/1.1 ")
            append(InternalServerError)
            append("\r\n\r\n")
            append("This script failed to compile.")
            append("\r\n\r\n")
        }

        val responseInfo = CgiResponseInfo(envRequest)

        val evalConfig = createJvmEvaluationConfigurationFromTemplate<CgiScript> { 
            constructorArgs(responseInfo)
        }

        val result = BasicJvmScriptEvaluator()(script, evalConfig)

        result.onFailure { 
            return buildString {
                append("HTTP/1.1 ")
                append(InternalServerError)
                append("\r\n\r\n")
                append(it.reports.joinToString("\n", transform = ScriptDiagnostic::render))
                append("\r\n\r\n")
            }
        }

        return try {
            responseInfo.result
        } catch (_: UninitializedPropertyAccessException) {
            buildString {
                append("HTTP/1.1 ")
                append(InternalServerError)
                append("\r\n\r\n")
                append("This script failed to respond.")
                append("\r\n\r\n")
            }
        }
    }
}
