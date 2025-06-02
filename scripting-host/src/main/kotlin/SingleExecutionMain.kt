package com.sschr15.scripting

import com.sschr15.scripting.api.CgiScript
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.onFailure
import kotlin.script.experimental.api.scriptExecutionWrapper
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate
import kotlin.system.exitProcess

object SingleExecutionMain {
    private val logger = KotlinLogging.logger {}

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        if (args.isEmpty()) {
            println("Usage: SingleExecutionMain <script> [script, ...]")
            exitProcess(1)
        }

        if (args.size == 1) {
            runScript(args.single())
        } else {
            args.forEach {
                logger.info { "Running script: $it" }
                runScript(it)
                logger.info { "" }
            }

            logger.info { "Finished running scripts." }
        }
    }

    suspend fun runScript(pathName: String) {
        val responseInfo = CgiResponseInfo(System::getenv)
        responseInfo.result = "<no response received>".encodeToByteArray()

        val compilationConfig = createJvmCompilationConfigurationFromTemplate<CgiScript>()
        val evaluationConfig = createJvmEvaluationConfigurationFromTemplate<CgiScript> { 
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

        val scriptPath = Path(pathName)
        val source = scriptPath.readText().toScriptSource(scriptPath.nameWithoutExtension)

//        BasicJvmScriptingHost().eval(source, compilationConfig, evaluationConfig)
//            .onFailure { diagnostics ->
//                diagnostics.reports.forEach { it.logTo(logger) }
//                exitProcess(1)
//            }

        val host = BasicJvmScriptingHost()
        val script = host.compiler(source, compilationConfig)
            .onFailure { diagnostics -> 
                diagnostics.reports.forEach { it.logTo(logger) }
                throw IllegalStateException("Failed to compile script at $scriptPath").apply { 
                    diagnostics.reports.mapNotNull { it.exception }.forEach { addSuppressed(it) }
                }
            }
            .valueOrThrow()

        host.evaluator(script, evaluationConfig)
            .onFailure { diagnostics -> 
                diagnostics.reports.forEach { it.logTo(logger) }
                throw IllegalStateException("Failed to evaluate script at $scriptPath").apply {
                    diagnostics.reports.mapNotNull { it.exception }.forEach { addSuppressed(it) }
                }
            }

        try {
            logger.info { responseInfo.result.decodeToString() }
        } catch (_: UninitializedPropertyAccessException) {
            logger.info { "The script at $scriptPath failed to respond." }
        }
    }
}
