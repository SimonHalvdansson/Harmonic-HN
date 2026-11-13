@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.network.toNetworkUrlOrNull
import com.simon.harmonichackernews.ui.comments.summaryMarkdownAnnotatedString
import com.simon.harmonichackernews.ui.content.SearchHighlightRun
import com.simon.harmonichackernews.ui.content.SearchHighlightTransition
import com.simon.harmonichackernews.ui.content.searchMatchRanges
import org.junit.Rule
import org.junit.Test
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Frozen pre-change helpers versus production; fixtures and parity checks are outside timing. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UiHelperAllocationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var result: Any? = null

    private val prose = "The reader preserves every sentence and displays the same content after each update. ".repeat(48)
    private val formatted = "A **bold statement** with _emphasis_ and `code`, then ordinary prose and [details](https://example.org). ".repeat(40)

    @Test fun summaryProseBefore() = measureSummary(prose, before = true)
    @Test fun summaryProseOptimized() = measureSummary(prose, before = false)
    @Test fun summaryFormattedBefore() = measureSummary(formatted, before = true)
    @Test fun summaryFormattedOptimized() = measureSummary(formatted, before = false)
    @Test fun searchRetarget1Before() = measureSearch(1, before = true)
    @Test fun searchRetarget1Optimized() = measureSearch(1, before = false)
    @Test fun searchRetarget24Before() = measureSearch(24, before = true)
    @Test fun searchRetarget24Optimized() = measureSearch(24, before = false)
    @Test fun searchRetarget96Before() = measureSearch(96, before = true)
    @Test fun searchRetarget96Optimized() = measureSearch(96, before = false)

    private fun measureSummary(markdown: String, before: Boolean) {
        check(legacySummaryMarkdownAnnotatedString(markdown) == summaryMarkdownAnnotatedString(markdown))
        if (before) benchmarkRule.measureRepeated { result = legacySummaryMarkdownAnnotatedString(markdown) }
        else benchmarkRule.measureRepeated { result = summaryMarkdownAnnotatedString(markdown) }
    }

    private fun measureSearch(count: Int, before: Boolean) {
        val body = buildAnnotatedString {
            repeat(count) {
                val start = length
                append("A matching needle and nearby context. ")
                addStyle(SpanStyle(fontWeight = FontWeight.Light), start, length)
                addStyle(SpanStyle(fontWeight = FontWeight.Medium), start + 4, start + 14)
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start + 11, start + 11)
            }
        }
        val previous: SearchHighlightTransition = SearchHighlightTransition().retarget(body, "matching needle", 0f)
        val expected: SearchHighlightTransition = legacyRetarget(previous.runs, body, "needle", 0.4f)
        check(expected.runs == previous.retarget(body, "needle", 0.4f).runs)
        if (before) benchmarkRule.measureRepeated {
            result = legacyRetarget(previous.runs, body, "needle", 0.4f)
        } else benchmarkRule.measureRepeated {
            result = previous.retarget(body, "needle", 0.4f)
        }
    }
}

private fun legacyRetarget(
    runs: List<SearchHighlightRun>,
    body: AnnotatedString,
    searchTerm: String,
    progress: Float,
): SearchHighlightTransition {
        val matches = searchMatchRanges(body.text, searchTerm)
        // Ordinary comments have no search animation. Their formatting cannot create a run.
        if (runs.isEmpty() && matches.isEmpty()) return SearchHighlightTransition()
        val boundaries = buildSet {
            runs.forEach { add(it.start); add(it.end) }
            matches.forEach { add(it.start); add(it.end) }
            body.spanStyles.forEach { span ->
                if (span.item.fontWeight != null) { add(span.start); add(span.end) }
            }
        }.sorted()
        var previousIndex = 0
        var matchIndex = 0
        return SearchHighlightTransition(buildList {
            for (index in 0 until boundaries.lastIndex) {
                val start = boundaries[index]
                val end = boundaries[index + 1]
                while (previousIndex < runs.size && runs[previousIndex].end <= start) previousIndex++
                while (matchIndex < matches.size && matches[matchIndex].end <= start) matchIndex++
                val from = runs.getOrNull(previousIndex)?.takeIf { start >= it.start }?.amount(progress) ?: 0f
                val to = if (matches.getOrNull(matchIndex)?.let { start >= it.start } == true) 1f else 0f
                if (from > SearchHighlightThreshold || to > 0f) {
                    add(SearchHighlightRun(start, end, from, to, body.baseFontWeightAt(start)))
                }
            }
        })
}

