package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.GitLabInfo
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLPathPart

internal object GitLabLinkPreview {
    fun isGitLabUrl(url: String?): Boolean = gitLabProjectPath(url) != null

    fun gitLabProjectPath(url: String?): String? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        if (parsed.host.lowercase().removePrefix("www.") != "gitlab.com") return null
        return parsed.pathSegments
            .filter(String::isNotEmpty)
            .takeWhile { it != "-" }
            .takeIf { it.size >= 2 }
            ?.joinToString("/")
    }

    fun parseGitLab(response: String): GitLabInfo {
        val json = JsonObject(response)
        return GitLabInfo(
            name = json.nullableString("name"),
            namespace = json.optJSONObject("namespace")?.nullableString("full_path")
                ?: json.nullableString("namespace"),
            description = json.nullableString("description"),
            website = json.nullableString("web_url"),
            visibility = json.nullableString("visibility"),
            stars = json.optInt("star_count"),
            forks = json.optInt("forks_count"),
        )
    }

    private fun JsonObject.nullableString(key: String): String? =
        (opt(key) as? String)?.takeUnless(String::isEmpty)
}

internal suspend fun HttpClient.loadGitLabInfo(url: String): GitLabInfo {
    val projectPath = GitLabLinkPreview.gitLabProjectPath(url)
        ?: throw LinkPreviewException("Invalid GitLab URL")
    val endpoint = "https://gitlab.com/api/v4/projects/${projectPath.encodeURLPathPart()}"
    return GitLabLinkPreview.parseGitLab(getTextOrThrow(endpoint))
}
