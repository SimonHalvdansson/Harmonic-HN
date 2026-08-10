package com.simon.harmonichackernews.linkpreview

import android.content.Context
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.network.LinkPreviewUrls
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.QueueRequest as Request
import com.simon.harmonichackernews.network.StringRequest
import com.simon.harmonichackernews.serialization.JsonObject as JSONObject

/** Android callback adapter retained while the shared provider request is being stabilized. */
object GitHubInfoGetter {
    fun isValidGitHubUrl(url: String): Boolean = LinkPreviewUrls.isGitHubUrl(url)

    fun getInfo(
        githubUrl: String,
        ctx: Context,
        callback: GetterCallback,
    ) {
        val repository = LinkPreviewUrls.gitHubRepository(githubUrl)
        if (repository == null) {
            callback.onFailure("Invalid GitHub URL")
            return
        }

        // Keep the established Android request path for this provider. The shared Ktor migration
        // silently stopped producing cards for otherwise valid GitHub fixtures.
        val request = StringRequest(
            Request.Method.GET,
            "https://api.github.com/repos/${repository.owner}/${repository.name}",
            { response ->
                runCatching { parseResponse(response.orEmpty()) }
                    .onSuccess(callback::onSuccess)
                    .onFailure {
                        it.printStackTrace()
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

    private fun parseResponse(response: String): RepoInfo {
        val json = JSONObject(response)
        return RepoInfo().apply {
            owner = json.optJSONObject("owner")?.optString("login", null)
            name = json.optString("name")
            about = json.optString("description", null)
            website = json.optString("homepage", null)
            license = json.optJSONObject("license")?.let {
                if (it.optString("name") == "Other") "Other" else it.optString("spdx_id", null)
            }
            language = json.optString("language", null)
            stars = json.optInt("stargazers_count")
            watching = json.optInt("subscribers_count")
            forks = json.optInt("forks_count")
        }
    }

    interface GetterCallback {
        fun onSuccess(repoInfo: RepoInfo)
        fun onFailure(reason: String)
    }
}
