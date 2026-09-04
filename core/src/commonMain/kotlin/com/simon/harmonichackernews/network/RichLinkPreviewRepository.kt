package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.data.LinkPreviewDetail
import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.serialization.JsonArray
import com.simon.harmonichackernews.serialization.JsonObject
import com.simon.harmonichackernews.utils.HtmlTextUtils
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.URLBuilder
import io.ktor.http.encodeURLPathPart
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

internal data class GitHubPreviewTarget(
    val type: LinkPreviewType,
    val owner: String,
    val repository: String,
    val identifier: String? = null,
    val ref: String? = null,
    val filePath: String? = null,
)

internal data class HuggingFacePreviewTarget(
    val type: LinkPreviewType,
    val owner: String? = null,
    val name: String,
)

internal data class PackagePreviewTarget(
    val type: LinkPreviewType,
    val name: String,
    val variant: String? = null,
)

/** URL classification shared by provider selection, settings fixtures and parser tests. */
object RichLinkPreviewUrls {
    fun type(url: String?): LinkPreviewType? = LinkPreviewProviders.type(url)

    internal fun githubTarget(url: String?): GitHubPreviewTarget? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
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

    internal fun huggingFaceTarget(url: String?): HuggingFacePreviewTarget? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        if (parsed.host.lowercase().removePrefix("www.") != "huggingface.co") return null
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        return when {
            segments.size >= 3 && segments[0] == "datasets" -> HuggingFacePreviewTarget(
                LinkPreviewType.HUGGING_FACE_DATASET,
                segments[1],
                segments[2],
            )
            segments.size >= 3 && segments[0] == "spaces" -> HuggingFacePreviewTarget(
                LinkPreviewType.HUGGING_FACE_SPACE,
                segments[1],
                segments[2],
            )
            segments.size >= 2 && segments[0] == "papers" -> HuggingFacePreviewTarget(
                LinkPreviewType.HUGGING_FACE_PAPER,
                name = segments[1],
            )
            segments.size >= 3 && segments[0] == "collections" -> HuggingFacePreviewTarget(
                LinkPreviewType.HUGGING_FACE_COLLECTION,
                segments[1],
                segments[2],
            )
            LinkPreviewUrls.huggingFaceModel(url) != null -> HuggingFacePreviewTarget(
                LinkPreviewType.HUGGING_FACE_MODEL,
                segments[0],
                segments[1],
            )
            else -> null
        }
    }

    internal fun packageTarget(url: String?): PackagePreviewTarget? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        val host = parsed.host.lowercase().removePrefix("www.")
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        return when {
            host == "npmjs.com" && segments.firstOrNull() == "package" && segments.size >= 2 ->
                PackagePreviewTarget(
                    LinkPreviewType.NPM_PACKAGE,
                    segments.drop(1).take(2).joinToString("/"),
                )
            host == "pypi.org" && segments.firstOrNull() == "project" && segments.size >= 2 ->
                PackagePreviewTarget(LinkPreviewType.PYPI_PACKAGE, segments[1])
            host == "crates.io" && segments.firstOrNull() == "crates" && segments.size >= 2 ->
                PackagePreviewTarget(LinkPreviewType.CRATES_PACKAGE, segments[1])
            host == "pkg.go.dev" && segments.isNotEmpty() && segments.first() != "vuln" ->
                PackagePreviewTarget(LinkPreviewType.GO_PACKAGE, segments.joinToString("/"))
            host == "formulae.brew.sh" && segments.size >= 2 &&
                segments[0] in setOf("formula", "cask") -> PackagePreviewTarget(
                    LinkPreviewType.HOMEBREW_PACKAGE,
                    segments[1],
                    variant = segments[0],
                )
            else -> null
        }
    }

    internal fun statusPageIncident(url: String?): Pair<String, String>? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        val host = parsed.host.lowercase()
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        if (!host.endsWith(".statuspage.io") || segments.size < 2 || segments[0] != "incidents") {
            return null
        }
        return host to segments[1]
    }

    internal fun crossrefDoi(url: String?): String? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        val host = parsed.host.lowercase().removePrefix("www.")
        if (host !in setOf("doi.org", "dx.doi.org")) return null
        return parsed.pathSegments.filter(String::isNotEmpty).joinToString("/").takeIf(String::isNotEmpty)
    }

    internal fun usgsEventId(url: String?): String? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        if (parsed.host.lowercase() != "earthquake.usgs.gov") return null
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        val index = segments.indexOf("eventpage")
        return segments.getOrNull(index + 1)?.takeIf(String::isNotEmpty)
    }

    internal fun isSubstackArticle(url: String?): Boolean {
        val parsed = url?.toNetworkUrlOrNull() ?: return false
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        return parsed.host.lowercase().endsWith(".substack.com") &&
            segments.size >= 2 && segments[0] == "p"
    }

    internal fun mastodonStatus(url: String?): Pair<String, String>? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        if (segments.size < 2 || !segments[0].startsWith("@") || segments[1].any { !it.isDigit() }) {
            return null
        }
        return parsed.host to segments[1]
    }

    internal fun isBlueskyPost(url: String?): Boolean {
        val parsed = url?.toNetworkUrlOrNull() ?: return false
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        return parsed.host.lowercase() == "bsky.app" && segments.size >= 4 &&
            segments[0] == "profile" && segments[2] == "post"
    }

    internal fun isRedditPost(url: String?): Boolean {
        val parsed = url?.toNetworkUrlOrNull() ?: return false
        val host = parsed.host.lowercase().removePrefix("www.").removePrefix("old.")
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        return host == "reddit.com" && "comments" in segments
    }
}

