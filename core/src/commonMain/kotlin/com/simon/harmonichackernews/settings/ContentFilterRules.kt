package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.utils.DomainNamePolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.dexpace.kuri.idna.Idn
import org.dexpace.kuri.percent.Percent

enum class TitleFilterMatchMode(val storedValue: String) {
    WORDS_PHRASE("words_phrase"),
    CONTAINS_TEXT("contains_text"),
}

data class TitleFilterRule(
    val text: String,
    val mode: TitleFilterMatchMode = TitleFilterMatchMode.WORDS_PHRASE,
) {
    private val normalizedText = text.trim().lowercase()
    private val normalizedPhrase = collapseFilterWhitespace(normalizedText)

    /**
     * Contains text retains the shipped lowercase-substring behavior. Words/phrase matches a
     * literal phrase surrounded by Unicode word boundaries: letters, numbers, combining marks,
     * and connector punctuation continue a word. Apostrophes between word characters also
     * continue it; hyphens separate words. Punctuation inside the phrase stays literal and runs
     * of Unicode whitespace collapse to a space. This deliberately is not UAX #29 segmentation:
     * contiguous Han text is one word, and accents are neither removed nor normalized away.
     */
    fun matches(title: String): Boolean {
        if (normalizedText.isEmpty()) return false
        val candidate = title.lowercase()
        if (mode == TitleFilterMatchMode.CONTAINS_TEXT) return candidate.contains(normalizedText)
        return matchesPhrase(collapseFilterWhitespace(candidate))
    }

    private fun matchesPhrase(candidate: String): Boolean {
        val phrase = normalizedPhrase
        var start = candidate.indexOf(phrase)
        while (start >= 0) {
            val end = start + phrase.length
            if (!candidate.hasWordCharacterBefore(start) && !candidate.hasWordCharacterAt(end)) {
                return true
            }
            start = candidate.indexOf(phrase, startIndex = start + 1)
        }
        return false
    }

    internal companion object {
        /** A feed title needs each normalization at most once, even with several filter rules. */
        fun matchesAny(title: String, rules: List<TitleFilterRule>): Boolean {
            var normalizedTitle: String? = null
            var normalizedPhrase: String? = null
            for (rule in rules) {
                if (rule.normalizedText.isEmpty()) continue
                val candidate = normalizedTitle ?: title.lowercase().also { normalizedTitle = it }
                val matches = if (rule.mode == TitleFilterMatchMode.CONTAINS_TEXT) {
                    candidate.contains(rule.normalizedText)
                } else {
                    val phrase = normalizedPhrase ?: collapseFilterWhitespace(candidate)
                        .also { normalizedPhrase = it }
                    rule.matchesPhrase(phrase)
                }
                if (matches) return true
            }
            return false
        }
    }
}

data class DomainFilterRule(
    val domain: String,
    val includeSubdomains: Boolean = true,
    val legacyContainsText: Boolean = false,
) {
    private val normalizedDomain = if (legacyContainsText) null else DomainFilterHostPolicy.normalize(domain)
    private val subdomainSuffix = normalizedDomain
        ?.takeIf { includeSubdomains && !DomainFilterHostPolicy.isIpv4(it) }
        ?.let { ".$it" }

    fun matches(url: String): Boolean = if (legacyContainsText) {
        matchesLegacyDomain(DomainNamePolicy.fromUrl(url).orEmpty())
    } else {
        DomainFilterHostPolicy.normalize(url)?.let(::matchesHost) == true
    }

    /** [normalizedHost] comes from [DomainFilterHostPolicy], including its ASCII IDNA conversion. */
    fun matchesHost(normalizedHost: String): Boolean {
        if (legacyContainsText) return matchesLegacyDomain(normalizedHost)
        val configured = normalizedDomain ?: return false
        return normalizedHost == configured ||
            (subdomainSuffix != null && normalizedHost.endsWith(subdomainSuffix))
    }

    /** Older domain filters matched the displayed domain (which omits a leading www.). */
    fun matchesLegacyDomain(displayDomain: String): Boolean {
        if (!legacyContainsText) return false
        val needle = domain.trim().lowercase()
        return needle.isNotEmpty() && displayDomain.lowercase().contains(needle)
    }
}