private fun AnnotatedString.baseFontWeightAt(index: Int): FontWeight = spanStyles
    .lastOrNull { range -> index >= range.start && index < range.end && range.item.fontWeight != null }
    ?.item
    ?.fontWeight
    ?: FontWeight.Normal

private const val SearchHighlightThreshold = 0.001f

private fun legacySummaryMarkdownAnnotatedString(
    markdown: String,
    linkColor: Color = Color.Unspecified,
    enableBoldFormatting: Boolean = true,
    baseUrl: String? = null,
    onOpenLink: ((String) -> Unit)? = null,
): AnnotatedString = if (markdown.length > MAX_FORMATTED_MARKDOWN_CHARS) {
    // Preserve all text while bounding optional formatting of unexpectedly large provider output.
    AnnotatedString(markdown)
} else buildAnnotatedString {
    val lines = compactSummaryMarkdownListSpacing(
        markdown.stripMarkdownHtmlComments().trim().lines(),
    )
    var emittedLine = false
    var inFencedCodeBlock = false
    lines.forEach { sourceLine ->
        val line = sourceLine.trimEnd()
        val trimmedStart = line.trimStart()
        if (trimmedStart.startsWith("```")) {
            inFencedCodeBlock = !inFencedCodeBlock
            return@forEach
        }
        if (emittedLine) append('\n')
        emittedLine = true
        if (inFencedCodeBlock) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(line) }
            return@forEach
        }
        val bulletItem = trimmedStart.markdownBulletItemContent()
        val numberedItem = trimmedStart.markdownNumberedItemContent()
        when {
            bulletItem != null -> {
                val task = bulletItem.markdownTaskItemContent()
                append(task?.first ?: "• ")
                appendSummaryMarkdownInline(
                    task?.second ?: bulletItem,
                    linkColor,
                    enableBoldFormatting,
                )
            }

            numberedItem != null -> {
                val (number, content) = numberedItem
                append(number)
                append(". ")
                appendSummaryMarkdownInline(content, linkColor, enableBoldFormatting)
            }

            trimmedStart.startsWith("#") -> {
                val heading = trimmedStart.trimStart('#').trimStart()
                if (enableBoldFormatting) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendSummaryMarkdownInline(heading, linkColor, true)
                    }
                } else {
                    appendSummaryMarkdownInline(heading, linkColor, false)
                }
            }

            trimmedStart.startsWith("> ") -> {
                val quote = trimmedStart.drop(2)
                val alert = quote.markdownAlertLabel()
                if (alert != null) {
                    if (enableBoldFormatting) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(alert) }
                    } else {
                        append(alert)
                    }
                } else {
                    append("› ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendSummaryMarkdownInline(quote, linkColor, enableBoldFormatting)
                    }
                }
            }

            else -> appendSummaryMarkdownInline(
                line.stripMarkdownHtmlTags(),
                linkColor,
                enableBoldFormatting,
            )
        }
    }
}.let { parsed ->
    val links = parsed.getLinkAnnotations(0, parsed.length)
    if (links.isEmpty()) return@let parsed
    val parsedBaseUrl = baseUrl?.toNetworkUrlOrNull()
    // Rebind parsed links after rendering so nested emphasis, lists and headings all use
    // the same base URL and host routing, without retaining a platform URI handler.
    buildAnnotatedString {
        append(AnnotatedString(parsed.text, parsed.spanStyles, parsed.paragraphStyles))
        links.forEach { range ->
            val link = range.item as? LinkAnnotation.Url ?: return@forEach
            val url = parsedBaseUrl?.resolve(link.url)?.toString() ?: link.url
            addLink(
                LinkAnnotation.Url(
                    url = url,
                    styles = link.styles,
                    linkInteractionListener = onOpenLink?.let { open ->
                        LinkInteractionListener { open(url) }
                    },
                ),
                range.start,
                range.end,
            )
        }
    }
}

