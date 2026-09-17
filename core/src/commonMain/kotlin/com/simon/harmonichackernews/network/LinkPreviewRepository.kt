package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.ArxivInfo
import com.simon.harmonichackernews.data.GitLabInfo
import com.simon.harmonichackernews.data.HuggingFaceModelInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.data.OpenRouterModelInfo
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.data.StackExchangeInfo
import com.simon.harmonichackernews.data.WikipediaInfo
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder

/** Suspend-first previews; provider implementations live in their named files. */
interface LinkPreviewRepository {
    suspend fun load(type: LinkPreviewType, url: String): LinkPreviewData

    suspend fun getGitHubInfo(url: String): RepoInfo =
        load(url = url, type = LinkPreviewType.GITHUB_REPOSITORY)
            .requirePayload<LinkPreviewData.GitHub>().value

    suspend fun getWikipediaInfo(url: String): WikipediaInfo =
        load(url = url, type = LinkPreviewType.WIKIPEDIA)
            .requirePayload<LinkPreviewData.Wikipedia>().value

    suspend fun getArchiveUrl(url: String): String
}

class KtorLinkPreviewRepository(
    private val client: suspend () -> HttpClient,
) : LinkPreviewRepository {
    constructor(client: HttpClient) : this({ client })
    override suspend fun load(type: LinkPreviewType, url: String): LinkPreviewData =
        LinkPreviewProviders.load(client(), type, url)

    override suspend fun getArchiveUrl(url: String): String {
        val endpoint = URLBuilder("https://archive.org/wayback/available").apply {
            parameters.append("url", url)
        }.buildString()
        return LinkPreviewParsers.parseArchiveUrl(client().getTextOrThrow(endpoint))
            ?: throw LinkPreviewException("No saved copy on archive.org found")
    }
}

private inline fun <reified T : LinkPreviewData> LinkPreviewData.requirePayload(): T =
    this as? T ?: throw LinkPreviewException("Unexpected ${type.title} preview payload")

class LinkPreviewException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Stable URL helpers used by application code. */
object LinkPreviewUrls {
    fun isArxivUrl(url: String?): Boolean =
        ArxivLinkPreview.isArxivUrl(url)

    fun arxivId(url: String?): String? =
        ArxivLinkPreview.arxivId(url)

    fun isGitHubUrl(url: String?): Boolean =
        GitHubLinkPreview.isGitHubUrl(url)

    fun gitHubRepository(url: String?): GitHubRepository? =
        GitHubLinkPreview.gitHubRepository(url)

    fun isGitLabUrl(url: String?): Boolean =
        GitLabLinkPreview.isGitLabUrl(url)

    fun gitLabProjectPath(url: String?): String? =
        GitLabLinkPreview.gitLabProjectPath(url)

    fun isHuggingFaceUrl(url: String?): Boolean =
        HuggingFaceLinkPreview.isHuggingFaceUrl(url)

    fun huggingFaceModel(url: String?): HuggingFaceModel? =
        HuggingFaceLinkPreview.huggingFaceModel(url)

    fun isOpenRouterUrl(url: String?): Boolean =
        OpenRouterLinkPreview.isOpenRouterUrl(url)

    fun openRouterModel(url: String?): OpenRouterModel? =
        OpenRouterLinkPreview.openRouterModel(url)

    fun isStackExchangeUrl(url: String?): Boolean =
        StackExchangeLinkPreview.isStackExchangeUrl(url)

    fun stackExchangeRequest(url: String?): StackExchangeRequest? =
        StackExchangeLinkPreview.stackExchangeRequest(url)

    fun isWikipediaUrl(url: String?): Boolean =
        WikipediaLinkPreview.isWikipediaUrl(url)

    fun wikipediaTitle(url: String?): String? =
        WikipediaLinkPreview.wikipediaTitle(url)
}

/** Stable parsing API; implementation details belong to each provider. */
object LinkPreviewParsers {
    fun parseArxiv(response: String, arxivId: String): ArxivInfo? =
        ArxivLinkPreview.parseArxiv(response, arxivId)

    fun parseArxivAbstractPage(response: String, arxivId: String): ArxivInfo? =
        ArxivLinkPreview.parseArxivAbstractPage(response, arxivId)

    fun parseArxivHtmlUrl(response: String): String? =
        ArxivLinkPreview.parseArxivHtmlUrl(response)

    fun parseGitHub(response: String): RepoInfo =
        GitHubLinkPreview.parseGitHub(response)

    fun parseGitLab(response: String): GitLabInfo =
        GitLabLinkPreview.parseGitLab(response)

    fun parseHuggingFace(response: String): HuggingFaceModelInfo =
        HuggingFaceLinkPreview.parseHuggingFace(response)

    fun parseOpenRouter(response: String): OpenRouterModelInfo =
        OpenRouterLinkPreview.parseOpenRouter(response)

    fun parseStackExchange(
        response: String,
        request: StackExchangeRequest,
    ): StackExchangeInfo? =
        StackExchangeLinkPreview.parseStackExchange(response, request)

    fun parseWikipedia(response: String): WikipediaInfo? =
        WikipediaLinkPreview.parseWikipedia(response)

    fun firstWikipediaParagraph(summaryHtml: String?): String =
        WikipediaLinkPreview.firstWikipediaParagraph(summaryHtml)

    fun parseArchiveUrl(response: String): String? {
        val snapshots = JsonObject(response).getJSONObject("archived_snapshots")
        val closest = snapshots.optJSONObject("closest") ?: return null
        return closest.takeIf { it.optBoolean("available") }?.nullableString("url")
    }

    private fun JsonObject.nullableString(key: String): String? =
        (opt(key) as? String)?.takeUnless(String::isEmpty)
}

/** URL classification shared by provider selection, settings fixtures and tests. */
object RichLinkPreviewUrls {
    fun type(url: String?): LinkPreviewType? = LinkPreviewProviders.type(url)
}
