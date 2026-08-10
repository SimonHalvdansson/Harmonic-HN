package com.simon.harmonichackernews.linkpreview

import android.content.Context
import com.simon.harmonichackernews.data.WikipediaInfo
import com.simon.harmonichackernews.network.LinkPreviewParsers
import com.simon.harmonichackernews.network.LinkPreviewUrls
import com.simon.harmonichackernews.network.NetworkComponent
import kotlinx.coroutines.Job

/** Android callback adapter for the shared suspend-first preview repository. */
object WikipediaGetter {
    fun isValidWikipediaUrl(url: String): Boolean = LinkPreviewUrls.isWikipediaUrl(url)

    fun getInfo(
        wikipediaUrl: String,
        @Suppress("UNUSED_PARAMETER") ctx: Context,
        callback: GetterCallback,
    ): Job? {
        if (!isValidWikipediaUrl(wikipediaUrl)) {
            callback.onFailure("Invalid Wikipedia URL")
            return null
        }
        return NetworkComponent.launchCallbackRequest(
            request = { NetworkComponent.linkPreviewRepository.getWikipediaInfo(wikipediaUrl) },
            onSuccess = callback::onSuccess,
            onFailure = { callback.onFailure(it.message ?: "Failed to load Wikipedia preview") },
        )
    }

    fun getFirstParagraphText(summaryHtml: String?): String =
        LinkPreviewParsers.firstWikipediaParagraph(summaryHtml)

    interface GetterCallback {
        fun onSuccess(wikiInfo: WikipediaInfo)
        fun onFailure(reason: String)
    }
}
