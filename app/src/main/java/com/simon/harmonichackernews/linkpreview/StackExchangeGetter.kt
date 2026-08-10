package com.simon.harmonichackernews.linkpreview

import android.content.Context
import com.simon.harmonichackernews.data.StackExchangeInfo
import com.simon.harmonichackernews.network.LinkPreviewUrls
import com.simon.harmonichackernews.network.NetworkComponent
import kotlinx.coroutines.Job

/** Android callback adapter for the shared suspend-first preview repository. */
object StackExchangeGetter {
    fun isValidStackExchangeUrl(url: String?): Boolean = LinkPreviewUrls.isStackExchangeUrl(url)

    fun getInfo(
        stackExchangeUrl: String,
        @Suppress("UNUSED_PARAMETER") ctx: Context,
        callback: GetterCallback,
    ): Job? {
        if (!isValidStackExchangeUrl(stackExchangeUrl)) {
            callback.onFailure("Invalid Stack Exchange URL")
            return null
        }
        return NetworkComponent.launchCallbackRequest(
            request = {
                NetworkComponent.linkPreviewRepository.getStackExchangeInfo(stackExchangeUrl)
            },
            onSuccess = callback::onSuccess,
            onFailure = {
                callback.onFailure(it.message ?: "Failed to load Stack Exchange preview")
            },
        )
    }

    interface GetterCallback {
        fun onSuccess(stackExchangeInfo: StackExchangeInfo)
        fun onFailure(reason: String)
    }
}
