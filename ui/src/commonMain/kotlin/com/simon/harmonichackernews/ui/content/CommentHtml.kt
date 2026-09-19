package com.simon.harmonichackernews.ui.content

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
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

fun htmlAnnotatedString(
    html: String,
    linkColor: Color,
    linkListener: LinkInteractionListener,
): AnnotatedString = runCatching {
    val prepared = CommentHtmlTextCache.get(html)
    buildAnnotatedString {
        append(prepared)
        prepared.getStringAnnotations(COMMENT_URL_TAG, 0, prepared.length).forEach { link ->
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
internal fun prepareCommentHtml(html: String): AnnotatedString {
    val document = Ksoup.parse(preserveLegacyCommentParagraphSpacing(html))
    return buildAnnotatedString {
        val renderer = CommentHtmlRenderer(this)
        document.body().childNodes().forEach { renderer.appendNode(it) }
    }.trimmed()
}

private const val COMMENT_URL_TAG = "harmonic-comment-url"

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
                } else if (!preformatted) {
                    text = text.replace(htmlWhitespace, " ")
                    if (length == 0 || trailingLineBreaks > 0) text = text.trimStart(' ')
                    val nextTag = (node.nextSibling() as? Element)?.normalName()
                    if (nextTag in setOf("p", "br", "pre", "div")) text = text.trimEnd(' ')
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

private val htmlWhitespace = Regex("[ \\t\\r\\n\\u000c]+")

private fun htmlSpanStyle(tag: String): SpanStyle? = when (tag) {
    "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
    "i", "em" -> SpanStyle(fontStyle = FontStyle.Italic)
    "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
    "s", "strike", "del" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    "pre", "code", "tt" -> SpanStyle(fontFamily = FontFamily.Monospace)
    "small" -> SpanStyle(fontSize = 0.8.em)
    else -> null
}

private fun AnnotatedString.trimmed(): AnnotatedString {
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
