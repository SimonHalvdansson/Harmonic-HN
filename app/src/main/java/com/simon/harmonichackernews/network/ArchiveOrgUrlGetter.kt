package com.simon.harmonichackernews.network

import android.content.Context
import kotlinx.coroutines.Job

/** Android callback adapter for the shared archive.org repository operation. */
object ArchiveOrgUrlGetter {
    fun getArchiveUrl(
        url: String?,
        @Suppress("UNUSED_PARAMETER") ctx: Context,
        callback: GetterCallback,
    ): Job? {
        val target = url?.takeIf(String::isNotBlank)
        if (target == null) {
            callback.onFailure("Missing URL")
            return null
        }
        return NetworkComponent.launchCallbackRequest(
            request = { NetworkComponent.linkPreviewRepository.getArchiveUrl(target) },
            onSuccess = callback::onSuccess,
            onFailure = { callback.onFailure(it.message ?: "Couldn't connect to archive.org") },
        )
    }

    interface GetterCallback {
        fun onSuccess(url: String?)
        fun onFailure(reason: String?)
    }
}
