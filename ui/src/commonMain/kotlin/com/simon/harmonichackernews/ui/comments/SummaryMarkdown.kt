package com.simon.harmonichackernews.ui.comments

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.rememberTextMeasurer

@Composable
internal fun SummaryMarkdownText(
    markdown: String,
    color: Color,
    linkColor: Color,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
) {
    val rendered = remember(markdown, linkColor) {
        summaryMarkdownAnnotatedString(markdown, linkColor)
    }
    val indentPrefix = remember(markdown) { summaryMarkdownHangingIndentPrefix(markdown) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val hangingIndent = remember(indentPrefix, fontFamily, fontSize, textMeasurer, density) {
        indentPrefix?.let { prefix ->
            val width = textMeasurer.measure(
                text = prefix,
                style = TextStyle(fontFamily = fontFamily, fontSize = fontSize),
                maxLines = 1,
            ).size.width
            with(density) { width.toSp() }
        }
    }
    Text(
        text = rendered,
        modifier = modifier,
        color = color,
        fontFamily = fontFamily,
        fontSize = fontSize,
        lineHeight = lineHeight,
        style = if (hangingIndent == null) {
            TextStyle.Default
        } else {
            TextStyle.Default.copy(textIndent = TextIndent(restLine = hangingIndent))
        },
    )
}

/** Renders the compact Markdown subset requested from summary providers. */
internal fun summaryMarkdownAnnotatedString(
    markdown: String,
    linkColor: Color = Color.Unspecified,
): AnnotatedString = buildAnnotatedString {
    val lines = compactSummaryMarkdownListSpacing(markdown.trim().lines())
    lines.forEachIndexed { index, sourceLine ->
        if (index > 0) append('\n')
        val line = sourceLine.trimEnd()
        val trimmedStart = line.trimStart()
        val bulletItem = trimmedStart.markdownBulletItemContent()
        val numberedItem = trimmedStart.markdownNumberedItemContent()
        when {
            bulletItem != null -> {
                append("• ")
                appendSummaryMarkdownInline(bulletItem, linkColor)
            }

            numberedItem != null -> {
                val (number, content) = numberedItem
                append(number)
                append(". ")
                appendSummaryMarkdownInline(content, linkColor)
            }

            trimmedStart.startsWith("#") -> {
                val heading = trimmedStart.trimStart('#').trimStart()
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendSummaryMarkdownInline(heading, linkColor)
                }
            }

            trimmedStart.startsWith("> ") -> {
                append("› ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendSummaryMarkdownInline(trimmedStart.drop(2), linkColor)
                }
            }

            else -> appendSummaryMarkdownInline(line, linkColor)
        }
    }
}

private fun compactSummaryMarkdownListSpacing(lines: List<String>): List<String> =
    lines.filterIndexed { index, line ->
        if (line.isNotBlank()) return@filterIndexed true
        val previous = lines.subList(0, index).lastOrNull(String::isNotBlank)
        val next = lines.subList(index + 1, lines.size).firstOrNull(String::isNotBlank)
        previous?.isMarkdownListItem() != true || next?.isMarkdownListItem() != true
    }

private fun summaryMarkdownHangingIndentPrefix(markdown: String): String? {
    val prefixes = compactSummaryMarkdownListSpacing(markdown.trim().lines())
        .filter(String::isNotBlank)
        .map { line ->
            val trimmed = line.trimStart()
            val numberedItem = trimmed.markdownNumberedItemContent()
            when {
                trimmed.markdownBulletItemContent() != null -> "• "
                numberedItem != null -> "${numberedItem.first}. "
                else -> return null
            }
        }
    return prefixes.maxByOrNull(String::length)
}

private fun String.isMarkdownListItem(): Boolean {
    val trimmed = trimStart()
    return trimmed.markdownBulletItemContent() != null ||
        trimmed.markdownNumberedItemContent() != null
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

private fun AnnotatedString.Builder.appendSummaryMarkdownInline(
    source: String,
    linkColor: Color,
) {
    var index = 0
    while (index < source.length) {
        when {
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
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendSummaryMarkdownInline(source.substring(index + 2, end), linkColor)
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
                val labelEnd = source.indexOf("](", index + 1)
                val urlEnd = if (labelEnd >= 0) source.indexOf(')', labelEnd + 2) else -1
                if (labelEnd < 0 || urlEnd < 0) {
                    append('[')
                    index++
                } else {
                    val label = source.substring(index + 1, labelEnd)
                    val url = source.substring(labelEnd + 2, urlEnd)
                    val linkStyle = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    )
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(linkStyle))) {
                        appendSummaryMarkdownInline(label, linkColor)
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
                        appendSummaryMarkdownInline(source.substring(index + 1, end), linkColor)
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

private const val MARKDOWN_ESCAPABLE_CHARACTERS = "\\`*{}[]()#+-.!_>"
