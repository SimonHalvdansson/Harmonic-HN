package com.simon.harmonichackernews.linkpreview

import android.content.Context
import android.net.Uri
import com.simon.harmonichackernews.network.QueueRequest as Request
import com.simon.harmonichackernews.network.StringRequest
import com.simon.harmonichackernews.data.GitLabInfo
import com.simon.harmonichackernews.network.NetworkComponent
import java.util.Locale
import com.simon.harmonichackernews.serialization.JsonObject as JSONObject

object GitLabInfoGetter {
    fun isValidGitLabUrl(url: String?): Boolean = getProjectPath(url) != null

    fun getInfo(gitLabUrl: String, ctx: Context, callback: GetterCallback) {
        val projectPath = getProjectPath(gitLabUrl)
        if (projectPath == null) {
            callback.onFailure("Invalid GitLab URL")
            return
        }

        val encodedProjectPath = Uri.encode(projectPath, "/").replace("/", "%2F")
        val apiUrl = "https://gitlab.com/api/v4/projects/$encodedProjectPath"
        val request = StringRequest(
            Request.Method.GET,
            apiUrl,
            { response ->
                runCatching { parseResponse(response.orEmpty()) }
                    .onSuccess(callback::onSuccess)
                    .onFailure { error ->
                        error.printStackTrace()
                        callback.onFailure("Failed to parse GitLab API response")
                    }
            },
            { error ->
                error?.printStackTrace()
                callback.onFailure("Couldn't connect to GitLab API")
            },
        )
        NetworkComponent.getRequestQueueInstance(ctx).add(request)
    }

    private fun getProjectPath(url: String?): String? {
        url ?: return null

        try {
            val uri = Uri.parse(url)
            val host = uri.host
                ?.lowercase(Locale.getDefault())
                ?.removePrefix("www.")
            if (host != "gitlab.com") return null

            val projectSegments = uri.pathSegments.takeWhile { it != "-" }
            return projectSegments
                .takeIf { it.size >= 2 }
                ?.joinToString("/")
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseResponse(response: String): GitLabInfo {
        val json = JSONObject(response)
        return GitLabInfo().apply {
            name = LinkPreviewJsonUtils.getString(json, "name")
            namespace = json.optJSONObject("namespace")?.let {
                LinkPreviewJsonUtils.getString(it, "full_path")
            } ?: LinkPreviewJsonUtils.getString(json, "namespace")
            description = LinkPreviewJsonUtils.getString(json, "description")
            website = LinkPreviewJsonUtils.getString(json, "web_url")
            visibility = LinkPreviewJsonUtils.getString(json, "visibility")
            stars = json.optInt("star_count")
            forks = json.optInt("forks_count")
        }
    }

    interface GetterCallback {
        fun onSuccess(gitLabInfo: GitLabInfo)

        fun onFailure(reason: String)
    }
}