private fun compactSummaryMarkdownListSpacing(lines: List<String>): List<String> = buildList {
    var index = 0
    var previous: String? = null
    while (index < lines.size) {
        val line = lines[index]
        if (line.isNotBlank()) {
            add(line)
            previous = line
            index++
            continue
        }
        // Inspect each blank run once and retain its first original line when spacing survives.
        do {
            index++
        } while (index < lines.size && lines[index].isBlank())
        val next = lines.getOrNull(index)
        val compactListGap = previous?.isMarkdownListItem() == true &&
            next?.isMarkdownListItem() == true
        val compactHeadingGap = previous?.isMarkdownHeading() == true
        if (!compactListGap && !compactHeadingGap) add(line)
    }
}

private fun String.isMarkdownListItem(): Boolean {
    val trimmed = trimStart()
    return trimmed.markdownBulletItemContent() != null ||
        trimmed.markdownNumberedItemContent() != null
}

private fun String.isMarkdownHeading(): Boolean = trimStart().let { trimmed ->
    trimmed.startsWith('#') && trimmed.dropWhile { it == '#' }.startsWith(' ')
}

private fun String.markdownBulletItemContent(): String? = when {
    startsWith("- ") || startsWith("* ") || startsWith("+ ") || startsWith("• ") -> drop(2)
    else -> null
}

private fun String.markdownNumberedItemContent(): Pair<String, String>? {
    val delimiter = indexOf(". ")
    if (delimiter <= 0 || !substring(0, delimiter).all(Char::isDigit)) return null
    return substring(0, delimiter) to substring(delimiter + 2)
}

private fun String.markdownTaskItemContent(): Pair<String, String>? = when {
    startsWith("[x] ", ignoreCase = true) -> "☑ " to drop(4)
    startsWith("[ ] ") -> "☐ " to drop(4)
    else -> null
}

private fun String.markdownAlertLabel(): String? {
    if (!startsWith("[!") || !endsWith(']')) return null
    return substring(2, length - 1)
        .lowercase()
        .replaceFirstChar(Char::uppercase)
}

private fun String.stripMarkdownHtmlComments(): String {
    val first = indexOf("<!--")
    if (first < 0) return this
    val source = this
    return buildString(length) {
        var copiedThrough = 0
        var start = first
        while (start >= 0) {
            val end = source.indexOf("-->", start + 4)
            if (end < 0) break // No later opener can close either; retain the unfinished text.
            append(source, copiedThrough, start)
            copiedThrough = end + 3
            start = source.indexOf("<!--", copiedThrough)
        }
        append(source, copiedThrough, source.length)
    }
}

