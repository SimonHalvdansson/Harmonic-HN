package com.simon.harmonichackernews.linkpreview

import android.content.Context
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.network.NetworkComponent
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.json.JSONObject

object GitHubInfoGetter {
    fun isValidGitHubUrl(url: String): Boolean {
        val regex = "^(https|http)://github\\.com/[^/]+/[^/]+(/.*)?$"
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(url)
        return matcher.matches()
    }

    fun getInfo(githubUrl: String, ctx: Context, callback: GetterCallback) {
        try {
            val parts = githubUrl.split("github.com/".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[1].split("/".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            val username = parts[0]
            val repoName: String? = parts[1]

            val apiUrl = "https://api.github.com/repos/" + username + "/" + repoName

            val stringRequest = StringRequest(
                Request.Method.GET, apiUrl,
                Response.Listener { response: String? ->
                    try {
                        // Parse JSON response from GitHub API
                        val jsonResponse = JSONObject(response)
                        val repoInfo = RepoInfo()

                        if (jsonResponse.has("owner")) {
                            val owner = jsonResponse.getJSONObject("owner")
                            repoInfo.owner = LinkPreviewJsonUtils.getString(owner, "login")
                        }

                        repoInfo.name = jsonResponse.optString("name")
                        repoInfo.about = LinkPreviewJsonUtils.getString(jsonResponse, "description")
                        repoInfo.website = LinkPreviewJsonUtils.getString(jsonResponse, "homepage")

                        if (jsonResponse.has("license") && jsonResponse.get("license")
                                .toString() != "null"
                        ) {
                            val license = jsonResponse.getJSONObject("license")
                            if (license.has("name") && license.getString("name") == "Other") {
                                repoInfo.license = "Other"
                            } else {
                                repoInfo.license =
                                    LinkPreviewJsonUtils.getString(license, "spdx_id")
                            }
                        }

                        repoInfo.language = LinkPreviewJsonUtils.getString(jsonResponse, "language")
                        repoInfo.stars = jsonResponse.optInt("stargazers_count")
                        repoInfo.watching = jsonResponse.optInt("subscribers_count")
                        repoInfo.forks = jsonResponse.optInt("forks_count")

                        callback.onSuccess(repoInfo)
                    } catch (e: Exception) {
                        callback.onFailure("Failed to parse GitHub API response")
                        e.printStackTrace()
                    }
                },
                Response.ErrorListener { error: VolleyError? ->
                    error!!.printStackTrace()
                    callback.onFailure("Couldn't connect to GitHub API")
                })

            val queue = NetworkComponent.getRequestQueueInstance(ctx)
            queue.add<String?>(stringRequest)
        } catch (e: Exception) {
            callback.onFailure("Invalid GitHub URL")
        }
    }

    interface GetterCallback {
        fun onSuccess(repoInfo: RepoInfo?)

        fun onFailure(reason: String?)
    }
}
