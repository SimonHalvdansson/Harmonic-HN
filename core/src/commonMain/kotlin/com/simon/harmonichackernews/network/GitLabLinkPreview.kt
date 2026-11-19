package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.GitLabInfo
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLPathPart

internal object GitLabLinkPreview {
    fun isGitLabUrl(url: String?): Boolean = gitLabProjectPath(url) != null

    fun gitLabProjectPath(url: String?): String? {
        return gitLabProjectPath(url?.toNetworkUrlOrNull() ?: return null)
    }

    fun gitLabProjectPath(parsed: NetworkUrl): String? {
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
            name = json.nonEmptyString("name"),
            namespace = json.optJSONObject("namespace")?.nonEmptyString("full_path")
                ?: json.nonEmptyString("namespace"),
            description = json.nonEmptyString("description"),
            website = json.nonEmptyString("web_url"),
            visibility = json.nonEmptyString("visibility"),
            stars = json.optInt("star_count"),
            forks = json.optInt("forks_count"),
        )
    }
}

internal suspend fun HttpClient.loadGitLabInfo(url: String): GitLabInfo {
    val projectPath = GitLabLinkPreview.gitLabProjectPath(url)
        ?: throw LinkPreviewException("Invalid GitLab URL")
    val endpoint = "https://gitlab.com/api/v4/projects/${projectPath.encodeURLPathPart()}"
    return GitLabLinkPreview.parseGitLab(getTextOrThrow(endpoint))
}
