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
    internal var bytes = ByteArray(0)
}

@ResponseBuilderDsl
public fun CgiScript.respond(code: HttpStatusCode, block: ResponseBuilder.() -> Unit) {
    val builder = ResponseBuilder()
    block(builder)
//    info.result = buildString {
//        appendLine("HTTP/1.1 ${code.number} ${code.message}")
//        builder.headers.forEach { (key, value) ->
//            appendLine("$key: $value")
//        }
//        appendLine()
//        append(builder.text)
//    }

    val httpHeader = buildString {
        appendLine("HTTP/1.1 ${code.number} ${code.message}")
        var contentLength = false
        builder.headers.forEach { (key, value) ->
            appendLine("$key: $value")
            if (key.equals("Content-Length", ignoreCase = true)) {
                contentLength = true
            }
        }

        if (!contentLength) {
            appendLine("Content-Length: ${builder.bytes.size}")
        }

        appendLine()
    }

    info.result = httpHeader.encodeToByteArray() + builder.bytes
}

@ResponseBuilderDsl
@HtmlTagMarker // What's better than one DSL marker? Two DSL markers!
public fun ResponseBuilder.html(prettyPrint: Boolean = true, xhtmlCompatible: Boolean = false, builder: HTML.() -> Unit) {
    if (bytes.isNotEmpty()) throw IllegalStateException("Cannot call html() already writing a response body.")
    headers.add("Content-Type" to "text/html; charset=utf-8")
    bytes = createHTML(prettyPrint, xhtmlCompatible).html(block = builder).encodeToByteArray()
}

@ResponseBuilderDsl
public fun ResponseBuilder.headers(block: HeaderBuilder.() -> Unit) {
    val builder = HeaderBuilder(this)
    block(builder)
}

@ResponseBuilderDsl
public fun ResponseBuilder.text(text: String) {
    if (bytes.isNotEmpty()) throw IllegalStateException("Cannot call text() already writing a response body.")
    headers.add("Content-Type" to "text/plain")
    bytes = text.encodeToByteArray()
}

@ResponseBuilderDsl
public inline fun ResponseBuilder.text(block: StringBuilder.() -> Unit) =
    text(buildString(block))
