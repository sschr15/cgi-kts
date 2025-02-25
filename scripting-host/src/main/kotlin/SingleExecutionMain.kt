package com.sschr15.scripting

import com.sschr15.scripting.api.CgiScript
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.onFailure
import kotlin.script.experimental.api.scriptExecutionWrapper
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate
import kotlin.system.exitProcess

object SingleExecutionMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val responseInfo = CgiResponseInfo(System::getenv)
        responseInfo.result = "<no response received>"

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

        val scriptPath = Path(args[0])
        val source = scriptPath.readText().toScriptSource(scriptPath.nameWithoutExtension)

        BasicJvmScriptingHost().eval(source, compilationConfig, evaluationConfig)
            .onFailure { diagnostics ->
                diagnostics.reports.filter { it.severity > ScriptDiagnostic.Severity.DEBUG }.forEach {
                    System.err.println(it.render(withStackTrace = true))
                }
                exitProcess(1)
            }

        try {
            println(responseInfo.result)
        } catch (_: UninitializedPropertyAccessException) {
            println("The script at $scriptPath failed to respond.")
        }
    }
}
