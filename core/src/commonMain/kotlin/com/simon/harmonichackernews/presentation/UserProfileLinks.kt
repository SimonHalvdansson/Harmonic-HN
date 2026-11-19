package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.toNetworkUrlOrNull

/** A plausible web link in the rendered, plain text of a user biography. [end] is exclusive. */
data class UserProfileLink(val start: Int, val end: Int, val url: String)

/**
 * HN biographies often contain plain URLs rather than anchors. Detect them without changing
 * the text, so hosts can opt in to adding link annotations while preserving its formatting.
 * Bare domains are deliberately conservative: code filenames and email addresses stay text.
 */
fun detectUserProfileLinks(text: String): List<UserProfileLink> = buildList {
    PROFILE_LINK_CANDIDATE.findAll(text).forEach { match ->
        val start = match.range.first
        val preceding = text.getOrNull(start - 1)
        if (preceding != null && (preceding.isLetterOrDigit() || preceding in "_.-@/:")) {
            return@forEach
        }

        val candidate = trimProfileLinkPunctuation(match.value)
        if (candidate.isEmpty()) return@forEach
        val end = start + candidate.length
        val following = text.getOrNull(end)
        if (following != null && (following.isLetterOrDigit() || following in "_-@")) {
            return@forEach
        }

        val explicitScheme = candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true)
        val url = if (explicitScheme) candidate else "https://$candidate"
        val parsed = url.toNetworkUrlOrNull() ?: return@forEach
        val scheme = parsed.scheme.lowercase()
        if (scheme != "http" && scheme != "https") return@forEach
        val authority = url.substringAfter("://").substringBefore('/').substringBefore('?')
            .substringBefore('#')
        if ('@' in authority || '\\' in candidate || !PROFILE_HOST.matches(parsed.host)) {
            return@forEach
        }
        if (!explicitScheme && !candidate.startsWith("www.", ignoreCase = true) &&
            parsed.host.substringAfterLast('.').lowercase() !in BARE_PROFILE_LINK_SUFFIXES
        ) {
            return@forEach
        }
        add(UserProfileLink(start = start, end = end, url = url))
    }
}

private fun trimProfileLinkPunctuation(value: String): String {
    var end = value.length
    var countedBrackets = false
    var parentheses = 0
    var squareBrackets = 0
    var braces = 0
    while (end > 0) {
        val last = value[end - 1]
        if (!countedBrackets && (last == ')' || last == ']' || last == '}')) {
            // Count once, only when a closing bracket reaches the suffix. Removed punctuation
            // cannot affect the balance, and each removed closer updates its own count below.
            for (character in value) {
                when (character) {
                    '(' -> parentheses--
                    ')' -> parentheses++
                    '[' -> squareBrackets--
                    ']' -> squareBrackets++
                    '{' -> braces--
                    '}' -> braces++
                }
            }
            countedBrackets = true
        }
        val trim = when (last) {
            '.', ',', ';', ':', '!', '?' -> true
            ')' -> parentheses > 0
            ']' -> squareBrackets > 0
            '}' -> braces > 0
            else -> false
        }
        if (!trim) break
        when (last) {
            ')' -> parentheses--
            ']' -> squareBrackets--
            '}' -> braces--
        }
        end--
    }
    return value.substring(0, end)
}

private val PROFILE_LINK_CANDIDATE = Regex(
    """https?://[^\s<>"']+|(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,63}(?::[0-9]{1,5})?(?:[/?#][^\s<>"']*)?""",
    RegexOption.IGNORE_CASE,
)

private val PROFILE_HOST = Regex(
    """(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}""",
    RegexOption.IGNORE_CASE,
)

// Explicit HTTP(S) and www links can use any valid domain suffix. These common bare suffixes
// avoid turning e.g. Main.kt, Node.js, and notes.txt into websites merely because they have dots.
private val BARE_PROFILE_LINK_SUFFIXES = setOf(
    "com", "org", "net", "edu", "gov", "mil", "int", "io", "dev", "app", "ai", "me",
    "co", "info", "biz", "name", "pro", "xyz", "online", "site", "tech", "blog", "wiki",
    "social", "cloud", "software", "systems", "engineering", "design", "digital", "email",
    "space", "studio", "tools", "work", "world", "network", "website", "page", "link",
    "live", "news", "today", "codes", "computer", "uk", "us", "ca", "au", "nz", "de",
    "fr", "nl", "be", "ch", "at", "se", "no", "dk", "fi", "is", "ie", "it", "es",
    "pt", "eu", "in", "jp", "kr", "cn", "tw", "hk", "sg", "id", "br", "mx", "za",
    "tv", "cc", "to", "fm", "gg", "im", "nu", "sh", "so", "ac", "vc", "lol",
)
