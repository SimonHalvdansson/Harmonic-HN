package com.simon.harmonichackernews.linkpreview

import android.content.Context
import com.simon.harmonichackernews.data.ArxivInfo
import com.simon.harmonichackernews.network.LinkPreviewUrls
import com.simon.harmonichackernews.network.NetworkComponent
import kotlinx.coroutines.Job

/** Android callback adapter for the shared suspend-first preview repository. */
object ArxivAbstractGetter {
    fun isValidArxivUrl(url: String): Boolean = LinkPreviewUrls.isArxivUrl(url)

    fun getAbstract(
        url: String,
        @Suppress("UNUSED_PARAMETER") ctx: Context,
        callback: GetterCallback,
    ): Job? {
        if (!isValidArxivUrl(url)) {
            callback.onFailure("Invalid ArXiv URL")
            return null
        }
        return NetworkComponent.launchCallbackRequest(
            request = { NetworkComponent.linkPreviewRepository.getArxivInfo(url) },
            onSuccess = callback::onSuccess,
            onFailure = { callback.onFailure(it.message ?: "Failed to load ArXiv preview") },
        )
    }

    interface GetterCallback {
        fun onSuccess(arxivInfo: ArxivInfo)
        fun onFailure(reason: String)
    }
}
