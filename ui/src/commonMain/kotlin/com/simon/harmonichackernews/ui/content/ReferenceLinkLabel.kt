package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.network.LinkSummaryParser
import com.simon.harmonichackernews.network.LinkPreviewUrls
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import com.simon.harmonichackernews.utils.referenceLinkFallbackLabel
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Paragraph-sized gaps around a reference run, compact gaps between its individual links. */
internal fun referenceBlockTopPadding(blocks: List<CollectedReferenceLinks.ContentBlock>, index: Int) = when {
    index == 0 -> 0.dp
    blocks[index - 1].isLink() && blocks[index].isLink() -> 4.dp
    else -> 12.dp
}

internal fun shouldResolveReferenceLinkTitle(url: String): Boolean =
    LinkSummaryParser.hackerNewsItemId(url) != null ||
        LinkSummaryParser.isYoutubeVideoUrl(url) ||
        LinkPreviewUrls.isWikipediaUrl(url) ||
        LinkPreviewUrls.isArxivUrl(url)

internal fun hasReferenceLinkTitle(label: String, url: String): Boolean {
    fun normalized(value: String) = value.trim().removePrefix("https://")
        .removePrefix("http://").removePrefix("www.").trimEnd('/')
    val title = normalized(label)
    return title.isNotBlank() && title != normalized(url) &&
        !label.startsWith("https://") && !label.startsWith("http://")
}

/** Resolves supported reference links to useful source titles. */
@Composable
fun rememberReferenceLinkLabel(link: CollectedReferenceLinks.ReferenceLink, resolveAllTitles: Boolean = false): String {
    val url = link.url.orEmpty()
    val fallback = referenceLinkFallbackLabel(link)
    if (!resolveAllTitles && !shouldResolveReferenceLinkTitle(url)) return fallback
    val dependencies = LocalHarmonicUiDependencies.current
    var label by remember(url, link.resolvedTitle) { mutableStateOf(fallback) }

    LaunchedEffect(url) {
        val summary = try {
            dependencies.previewResources.loadLinkSummary(url, fallback, resolvedSummary = link.resolvedSummary)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        currentCoroutineContext().ensureActive()
        val isSupportedSummary = when {
            LinkSummaryParser.hackerNewsItemId(url) != null ->
                summary?.contentType == LinkSummaryParser.HACKER_NEWS_ITEM_CONTENT_TYPE
            else -> summary != null
        }
        if (summary != null && isSupportedSummary) {
            link.resolvedSummary = summary
            summary.title.takeIf(String::isNotBlank)?.let { title ->
                link.resolvedTitle = title
                label = title
            }
        }
    }

    return label
}

/** Keeps the legacy reference-row favicon behavior when a label is replaced with an HN title. */
@Composable
fun rememberReferenceLinkFaviconUrl(link: CollectedReferenceLinks.ReferenceLink): String? {
    val url = link.url.orEmpty()
    val provider = LocalHarmonicUiDependencies.current.userSettings.story.faviconProvider
    return remember(url, provider) {
        runCatching { FaviconUrlBuilder.faviconUrl(url, provider) }.getOrNull()
    }
}
