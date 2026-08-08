package com.simon.harmonichackernews.linkpreview

import android.content.Context
import com.simon.harmonichackernews.network.QueueRequest as Request
import com.simon.harmonichackernews.network.StringRequest
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.serialization.JsonObject as JSONObject

object GitHubInfoGetter {
    private val githubUrlRegex = Regex("^https?://github\\.com/[^/]+/[^/]+(/.*)?$")

    fun isValidGitHubUrl(url: String): Boolean = githubUrlRegex.matches(url)

    fun getInfo(githubUrl: String, ctx: Context, callback: GetterCallback) {
        val repository = getRepositoryPath(githubUrl)
        if (repository == null) {
            callback.onFailure("Invalid GitHub URL")
            return
        }

        val apiUrl = "https://api.github.com/repos/${repository.owner}/${repository.name}"
        val request = StringRequest(
            Request.Method.GET,
            apiUrl,
            { response ->
                runCatching { parseResponse(response.orEmpty()) }
                    .onSuccess(callback::onSuccess)
                    .onFailure { error ->
                        error.printStackTrace()
                        callback.onFailure("Failed to parse GitHub API response")
                    }
            },
            { error ->
                error?.printStackTrace()
                callback.onFailure("Couldn't connect to GitHub API")
            },
        )
        NetworkComponent.getRequestQueueInstance(ctx).add(request)
    }

    private fun getRepositoryPath(url: String): RepositoryPath? {
        if (!isValidGitHubUrl(url)) return null

        val parts = url.substringAfter("github.com/").split('/')
        return RepositoryPath(owner = parts[0], name = parts[1])
    }

    private fun parseResponse(response: String): RepoInfo {
        val json = JSONObject(response)
        return RepoInfo().apply {
            owner = json.optJSONObject("owner")?.let {
                LinkPreviewJsonUtils.getString(it, "login")
            }
            name = json.optString("name")
            about = LinkPreviewJsonUtils.getString(json, "description")
            website = LinkPreviewJsonUtils.getString(json, "homepage")
            license = json.optJSONObject("license")?.let {
                if (it.optString("name") == "Other") {
                    "Other"
                } else {
                    LinkPreviewJsonUtils.getString(it, "spdx_id")
                }
            }
            language = LinkPreviewJsonUtils.getString(json, "language")
            stars = json.optInt("stargazers_count")
            watching = json.optInt("subscribers_count")
            forks = json.optInt("forks_count")
        }
    }

    private data class RepositoryPath(val owner: String, val name: String)

    interface GetterCallback {
        fun onSuccess(repoInfo: RepoInfo)

        fun onFailure(reason: String)
    }
}
