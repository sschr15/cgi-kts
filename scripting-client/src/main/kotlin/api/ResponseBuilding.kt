package com.sschr15.scripting.api

import kotlinx.datetime.Instant
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.html.HTML
import kotlinx.html.HtmlTagMarker
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlin.time.Duration

private val cookieDateTimeFormat = DateTimeComponents.Format {
    dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
    chars(", ")
    dayOfMonth()
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    year()
    chars(" ")
    hour()
    chars(":")
    minute()
    chars(":")
    second()
    chars(" GMT")
}

@DslMarker
public annotation class ResponseBuilderDsl

public enum class CookieSameSite {
    Strict,
    Lax,
    None
}

public class HeaderBuilder internal constructor(private val builder: ResponseBuilder) {
    public operator fun String.invoke(value: String) {
        builder.headers += this to value
    }

    public operator fun set(key: String, value: String) {
        builder.headers += key to value
    }

    /**
     * Convenience function to set a cookie.
     */
    public fun cookie(
        name: String,
        value: String,
        secure: Boolean = false,
        httpOnly: Boolean = false,
        sameSite: CookieSameSite? = null,
        partitioned: Boolean = false,
        expires: Instant? = null,
        maxAge: Duration? = null,
        domain: String? = null,
        path: String? = null,
    ) {
        builder.headers += "Set-Cookie" to buildString {
            append("$name=$value")
            if (secure) append("; Secure")
            if (httpOnly) append("; HttpOnly")
            if (sameSite != null) append("; SameSite=$sameSite")
            if (partitioned) append("; Partitioned")
            if (expires != null) append("; Expires=${expires.format(cookieDateTimeFormat)}")
            if (maxAge != null) append("; Max-Age=${maxAge.inWholeSeconds}")
            if (domain != null) append("; Domain=$domain")
            if (path != null) append("; Path=$path")
        }
    }
}

public class ResponseBuilder {
    internal val headers = mutableSetOf<Pair<String, String>>()
    internal val text = StringBuilder()
}

@ResponseBuilderDsl
public fun CgiScript.respond(code: HttpStatusCode, block: ResponseBuilder.() -> Unit) {
    val builder = ResponseBuilder()
    block(builder)
    info.result = buildString {
        appendLine("HTTP/1.1 ${code.number} ${code.message}")
        builder.headers.forEach { (key, value) ->
            appendLine("$key: $value")
        }
        appendLine()
        append(builder.text)
    }
}

@ResponseBuilderDsl
@HtmlTagMarker // What's better than one DSL marker? Two DSL markers!
public fun ResponseBuilder.html(prettyPrint: Boolean = true, xhtmlCompatible: Boolean = false, builder: HTML.() -> Unit) {
    headers.add("Content-Type" to "text/html")
    text.append(createHTML(prettyPrint, xhtmlCompatible).html(block = builder))
    headers.add("Content-Length" to text.length.toString())
}

@ResponseBuilderDsl
public fun ResponseBuilder.headers(block: HeaderBuilder.() -> Unit) {
    val builder = HeaderBuilder(this)
    block(builder)
}
