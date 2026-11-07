package com.simon.harmonichackernews.desktop

import com.simon.harmonichackernews.network.NitterInstance
import com.simon.harmonichackernews.network.NitterPreview
import com.simon.harmonichackernews.presentation.WebContentPolicy

/** Immutable policy safe to read from either WebKit's main thread or SWT's browser thread. */
internal data class DesktopArticleNavigation(
    val archiveDomains: List<String> = emptyList(),
    val redirectNitter: Boolean = false,
    val nitterInstance: String = NitterInstance.DEFAULT_URL,
) {
    fun resolve(url: String): String {
        val archived = WebContentPolicy.resolveUrl(url, archiveDomains)?.loadUrl ?: url
        return if (redirectNitter && NitterPreview.isConvertibleUrl(archived)) {
            NitterPreview.convertUrl(archived, nitterInstance)
        } else archived
    }
}

internal data class DesktopCachedArticle(val sourceUrl: String, val html: String)
