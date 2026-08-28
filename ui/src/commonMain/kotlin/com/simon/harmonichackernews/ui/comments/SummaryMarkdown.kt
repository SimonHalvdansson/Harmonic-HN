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
import androidx.compose.ui.text.style.TextOverflow
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
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
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
    val rendered = remember(markdown, linkColor) {
        summaryMarkdownAnnotatedString(markdown, linkColor)
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
            // One paragraph style applies the indent to every list paragraph without Compose's
            // large inter-paragraph leading produced by separate ranged ParagraphStyles.
            TextStyle.Default.copy(textIndent = TextIndent(restLine = hangingIndent))
        },
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** Renders the compact Markdown subset requested from summary providers. */
internal fun summaryMarkdownAnnotatedString(
    markdown: String,
    linkColor: Color = Color.Unspecified,
): AnnotatedString = buildAnnotatedString {
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
                appendSummaryMarkdownInline(task?.second ?: bulletItem, linkColor)
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
                val quote = trimmedStart.drop(2)
                val alert = quote.markdownAlertLabel()
                if (alert != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(alert) }
                } else {
                    append("› ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendSummaryMarkdownInline(quote, linkColor)
                    }
                }
            }

            else -> appendSummaryMarkdownInline(line.stripMarkdownHtmlTags(), linkColor)
        }
    }
}

private fun compactSummaryMarkdownListSpacing(lines: List<String>): List<String> = buildList {
    lines.forEachIndexed { index, line ->
        if (line.isNotBlank()) {
            add(line)
            return@forEachIndexed
        }
        val previous = lastOrNull { it.isNotBlank() }
        val next = lines.asSequence().drop(index + 1).firstOrNull { it.isNotBlank() }
        val compactListGap = previous?.isMarkdownListItem() == true &&
            next?.isMarkdownListItem() == true
        val compactHeadingGap = previous?.isMarkdownHeading() == true
        val duplicateBlankLine = lastOrNull()?.isBlank() == true
        if (!compactListGap && !compactHeadingGap && !duplicateBlankLine) add(line)
    }
}

private fun summaryMarkdownHangingIndentPrefix(markdown: String): String? {
    val prefixes = compactSummaryMarkdownListSpacing(markdown.trim().lines())
        .filter(String::isNotBlank)
        .map { line ->
            val trimmed = line.trimStart()
            val numberedItem = trimmed.markdownNumberedItemContent()
            val bulletItem = trimmed.markdownBulletItemContent()
            val taskPrefix = bulletItem?.markdownTaskItemContent()?.first
            when {
                taskPrefix != null -> taskPrefix
                bulletItem != null -> "• "
                numberedItem != null -> "${numberedItem.first}. "
                // Mixed prose/Markdown blocks keep their normal paragraph layout. AI summaries
                // are list-only, so they receive one compact hanging indent across all bullets.
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

private fun String.stripMarkdownHtmlComments(): String =
    replace(Regex("<!--[\\s\\S]*?-->"), "")

private fun String.stripMarkdownHtmlTags(): String =
    replace(Regex("</?[A-Za-z][^>]*>"), "")

private fun AnnotatedString.Builder.appendSummaryMarkdownInline(
    source: String,
    linkColor: Color,
) {
    var index = 0
    while (index < source.length) {
        when {
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
