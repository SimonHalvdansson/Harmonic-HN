package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

fun htmlAnnotatedString(
    html: String,
    linkColor: Color,
    linkListener: LinkInteractionListener,
    highlightQuotes: Boolean = true,
    retainQuoteDecoration: Boolean = false,
): AnnotatedString = runCatching {
    val prepared = CommentHtmlTextCache.get(html, highlightQuotes, retainQuoteDecoration)
    val links = prepared.getStringAnnotations(COMMENT_URL_TAG, 0, prepared.length)
    // Most comments have no links to bind. Their cached text already contains every style.
    if (links.isEmpty()) {
        return@runCatching prepared
    }
    buildAnnotatedString {
        append(prepared)
        links.forEach { link ->
            addLink(
                LinkAnnotation.Url(
                    url = link.item,
                    styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = linkListener,
                ),
                link.start,
                link.end,
            )
        }
    }
}.getOrElse { AnnotatedString(Ksoup.parse(html).text()) }

/** No theme, listener, mutable DOM, or screen references are retained in the prepared text. */
internal fun prepareCommentHtml(html: String): AnnotatedString = prepareCommentHtml(html, true)

internal fun prepareCommentHtml(html: String, highlightQuotes: Boolean): AnnotatedString =
    parseCommentHtml(html).let { if (highlightQuotes) it.withHighlightedQuotedLines() else it }

/** All quote presentations share one HTML parse and one cache entry. No bound link listeners. */
@Immutable
internal data class PreparedCommentHtml(
    val plain: AnnotatedString,
    val highlighted: AnnotatedString = plain,
    val decoratedPlain: AnnotatedString = plain,
) {
    fun text(highlightQuotes: Boolean, retainQuoteDecoration: Boolean = false): AnnotatedString = when {
        highlightQuotes -> highlighted
        retainQuoteDecoration -> decoratedPlain
        else -> plain
    }

    // Keep the previous conservative allowance for ordinary text, and account for quote variants.
    fun weightedChars(sourceLength: Int): Int = maxOf(
        sourceLength * 3,
        sourceLength + plain.length +
            (if (highlighted === plain) 0 else highlighted.length) +
            (if (decoratedPlain === plain) 0 else decoratedPlain.length),
    )
}

internal fun prepareCommentHtmlContent(html: String): PreparedCommentHtml {
    val plain = parseCommentHtml(html)
    val quotes = plain.quotedLines()
    if (quotes.isEmpty()) return PreparedCommentHtml(plain)
    return PreparedCommentHtml(
        plain = plain,
        highlighted = plain.withHighlightedQuotedLines(quotes, removeMarkers = true),
        decoratedPlain = plain.withHighlightedQuotedLines(quotes, removeMarkers = false),
    )
}

private fun parseCommentHtml(html: String): AnnotatedString {
    val document = Ksoup.parse(preserveLegacyCommentParagraphSpacing(html))
    return buildAnnotatedString {
        val renderer = CommentHtmlRenderer(this)
        document.body().childNodes().forEach { renderer.appendNode(it) }
    }.trimmedCommentText()
}

private const val COMMENT_URL_TAG = "harmonic-comment-url"
internal const val COMMENT_QUOTE_TAG = "harmonic-comment-quote"

/** Mark HN's leading > quote paragraphs without altering links or inline formatting. */
internal fun AnnotatedString.withHighlightedQuotedLines(removeMarkers: Boolean = true): AnnotatedString {
    return withHighlightedQuotedLines(quotedLines(), removeMarkers)
}

private data class QuotedLine(val start: Int, val contentStart: Int, val end: Int)

private fun AnnotatedString.quotedLines(): List<QuotedLine> {
    if ('>' !in text) return emptyList()
    val sourceText = text
    val codeSpans = spanStyles.filter { it.item.fontFamily == FontFamily.Monospace }
    return buildList {
        var lineStart = 0
        while (lineStart < sourceText.length) {
            val lineEnd = sourceText.indexOf('\n', lineStart).let { if (it < 0) sourceText.length else it }
            var marker = lineStart
            while (marker < lineEnd && marker - lineStart < 3 && sourceText[marker] == ' ') marker++
            if (marker < lineEnd && sourceText[marker] == '>' &&
                codeSpans.none { it.start <= marker && it.end > marker }
            ) {
                var contentStart = marker
                while (contentStart < lineEnd && sourceText[contentStart] == '>') contentStart++
                if (contentStart < lineEnd && sourceText[contentStart] == ' ') contentStart++
                add(QuotedLine(lineStart, contentStart, lineEnd))
            }
            lineStart = if (lineEnd < sourceText.length) lineEnd + 1 else sourceText.length
        }
    }
}

