package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.network.toNetworkUrlOrNull
import kotlin.time.Clock

object ExternalUrlPolicy {
    fun ensureHttpScheme(value: String): String = value.takeIf {
        it.startsWith("http://", ignoreCase = true) ||
            it.startsWith("https://", ignoreCase = true)
    } ?: "http://$value"
}

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
    const val TWO_HOURS_MILLIS: Long = 2L * 60L * 60L * 1_000L
    const val TWO_WEEKS_MILLIS: Long = 14L * 24L * 60L * 60L * 1_000L

    fun isOlderThan(epochSeconds: Int, nowEpochMillis: Long, ageMillis: Long): Boolean =
        nowEpochMillis - epochSeconds.toLong() * 1_000L > ageMillis

    fun isOlderThanTwoHours(
        epochSeconds: Int,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean = isOlderThan(epochSeconds, nowEpochMillis, TWO_HOURS_MILLIS)

    fun isOlderThanTwoWeeks(
        epochSeconds: Int,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean = isOlderThan(epochSeconds, nowEpochMillis, TWO_WEEKS_MILLIS)
}

object DomainNamePolicy {
    fun fromUrl(url: String?): String? {
        val host = url?.removeSuffix("#")?.toNetworkUrlOrNull()?.host.orEmpty()
        return host.takeIf(String::isNotEmpty)?.removePrefix("www.")
    }

    fun formatForDisplay(domain: String?, includeTopLevelDomain: Boolean): String? {
        if (includeTopLevelDomain || domain.isNullOrEmpty()) return domain
        val lastDotIndex = domain.lastIndexOf('.')
        return if (lastDotIndex <= 0) domain else domain.substring(0, lastDotIndex)
    }
}

object TimeWindowPolicy {
    private const val MINUTES_PER_DAY = 24L * 60L

    /** Returns whether [currentTime] is in the half-open interval, including overnight ranges. */
    fun containsMinutes(initialTime: Long, finalTime: Long, currentTime: Long): Boolean {
        var normalizedFinalTime = finalTime
        var normalizedCurrentTime = currentTime
        if (normalizedFinalTime < initialTime) normalizedFinalTime += MINUTES_PER_DAY
        if (normalizedCurrentTime < initialTime) normalizedCurrentTime += MINUTES_PER_DAY
        return normalizedCurrentTime in initialTime..<normalizedFinalTime
    }
}