internal suspend fun HttpClient.loadGitHubPreview(
    type: LinkPreviewType,
    url: String,
): LinkPreviewInfo {
    val target = RichLinkPreviewUrls.githubTarget(url)?.takeIf { it.type == type }
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
        RichLinkPreviewParsers.parseGitHub(type, getTextOrThrow(endpoint), target, url)
    } catch (error: HttpStatusException) {
        if (error.statusCode != 403 && error.statusCode != 429) throw error
        RichLinkPreviewParsers.parseGitHubPage(type, getTextOrThrow(url), target, url)
    }
}

internal suspend fun HttpClient.loadHuggingFacePreview(
    type: LinkPreviewType,
    url: String,
): LinkPreviewInfo {
    val target = RichLinkPreviewUrls.huggingFaceTarget(url)?.takeIf { it.type == type }
        ?: throw LinkPreviewException("Invalid ${type.title} URL")
    val endpoint = when (type) {
        LinkPreviewType.HUGGING_FACE_DATASET ->
            "https://huggingface.co/api/datasets/${apiPath(target.owner.orEmpty(), target.name)}"
        LinkPreviewType.HUGGING_FACE_SPACE ->
            "https://huggingface.co/api/spaces/${apiPath(target.owner.orEmpty(), target.name)}"
        LinkPreviewType.HUGGING_FACE_PAPER ->
            "https://huggingface.co/api/papers/${target.name.encodeURLPathPart()}"
        LinkPreviewType.HUGGING_FACE_COLLECTION ->
            "https://huggingface.co/api/collections/${apiPath(target.owner.orEmpty(), target.name)}"
        else -> error("Unexpected Hugging Face preview type")
    }
    return RichLinkPreviewParsers.parseHuggingFace(type, getTextOrThrow(endpoint), target, url)
}

internal suspend fun HttpClient.loadStatusPagePreview(url: String): LinkPreviewInfo {
    val (host, incident) = RichLinkPreviewUrls.statusPageIncident(url)
        ?: throw LinkPreviewException("Invalid Statuspage incident URL")
    return RichLinkPreviewParsers.parseStatusPage(
        getTextOrThrow("https://$host/api/v2/incidents/${incident.encodeURLPathPart()}.json"),
        url,
    )
}

internal suspend fun HttpClient.loadCrossrefPreview(url: String): LinkPreviewInfo {
    val doi = RichLinkPreviewUrls.crossrefDoi(url)
        ?: throw LinkPreviewException("Invalid Crossref DOI URL")
    return RichLinkPreviewParsers.parseCrossref(
        getTextOrThrow("https://api.crossref.org/works/${doi.encodeURLPathPart()}"),
        doi,
        url,
    )
}

internal suspend fun HttpClient.loadUsgsPreview(url: String): LinkPreviewInfo {
    val eventId = RichLinkPreviewUrls.usgsEventId(url)
        ?: throw LinkPreviewException("Invalid USGS earthquake URL")
    val endpoint = URLBuilder("https://earthquake.usgs.gov/fdsnws/event/1/query").apply {
        parameters.append("format", "geojson")
        parameters.append("eventid", eventId)
    }.buildString()
    return RichLinkPreviewParsers.parseUsgs(getTextOrThrow(endpoint), eventId, url)
}

internal suspend fun HttpClient.loadMastodonPreview(url: String): LinkPreviewInfo {
    val (host, statusId) = RichLinkPreviewUrls.mastodonStatus(url)
        ?: throw LinkPreviewException("Invalid Mastodon post URL")
    return RichLinkPreviewParsers.parseMastodon(
        getTextOrThrow("https://$host/api/v1/statuses/${statusId.encodeURLPathPart()}"),
        url,
    )
}