private fun AnnotatedString.withHighlightedQuotedLines(
    quotes: List<QuotedLine>,
    removeMarkers: Boolean,
): AnnotatedString {
    if (quotes.isEmpty()) return this
    return buildAnnotatedString {
        var copiedThrough = 0
        quotes.forEach { quote ->
            append(this@withHighlightedQuotedLines, copiedThrough, quote.start)
            val start = length
            append(this@withHighlightedQuotedLines, if (removeMarkers) quote.contentStart else quote.start, quote.end)
            if (length > start) {
                // ParagraphStyle would add implicit paragraph breaks; draw the decoration instead.
                addStringAnnotation(COMMENT_QUOTE_TAG, "quote", start, length)
            }
            copiedThrough = quote.end
        }
        append(this@withHighlightedQuotedLines, copiedThrough, this@withHighlightedQuotedLines.length)
    }
}

/** Split only at blank lines adjacent to quotes, preserving inline annotations. */
internal fun AnnotatedString.quoteSpacedSegments(): List<AnnotatedString> {
    val quotes = getStringAnnotations(COMMENT_QUOTE_TAG, 0, length)
    val blankLines = mutableSetOf<Int>()
    quotes.forEach { quote ->
        if (quote.start >= 2 && text.substring(quote.start - 2, quote.start) == "\n\n") {
            blankLines += quote.start - 2
        }
        if (quote.end + 1 < length && text.substring(quote.end, quote.end + 2) == "\n\n") {
            blankLines += quote.end
        }
    }
    if (blankLines.isEmpty()) return listOf(this)
    return buildList {
        var copiedThrough = 0
        blankLines.sorted().forEach { index ->
            add(this@quoteSpacedSegments.subSequence(copiedThrough, index))
            copiedThrough = index + 2
        }
        add(this@quoteSpacedSegments.subSequence(copiedThrough, this@quoteSpacedSegments.length))
    }
}

private class CommentHtmlRenderer(private val builder: AnnotatedString.Builder) {
    private var pendingCodeBoundary = false
    // Keep just the tail needed for spacing; materializing the entire builder per text node
    // repeatedly copied all preceding text and spans in formatting-heavy comments.
    private var trailingLineBreaks = 0

    private fun appendText(text: String) {
        builder.append(text)
        if (text.isEmpty()) return
        var breaks = 0
        var index = text.lastIndex
        while (index >= 0 && text[index] == '\n' && breaks < 2) {
            breaks++
            index--
        }
        trailingLineBreaks = if (index < 0) (trailingLineBreaks + breaks).coerceAtMost(2) else breaks
    }

    private fun ensureCodeBlockBoundary() {
        if (builder.length == 0) return
        repeat(2 - trailingLineBreaks) { appendText("\n") }
    }

    fun appendNode(node: Node, convertedCode: Boolean = false, preformatted: Boolean = false): Unit = with(builder) {
        when (node) {
            is TextNode -> {
                // Converted code stores authored spaces as NBSP and line breaks as <br>.
                // Ksoup may insert ordinary whitespace when expanding shortened links.
                var text = node.getWholeText()
                if (convertedCode) {
                    text = text.filterNot { it == ' ' || it == '\n' || it == '\r' }
                        // Compose preserves ordinary spaces, so restore word-wrap opportunities
                        // after removing whitespace introduced by HTML serialization.
                        .replace('\u00a0', ' ')
                } else if (!preformatted) {
                    text = collapseHtmlWhitespace(text)
                    if (length == 0 || trailingLineBreaks > 0) text = text.trimStart(' ')
                    val nextTag = (node.nextSibling() as? Element)?.normalName()
                    if (nextTag == "p" || nextTag == "br" || nextTag == "pre" || nextTag == "div") {
                        text = text.trimEnd(' ')
                    }
                }
                if (pendingCodeBoundary) {
                    text = text.trimStart()
                    if (text.isEmpty()) return
                    ensureCodeBlockBoundary()
                    pendingCodeBoundary = false
                }
                appendText(text)
            }
            is Element -> {
                val tag = node.normalName()
                if (tag == "script" || tag == "style") return
                if (tag == "br") {
                    if (!pendingCodeBoundary) appendText("\n")
                    return
                }

                val convertedBlock = tag == "div" && node.children().singleOrNull()?.normalName() == "tt"
                val codeBlock = tag == "pre" || convertedBlock
                if (codeBlock || pendingCodeBoundary) {
                    ensureCodeBlockBoundary()
                    pendingCodeBoundary = false
                }

                val start = length
                val style = htmlSpanStyle(tag)
                val children = if (convertedBlock) node.children().toList() else node.childNodes()
                if (style != null) pushStyle(style)
                children.forEach { child ->
                    appendNode(
                        child,
                        convertedCode = convertedCode || convertedBlock,
                        preformatted = preformatted || tag == "pre" || tag == "code" || tag == "tt",
                    )
                }
                if (style != null) pop()
                val end = length
                val url = node.attr("href").trim()
                if (tag == "a" && url.isNotEmpty() && start < end) {
                    addStringAnnotation(COMMENT_URL_TAG, url, start, end)
                }
                if (codeBlock) pendingCodeBoundary = true
            }
        }
    }
}

