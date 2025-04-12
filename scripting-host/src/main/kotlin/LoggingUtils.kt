package com.sschr15.scripting

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.Level
import java.io.File
import kotlin.script.experimental.api.ScriptDiagnostic

fun ScriptDiagnostic.logTo(
    logger: KLogger,
    withLocation: Boolean = true,
    withException: Boolean = true
) {
    val level = when (severity) {
        ScriptDiagnostic.Severity.DEBUG -> Level.DEBUG
        ScriptDiagnostic.Severity.INFO -> Level.INFO
        ScriptDiagnostic.Severity.WARNING -> Level.WARN
        ScriptDiagnostic.Severity.ERROR -> Level.ERROR
        ScriptDiagnostic.Severity.FATAL -> Level.ERROR
    }

    logger.at(level) {
        message = buildString {
            append(this@logTo.message)
            if (withLocation && (sourcePath != null || location != null)) {
                append(" (")
                sourcePath?.let { append(it.substringAfterLast(File.separatorChar)) }
                location?.let {
                    if (sourcePath != null) append(":")
                    append(it.start.line)
                    append(":")
                    append(it.start.col)
                }
                append(")")
            }
        }

        if (withException) cause = exception
    }
}