/** Each candidate ends at the next angle bracket; failed or unfinished tags are never rescanned. */
private fun String.stripMarkdownHtmlTags(): String {
    val first = indexOf('<')
    if (first < 0) return this
    val source = this
    return buildString(length) {
        var copiedThrough = 0
        var index = first
        while (index < source.length) {
            if (source[index] != '<') {
                index++
                continue
            }
            var position = index + 1
            if (position < source.length && source[position] == '/') position++
            if (position == source.length || !source[position].isAsciiLetter()) {
                index++
                continue
            }
            position++
            while (position < source.length &&
                (source[position].isAsciiLetter() || source[position] in '0'..'9' || source[position] == '-')
            ) position++
            if (position < source.length && source[position].isWhitespace()) {
                while (position < source.length && source[position] != '<' && source[position] != '>') position++
            } else if (position < source.length && source[position] == '/') {
                position++
            }
            if (position < source.length && source[position] == '>') {
                append(source, copiedThrough, index)
                copiedThrough = position + 1
                index = copiedThrough
            } else {
                index = position
            }
        }
        append(source, copiedThrough, source.length)
    }
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private val MARKDOWN_AUTOLINK = Regex("[A-Za-z][A-Za-z0-9+.-]{1,31}:[^<>\\s]*")
private val MARKDOWN_EMAIL_AUTOLINK = Regex("[^<>\\s@]+@[^<>\\s@]+\\.[^<>\\s@]+")

private fun String.markdownLinkEnd(start: Int): Int {
    var depth = 0
    var index = start
    while (index < length) {
        when (this[index]) {
            '\\' -> if (index + 1 < length && this[index + 1] in MARKDOWN_ESCAPABLE_CHARACTERS) index++
            '(' -> depth++
            ')' -> if (depth == 0) return index else depth--
        }
        index++
    }
    return -1
}

private fun String.unescapeMarkdownDestination(): String = buildString {
    var index = 0
    while (index < this@unescapeMarkdownDestination.length) {
        val char = this@unescapeMarkdownDestination[index]
        if (char == '\\' && index + 1 < this@unescapeMarkdownDestination.length &&
            this@unescapeMarkdownDestination[index + 1] in MARKDOWN_ESCAPABLE_CHARACTERS) index++
        append(this@unescapeMarkdownDestination[index++])
    }
}

private fun AnnotatedString.Builder.appendSummaryMarkdownInline(
    source: String,
    linkColor: Color,
    enableBoldFormatting: Boolean,
) {
    var index = 0
    var nextLinkLabelEnd = -2
    var nextLinkDestinationEnd = -1
    while (index < source.length) {
        when {
            source[index] == '<' -> {
                var end = index + 1
                while (end < source.length && source[end] != '<' && source[end] != '>' && !source[end].isWhitespace()) end++
                val label = if (end < source.length && source[end] == '>') source.substring(index + 1, end) else ""
                val url = when {
                    MARKDOWN_AUTOLINK.matches(label) -> label
                    MARKDOWN_EMAIL_AUTOLINK.matches(label) -> "mailto:$label"
                    else -> null
                }
                if (url == null) {
                    append('<')
                    index++
                } else {
                    val style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(style))) { append(label) }
                    index = end + 1
                }
            }

            source[index] == '!' && index + 1 < source.length && source[index + 1] == '[' -> {
                // Render image Markdown as its linked alt text in this compact text-only surface.
                index++
            }

            source[index] == '\\' && index + 1 < source.length &&
                source[index + 1] in MARKDOWN_ESCAPABLE_CHARACTERS -> {
                append(source[index + 1])
                index += 2
            }

            source.startsWith("**", index) || source.startsWith("__", index) -> {
                val delimiter = source.substring(index, index + 2)
                val end = source.indexOf(delimiter, index + 2)
                if (end < 0) {
                    append(delimiter)
                    index += 2
                } else {
                    val content = source.substring(index + 2, end)
                    if (enableBoldFormatting) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            appendSummaryMarkdownInline(content, linkColor, true)
                        }
                    } else {
                        appendSummaryMarkdownInline(content, linkColor, false)
                    }
                    index = end + 2
                }
            }

            source[index] == '`' -> {
                val end = source.indexOf('`', index + 1)
                if (end < 0) {
                    append('`')
                    index++
                } else {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(source.substring(index + 1, end))
                    }
                    index = end + 1
                }
            }

            source[index] == '[' -> {
                // Unfinished streamed links often contain many '[' characters. Share the failed
                // suffix lookup until the cursor passes that label, rather than scanning per '['.
                if (nextLinkLabelEnd != -1 && nextLinkLabelEnd < index) {
                    nextLinkLabelEnd = source.indexOf("](", index + 1)
                    nextLinkDestinationEnd = if (nextLinkLabelEnd >= 0) source.markdownLinkEnd(nextLinkLabelEnd + 2) else -1
                }
                val labelEnd = nextLinkLabelEnd
                val urlEnd = nextLinkDestinationEnd
                if (labelEnd < 0 || urlEnd < 0) {
                    append('[')
                    index++
                } else {
                    val label = source.substring(index + 1, labelEnd)
                    val url = source.substring(labelEnd + 2, urlEnd).unescapeMarkdownDestination()
                    val linkStyle = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    )
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(linkStyle))) {
                        appendSummaryMarkdownInline(label, linkColor, enableBoldFormatting)
                    }
                    index = urlEnd + 1
                }
            }

            source[index] == '*' || source[index] == '_' -> {
                val delimiter = source[index]
                val end = source.indexOf(delimiter, index + 1)
                if (end <= index + 1) {
                    append(delimiter)
                    index++
                } else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendSummaryMarkdownInline(
                            source.substring(index + 1, end),
                            linkColor,
                            enableBoldFormatting,
                        )
                    }
                    index = end + 1
                }
            }

            else -> {
                append(source[index])
                index++
            }
        }
    }
}

private val INCOMPLETE_BULLET_MARKERS = setOf("-", "*", "+", "•")
private const val MAX_FORMATTED_MARKDOWN_CHARS = 64 * 1024
private const val STREAMING_TEXT_FADE_MILLIS = 180
private const val MARKDOWN_ESCAPABLE_CHARACTERS = "\\`*{}[]()#+-.!_>"