/** Same five HTML whitespace characters as the renderer's original regex, including form feed. */
private fun Char.isCollapsibleHtmlWhitespace(): Boolean =
    this == ' ' || this == '\t' || this == '\r' || this == '\n' || this == '\u000c'

private fun collapseHtmlWhitespace(text: String): String {
    var previousSpace = false
    var needsCollapse = false
    for (character in text) {
        if (character.isCollapsibleHtmlWhitespace()) {
            if (character != ' ' || previousSpace) {
                needsCollapse = true
                break
            }
            previousSpace = true
        } else previousSpace = false
    }
    if (!needsCollapse) return text
    return buildString(text.length) {
        var inWhitespace = false
        for (character in text) {
            if (character.isCollapsibleHtmlWhitespace()) {
                if (!inWhitespace) append(' ')
                inWhitespace = true
            } else {
                append(character)
                inWhitespace = false
            }
        }
    }
}

// SpanStyle is immutable. All comments can share these style values without sharing ranges.
private object CommentHtmlStyles {
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    val italic = SpanStyle(fontStyle = FontStyle.Italic)
    val underline = SpanStyle(textDecoration = TextDecoration.Underline)
    val strike = SpanStyle(textDecoration = TextDecoration.LineThrough)
    val code = SpanStyle(fontFamily = FontFamily.Monospace)
    val small = SpanStyle(fontSize = 0.8.em)
}

private fun htmlSpanStyle(tag: String): SpanStyle? = when (tag) {
    "b", "strong" -> CommentHtmlStyles.bold
    "i", "em" -> CommentHtmlStyles.italic
    "u" -> CommentHtmlStyles.underline
    "s", "strike", "del" -> CommentHtmlStyles.strike
    "pre", "code", "tt" -> CommentHtmlStyles.code
    "small" -> CommentHtmlStyles.small
    else -> null
}

internal fun AnnotatedString.trimmedCommentText(): AnnotatedString {
    // Do not trim indentation or trailing whitespace belonging to preformatted code.
    val codeSpans = spanStyles.filter { it.item.fontFamily == FontFamily.Monospace }
    val start = minOf(
        text.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: length,
        codeSpans.minOfOrNull { it.start } ?: length,
    )
    val end = maxOf(
        text.indexOfLast { !it.isWhitespace() } + 1,
        codeSpans.maxOfOrNull { it.end } ?: 0,
    )
    if (start >= end) return AnnotatedString("")
    return subSequence(start, end)
}

internal fun preserveLegacyCommentParagraphSpacing(html: String): String = html
    .replace(formattedCommentParagraphStartPattern, "<br><br>")
    .replace(commentParagraphStartPattern, "<br><br>")
    .replace(commentParagraphBoundaryPattern, "</p><br><p")
    .replace(commentDivBoundaryPattern, "</div><br><div")

private val commentParagraphStartPattern = Regex("<p\\s*>", RegexOption.IGNORE_CASE)
private val formattedCommentParagraphStartPattern = Regex("[\\r\\n]+[ \\t]*<p\\s*>", RegexOption.IGNORE_CASE)
private val commentParagraphBoundaryPattern = Regex("</p>\\s*<p", RegexOption.IGNORE_CASE)
private val commentDivBoundaryPattern = Regex("</div>\\s*<div", RegexOption.IGNORE_CASE)
