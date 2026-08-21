package com.simon.harmonichackernews.network

import kotlin.math.min
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element

/** Conservative fallback extraction for pages whose description metadata is not useful.  */
object HtmlDescriptionExtractor {
    private const val MIN_DESCRIPTION_CHARS = 32
    private const val MIN_LETTER_CHARS = 20
    private const val MIN_LATIN_WORDS = 5
    private const val MAX_CANDIDATE_CHARS = 600
    private val LEADING_TITLE_SEPARATOR_PATTERN = Regex("^[|:–—\\-\\s]+")
    private val GITHUB_CONTRIBUTION_PATTERN = Regex(
        "(?i)\\.?\\s*Contribute to .+ development by creating an account on GitHub\\.?$",
    )
    private val POSITIVE_CONTAINER_PATTERN = Regex(
        "(?:^|[-_\\s])(article|articlebody|article-body|body|content|entry|main|post|story|text)"
                + "(?:$|[-_\\s])",
        RegexOption.IGNORE_CASE,
    )
    private val NEGATIVE_CONTAINER_PATTERN = Regex(
        ("(?:^|[-_\\s])(ad|advert|author|bio|breadcrumb|caption|comment|comments|consent|cookie|"
                + "credit|date|dialog|footer|header|login|menu|meta|modal|nav|newsletter|popup|"
                + "promo|recommend|related|reply|share|sidebar|signup|social|subscribe|tag|widget)"
                + "(?:$|[-_\\s])"),
        RegexOption.IGNORE_CASE,
    )
    private val BOILERPLATE_PATTERN = Regex(
        ("^(advertisement|all rights reserved|click here|enable javascript|home|homepage|loading|"
                + "log in|read more|sign in|sign up|skip to|subscribe|welcome)(?:[.!\\s]|$)|"
                + "^(please enable (?:java ?script|js)|this site uses cookies|we use cookies|your browser)|"
                + "^by .{0,120}\\bisbn\\b"),
        RegexOption.IGNORE_CASE,
    )
    private val MARKUP_OR_STYLE_PATTERN = Regex(
        "<[a-z][^>]*>|\\{[^}]{0,160}:|(?:^|[;{])\\s*[a-z-]{2,}\\s*:",
        RegexOption.IGNORE_CASE,
    )

    fun chooseDescription(
        metadataDescription: String?,
        document: Document,
        pageTitle: String?,
        fallbackTitle: String?
    ): String {
        val cleanedMetadata = clean(metadataDescription)
        if (isMeaningful(cleanedMetadata, pageTitle, fallbackTitle)) {
            return cleanedMetadata
        }

        val extracted = extract(document, pageTitle, fallbackTitle)
        return if (extracted.isEmpty()) cleanedMetadata else extracted
    }

    fun isMeaningful(value: String?, pageTitle: String?, fallbackTitle: String?): Boolean {
        val cleaned = clean(value)
        val qualityText = withoutProviderBoilerplate(cleaned)
        if (cleaned.length < MIN_DESCRIPTION_CHARS || countLetters(cleaned) < MIN_LETTER_CHARS
            || BOILERPLATE_PATTERN.containsMatchIn(cleaned)
            || MARKUP_OR_STYLE_PATTERN.containsMatchIn(cleaned)
            || duplicatesTitle(qualityText, pageTitle)
            || duplicatesTitle(qualityText, fallbackTitle)
        ) {
            return false
        }

        val letterCount = countLetters(cleaned)
        val latinLetterCount = countLatinLetters(cleaned)
        return latinLetterCount * 2 < letterCount || countWords(cleaned) >= MIN_LATIN_WORDS
    }

    private fun extract(document: Document, pageTitle: String?, fallbackTitle: String?): String {
        var bestParagraph: Element? = null
        var bestText = ""
        var bestScore = Int.MIN_VALUE
        var paragraphIndex = 0
        val excludedCache = mutableMapOf<Element, Boolean>()
        val containerScoreCache = mutableMapOf<Element, Int>()
        for (paragraph in document.select("p")) {
            val text = clean(paragraph.text())
            if (!isUsableParagraph(
                    paragraph,
                    text,
                    pageTitle,
                    fallbackTitle,
                    excludedCache,
                )
            ) {
                paragraphIndex++
                continue
            }

            val score = scoreParagraph(paragraph, text, paragraphIndex, containerScoreCache)
            if (score > bestScore) {
                bestParagraph = paragraph
                bestText = text
                bestScore = score
            }
            paragraphIndex++
        }
        if (bestParagraph != null) {
            return truncate(bestText)
        }

        for (container in document.select("[itemprop~=articleBody], article, main, [role=main]")) {
            if (isExcluded(container, excludedCache)) {
                continue
            }
            val text = withoutLeadingTitle(clean(container.text()), pageTitle)
            if (isMeaningful(text, pageTitle, fallbackTitle)) {
                return truncate(text)
            }
        }
        return ""
    }

    private fun isUsableParagraph(
        paragraph: Element,
        text: String,
        pageTitle: String?,
        fallbackTitle: String?,
        excludedCache: MutableMap<Element, Boolean>,
    ): Boolean {
        if (!isMeaningful(text, pageTitle, fallbackTitle) ||
            isExcluded(paragraph, excludedCache)
        ) {
            return false
        }

        var linkedChars = 0
        for (link in paragraph.select("a")) {
            linkedChars += clean(link.text()).length
        }
        return linkedChars <= text.length * 0.35f
    }