/** One host policy for pasted filter input, illustrative examples, and actual story URLs. */
object DomainFilterHostPolicy {
    /**
     * Accepts HTTP(S) URLs or bare hosts, optionally with a port/path. Credentials, backslashes,
     * malformed ports, and IPv6 are rejected. Ports, paths, queries, and fragments do not narrow
     * a domain rule. www remains an ordinary subdomain; a single DNS trailing dot is ignored.
     * Kuri's shared, nontransitional UTS #46 pipeline keeps Unicode/Punycode behavior identical
     * on Android, iOS, and desktop. Strict DNS label checks run after that web-compatible mapping.
     */
    fun normalize(input: String): String? {
        val value = input.trim()
        if (value.isEmpty() || value.length > MAX_INPUT_LENGTH ||
            value.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f || it == '\\' }
        ) return null
        val firstPathOrQuery = value.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val schemeSeparator = value.indexOf("://").takeIf {
            it >= 0 && (firstPathOrQuery < 0 || it < firstPathOrQuery)
        } ?: -1
        val remainder = when {
            schemeSeparator >= 0 -> {
                val scheme = value.substring(0, schemeSeparator)
                if (!scheme.equals("http", ignoreCase = true) &&
                    !scheme.equals("https", ignoreCase = true)
                ) return null
                value.substring(schemeSeparator + 3)
            }
            value.startsWith("//") -> value.substring(2)
            else -> value
        }
        val authority = remainder.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty() || '@' in authority || '[' in authority || ']' in authority) return null
        val colon = authority.indexOf(':')
        val host = if (colon >= 0) {
            if (colon != authority.lastIndexOf(':')) return null
            val port = authority.substring(colon + 1)
            val portNumber = port.toIntOrNull() ?: return null
            if (port.isEmpty() || port.any { it !in '0'..'9' } || portNumber !in 0..65535) {
                return null
            }
            authority.substring(0, colon)
        } else {
            authority
        }
        val decodedHost = Percent.decode(host)
        val ascii = Idn.toAscii(decodedHost).getOrNull()?.lowercase() ?: return null
        val canonical = ascii.removeSuffix(".")
        if (canonical.isEmpty() || canonical.length > MAX_HOST_LENGTH) return null
        val labels = canonical.split('.')
        if (labels.any { !isValidLabel(it) }) return null
        if (canonical.all { it in '0'..'9' || it == '.' } && !isIpv4(canonical)) return null
        return canonical
    }

    fun isIpv4(host: String): Boolean {
        val pieces = host.split('.')
        return pieces.size == 4 && pieces.all { piece ->
            piece.isNotEmpty() && piece.all { it in '0'..'9' } &&
                (piece.length == 1 || piece.first() != '0') &&
                piece.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun isValidLabel(label: String): Boolean {
        if (label.length !in 1..63 || label.first() == '-' || label.last() == '-' ||
            label.any { it !in 'a'..'z' && it !in '0'..'9' && it != '-' }
        ) return false
        if (!label.startsWith("xn--")) return true
        // The public WHATWG converter keeps invalid all-ASCII A-labels for web compatibility.
        // Decode and round-trip them explicitly so a saved rule always names a valid IDN label.
        val unicode = Idn.toUnicode(label)
        return unicode != label && Idn.toAscii(unicode).getOrNull() == label
    }

    private const val MAX_INPUT_LENGTH = 8192
    private const val MAX_HOST_LENGTH = 253
}

/** Versioned storage; null signals an absent or invalid document, never a request to erase rules. */
object ContentFilterRuleCodec {
    fun encodeTitleRules(rules: List<TitleFilterRule>): String = envelope(
        rules.map { rule ->
            require(rule.text.isNotBlank()) { "A title filter must contain text" }
            buildJsonObject {
                put("text", rule.text.trim())
                put("mode", rule.mode.storedValue)
            }
        },
    )

    fun decodeTitleRules(serialized: String?): List<TitleFilterRule>? {
        val rules = decodeEnvelope(serialized) ?: return null
        return rules.map { entry ->
            val value = entry as? JsonObject ?: return null
            val text = value.string("text")?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val modeValue = value.string("mode") ?: return null
            val mode = TitleFilterMatchMode.entries.firstOrNull { it.storedValue == modeValue } ?: return null
            TitleFilterRule(text, mode)
        }.distinctBy { rule -> rule.mode to rule.text.lowercase() }
    }

    fun encodeDomainRules(rules: List<DomainFilterRule>): String = envelope(
        rules.map { rule ->
            val domain = if (rule.legacyContainsText) rule.domain.trim() else {
                requireNotNull(DomainFilterHostPolicy.normalize(rule.domain)) { "Invalid domain filter" }
            }
            require(domain.isNotEmpty()) { "A domain filter must contain text" }
            buildJsonObject {
                put("domain", domain)
                put("includeSubdomains", rule.includeSubdomains)
                put("legacyContainsText", rule.legacyContainsText)
            }
        },
    )

    fun decodeDomainRules(serialized: String?): List<DomainFilterRule>? {
        val rules = decodeEnvelope(serialized) ?: return null
        return rules.map { entry ->
            val value = entry as? JsonObject ?: return null
            val rawDomain = value.string("domain")?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val includeSubdomains = value.boolean("includeSubdomains") ?: return null
            val legacyContainsText = value.boolean("legacyContainsText") ?: return null
            val domain = if (legacyContainsText) rawDomain else {
                DomainFilterHostPolicy.normalize(rawDomain) ?: return null
            }
            DomainFilterRule(domain, includeSubdomains, legacyContainsText)
        }.distinctBy { rule -> Triple(rule.domain.lowercase(), rule.includeSubdomains, rule.legacyContainsText) }
    }

    private fun envelope(rules: List<JsonObject>): String = buildJsonObject {
        put("version", VERSION)
        put("rules", buildJsonArray { rules.forEach(::add) })
    }.toString()

    private fun decodeEnvelope(serialized: String?): JsonArray? {
        if (serialized.isNullOrBlank()) return null
        val value = runCatching { Json.parseToJsonElement(serialized) as? JsonObject }.getOrNull() ?: return null
        val version = value["version"] as? JsonPrimitive ?: return null
        if (version.isString || version.intOrNull != VERSION) return null
        return value["rules"] as? JsonArray
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.boolean(key: String): Boolean? =
        (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

    private const val VERSION = 1
}

private fun collapseFilterWhitespace(text: String): String = buildString(text.length) {
    var pendingSpace = false
    for (character in text) {
        if (character.isWhitespace()) {
            pendingSpace = isNotEmpty()
        } else {
            if (pendingSpace) append(' ')
            append(character)
            pendingSpace = false
        }
    }
}

private fun String.hasWordCharacterBefore(index: Int): Boolean =
    index > 0 && hasWordCharacterAt(previousCodePointIndex(index))

private fun String.hasWordCharacterAt(index: Int): Boolean {
    if (index !in indices) return false
    val end = nextCodePointIndex(index)
    if (ContentFilterWordCharacters.contains(filterCodePointAt(index))) return true
    if (this[index] != '\'' && this[index] != '\u2019') return false
    // Quotation marks delimit a word. An apostrophe joining two word characters continues it.
    if (index == 0 || end == length) return false
    return ContentFilterWordCharacters.contains(filterCodePointAt(previousCodePointIndex(index))) &&
        ContentFilterWordCharacters.contains(filterCodePointAt(end))
}

private fun String.filterCodePointAt(index: Int): Int {
    val first = this[index]
    return if (first.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
        0x10000 + ((first.code - 0xD800) shl 10) + (this[index + 1].code - 0xDC00)
    } else {
        first.code
    }
}

private fun String.previousCodePointIndex(index: Int): Int =
    if (index >= 2 && this[index - 1].isLowSurrogate() && this[index - 2].isHighSurrogate()) index - 2 else index - 1

private fun String.nextCodePointIndex(index: Int): Int =
    if (index + 1 < length && this[index].isHighSurrogate() && this[index + 1].isLowSurrogate()) index + 2 else index + 1
