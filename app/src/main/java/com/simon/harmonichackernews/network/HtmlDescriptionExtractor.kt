package com.simon.harmonichackernews.network

import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.min
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Conservative fallback extraction for pages whose description metadata is not useful.  */
internal object HtmlDescriptionExtractor {
    private const val MIN_DESCRIPTION_CHARS = 32
    private const val MIN_LETTER_CHARS = 20
    private const val MIN_LATIN_WORDS = 5
    private const val MAX_CANDIDATE_CHARS = 600
    private val WORD_PATTERN: Pattern = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’_-]*")
    private val SENTENCE_PUNCTUATION_PATTERN: Pattern = Pattern.compile("[.!?。！？]")
    private val POSITIVE_CONTAINER_PATTERN: Pattern = Pattern.compile(
        "(?:^|[-_\\s])(article|articlebody|article-body|body|content|entry|main|post|story|text)"
                + "(?:$|[-_\\s])",
        Pattern.CASE_INSENSITIVE
    )
    private val NEGATIVE_CONTAINER_PATTERN: Pattern = Pattern.compile(
        ("(?:^|[-_\\s])(ad|advert|author|bio|breadcrumb|caption|comment|comments|consent|cookie|"
                + "credit|date|dialog|footer|header|login|menu|meta|modal|nav|newsletter|popup|"
                + "promo|recommend|related|reply|share|sidebar|signup|social|subscribe|tag|widget)"
                + "(?:$|[-_\\s])"),
        Pattern.CASE_INSENSITIVE
    )
    private val BOILERPLATE_PATTERN: Pattern = Pattern.compile(
        ("^(advertisement|all rights reserved|click here|enable javascript|home|homepage|loading|"
                + "log in|read more|sign in|sign up|skip to|subscribe|welcome)(?:[.!\\s]|$)|"
                + "^(please enable (?:java ?script|js)|this site uses cookies|we use cookies|your browser)|"
                + "^by .{0,120}\\bisbn\\b"),
        Pattern.CASE_INSENSITIVE
    )
    private val MARKUP_OR_STYLE_PATTERN: Pattern = Pattern.compile(
        "<[a-z][^>]*>|\\{[^}]{0,160}:|(?:^|[;{])\\s*[a-z-]{2,}\\s*:",
        Pattern.CASE_INSENSITIVE
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
        if (cleaned.length < MIN_DESCRIPTION_CHARS || countLetters(cleaned) < MIN_LETTER_CHARS || BOILERPLATE_PATTERN.matcher(
                cleaned
            ).find()
            || MARKUP_OR_STYLE_PATTERN.matcher(cleaned).find()
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
        for (paragraph in document.select("p")) {
            val text = clean(paragraph.text())
            if (!isUsableParagraph(paragraph, text, pageTitle, fallbackTitle)) {
                paragraphIndex++
                continue
            }

            val score = scoreParagraph(paragraph, text, paragraphIndex)
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
            if (isExcluded(container)) {
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
        fallbackTitle: String?
    ): Boolean {
        if (!isMeaningful(text, pageTitle, fallbackTitle) || isExcluded(paragraph)) {
            return false
        }

        var linkedChars = 0
        for (link in paragraph.select("a")) {
            linkedChars += clean(link.text()).length
        }
        return linkedChars <= text.length * 0.35f
    }

    private fun isExcluded(element: Element?): Boolean {
        var current = element
        while (current != null) {
            val tag = current.tagName()
            if ("aside" == tag
                || "code" == tag
                || "dialog" == tag
                || "footer" == tag
                || "form" == tag
                || "header" == tag
                || "li" == tag
                || "nav" == tag
                || "pre" == tag
            ) {
                return true
            }

            val identifiers = current.id() + " " + current.className()
            if (NEGATIVE_CONTAINER_PATTERN.matcher(identifiers).find()
                || current.hasAttr("hidden")
                || "true".equals(current.attr("aria-hidden"), ignoreCase = true)
            ) {
                return true
            }
            val style = current.attr("style").lowercase().replace(" ", "")
            if (style.contains("display:none") || style.contains("visibility:hidden")) {
                return true
            }
            current = current.parent()
        }
        return false
    }

    private fun scoreParagraph(paragraph: Element, text: String, paragraphIndex: Int): Int {
        var score = min(text.length, 240) / 4
        if (SENTENCE_PUNCTUATION_PATTERN.matcher(text).find()) {
            score += 20
        }

        var positiveContainerFound = false
        var current = paragraph.parent()
        while (current != null) {
            if ("article" == current.tagName()
                || current.hasAttr("itemprop")
                && current.attr("itemprop").lowercase().contains("articlebody")
            ) {
                score += 500
                positiveContainerFound = true
                break
            }
            if ("main" == current.tagName() || "main".equals(
                    current.attr("role"),
                    ignoreCase = true
                )
            ) {
                score += 400
                positiveContainerFound = true
                break
            }
            val identifiers = current.id() + " " + current.className()
            if (POSITIVE_CONTAINER_PATTERN.matcher(identifiers).find()) {
                positiveContainerFound = true
            }
            current = current.parent()
        }
        if (positiveContainerFound) {
            score += 250
        }

        return score - min(paragraphIndex, 100) * 3
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
                value.substring(cleanedTitle.length).replaceFirst("^[|:–—\\-\\s]+".toRegex(), "")
            )
        }
        return value
    }

    private fun withoutProviderBoilerplate(value: String): String {
        return clean(
            value.replaceFirst(
                "(?i)\\.?\\s*Contribute to .+ development by creating an account on GitHub\\.?$".toRegex(),
                ""
            )
        )
    }

    private fun normalizeComparable(value: String?): String {
        return clean(value).lowercase().replace("[^\\p{L}\\p{N}]+".toRegex(), "")
    }

    private fun countWords(value: String): Int {
        var count = 0
        val matcher = WORD_PATTERN.matcher(value)
        while (matcher.find()) {
            count++
        }
        return count
    }

    private fun countLetters(value: String): Int {
        var count = 0
        for (i in 0..<value.length) {
            if (Character.isLetter(value.get(i))) {
                count++
            }
        }
        return count
    }

    private fun countLatinLetters(value: String): Int {
        var count = 0
        for (i in 0..<value.length) {
            if (Character.UnicodeScript.of(value.get(i).code) == Character.UnicodeScript.LATIN) {
                count++
            }
        }
        return count
    }

    private fun clean(value: String?): String {
        return if (value == null) "" else value.replace('\u00a0', ' ')
            .replace("\\s+".toRegex(), " ").trim { it <= ' ' }
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
