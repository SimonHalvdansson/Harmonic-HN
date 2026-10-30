package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import com.simon.harmonichackernews.network.toNetworkUrlOrNull

@Composable
internal fun SummaryMarkdownText(
    markdown: String,
    color: Color,
    linkColor: Color,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    onOpenLink: (String) -> Unit,
    baseUrl: String? = null,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    enableBoldFormatting: Boolean = true,
    animateStreamingText: Boolean = false,
    animationContentKey: Any? = null,
    onOverflowChanged: ((Boolean) -> Unit)? = null,
) {
    val listItems = remember(markdown) { summaryMarkdownListItems(markdown) }
    if (listItems != null) {
        SummaryMarkdownList(
            items = listItems,
            maxLines = maxLines,
            overflow = overflow,
            color = color,
            linkColor = linkColor,
            fontFamily = fontFamily,
            fontSize = fontSize,
            lineHeight = lineHeight,
            onOpenLink = onOpenLink,
            baseUrl = baseUrl,
            modifier = modifier,
            enableBoldFormatting = enableBoldFormatting,
            animateStreamingText = animateStreamingText,
            animationContentKey = animationContentKey,
            onOverflowChanged = onOverflowChanged,
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
        onOpenLink = onOpenLink,
        baseUrl = baseUrl,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        enableBoldFormatting = enableBoldFormatting,
        animateStreamingText = animateStreamingText,
        animationContentKey = animationContentKey,
        onOverflowChanged = onOverflowChanged,
    )
}

@Composable
private fun SummaryMarkdownList(
    items: List<SummaryMarkdownListItem>,
    maxLines: Int,
    overflow: TextOverflow,
    color: Color,
    linkColor: Color,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    onOpenLink: (String) -> Unit,
    baseUrl: String?,
    modifier: Modifier,
    enableBoldFormatting: Boolean,
    animateStreamingText: Boolean,
    animationContentKey: Any?,
    onOverflowChanged: ((Boolean) -> Unit)?,
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
    val bodyStyle = LocalTextStyle.current.copy(fontFamily = fontFamily, fontSize = fontSize, lineHeight = lineHeight)
    BoxWithConstraints(modifier) {
        val textWidth = (constraints.maxWidth - with(density) { markerWidth.roundToPx() }).coerceAtLeast(0)
        Column {
            var remainingLines = maxLines
            var truncated = false
            items.forEachIndexed { index, item ->
                if (remainingLines <= 0) {
                    truncated = true
                    return@forEachIndexed
                }
                val allowedLines = remainingLines
                val lineCount = if (maxLines == Int.MAX_VALUE) 0 else textMeasurer.measure(
                    text = summaryMarkdownAnnotatedString(item.content, linkColor, enableBoldFormatting, baseUrl, onOpenLink),
                    style = bodyStyle,
                    constraints = Constraints(maxWidth = textWidth),
                ).lineCount
                remainingLines -= lineCount
                if (lineCount > allowedLines) truncated = true
                val content = if (remainingLines <= 0 && lineCount <= allowedLines && index < items.lastIndex &&
                    overflow == TextOverflow.Ellipsis) item.content + "…" else item.content
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
                        markdown = content,
                        maxLines = allowedLines,
                        overflow = overflow,
                        color = color,
                        linkColor = linkColor,
                        fontFamily = fontFamily,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        onOpenLink = onOpenLink,
                        baseUrl = baseUrl,
                        modifier = Modifier.weight(1f),
                        enableBoldFormatting = enableBoldFormatting,
                        animateStreamingText = animateStreamingText,
                        animationContentKey = animationContentKey to index,
                    )
                }
            }
            SideEffect { onOverflowChanged?.invoke(truncated) }
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
    onOpenLink: (String) -> Unit,
    baseUrl: String?,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    enableBoldFormatting: Boolean = true,
    animateStreamingText: Boolean = false,
    animationContentKey: Any? = null,
    onOverflowChanged: ((Boolean) -> Unit)? = null,
) {
    val rendered = remember(markdown, linkColor, enableBoldFormatting, baseUrl, onOpenLink) {
        summaryMarkdownAnnotatedString(markdown, linkColor, enableBoldFormatting, baseUrl, onOpenLink)
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
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val fading by remember(fadeAlpha) { derivedStateOf { fadeAlpha.value < 1f } }
    val fadeModifier = if (fadeRange != null && fading) Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            // Text may add link styles to its layout input. Match characters, not annotations.
            val path = layout?.takeIf { it.layoutInput.text.text == rendered.text }
                ?.getPathForRange(fadeRange.first, fadeRange.last + 1)
            onDrawWithContent {
                drawContent()
                // Read animation state only while drawing. Keep text, links and layout stable.
                if (path != null) drawPath(
                    path, Color.Black, alpha = 1f - fadeAlpha.value, blendMode = BlendMode.DstOut,
                )
            }
        } else Modifier
    Text(
        onTextLayout = {
            layout = it
            onOverflowChanged?.invoke(it.hasVisualOverflow)
        },
        text = rendered,
        modifier = modifier.then(fadeModifier),
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
    if (markdown.length > MAX_FORMATTED_MARKDOWN_CHARS) return null
    val lines = markdown.stripMarkdownHtmlComments().trim().lines().filter(String::isNotBlank)
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

internal fun String.stripMarkdownHtmlComments(): String {
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
