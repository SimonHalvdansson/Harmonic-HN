package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.network.LinkSummaryParser
import com.simon.harmonichackernews.network.toNetworkUrlOrNull
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

internal sealed interface ReleaseMarkdownBlock {
    data class Text(val markdown: String) : ReleaseMarkdownBlock
    data class Image(val url: String, val alt: String, val link: String?) : ReleaseMarkdownBlock
}

private const val RELEASE_PREVIEW_LINE_BUDGET = 20
private const val RELEASE_PREVIEW_IMAGE_LINE_COST = 8
private const val RELEASE_PREVIEW_CHARACTERS_PER_LINE = 56

/** Keep images in document order, including GitHub's linked HTML video thumbnails. */
internal fun releaseMarkdownBlocks(markdown: String, pageUrl: String): List<ReleaseMarkdownBlock> {
    val source = markdown.replace(Regex("<!--[\\s\\S]*?-->"), "")
    val blocks = mutableListOf<ReleaseMarkdownBlock>()
    val text = StringBuilder()
    var fence: String? = null
    fun flushText() {
        text.toString().trim().takeIf(String::isNotEmpty)?.let {
            blocks += ReleaseMarkdownBlock.Text(it)
        }
        text.clear()
    }
    fun resolve(value: String): String? =
        (pageUrl.toNetworkUrlOrNull()?.resolve(value)?.toString() ?: value)
            .let(LinkSummaryParser::normalizeHttpUrl)

    // Code fences are deliberately left to the text renderer; image examples are not images.
    val lines = source.lines()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            val marker = trimmed.take(3)
            fence = if (fence == marker) null else fence ?: marker
        }
        if (fence != null || trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            text.appendLine(line)
            index++
            continue
        }
        // An HTML anchor and its image can span several lines (as in Audacity's release notes).
        var chunk = line
        if (trimmed.startsWith("<a ", ignoreCase = true) && !chunk.contains("</a>", true)) {
            val end = (index + 1 until lines.size).firstOrNull { lines[it].contains("</a>", true) }
            if (end != null && end - index <= 8) {
                chunk = lines.subList(index, end + 1).joinToString("\n")
                index = end
            }
        }
        var consumed = 0
        RELEASE_IMAGE.findAll(chunk).forEach { match ->
            if (match.range.first > 0 && chunk[match.range.first - 1] == '\\') return@forEach
            // Leave inline code untouched too.
            if (chunk.take(match.range.first).count { it == '`' } % 2 != 0) return@forEach
            val token = match.value
            val image = if (token.startsWith('<')) {
                val document = Ksoup.parse(token, baseUri = pageUrl)
                document.selectFirst("img[src]")?.let { element ->
                    resolve(element.attr("src"))?.let { url ->
                        ReleaseMarkdownBlock.Image(
                            url, element.attr("alt"),
                            document.selectFirst("a[href]")?.attr("href")?.let(::resolve),
                        )
                    }
                }
            } else {
                val imageMatch = MARKDOWN_IMAGE.find(token) ?: return@forEach
                resolve(imageMatch.groupValues[2].removeSurrounding("<", ">"))?.let { url ->
                    val link = if (token.startsWith("[!")) {
                        token.substring(imageMatch.range.last + 1).removePrefix("](").removeSuffix(")")
                            .let(::resolve)
                    } else null
                    ReleaseMarkdownBlock.Image(url, imageMatch.groupValues[1], link)
                }
            }
            if (image != null) {
                text.append(chunk.substring(consumed, match.range.first))
                flushText()
                blocks += image
                consumed = match.range.last + 1
            }
        }
        text.appendLine(chunk.substring(consumed))
        index++
    }
    flushText()
    return blocks
}

/**
 * Bounds the whole release preview rather than each text fragment. Images keep their source
 * position and consume part of the same approximate line budget as the surrounding markdown.
 */
internal fun releaseMarkdownPreviewBlocks(
    markdown: String,
    pageUrl: String,
    lineBudget: Int = RELEASE_PREVIEW_LINE_BUDGET,
): List<ReleaseMarkdownBlock> {
    require(lineBudget > 0)
    val source = releaseMarkdownBlocks(markdown, pageUrl)
    val preview = mutableListOf<ReleaseMarkdownBlock>()
    var remainingLines = lineBudget
    var truncated = false

    blockLoop@ for (block in source) {
        if (remainingLines <= 0) {
            truncated = true
            break
        }
        when (block) {
            is ReleaseMarkdownBlock.Image -> {
                if (remainingLines < RELEASE_PREVIEW_IMAGE_LINE_COST) {
                    truncated = true
                    break@blockLoop
                }
                preview += block
                remainingLines -= RELEASE_PREVIEW_IMAGE_LINE_COST
            }
            is ReleaseMarkdownBlock.Text -> {
                val keptLines = mutableListOf<String>()
                var textTruncated = false
                for (line in block.markdown.lines()) {
                    if (line.isBlank()) {
                        keptLines += line
                        continue
                    }
                    val estimatedLines = maxOf(
                        1,
                        (line.trim().length + RELEASE_PREVIEW_CHARACTERS_PER_LINE - 1) /
                            RELEASE_PREVIEW_CHARACTERS_PER_LINE,
                    )
                    if (estimatedLines > remainingLines) {
                        truncated = true
                        textTruncated = true
                        break
                    }
                    keptLines += line
                    remainingLines -= estimatedLines
                }
                keptLines.joinToString("\n").trim().takeIf(String::isNotEmpty)?.let {
                    preview += ReleaseMarkdownBlock.Text(it)
                }
                if (textTruncated) break@blockLoop
            }
        }
    }

    if (truncated) {
        val last = preview.lastOrNull()
        if (last is ReleaseMarkdownBlock.Text) {
            preview[preview.lastIndex] = last.copy(markdown = "${last.markdown.trimEnd()}\n\n...")
        } else {
            preview += ReleaseMarkdownBlock.Text("...")
        }
    }
    return preview
}

private val MARKDOWN_IMAGE = Regex("!\\[([^]]*)]\\((<[^>]+>|[^\\s]+?)(?:\\s+\"[^\"]*\")?\\)")
private val RELEASE_IMAGE = Regex(
    "<a\\b[^>]*>\\s*<img\\b[^>]*>\\s*</a>|<img\\b[^>]*>|" +
        "\\[!\\[[^]]*]\\([^\\n]+?\\)]\\([^\\n]+?\\)|!\\[[^]]*]\\([^\\n]+?\\)",
    RegexOption.IGNORE_CASE,
)

@Composable
internal fun ReleaseMarkdownContent(markdown: String, pageUrl: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown, pageUrl) { releaseMarkdownPreviewBlocks(markdown, pageUrl) }
    val platform = LocalCommentsPreviewPlatform.current
    Column(modifier) {
        blocks.forEach { block ->
            when (block) {
                is ReleaseMarkdownBlock.Text -> SummaryMarkdownText(
                    markdown = block.markdown,
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = HarmonicTheme.colors.storyNormal,
                    linkColor = HarmonicTheme.colors.link,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
                is ReleaseMarkdownBlock.Image -> AsyncImage(
                    model = block.url,
                    contentDescription = block.alt.ifBlank { "Release image" },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        .clickable { platform.openCustomTab(block.link ?: block.url) },
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
    }
}
