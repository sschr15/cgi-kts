package com.sschr15.scripting

import com.sschr15.scripting.api.CgiScript
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.onFailure
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate
import kotlin.system.exitProcess

object SingleExecutionMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val responseInfo = CgiResponseInfo(System::getenv)

        val compilationConfig = createJvmCompilationConfigurationFromTemplate<CgiScript>()
        val evaluationConfig = createJvmEvaluationConfigurationFromTemplate<CgiScript> { 
            constructorArgs(responseInfo)
        }

        val scriptPath = Path(args[0])
        val source = scriptPath.readText().toScriptSource(scriptPath.nameWithoutExtension)

        BasicJvmScriptingHost().eval(source, compilationConfig, evaluationConfig)
            .onFailure { diagnostics ->
                diagnostics.reports.forEach {
                    System.err.println(it.render())
                }
                exitProcess(1)
            }

        println(responseInfo.result)
    }
}