internal suspend fun HttpClient.loadSubstackPreview(url: String): LinkPreviewInfo {
    val parsed = url.toNetworkUrlOrNull()
        ?: throw LinkPreviewException("Invalid Substack article URL")
    return withTimeout(SUBSTACK_REQUEST_TIMEOUT_MILLIS) {
        coroutineScope {
            val pageResponse = async { getTextOrThrow(url) }
            val publicationImage = async {
                loadSubstackPublicationImage("${parsed.scheme}://${parsed.host}/feed")
            }
            RichLinkPreviewParsers.parseSubstackPage(
                pageResponse.await(),
                url,
                publicationImage.await(),
            )
        }
    }
}

private suspend fun HttpClient.loadSubstackPublicationImage(feedUrl: String): String? = try {
    withTimeoutOrNull(SUBSTACK_FEED_TIMEOUT_MILLIS) {
        val feedHeader = getTextPrefixOrThrow(
            url = feedUrl,
            maxBytes = SUBSTACK_FEED_HEADER_MAX_BYTES,
            stopMarkers = listOf("</image>", "<item>"),
        )
        RichLinkPreviewParsers.parseSubstackChannelImage(feedHeader)
    }
} catch (error: CancellationException) {
    throw error
} catch (_: Throwable) {
    null
}

private suspend fun HttpClient.getTextPrefixOrThrow(
    url: String,
    maxBytes: Int,
    stopMarkers: List<String>,
): String = prepareGet(url).execute { response ->
    val channel = response.bodyAsChannel()
    try {
        if (response.status.value !in 200..299) {
            throw HttpStatusException(response.status.value, response.status.description, url)
        }
        val bytes = ByteArray(maxBytes)
        var size = 0
        var text = ""
        while (size < maxBytes) {
            val read = channel.readAvailable(
                bytes,
                size,
                minOf(SUBSTACK_FEED_READ_CHUNK_BYTES, maxBytes - size),
            )
            if (read < 0) break
            if (read == 0) continue
            size += read
            text = bytes.decodeToString(endIndex = size)
            if (stopMarkers.any(text::contains)) break
        }
        text
    } finally {
        channel.cancel()
    }
}

internal suspend fun HttpClient.loadBlueskyPreview(url: String): LinkPreviewInfo {
    val parsed = url.toNetworkUrlOrNull()
        ?: throw LinkPreviewException("Invalid Bluesky post URL")
    val segments = parsed.pathSegments.filter(String::isNotEmpty)
    val handle = segments.getOrNull(1)
        ?: throw LinkPreviewException("Invalid Bluesky post URL")
    val rkey = segments.getOrNull(3)
        ?: throw LinkPreviewException("Invalid Bluesky post URL")
    val did = if (handle.startsWith("did:")) {
        handle
    } else {
        val resolveEndpoint = URLBuilder(
            "https://public.api.bsky.app/xrpc/com.atproto.identity.resolveHandle",
        ).apply { parameters.append("handle", handle) }.buildString()
        JsonObject(getTextOrThrow(resolveEndpoint)).optString("did")
            .takeIf(String::isNotBlank)
            ?: throw LinkPreviewException("Bluesky handle not found")
    }
    val threadEndpoint = URLBuilder(
        "https://public.api.bsky.app/xrpc/app.bsky.feed.getPostThread",
    ).apply {
        parameters.append("uri", "at://$did/app.bsky.feed.post/$rkey")
        parameters.append("depth", "0")
    }.buildString()
    return RichLinkPreviewParsers.parseBluesky(getTextOrThrow(threadEndpoint), url)
}

internal suspend fun HttpClient.loadOEmbedPreview(
    type: LinkPreviewType,
    baseUrl: String,
    url: String,
): LinkPreviewInfo {
    val endpoint = URLBuilder(baseUrl).apply { parameters.append("url", url) }.buildString()
    return RichLinkPreviewParsers.parseOEmbed(type, getTextOrThrow(endpoint), url)
}

internal suspend fun HttpClient.loadPackagePreview(
    type: LinkPreviewType,
    url: String,
): LinkPreviewInfo {
    val target = RichLinkPreviewUrls.packageTarget(url)?.takeIf { it.type == type }
        ?: throw LinkPreviewException("Invalid ${type.title} URL")
    val response = when (type) {
        LinkPreviewType.NPM_PACKAGE ->
            getTextOrThrow("https://registry.npmjs.org/${target.name.encodeURLPathPart()}/latest")
        LinkPreviewType.PYPI_PACKAGE ->
            getTextOrThrow("https://pypi.org/pypi/${target.name.encodeURLPathPart()}/json")
        LinkPreviewType.CRATES_PACKAGE ->
            getTextOrThrow("https://crates.io/api/v1/crates/${target.name.encodeURLPathPart()}")
        LinkPreviewType.GO_PACKAGE -> getTextOrThrow(
            "https://pkg.go.dev/v1beta/package/${target.name.split('/').joinToString("/") { it.encodeURLPathPart() }}",
        )
        LinkPreviewType.HOMEBREW_PACKAGE -> getTextOrThrow(
            "https://formulae.brew.sh/api/${target.variant}/${target.name.encodeURLPathPart()}.json",
        )
        else -> error("Unexpected package preview type")
    }
    return RichLinkPreviewParsers.parsePackage(type, response, target, url)
}

