package com.sschr15.scripting.api

import com.sschr15.scripting.CgiResponseInfo
import com.sschr15.scripting.CgiScriptCompilationConfiguration
import com.sschr15.scripting.CgiScriptStoppedException
import java.net.URLDecoder
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    fileExtension = "cgi.kts",
    compilationConfiguration = CgiScriptCompilationConfiguration::class
)
public open class CgiScript(internal val info: CgiResponseInfo)

/** The URI being requested. */
public val CgiScript.requestUri: String get() = requestEnvironmentVariable("REQUEST_URI") ?: ""

/** The query parameters being requested, in its raw form. */
public val CgiScript.queryString: String get() = requestEnvironmentVariable("QUERY_STRING") ?: ""

private fun decode(str: String) = URLDecoder.decode(str, Charsets.UTF_8)

/** The query parameters being requested, in a map form. */
public val CgiScript.query: Map<String, String> get() {
    if (queryString.isBlank()) return emptyMap()

    return queryString.split("&").associate {
        val (key, value) = it.split("=").map(::decode)
        key to value
    }
}

/** The requester's user agent. */
public val CgiScript.userAgent: String get() = requestEnvironmentVariable("HTTP_USER_AGENT") ?: ""

/** The request type. */
public val CgiScript.requestType: HttpRequestType
    get() = HttpRequestType.valueOf(requestEnvironmentVariable("REQUEST_METHOD") ?: "GET")

/**
 * Request an environment variable from the CGI-running process.
 * This is not guaranteed to be the same process (or even the same user) running the code.
 */
public fun CgiScript.requestEnvironmentVariable(env: String): String? = info.envRequest(env)

/**
 * Terminate the script early.
 */
public fun CgiScript.stop(): Nothing {
    throw CgiScriptStoppedException()
}

/**
 * Respond with any custom text instead of a standard HTTP response.
 */
@PossiblyUnsafeBehavior("Non-standard HTTP responses may not be properly interpreted by a client.")
public fun CgiScript.respond(text: String) {
    info.result = text
}
