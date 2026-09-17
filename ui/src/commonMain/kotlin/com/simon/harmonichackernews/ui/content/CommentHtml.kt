package com.simon.harmonichackernews.ui.content

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
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
        document.body().childNodes().forEach { appendHtmlNode(it) }
    }.trimmed()
}

private const val COMMENT_URL_TAG = "harmonic-comment-url"

private fun AnnotatedString.Builder.appendHtmlNode(
    node: Node,
) {
    when (node) {
        is TextNode -> append(node.getWholeText())
        is Element -> {
            val tag = node.normalName()
            if (tag == "script" || tag == "style") return
            if (tag == "br") {
                append('\n')
                return
            }

            val start = length
            val style = htmlSpanStyle(tag)
            if (style == null) {
                node.childNodes().forEach { child -> appendHtmlNode(child) }
            } else {
                pushStyle(style)
                node.childNodes().forEach { child -> appendHtmlNode(child) }
                pop()
            }
            val end = length
            val url = node.attr("href").trim()
            if (tag == "a" && url.isNotEmpty() && start < end) {
                addStringAnnotation(COMMENT_URL_TAG, url, start, end)
            }
        }
    }
}

private fun htmlSpanStyle(tag: String): SpanStyle? = when (tag) {
    "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
    "i", "em" -> SpanStyle(fontStyle = FontStyle.Italic)
    "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
    "s", "strike", "del" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    else -> null
}

private fun AnnotatedString.trimmed(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return AnnotatedString("")
    val end = text.indexOfLast { !it.isWhitespace() } + 1
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
