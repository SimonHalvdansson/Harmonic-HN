package com.simon.harmonichackernews.linkpreview

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.simon.harmonichackernews.data.GitLabInfo
import com.simon.harmonichackernews.network.NetworkComponent
import java.util.Locale
import org.json.JSONObject

object GitLabInfoGetter {
    fun isValidGitLabUrl(url: String?): Boolean {
        return getProjectPath(url) != null
    }

    fun getInfo(gitLabUrl: String, ctx: Context, callback: GetterCallback) {
        try {
            val projectPath = getProjectPath(gitLabUrl)
            if (TextUtils.isEmpty(projectPath)) {
                callback.onFailure("Invalid GitLab URL")
                return
            }

            val apiUrl = "https://gitlab.com/api/v4/projects/" + Uri.encode(projectPath, "/")
                .replace("/", "%2F")

            val stringRequest = StringRequest(
                Request.Method.GET, apiUrl,
                Response.Listener { response: String? ->
                    try {
                        val jsonResponse = JSONObject(response)
                        val gitLabInfo = GitLabInfo()

                        gitLabInfo.name = LinkPreviewJsonUtils.getString(jsonResponse, "name")
                        gitLabInfo.namespace =
                            LinkPreviewJsonUtils.getString(jsonResponse, "namespace")
                        gitLabInfo.description =
                            LinkPreviewJsonUtils.getString(jsonResponse, "description")
                        gitLabInfo.website = LinkPreviewJsonUtils.getString(jsonResponse, "web_url")
                        gitLabInfo.visibility =
                            LinkPreviewJsonUtils.getString(jsonResponse, "visibility")
                        gitLabInfo.stars = jsonResponse.optInt("star_count")
                        gitLabInfo.forks = jsonResponse.optInt("forks_count")

                        if (jsonResponse.has("namespace") && jsonResponse.get("namespace")
                                .toString() != "null"
                        ) {
                            val namespace = jsonResponse.getJSONObject("namespace")
                            gitLabInfo.namespace =
                                LinkPreviewJsonUtils.getString(namespace, "full_path")
                        }

                        callback.onSuccess(gitLabInfo)
                    } catch (e: Exception) {
                        callback.onFailure("Failed to parse GitLab API response")
                        e.printStackTrace()
                    }
                },
                Response.ErrorListener { error: VolleyError? ->
                    error!!.printStackTrace()
                    callback.onFailure("Couldn't connect to GitLab API")
                })

            val queue = NetworkComponent.getRequestQueueInstance(ctx)
            queue.add<String?>(stringRequest)
        } catch (e: Exception) {
            callback.onFailure("Invalid GitLab URL")
        }
    }

    private fun getProjectPath(url: String?): String? {
        try {
            val uri = Uri.parse(url)
            var host = uri.getHost()
            if (host == null) {
                return null
            }

            host = host.lowercase(Locale.getDefault())
            if (host.startsWith("www.")) {
                host = host.substring(4)
            }

            if (host != "gitlab.com") {
                return null
            }

            val segments = uri.getPathSegments()
            if (segments.size < 2) {
                return null
            }

            var projectPathEnd = segments.size
            for (i in segments.indices) {
                if (segments.get(i) == "-") {
                    projectPathEnd = i
                    break
                }
            }

            if (projectPathEnd < 2) {
                return null
            }

            val builder = StringBuilder()
            for (i in 0..<projectPathEnd) {
                if (i > 0) {
                    builder.append("/")
                }
                builder.append(segments.get(i))
            }
            return builder.toString()
        } catch (e: Exception) {
            return null
        }
    }

    interface GetterCallback {
        fun onSuccess(gitLabInfo: GitLabInfo?)

        fun onFailure(reason: String?)
    }
}
