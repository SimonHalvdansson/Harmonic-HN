package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.network.LinkSummaryParser
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import com.simon.harmonichackernews.utils.ReferenceLinkRowUtils

/** Resolves HN story references to their post title while leaving HN comment references alone. */
@Composable
fun rememberReferenceLinkLabel(link: CollectedReferenceLinks.ReferenceLink): String {
    val url = link.url.orEmpty()
    val fallback = ReferenceLinkRowUtils.getReferenceLinkLabel(link)
    if (LinkSummaryParser.hackerNewsItemId(url) == null) return fallback
    val dependencies = LocalHarmonicUiDependencies.current
    var label by remember(url, link.resolvedTitle) { mutableStateOf(fallback) }

    LaunchedEffect(url) {
        val summary = runCatching {
            dependencies.previewResources.cachedLinkSummary(url)?.takeIf {
                it.contentType == LinkSummaryParser.HACKER_NEWS_ITEM_CONTENT_TYPE
            } ?: dependencies.network.linkSummaryRepository.load(url, fallback).also {
                dependencies.previewResources.saveLinkSummary(url, it)
            }
        }.getOrNull()
        if (summary != null && LinkSummaryParser.isHackerNewsStory(summary)) {
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