    private fun isExcluded(
        element: Element,
        cache: MutableMap<Element, Boolean>,
    ): Boolean {
        cache[element]?.let { return it }
        val tag = element.tagName()
        val identifiers = element.id() + " " + element.className()
        val style = element.attr("style").lowercase().replace(" ", "")
        val excluded = tag == "aside" || tag == "code" || tag == "dialog" ||
            tag == "footer" || tag == "form" || tag == "header" || tag == "li" ||
            tag == "nav" || tag == "pre" ||
            NEGATIVE_CONTAINER_PATTERN.containsMatchIn(identifiers) ||
            element.hasAttr("hidden") ||
            "true".equals(element.attr("aria-hidden"), ignoreCase = true) ||
            style.contains("display:none") || style.contains("visibility:hidden") ||
            element.parent()?.let { isExcluded(it, cache) } == true
        cache[element] = excluded
        return excluded
    }

    private fun scoreParagraph(
        paragraph: Element,
        text: String,
        paragraphIndex: Int,
        containerScoreCache: MutableMap<Element, Int>,
    ): Int {
        var score = min(text.length, 240) / 4
        if (text.any { it == '.' || it == '!' || it == '?' || it == '。' || it == '！' || it == '？' }) {
            score += 20
        }
        score += paragraph.parent()?.let { positiveContainerScore(it, containerScoreCache) } ?: 0
        return score - min(paragraphIndex, 100) * 3
    }

    private fun positiveContainerScore(
        element: Element,
        cache: MutableMap<Element, Int>,
    ): Int {
        cache[element]?.let { return it }
        val score = when {
            element.tagName() == "article" ||
                element.attr("itemprop").contains("articlebody", ignoreCase = true) -> 750
            element.tagName() == "main" ||
                element.attr("role").equals("main", ignoreCase = true) -> 650
            else -> {
                val ancestorScore = element.parent()?.let {
                    positiveContainerScore(it, cache)
                } ?: 0
                if (ancestorScore > 0 || POSITIVE_CONTAINER_PATTERN.containsMatchIn(
                        element.id() + " " + element.className(),
                    )
                ) maxOf(ancestorScore, 250) else 0
            }
        }
        cache[element] = score
        return score
    }

    private fun duplicatesTitle(value: String?, pageTitle: String?): Boolean {
        val normalizedValue = normalizeComparable(value)
        val normalizedTitle = normalizeComparable(pageTitle)
        return !normalizedTitle.isEmpty()
                && (normalizedValue == normalizedTitle
                || normalizedValue.startsWith(normalizedTitle)
                && normalizedValue.length - normalizedTitle.length < 16)
    }

    private fun withoutLeadingTitle(value: String, pageTitle: String?): String {
        val cleanedTitle = clean(pageTitle)
        if (!cleanedTitle.isEmpty()
            && value.regionMatches(0, cleanedTitle, 0, cleanedTitle.length, ignoreCase = true)
        ) {
            return clean(
                value.substring(cleanedTitle.length)
                    .replaceFirst(LEADING_TITLE_SEPARATOR_PATTERN, "")
            )
        }
        return value
    }

    private fun withoutProviderBoilerplate(value: String): String {
        return clean(
            value.replaceFirst(GITHUB_CONTRIBUTION_PATTERN, "")
        )
    }

    private fun normalizeComparable(value: String?): String {
        val cleaned = clean(value)
        return buildString(cleaned.length) {
            cleaned.forEach { character ->
                if (character.isLetterOrDigit()) append(character.lowercaseChar())
            }
        }
    }

    private fun countWords(value: String): Int {
        var count = 0
        var inWord = false
        for (character in value) {
            when {
                character.isLetterOrDigit() -> {
                    if (!inWord) count++
                    inWord = true
                }
                inWord && (character == '\'' || character == '’' ||
                    character == '_' || character == '-') -> Unit
                else -> inWord = false
            }
        }
        return count
    }

    private fun countLetters(value: String): Int {
        var count = 0
        for (i in 0..<value.length) {
            if (value[i].isLetter()) {
                count++
            }
        }
        return count
    }

    private fun countLatinLetters(value: String): Int {
        var count = 0
        for (i in 0..<value.length) {
            val code = value[i].code
            if (code in 0x0041..0x024f || code in 0x1e00..0x1eff || code in 0xab30..0xab6f) {
                count++
            }
        }
        return count
    }

    private fun clean(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val result = StringBuilder(value.length)
        var pendingSpace = false
        for (source in value) {
            val character = if (source == '\u00a0') ' ' else source
            if (character.isWhitespace()) {
                pendingSpace = result.isNotEmpty()
            } else {
                if (pendingSpace) result.append(' ')
                result.append(character)
                pendingSpace = false
            }
        }
        return result.toString()
    }

    private fun truncate(value: String?): String {
        val cleaned = clean(value)
        if (cleaned.length <= MAX_CANDIDATE_CHARS) {
            return cleaned
        }
        val lastSpace = cleaned.lastIndexOf(' ', MAX_CANDIDATE_CHARS - 1)
        val end = if (lastSpace >= MAX_CANDIDATE_CHARS * 0.75f) lastSpace else MAX_CANDIDATE_CHARS
        return cleaned.substring(0, end).trim { it <= ' ' } + "…"
    }
}
