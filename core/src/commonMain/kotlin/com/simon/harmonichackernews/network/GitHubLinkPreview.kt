package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.data.LinkPreviewDetail
import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.serialization.JsonObject
import com.simon.harmonichackernews.utils.HtmlTextUtils
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.encodeURLPathPart

data class GitHubRepository(val owner: String, val name: String)

internal data class GitHubPreviewTarget(
    val type: LinkPreviewType,
    val owner: String,
    val repository: String,
    val identifier: String? = null,
    val ref: String? = null,
    val filePath: String? = null,
)

internal object GitHubLinkPreview {
    fun isGitHubUrl(url: String?): Boolean = githubTarget(url) != null

    fun gitHubRepository(url: String?): GitHubRepository? {
        val target = githubTarget(url) ?: return null
        return GitHubRepository(target.owner, target.repository)
    }

    internal fun githubTarget(url: String?): GitHubPreviewTarget? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        if (parsed.scheme !in setOf("http", "https")) return null
        if (parsed.host.lowercase().removePrefix("www.") != "github.com") return null
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        if (segments.size < 2) return null
        val owner = segments[0]
        val repository = segments[1].removeSuffix(".git")
        if (repository.isBlank()) return null
        return when {
            segments.size >= 4 && segments[2] == "issues" -> GitHubPreviewTarget(
                LinkPreviewType.GITHUB_ISSUE,
                owner,
                repository,
                identifier = segments[3],
            )
            segments.size >= 4 && segments[2] == "pull" -> GitHubPreviewTarget(
                LinkPreviewType.GITHUB_PULL_REQUEST,
                owner,
                repository,
                identifier = segments[3],
            )
            segments.size >= 5 && segments[2] == "blob" -> GitHubPreviewTarget(
                LinkPreviewType.GITHUB_FILE,
                owner,
                repository,
                ref = segments[3],
                filePath = segments.drop(4).joinToString("/"),
            )
            segments.size >= 5 && segments[2] == "releases" && segments[3] == "tag" ->
                GitHubPreviewTarget(
                    LinkPreviewType.GITHUB_RELEASE,
                    owner,
                    repository,
                    identifier = segments.drop(4).joinToString("/"),
                )
            segments.size >= 4 && segments[2] == "discussions" -> GitHubPreviewTarget(
                LinkPreviewType.GITHUB_DISCUSSION,
                owner,
                repository,
                identifier = segments[3],
            )
            else -> GitHubPreviewTarget(LinkPreviewType.GITHUB_REPOSITORY, owner, repository)
        }
    }

    fun parseGitHub(response: String): RepoInfo {
        val json = JsonObject(response)
        val ownerJson = json.optJSONObject("owner")
        return RepoInfo(
            owner = ownerJson?.nullableString("login"),
            avatarUrl = ownerJson?.nullableString("avatar_url"),
            name = json.optString("name"),
            about = json.nullableString("description"),
            website = json.nullableString("homepage"),
            license = json.optJSONObject("license")?.let {
                if (it.optString("name") == "Other") "Other" else it.nullableString("spdx_id")
            },
            language = json.nullableString("language"),
            stars = json.optInt("stargazers_count"),
            watching = json.optInt("subscribers_count"),
            forks = json.optInt("forks_count"),
        )
    }

    fun parseGitHub(
        type: LinkPreviewType,
        response: String,
        target: GitHubPreviewTarget,
        url: String,
    ): LinkPreviewInfo {
        val json = JsonObject(response)
        val user = json.optJSONObject("user") ?: json.optJSONObject("author")
        val author = user?.nonBlankString("login")
        val avatar = user?.nonBlankString("avatar_url")
        val repoLabel = "${target.owner} / ${target.repository}"
        return when (type) {
            LinkPreviewType.GITHUB_ISSUE -> LinkPreviewInfo(
                type,
                json.optString("title").requiredPreviewTitle(type),
                "$repoLabel · #${target.identifier}",
                json.nonBlankString("body"),
                avatar,
                url,
                details(
                    "State" to json.nonBlankString("state")?.titleCase(),
                    "Author" to author,
                    "Comments" to json.optLong("comments").toString(),
                    "Updated" to json.nonBlankString("updated_at")?.dateOnly(),
                    displayText = mapOf(
                        "Comments" to formatCount(json.optLong("comments"), "comment", "comments"),
                    ),
                ),
            )
            LinkPreviewType.GITHUB_PULL_REQUEST -> LinkPreviewInfo(
                type,
                json.optString("title").requiredPreviewTitle(type),
                "$repoLabel · #${target.identifier}",
                json.nonBlankString("body"),
                avatar,
                url,
                details(
                    "State" to when {
                        json.optBoolean("merged") -> "Merged"
                        json.optBoolean("draft") -> "Draft"
                        else -> json.nonBlankString("state")?.titleCase()
                    },
                    "Author" to author,
                    "Changes" to "+${json.optLong("additions")} / −${json.optLong("deletions")}",
                    "Files" to json.optLong("changed_files").toString(),
                    "Commits" to json.optLong("commits").toString(),
                    "Updated" to json.nonBlankString("updated_at")?.dateOnly(),
                ),
            )
            LinkPreviewType.GITHUB_FILE -> LinkPreviewInfo(
                type,
                json.optString("name").requiredPreviewTitle(type),
                "$repoLabel · ${target.ref}",
                json.nonBlankString("path"),
                null,
                url,
                details(
                    "Type" to json.nonBlankString("type")?.titleCase(),
                    "Size" to formatBytes(json.optLong("size")),
                    "Revision" to json.nonBlankString("sha")?.take(10),
                    "Download" to json.nonBlankString("download_url")?.let { "Available" },
                ),
            )
            LinkPreviewType.GITHUB_RELEASE -> {
                val tag = json.optString("tag_name").requiredPreviewTitle(type)
                val body = json.nonBlankString("body")
                val name = json.nonBlankString("name")
                    ?: githubReleaseHeading(body)
                    ?: tag
                LinkPreviewInfo(
                    type,
                    name,
                    listOfNotNull(
                        "GitHub release",
                        repoLabel,
                        tag.takeUnless { it == name },
                    ).joinToString(" · "),
                    body,
                    githubReleaseImage(body, url),
                    url,
                    details(
                        "Author" to author,
                        "Published" to json.nonBlankString("published_at")?.dateOnly(),
                        "Kind" to when {
                            json.optBoolean("prerelease") -> "Pre-release"
                            json.optBoolean("draft") -> "Draft"
                            else -> "Release"
                        },
                        "Assets" to json.optJSONArray("assets")?.length()?.toString(),
                    ),
                )
            }
            LinkPreviewType.GITHUB_DISCUSSION -> LinkPreviewInfo(
                type,
                json.optString("title").requiredPreviewTitle(type),
                "$repoLabel · #${target.identifier}",
                json.nonBlankString("body"),
                avatar,
                url,
                details(
                    "Category" to json.optJSONObject("category")?.nonBlankString("name"),
                    "Author" to author,
                    "Comments" to json.optLong("comments").toString(),
                    "Answered" to json.nonBlankString("answer_html_url")?.let { "Yes" },
                    "Updated" to json.nonBlankString("updated_at")?.dateOnly(),
                    displayText = mapOf(
                        "Comments" to formatCount(json.optLong("comments"), "comment", "comments"),
                    ),
                ),
            )
            else -> throw LinkPreviewException("Unsupported GitHub preview response")
        }
    }

    fun parseGitHubPage(
        type: LinkPreviewType,
        response: String,
        target: GitHubPreviewTarget,
        url: String,
    ): LinkPreviewInfo {
        val document = Ksoup.parse(response, baseUri = url)
        val repository = "${target.owner}/${target.repository}"
        val rawTitle = document.selectFirst("meta[property=og:title]")?.attr("content")
            .orEmpty()
            .requiredPreviewTitle(type)
        val providerSuffix = " · $repository"
        val pageTitle = rawTitle.removeSuffix(providerSuffix).let { title ->
            when (type) {
                LinkPreviewType.GITHUB_ISSUE ->
                    title.removeSuffix(" · Issue #${target.identifier}")
                LinkPreviewType.GITHUB_PULL_REQUEST ->
                    title.removeSuffix(" · Pull Request #${target.identifier}")
                LinkPreviewType.GITHUB_DISCUSSION ->
                    title.removeSuffix(" · Discussion #${target.identifier}")
                LinkPreviewType.GITHUB_RELEASE -> title.removePrefix("Release ")
                else -> title
            }
        }
        val repoLabel = "${target.owner} / ${target.repository}"
        val subtitle = when (type) {
            LinkPreviewType.GITHUB_ISSUE,
            LinkPreviewType.GITHUB_PULL_REQUEST,
            LinkPreviewType.GITHUB_DISCUSSION,
            -> "$repoLabel · #${target.identifier}"
            LinkPreviewType.GITHUB_FILE -> "$repoLabel · ${target.ref}"
            LinkPreviewType.GITHUB_RELEASE -> "GitHub release · $repoLabel"
            else -> repoLabel
        }
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
            ?.let(HtmlTextUtils::plainText)
            ?.let {
                HtmlTextUtils.normalizeAndTruncatePlainText(
                    it,
                    GITHUB_PAGE_DESCRIPTION_MAX_CHARS,
                )
            }
            ?.takeIf(String::isNotBlank)
        val author = document.selectFirst("meta[property=og:author:username]")
            ?.attr("content")
            ?.takeIf(String::isNotBlank)
        val imageUrl = if (type == LinkPreviewType.GITHUB_RELEASE) {
            document.selectFirst("meta[property=og:image], meta[name=twitter:image]")
                ?.attr("content")
                ?.let(LinkSummaryParser::normalizeHttpUrl)
        } else {
            null
        }
        return LinkPreviewInfo(
            type = type,
            title = pageTitle,
            subtitle = subtitle,
            description = description,
            imageUrl = imageUrl,
            url = url,
            details = listOfNotNull(
                LinkPreviewDetail("Kind", "Release")
                    .takeIf { type == LinkPreviewType.GITHUB_RELEASE },
                author?.let { LinkPreviewDetail("Author", it) },
            ),
        )
    }

    private fun githubReleaseHeading(body: String?): String? = body
        ?.lineSequence()
        ?.map(String::trim)
        ?.firstOrNull { line -> line.startsWith('#') && line.dropWhile { it == '#' }.startsWith(' ') }
        ?.dropWhile { it == '#' }
        ?.trim()
        ?.removeSurrounding("**")
        ?.takeIf(String::isNotBlank)

    private fun githubReleaseImage(body: String?, pageUrl: String): String? {
        if (body.isNullOrBlank()) return null
        val htmlImage = Ksoup.parse(body, baseUri = pageUrl)
            .selectFirst("img[src]")
            ?.let { image -> image.absUrl("src").ifBlank { image.attr("src") } }
            ?.let(LinkSummaryParser::normalizeHttpUrl)
        if (htmlImage != null) return htmlImage
        return Regex("!\\[[^]]*]\\((https?://[^\\s)]+)")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(LinkSummaryParser::normalizeHttpUrl)
    }

    private const val GITHUB_PAGE_DESCRIPTION_MAX_CHARS = 600

    private fun formatCount(count: Long, singular: String, plural: String): String =
        "$count ${if (count == 1L) singular else plural}"

    private fun formatBytes(bytes: Long): String? = when {
        bytes <= 0 -> null
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

    private fun JsonObject.nullableString(key: String): String? =
        (opt(key) as? String)?.takeUnless(String::isEmpty)
}

