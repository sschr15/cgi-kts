package com.sschr15.scripting.api

import com.sschr15.scripting.CgiResponseInfo
import com.sschr15.scripting.CgiScriptCompilationConfiguration
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    fileExtension = "cgi.kts",
    compilationConfiguration = CgiScriptCompilationConfiguration::class
)
public open class CgiScript(internal val info: CgiResponseInfo) {

    /** The URI being requested. */
    public val requestUri: String = requestEnvironmentVariable("REQUEST_URI") ?: ""
    /** The query parameters being requested, in its raw form. */
    public val queryString: String = requestEnvironmentVariable("QUERY_STRING") ?: ""
    /** The query parameters being requested, in a map form. */
    public val query: Map<String, String> by lazy {
        if (queryString.isBlank()) return@lazy emptyMap()

        queryString.split("&").associate {
            val (key, value) = it.split("=")
            key to value
        }
    }

    /** The requester's user agent. */
    public val userAgent: String = requestEnvironmentVariable("HTTP_USER_AGENT") ?: ""

    /** The request type. */
    public val requestType: HttpRequestType = HttpRequestType.valueOf(requestEnvironmentVariable("REQUEST_METHOD") ?: "GET")

    /**
     * Request an environment variable from the CGI-running process. This is not guaranteed to be
     * the same process running the code.
     */
    public fun requestEnvironmentVariable(env: String): String? = info.envRequest(env)
}
