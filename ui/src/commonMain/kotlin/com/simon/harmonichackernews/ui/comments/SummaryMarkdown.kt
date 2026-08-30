package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit

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
    enableBoldFormatting: Boolean = true,
    animateStreamingText: Boolean = false,
    animationContentKey: Any? = null,
) {
    val listItems = remember(markdown) { summaryMarkdownListItems(markdown) }
    if (listItems != null && maxLines == Int.MAX_VALUE) {
        SummaryMarkdownList(
            items = listItems,
            color = color,
            linkColor = linkColor,
            fontFamily = fontFamily,
            fontSize = fontSize,
            lineHeight = lineHeight,
            modifier = modifier,
            enableBoldFormatting = enableBoldFormatting,
            animateStreamingText = animateStreamingText,
            animationContentKey = animationContentKey,
        )
        return
    }
    SummaryMarkdownSingleText(
        markdown = markdown,
        color = color,
        linkColor = linkColor,
        fontFamily = fontFamily,
        fontSize = fontSize,
        lineHeight = lineHeight,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        enableBoldFormatting = enableBoldFormatting,
        animateStreamingText = animateStreamingText,
        animationContentKey = animationContentKey,
    )
}

@Composable
private fun SummaryMarkdownList(
    items: List<SummaryMarkdownListItem>,
    color: Color,
    linkColor: Color,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier,
    enableBoldFormatting: Boolean,
    animateStreamingText: Boolean,
    animationContentKey: Any?,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val markerWidth = remember(items, fontFamily, fontSize, textMeasurer, density) {
        val width = items.maxOf { item ->
            textMeasurer.measure(
                text = item.marker,
                style = TextStyle(fontFamily = fontFamily, fontSize = fontSize),
                maxLines = 1,
            ).size.width
        }
        with(density) { width.toDp() }
    }
    Column(modifier) {
        items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = item.marker,
                    modifier = Modifier.width(markerWidth),
                    color = color,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                )
                SummaryMarkdownSingleText(
                    markdown = item.content,
                    color = color,
                    linkColor = linkColor,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    modifier = Modifier.weight(1f),
                    enableBoldFormatting = enableBoldFormatting,
                    animateStreamingText = animateStreamingText,
                    animationContentKey = animationContentKey to index,
                )
            }
        }
    }
}

@Composable
private fun SummaryMarkdownSingleText(
    markdown: String,
    color: Color,
    linkColor: Color,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    enableBoldFormatting: Boolean = true,
    animateStreamingText: Boolean = false,
    animationContentKey: Any? = null,
) {
    val rendered = remember(markdown, linkColor, enableBoldFormatting) {
        summaryMarkdownAnnotatedString(markdown, linkColor, enableBoldFormatting)
    }
    val history = remember(animationContentKey) { StreamingTextHistory() }
    val fadeRange = remember(animationContentKey, rendered.text, animateStreamingText) {
        streamingTextFadeRange(
            previous = history.renderedText,
            current = rendered.text,
            streaming = animateStreamingText,
            wasStreaming = history.streaming,
        )
    }
    val fadeAlpha = remember(animationContentKey, rendered.text, animateStreamingText) {
        Animatable(if (fadeRange == null) 1f else 0f)
    }
    SideEffect {
        history.renderedText = rendered.text
        history.streaming = animateStreamingText
    }
    LaunchedEffect(fadeAlpha, fadeRange) {
        if (fadeRange != null) {
            fadeAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = STREAMING_TEXT_FADE_MILLIS,
                    easing = LinearOutSlowInEasing,
                ),
            )
        }
    }
    val displayed = if (fadeRange == null || fadeAlpha.value >= 1f) {
        rendered
    } else {
        buildAnnotatedString {
            append(rendered)
            addStyle(
                SpanStyle(color = color.copy(alpha = color.alpha * fadeAlpha.value)),
                fadeRange.first,
                fadeRange.last + 1,
            )
        }
    }
    Text(
        text = displayed,
        modifier = modifier,
        color = color,
        fontFamily = fontFamily,
        fontSize = fontSize,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = overflow,
    )
}

internal data class SummaryMarkdownListItem(
    val marker: String,
    val content: String,
)

internal fun summaryMarkdownListItems(markdown: String): List<SummaryMarkdownListItem>? {
    val lines = compactSummaryMarkdownListSpacing(
        markdown.stripMarkdownHtmlComments().trim().lines(),
    ).filter(String::isNotBlank)
    if (lines.isEmpty()) return null
    return lines.mapIndexed { index, sourceLine ->
        val line = sourceLine.trimStart().trimEnd()
        val bulletItem = line.markdownBulletItemContent()
        val numberedItem = line.markdownNumberedItemContent()
        val incompleteBullet = index == lines.lastIndex && line in INCOMPLETE_BULLET_MARKERS
        when {
            bulletItem != null -> {
                val task = bulletItem.markdownTaskItemContent()
                SummaryMarkdownListItem(
                    marker = task?.first ?: "• ",
                    content = task?.second ?: bulletItem,
                )
            }

            numberedItem != null -> SummaryMarkdownListItem(
                marker = "${numberedItem.first}. ",
                content = numberedItem.second,
            )

            incompleteBullet -> SummaryMarkdownListItem(marker = "• ", content = "")
            else -> return null
        }
    }
}

internal fun streamingTextFadeRange(
    previous: String,
    current: String,
    streaming: Boolean,
    wasStreaming: Boolean,
): IntRange? {
    if ((!streaming && !wasStreaming) || current.isEmpty() || current == previous) return null
    val commonPrefixLength = previous.commonPrefixWith(current).length
    if (commonPrefixLength >= current.length) return null
    return commonPrefixLength until current.length
}

private class StreamingTextHistory(
    var renderedText: String = "",
    var streaming: Boolean = false,
)

/** Renders the compact Markdown subset requested from summary providers. */
internal fun summaryMarkdownAnnotatedString(
    markdown: String,
    linkColor: Color = Color.Unspecified,
    enableBoldFormatting: Boolean = true,
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
    enableBoldFormatting: Boolean,
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
private const val STREAMING_TEXT_FADE_MILLIS = 180
private const val MARKDOWN_ESCAPABLE_CHARACTERS = "\\`*{}[]()#+-.!_>"
