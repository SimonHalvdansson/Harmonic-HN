package com.simon.harmonichackernews.linkpreview

import android.content.Context
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.network.LinkPreviewUrls
import com.simon.harmonichackernews.network.NetworkComponent
import kotlinx.coroutines.Job

/** Android callback adapter for the shared suspend-first preview repository. */
object GitHubInfoGetter {
    fun isValidGitHubUrl(url: String): Boolean = LinkPreviewUrls.isGitHubUrl(url)

    fun getInfo(
        githubUrl: String,
        @Suppress("UNUSED_PARAMETER") ctx: Context,
        callback: GetterCallback,
    ): Job? {
        if (!isValidGitHubUrl(githubUrl)) {
            callback.onFailure("Invalid GitHub URL")
            return null
        }
        return NetworkComponent.launchCallbackRequest(
            request = { NetworkComponent.linkPreviewRepository.getGitHubInfo(githubUrl) },
            onSuccess = callback::onSuccess,
            onFailure = { callback.onFailure(it.message ?: "Failed to load GitHub preview") },
        )
    }

    interface GetterCallback {
        fun onSuccess(repoInfo: RepoInfo)
        fun onFailure(reason: String)
    }
}
