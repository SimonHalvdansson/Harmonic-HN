package com.simon.harmonichackernews.linkpreview

import android.content.Context
import com.simon.harmonichackernews.data.GitLabInfo
import com.simon.harmonichackernews.network.LinkPreviewUrls
import com.simon.harmonichackernews.network.NetworkComponent
import kotlinx.coroutines.Job

/** Android callback adapter for the shared suspend-first preview repository. */
object GitLabInfoGetter {
    fun isValidGitLabUrl(url: String?): Boolean = LinkPreviewUrls.isGitLabUrl(url)

    fun getInfo(
        gitLabUrl: String,
        @Suppress("UNUSED_PARAMETER") ctx: Context,
        callback: GetterCallback,
    ): Job? {
        if (!isValidGitLabUrl(gitLabUrl)) {
            callback.onFailure("Invalid GitLab URL")
            return null
        }
        return NetworkComponent.launchCallbackRequest(
            request = { NetworkComponent.linkPreviewRepository.getGitLabInfo(gitLabUrl) },
            onSuccess = callback::onSuccess,
            onFailure = { callback.onFailure(it.message ?: "Failed to load GitLab preview") },
        )
    }

    interface GetterCallback {
        fun onSuccess(gitLabInfo: GitLabInfo)
        fun onFailure(reason: String)
    }
}