private fun apiPath(vararg parts: String): String =
    parts.joinToString("/") { it.encodeURLPathPart() }

private const val SUBSTACK_REQUEST_TIMEOUT_MILLIS = 15_000L
private const val SUBSTACK_FEED_TIMEOUT_MILLIS = 5_000L
private const val SUBSTACK_FEED_HEADER_MAX_BYTES = 64 * 1024
private const val SUBSTACK_FEED_READ_CHUNK_BYTES = 4 * 1024

internal object RichLinkPreviewParsers {
    private const val GITHUB_PAGE_DESCRIPTION_MAX_CHARS = 600
    private const val MASTODON_DESCRIPTION_MAX_CHARS = 600
    private const val SUBSTACK_DESCRIPTION_MAX_CHARS = 600
    private val monthAbbreviations = listOf(
        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec",
    )

    fun parseGitHub(
        type: LinkPreviewType,
        response: String,
        target: GitHubPreviewTarget,
        url: String,
    ): LinkPreviewInfo {
        val json = JsonObject(response)
        val user = json.optJSONObject("user") ?: json.optJSONObject("author")
        val author = user?.nullableString("login")
        val avatar = user?.nullableString("avatar_url")
        val repoLabel = "${target.owner} / ${target.repository}"
        return when (type) {
            LinkPreviewType.GITHUB_ISSUE -> LinkPreviewInfo(
                type,
                json.optString("title").requiredPreviewTitle(type),
                "$repoLabel · #${target.identifier}",
                json.nullableString("body"),
                avatar,
                url,
                details(
                    "State" to json.nullableString("state")?.titleCase(),
                    "Author" to author,
                    "Comments" to json.optLong("comments").toString(),
                    "Updated" to json.nullableString("updated_at")?.dateOnly(),
                    displayText = mapOf(
                        "Comments" to formatCount(json.optLong("comments"), "comment", "comments"),
                    ),
                ),
            )
            LinkPreviewType.GITHUB_PULL_REQUEST -> LinkPreviewInfo(
                type,
                json.optString("title").requiredPreviewTitle(type),
                "$repoLabel · #${target.identifier}",
                json.nullableString("body"),
                avatar,
                url,
                details(
                    "State" to when {
                        json.optBoolean("merged") -> "Merged"
                        json.optBoolean("draft") -> "Draft"
                        else -> json.nullableString("state")?.titleCase()
                    },
                    "Author" to author,
                    "Changes" to "+${json.optLong("additions")} / −${json.optLong("deletions")}",
                    "Files" to json.optLong("changed_files").toString(),
                    "Commits" to json.optLong("commits").toString(),
                    "Updated" to json.nullableString("updated_at")?.dateOnly(),
                ),
            )
            LinkPreviewType.GITHUB_FILE -> LinkPreviewInfo(
                type,
                json.optString("name").requiredPreviewTitle(type),
                "$repoLabel · ${target.ref}",
                json.nullableString("path"),
                null,
                url,
                details(
                    "Type" to json.nullableString("type")?.titleCase(),
                    "Size" to formatBytes(json.optLong("size")),
                    "Revision" to json.nullableString("sha")?.take(10),
                    "Download" to json.nullableString("download_url")?.let { "Available" },
                ),
            )
            LinkPreviewType.GITHUB_RELEASE -> {
                val tag = json.optString("tag_name").requiredPreviewTitle(type)
                val body = json.nullableString("body")
                val name = json.nullableString("name")
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
                        "Published" to json.nullableString("published_at")?.dateOnly(),
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
                json.nullableString("body"),
                avatar,
                url,
                details(
                    "Category" to json.optJSONObject("category")?.nullableString("name"),
                    "Author" to author,
                    "Comments" to json.optLong("comments").toString(),
                    "Answered" to json.nullableString("answer_html_url")?.let { "Yes" },
                    "Updated" to json.nullableString("updated_at")?.dateOnly(),
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

    fun parseHuggingFace(
        type: LinkPreviewType,
        response: String,
        target: HuggingFacePreviewTarget,
        url: String,
    ): LinkPreviewInfo {
        val json = JsonObject(response)
        val id = json.nullableString("id") ?: listOfNotNull(target.owner, target.name).joinToString("/")
        return when (type) {
            LinkPreviewType.HUGGING_FACE_DATASET -> LinkPreviewInfo(
                type,
                id.substringAfterLast('/'),
                id.substringBeforeLast('/', missingDelimiterValue = target.owner.orEmpty()),
                json.nullableString("description"),
                null,
                url,
                details(
                    "Downloads" to json.optLong("downloads").toString(),
                    "Likes" to json.optLong("likes").toString(),
                    "Updated" to json.nullableString("lastModified")?.dateOnly(),
                    "Format" to tagValue(json, "format:"),
                    "Size" to tagValue(json, "size_categories:"),
                    "Access" to if (json.optBoolean("gated")) "Gated" else "Public",
                ),
            )
            LinkPreviewType.HUGGING_FACE_SPACE -> {
                val card = json.optJSONObject("cardData")
                val runtime = json.optJSONObject("runtime")
                LinkPreviewInfo(
                    type,
                    card?.nullableString("title") ?: id.substringAfterLast('/'),
                    id,
                    card?.nullableString("short_description"),
                    null,
                    url,
                    details(
                        "SDK" to (json.nullableString("sdk") ?: card?.nullableString("sdk")),
                        "Status" to runtime?.nullableString("stage")?.titleCase(),
                        "Hardware" to runtime?.optJSONObject("hardware")?.nullableString("current"),
                        "Likes" to json.optLong("likes").toString(),
                        "License" to card?.nullableString("license"),
                        "Updated" to json.nullableString("lastModified")?.dateOnly(),
                    ),
                )
            }
            LinkPreviewType.HUGGING_FACE_PAPER -> {
                val authors = json.optJSONArray("authors").objectStrings("name")
                LinkPreviewInfo(
                    type,
                    json.optString("title").requiredPreviewTitle(type),
                    "Paper ${json.optString("id", target.name)}",
                    json.nullableString("summary"),
                    null,
                    url,
                    details(
                        "Authors" to authors.take(3).joinToString(", ").takeIf(String::isNotEmpty),
                        "Upvotes" to json.optLong("upvotes").toString(),
                        "GitHub stars" to json.optLong("githubStars").takeIf { it > 0 }?.toString(),
                        "Published" to json.nullableString("publishedAt")?.dateOnly(),
                    ),
                )
            }
            LinkPreviewType.HUGGING_FACE_COLLECTION -> {
                val owner = json.optJSONObject("owner")
                LinkPreviewInfo(
                    type,
                    json.optString("title").requiredPreviewTitle(type),
                    owner?.nullableString("fullname") ?: target.owner,
                    json.nullableString("description"),
                    owner?.nullableString("avatarUrl"),
                    url,
                    details(
                        "Items" to json.optJSONArray("items")?.length()?.toString(),
                        "Followers" to owner?.optLong("followerCount")?.toString(),
                        "Updated" to json.nullableString("lastUpdated")?.dateOnly(),
                    ),
                )
            }
            else -> throw LinkPreviewException("Unsupported Hugging Face preview response")
        }
    }

    fun parseStatusPage(response: String, url: String): LinkPreviewInfo {
        val root = JsonObject(response)
        val incident = root.optJSONObject("incident") ?: root
        val page = root.optJSONObject("page")
        val latest = incident.optJSONArray("incident_updates")?.optJSONObject(0)
        return LinkPreviewInfo(
            LinkPreviewType.STATUS_PAGE,
            incident.optString("name").requiredPreviewTitle(LinkPreviewType.STATUS_PAGE),
            page?.nullableString("name"),
            latest?.nullableString("body"),
            null,
            url,
            details(
                "Status" to incident.nullableString("status")?.humanize(),
                "Impact" to incident.nullableString("impact")?.humanize(),
                "Started" to incident.nullableString("started_at")?.dateOnly(),
                "Updated" to incident.nullableString("updated_at")?.dateOnly(),
            ),
        )
    }

    fun parseCrossref(response: String, doi: String, url: String): LinkPreviewInfo {
        val message = JsonObject(response).getJSONObject("message")
        val title = message.optJSONArray("title")?.optString(0).orEmpty()
        val authors = message.optJSONArray("author")?.let { values ->
            (0..<values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { author ->
                    listOf(author.nullableString("given"), author.nullableString("family"))
                        .filterNotNull().joinToString(" ").takeIf(String::isNotBlank)
                }
            }
        }.orEmpty()
        val published = message.optJSONObject("published")
            ?.optJSONArray("date-parts")
            ?.optJSONArray(0)
            ?.let(::dateParts)
        return LinkPreviewInfo(
            LinkPreviewType.CROSSREF_ARTICLE,
            title.requiredPreviewTitle(LinkPreviewType.CROSSREF_ARTICLE),
            message.optJSONArray("container-title")?.optString(0),
            null,
            null,
            url,
            details(
                "Authors" to authors.take(3).joinToString(", ").takeIf(String::isNotEmpty),
                "Published" to published,
                "Type" to message.nullableString("type")?.humanize(),
                "Publisher" to message.nullableString("publisher"),
                "Citations" to message.optLong("is-referenced-by-count").toString(),
                "DOI" to doi,
            ),
        )
    }

    fun parseUsgs(response: String, eventId: String, url: String): LinkPreviewInfo {
        val root = JsonObject(response)
        val properties = root.getJSONObject("properties")
        return LinkPreviewInfo(
            LinkPreviewType.USGS_EARTHQUAKE,
            properties.nullableString("title") ?: properties.optString("place").requiredPreviewTitle(
                LinkPreviewType.USGS_EARTHQUAKE,
            ),
            "USGS event $eventId",
            properties.nullableString("place"),
            null,
            url,
            details(
                "Magnitude" to properties.numberString("mag"),
                "Depth" to root.optJSONObject("geometry")?.optJSONArray("coordinates")
                    ?.numberString(2)?.let { "$it km" },
                "Type" to properties.nullableString("type")?.titleCase(),
                "Significance" to properties.optLong("sig").toString(),
                "Tsunami" to if (properties.optInt("tsunami") == 1) "Warning" else "No warning",
                "Status" to properties.nullableString("status")?.titleCase(),
            ),
        )
    }

    fun parseSubstackChannelImage(response: String): String? =
        Ksoup.parseXml(response).selectFirst("channel > image > url")?.text()
            ?.takeIf(String::isNotBlank)

    fun parseSubstackPage(
        response: String,
        url: String,
        publicationImageUrl: String? = null,
    ): LinkPreviewInfo {
        val document = Ksoup.parse(response, baseUri = url)
        val article = document.select("script[type=application/ld+json]")
            .firstNotNullOfOrNull { script ->
                runCatching { JsonObject(script.data()) }.getOrNull()
                    ?.takeIf { it.optString("@type") == "NewsArticle" }
            }
        val articleTitle = (
            article?.nullableString("headline")
                ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ).orEmpty().requiredPreviewTitle(LinkPreviewType.SUBSTACK_ARTICLE)
        val publicationTitle = article?.optJSONObject("publisher")?.nullableString("name")
            ?: article?.optJSONArray("author")?.optJSONObject(0)?.nullableString("name")
        val headerTitle = publicationTitle ?: articleTitle
        val published = article?.nullableString("datePublished")
            ?: document.selectFirst("time[datetime]")?.attr("datetime")
        return LinkPreviewInfo(
            type = LinkPreviewType.SUBSTACK_ARTICLE,
            title = headerTitle,
            subtitle = articleTitle.takeUnless { it == headerTitle },
            description = document.selectFirst(".available-content p, .dt-post-body p")?.text()
                ?.let { HtmlTextUtils.normalizeAndTruncatePlainText(it, SUBSTACK_DESCRIPTION_MAX_CHARS) }
                ?.takeIf(String::isNotBlank)
                ?: document.selectFirst("meta[property=og:description]")?.attr("content"),
            imageUrl = publicationImageUrl,
            url = url,
            details = listOfNotNull(
                published?.compactPublishedDate()?.let { compactDate ->
                    LinkPreviewDetail("Published", published, compactDate)
                },
            ),
        )
    }

    fun parseMastodon(response: String, url: String): LinkPreviewInfo {
        val json = JsonObject(response)
        val account = json.getJSONObject("account")
        val displayName = account.nullableString("display_name") ?: account.optString("username")
        val media = json.optJSONArray("media_attachments")?.optJSONObject(0)
        val content = json.nullableString("content")
            ?.let(HtmlTextUtils::plainText)
            ?.let { HtmlTextUtils.normalizeAndTruncatePlainText(it, MASTODON_DESCRIPTION_MAX_CHARS) }
            ?.takeIf(String::isNotBlank)
        val contentWarning = json.nullableString("spoiler_text")
            ?.let(HtmlTextUtils::plainText)
            ?.let { HtmlTextUtils.normalizeAndTruncatePlainText(it, MASTODON_DESCRIPTION_MAX_CHARS) }
            ?.takeIf(String::isNotBlank)
        val description = listOfNotNull(
            contentWarning?.let { "Content warning: $it" },
            content,
        ).joinToString("\n\n").takeIf(String::isNotBlank)
        return LinkPreviewInfo(
            LinkPreviewType.MASTODON_POST,
            displayName.requiredPreviewTitle(LinkPreviewType.MASTODON_POST),
            "@${account.optString("acct")}",
            description,
            media?.nullableString("preview_url") ?: account.nullableString("avatar"),
            url,
            details(
                "Replies" to json.optLong("replies_count").toString(),
                "Boosts" to json.optLong("reblogs_count").toString(),
                "Favourites" to json.optLong("favourites_count").toString(),
                "Published" to json.nullableString("created_at")?.dateOnly(),
            ),
        )
    }

    fun parseBluesky(response: String, url: String): LinkPreviewInfo {
        val post = JsonObject(response).getJSONObject("thread").getJSONObject("post")
        val author = post.getJSONObject("author")
        val record = post.getJSONObject("record")
        val embed = post.optJSONObject("embed")
        val image = embed?.optJSONArray("images")?.optJSONObject(0)?.nullableString("thumb")
            ?: embed?.optJSONObject("media")?.optJSONArray("images")
                ?.optJSONObject(0)?.nullableString("thumb")
        return LinkPreviewInfo(
            LinkPreviewType.BLUESKY_POST,
            author.nullableString("displayName") ?: author.optString("handle"),
            "@${author.optString("handle")}",
            record.nullableString("text"),
            image ?: author.nullableString("avatar"),
            url,
            details(
                "Replies" to post.optLong("replyCount").toString(),
                "Reposts" to post.optLong("repostCount").toString(),
                "Likes" to post.optLong("likeCount").toString(),
                "Quotes" to post.optLong("quoteCount").toString(),
                "Published" to record.nullableString("createdAt")?.dateOnly(),
            ),
        )
    }

    fun parseOEmbed(type: LinkPreviewType, response: String, url: String): LinkPreviewInfo {
        val json = JsonObject(response)
        val author = json.nullableString("author_name")
        return LinkPreviewInfo(
            type,
            when (type) {
                LinkPreviewType.REDDIT_POST -> json.nullableString("title") ?: "Reddit post"
                else -> author ?: type.title
            },
            when (type) {
                LinkPreviewType.BLUESKY_POST -> "Bluesky"
                LinkPreviewType.REDDIT_POST -> author?.let { "$it · Reddit" } ?: "Reddit"
                else -> json.nullableString("provider_name")
            },
            null,
            null,
            url,
            details(
                "Provider" to json.nullableString("provider_name"),
                "Author" to author,
            ),
        )
    }

    fun parsePackage(
        type: LinkPreviewType,
        response: String,
        target: PackagePreviewTarget,
        url: String,
    ): LinkPreviewInfo = when (type) {
        LinkPreviewType.NPM_PACKAGE -> {
            val json = JsonObject(response)
            val author = json.optJSONObject("author")?.nullableString("name")
                ?: json.nullableString("author")
            LinkPreviewInfo(
                type,
                json.optString("name", target.name),
                "npm · ${json.optString("version")}",
                json.nullableString("description"),
                null,
                url,
                details(
                    "Version" to json.nullableString("version"),
                    "License" to json.nullableString("license"),
                    "Author" to author,
                    "Dependencies" to json.optJSONObject("dependencies")?.length()?.toString(),
                    "Node" to json.optJSONObject("engines")?.nullableString("node"),
                ),
            )
        }
        LinkPreviewType.PYPI_PACKAGE -> {
            val info = JsonObject(response).getJSONObject("info")
            LinkPreviewInfo(
                type,
                info.optString("name", target.name),
                "PyPI · ${info.optString("version")}",
                info.nullableString("summary"),
                null,
                url,
                details(
                    "Version" to info.nullableString("version"),
                    "License" to (
                        info.nullableString("license_expression")
                            ?: info.nullableString("license")?.take(48)
                    ),
                    "Author" to info.nullableString("author"),
                    "Python" to info.nullableString("requires_python"),
                    "Project URL" to info.nullableString("project_url"),
                ),
            )
        }
        LinkPreviewType.CRATES_PACKAGE -> {
            val crate = JsonObject(response).getJSONObject("crate")
            LinkPreviewInfo(
                type,
                crate.optString("name", target.name),
                "crates.io · ${crate.optString("newest_version")}",
                crate.nullableString("description"),
                null,
                url,
                details(
                    "Version" to crate.nullableString("newest_version"),
                    "Downloads" to crate.optLong("downloads").toString(),
                    "Recent downloads" to crate.optLong("recent_downloads").toString(),
                    "Updated" to crate.nullableString("updated_at")?.dateOnly(),
                    "Repository" to crate.nullableString("repository")?.shortHost(),
                ),
            )
        }
        LinkPreviewType.GO_PACKAGE -> {
            val json = JsonObject(response)
            LinkPreviewInfo(
                type,
                json.optString("name").requiredPreviewTitle(type),
                json.optString("path", target.name),
                json.nullableString("synopsis"),
                null,
                url,
                details(
                    "Version" to json.nullableString("version"),
                    "Module" to json.nullableString("modulePath"),
                    "Latest" to if (json.optBoolean("isLatest")) "Yes" else "No",
                    "Standard library" to if (json.optBoolean("isStandardLibrary")) "Yes" else "No",
                    "Redistributable" to if (json.optBoolean("isRedistributable")) "Yes" else "No",
                ),
            )
        }
        LinkPreviewType.HOMEBREW_PACKAGE -> {
            val json = JsonObject(response)
            val versions = json.optJSONObject("versions")
            val analytics = json.optJSONObject("analytics")
                ?.optJSONObject("install")?.optJSONObject("30d")
            val name = json.optString("name", target.name)
            LinkPreviewInfo(
                type,
                name,
                if (target.variant == "cask") "Homebrew cask" else "Homebrew formula",
                json.nullableString("desc"),
                null,
                url,
                details(
                    "Version" to (versions?.nullableString("stable") ?: json.nullableString("version")),
                    "License" to json.nullableString("license"),
                    "Installs (30d)" to analytics?.optLong(name)?.toString(),
                    "Dependencies" to json.optJSONArray("dependencies")?.length()?.toString(),
                    "Homepage" to json.nullableString("homepage")?.shortHost(),
                ),
            )
        }
        else -> throw LinkPreviewException("Unsupported package response")
    }

    private fun tagValue(json: JsonObject, prefix: String): String? = json.optJSONArray("tags")
        ?.let { tags ->
            (0..<tags.length()).map(tags::optString).firstOrNull { it.startsWith(prefix) }
        }
        ?.removePrefix(prefix)

    private fun dateParts(parts: JsonArray): String? {
        val year = parts.optInt(0).takeIf { it > 0 } ?: return null
        val month = parts.optInt(1).takeIf { it > 0 }
        val day = parts.optInt(2).takeIf { it > 0 }
        return listOfNotNull(year.toString(), month?.toString()?.padStart(2, '0'), day?.toString()?.padStart(2, '0'))
            .joinToString("-")
    }

    private fun details(
        vararg values: Pair<String, String?>,
        displayText: Map<String, String> = emptyMap(),
    ): List<LinkPreviewDetail> = values
        .mapNotNull { (label, value) ->
            value?.trim()?.takeIf { it.isNotEmpty() && it != "0" }?.let {
                LinkPreviewDetail(label, it, displayText[label])
            }
        }

    private fun formatCount(count: Long, singular: String, plural: String): String =
        "$count ${if (count == 1L) singular else plural}"

    private fun formatBytes(bytes: Long): String? = when {
        bytes <= 0 -> null
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

    private fun String.requiredPreviewTitle(type: LinkPreviewType): String =
        takeIf(String::isNotBlank) ?: throw LinkPreviewException("${type.title} data not found")

    private fun String.dateOnly(): String = take(10)
    private fun String.compactPublishedDate(): String? {
        Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(trim())?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return@let
            val month = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..12 } ?: return@let
            val day = match.groupValues[3].toIntOrNull()?.takeIf { it in 1..31 } ?: return@let
            return "${monthAbbreviations[month - 1]} $day, $year"
        }
        val parts = trim().split(Regex("\\s+"))
        val monthIndex = parts.indexOfFirst { value ->
            monthAbbreviations.any { it.equals(value, ignoreCase = true) }
        }
        if (monthIndex <= 0 || monthIndex >= parts.lastIndex) return null
        val month = monthAbbreviations.first { it.equals(parts[monthIndex], ignoreCase = true) }
        val day = parts[monthIndex - 1].trim(',').toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        val year = parts[monthIndex + 1].trim(',').toIntOrNull()?.takeIf { it in 1000..9999 } ?: return null
        return "$month $day, $year"
    }
    private fun String.titleCase(): String = humanize().replaceFirstChar(Char::uppercase)
    private fun String.humanize(): String = replace('_', ' ')
    private fun String.shortHost(): String? = toNetworkUrlOrNull()?.host?.removePrefix("www.")

    private fun JsonObject.nullableString(key: String): String? =
        (opt(key) as? String)?.takeUnless(String::isBlank)

    private fun JsonObject.numberString(key: String): String? = numberString(opt(key))
    private fun JsonArray.numberString(index: Int): String? = numberString(get(index))

    private fun numberString(value: Any?): String? = when (value) {
        is Long -> value.toString()
        is Int -> value.toString()
        is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        is Float -> if (value % 1f == 0f) value.toLong().toString() else value.toString()
        else -> null
    }

    private fun JsonArray?.objectStrings(key: String): List<String> = this?.let { values ->
        (0..<values.length()).mapNotNull { values.optJSONObject(it)?.nullableString(key) }
    }.orEmpty()
}
