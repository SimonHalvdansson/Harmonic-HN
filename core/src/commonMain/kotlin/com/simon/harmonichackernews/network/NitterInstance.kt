package com.simon.harmonichackernews.network

import io.ktor.http.Url

/** A Nitter server origin, shared by settings, redirects, and preview extraction. */
object NitterInstance {
    const val DEFAULT_URL = "https://nitter.net"

    fun normalize(value: String): String? {
        val input = value.trim()
        if (!input.startsWith("https://", ignoreCase = true) &&
            !input.startsWith("http://", ignoreCase = true)
        ) return null
        if (input.any { it.isWhitespace() || it == '\\' } || '?' in input || '#' in input) return null
        if (input.substringAfter("://").substringBefore('/').isBlank()) return null
        val url = runCatching { Url(input) }.getOrNull() ?: return null
        if (url.host.isBlank() || url.user != null || url.password != null ||
            url.encodedPath.trim('/') != "" || url.port !in 1..65535
        ) return null
        return "${url.protocol.name}://${url.host.lowercase()}" +
            if (url.port == url.protocol.defaultPort) "" else ":${url.port}"
    }

    fun effectiveUrl(value: String): String =
        // Settings snapshots read this during startup, even when redirects are disabled.
        if (value == DEFAULT_URL) DEFAULT_URL else normalize(value) ?: DEFAULT_URL
}
