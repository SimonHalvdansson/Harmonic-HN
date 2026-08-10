package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.network.toNetworkUrlOrNull

data class HackerNewsItemLink(
    val url: String,
    val itemId: Int,
    val scrollToCommentId: Int = -1,
)

object HackerNewsLinks {
    private val itemUrlPattern = Regex(
        "https?://news\\.ycombinator\\.com/item\\?[^\\s<>\"']+",
        RegexOption.IGNORE_CASE,
    )

    fun parseItemLink(url: String?): HackerNewsItemLink? {
        val networkUrl = url?.toNetworkUrlOrNull() ?: return null
        if (
            networkUrl.scheme.lowercase() !in setOf("http", "https") ||
            !networkUrl.host.equals("news.ycombinator.com", ignoreCase = true) ||
            networkUrl.encodedPath != "/item"
        ) {
            return null
        }
        val itemId = positiveInt(networkUrl.queryParameter("id")) ?: return null
        return HackerNewsItemLink(
            url = networkUrl.toString(),
            itemId = itemId,
            scrollToCommentId = positiveInt(networkUrl.fragment) ?: -1,
        )
    }

    fun findItemLink(text: String?): HackerNewsItemLink? {
        if (text == null) return null
        return itemUrlPattern.findAll(text).firstNotNullOfOrNull { match ->
            parseItemLink(match.value.trimEnd { it in ".,;:)]" })
        }
    }

    private fun positiveInt(value: String?): Int? =
        value?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
}

object ArchiveRedirectPolicy {
    private val archiveHosts = setOf(
        "archive.is",
        "archive.today",
        "archive.ph",
        "archive.vn",
        "archive.md",
    )

    fun parseDomains(value: String?): List<String> {
        val seen = mutableSetOf<String>()
        return value.orEmpty().split(',').mapNotNull { part ->
            normalizeDomain(part).takeIf { it.isNotEmpty() && seen.add(it) }
        }
    }

    fun normalizeDomain(value: String?): String {
        if (value.isNullOrBlank()) return ""
        var normalized = value.trim().lowercase()
        if (normalized.startsWith("//")) {
            normalized = "https:$normalized"
        } else if (!normalized.contains("://") && normalized.contains('/')) {
            normalized = "https://$normalized"
        }
        val parsedHost = normalized.toNetworkUrlOrNull()?.host
        if (!parsedHost.isNullOrEmpty()) normalized = parsedHost
        else normalized = normalized.substringBefore('/')
        normalized = normalized.substringBefore(':').trim('.')
        if (normalized.startsWith("www.")) normalized = normalized.removePrefix("www.")
        return normalized.takeIf {
            '.' in it && it.all { character -> character.isLetterOrDigit() || character in ".-" }
        }.orEmpty()
    }

    fun redirectUrl(url: String?, configuredDomains: Collection<String>): String? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        if (parsed.scheme.lowercase() !in setOf("http", "https")) return null
        val host = normalizeDomain(parsed.host)
        if (host.isEmpty() || host in archiveHosts) return null
        val matches = configuredDomains.any { configured ->
            val domain = normalizeDomain(configured)
            domain.isNotEmpty() && (host == domain || host.endsWith(".$domain"))
        }
        return if (matches) "https://archive.is/newest/${percentEncode(url.orEmpty())}" else null
    }

    private fun percentEncode(value: String): String = buildString {
        value.encodeToByteArray().forEach { rawByte ->
            val byte = rawByte.toInt() and 0xff
            val character = byte.toChar()
            if (
                character in 'a'..'z' || character in 'A'..'Z' ||
                character in '0'..'9' || character in "-_.~"
            ) {
                append(character)
            } else {
                append('%')
                append(HEX[byte ushr 4])
                append(HEX[byte and 0x0f])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}

object AgePolicy {
    fun isOlderThan(epochSeconds: Int, nowEpochMillis: Long, ageMillis: Long): Boolean =
        nowEpochMillis - epochSeconds.toLong() * 1_000L > ageMillis
}