internal suspend fun HttpClient.loadGitHubInfo(url: String): RepoInfo {
    val repository = GitHubLinkPreview.gitHubRepository(url)
        ?: throw LinkPreviewException("Invalid GitHub URL")
    val endpoint = "https://api.github.com/repos/" +
        repository.owner.encodeURLPathPart() + "/" + repository.name.encodeURLPathPart()
    return GitHubLinkPreview.parseGitHub(getTextOrThrow(endpoint))
}

internal suspend fun HttpClient.loadGitHubPreview(
    type: LinkPreviewType,
    url: String,
): LinkPreviewInfo {
    val target = GitHubLinkPreview.githubTarget(url)?.takeIf { it.type == type }
        ?: throw LinkPreviewException("Invalid ${type.title} URL")
    val root = "https://api.github.com/repos/${apiPath(target.owner, target.repository)}"
    val endpoint = when (type) {
        LinkPreviewType.GITHUB_ISSUE -> "$root/issues/${target.identifier?.encodeURLPathPart()}"
        LinkPreviewType.GITHUB_PULL_REQUEST -> "$root/pulls/${target.identifier?.encodeURLPathPart()}"
        LinkPreviewType.GITHUB_FILE -> URLBuilder(
            "$root/contents/${target.filePath.orEmpty().split('/').joinToString("/") { it.encodeURLPathPart() }}",
        ).apply { parameters.append("ref", target.ref.orEmpty()) }.buildString()
        LinkPreviewType.GITHUB_RELEASE -> "$root/releases/tags/${target.identifier?.encodeURLPathPart()}"
        LinkPreviewType.GITHUB_DISCUSSION -> "$root/discussions/${target.identifier?.encodeURLPathPart()}"
        else -> error("Unexpected GitHub preview type")
    }
    return try {
        GitHubLinkPreview.parseGitHub(type, getTextOrThrow(endpoint), target, url)
    } catch (error: HttpStatusException) {
        if (error.statusCode != 403 && error.statusCode != 429) throw error
        GitHubLinkPreview.parseGitHubPage(type, getTextOrThrow(url), target, url)
    }
}
