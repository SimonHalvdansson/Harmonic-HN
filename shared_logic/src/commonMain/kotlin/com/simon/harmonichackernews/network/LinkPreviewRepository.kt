package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.simon.harmonichackernews.data.ArxivInfo
import com.simon.harmonichackernews.data.GitLabInfo
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.data.StackExchangeInfo
import com.simon.harmonichackernews.data.WikipediaInfo
import com.simon.harmonichackernews.serialization.JsonObject
import com.simon.harmonichackernews.utils.ArxivResolver
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.encodeURLPathPart

/** Suspend-first provider previews. Platform UI and lifecycle concerns stay outside this API. */
interface LinkPreviewRepository {
    suspend fun getArxivInfo(url: String): ArxivInfo
    suspend fun getGitHubInfo(url: String): RepoInfo
    suspend fun getGitLabInfo(url: String): GitLabInfo
    suspend fun getStackExchangeInfo(url: String): StackExchangeInfo
    suspend fun getWikipediaInfo(url: String): WikipediaInfo
    suspend fun getArchiveUrl(url: String): String
}

class KtorLinkPreviewRepository(
    private val client: HttpClient,
) : LinkPreviewRepository {
    override suspend fun getArxivInfo(url: String): ArxivInfo {
        val arxivId = LinkPreviewUrls.arxivId(url)
            ?: throw LinkPreviewException("Invalid ArXiv URL")
        val endpoint = URLBuilder("https://export.arxiv.org/api/query").apply {
            parameters.append("id_list", arxivId)
        }.buildString()
        return LinkPreviewParsers.parseArxiv(client.getTextOrThrow(endpoint), arxivId)
            ?: throw LinkPreviewException("ArXiv data not found")
    }

    override suspend fun getGitHubInfo(url: String): RepoInfo {
        val repository = LinkPreviewUrls.gitHubRepository(url)
            ?: throw LinkPreviewException("Invalid GitHub URL")
        val endpoint = "https://api.github.com/repos/" +
            repository.owner.encodeURLPathPart() + "/" + repository.name.encodeURLPathPart()
        return LinkPreviewParsers.parseGitHub(client.getTextOrThrow(endpoint))
    }

    override suspend fun getGitLabInfo(url: String): GitLabInfo {
        val projectPath = LinkPreviewUrls.gitLabProjectPath(url)
            ?: throw LinkPreviewException("Invalid GitLab URL")
        val endpoint = "https://gitlab.com/api/v4/projects/${projectPath.encodeURLPathPart()}"
        return LinkPreviewParsers.parseGitLab(client.getTextOrThrow(endpoint))
    }

    override suspend fun getStackExchangeInfo(url: String): StackExchangeInfo {
        val request = LinkPreviewUrls.stackExchangeRequest(url)
            ?: throw LinkPreviewException("Invalid Stack Exchange URL")
        val path = if (request.isAnswer) {
            "answers/${request.id}/questions"
        } else {
            "questions/${request.id}"
        }
        val endpoint = URLBuilder("https://api.stackexchange.com/2.3/$path").apply {
            parameters.append("site", request.siteParam)
            parameters.append("filter", "withbody")
        }.buildString()
        return LinkPreviewParsers.parseStackExchange(
            client.getTextOrThrow(endpoint),
            request,
        ) ?: throw LinkPreviewException("Stack Exchange question not found")
    }

    override suspend fun getWikipediaInfo(url: String): WikipediaInfo {
        val title = LinkPreviewUrls.wikipediaTitle(url)
            ?: throw LinkPreviewException("Invalid Wikipedia URL")
        val endpoint = URLBuilder("https://en.wikipedia.org/w/api.php").apply {
            parameters.append("format", "json")
            parameters.append("action", "query")
            parameters.append("prop", "extracts")
            parameters.append("exintro", "")
            parameters.append("titles", title)
        }.buildString()
        return LinkPreviewParsers.parseWikipedia(client.getTextOrThrow(endpoint))
            ?: throw LinkPreviewException("Wikipedia did not return a visible summary")
    }

    override suspend fun getArchiveUrl(url: String): String {
        val endpoint = URLBuilder("https://archive.org/wayback/available").apply {
            parameters.append("url", url)
        }.buildString()
        return LinkPreviewParsers.parseArchiveUrl(client.getTextOrThrow(endpoint))
            ?: throw LinkPreviewException("No saved copy on archive.org found")
    }
}

class LinkPreviewException(message: String, cause: Throwable? = null) : Exception(message, cause)

object LinkPreviewUrls {
    private val arxivUrlRegex = Regex(
        "^https?://arxiv\\.org/(abs|pdf)/((\\d{4}\\.\\d{4,5}(v\\d+)?)|" +
            "([a-z\\-]+/\\d{2}\\d{4}))(\\.pdf)?$",
    )
    private val githubUrlRegex = Regex("^https?://github\\.com/[^/]+/[^/]+(/.*)?$")
    private val wikipediaUrlRegex = Regex("^https?://en\\.wikipedia\\.org/wiki/.+")

    private val stackExchangeSites = mapOf(
        "stackoverflow.com" to "stackoverflow",
        "serverfault.com" to "serverfault",
        "superuser.com" to "superuser",
        "askubuntu.com" to "askubuntu",
        "mathoverflow.net" to "mathoverflow",
        "stackapps.com" to "stackapps",
        "meta.stackoverflow.com" to "meta.stackoverflow",
        "meta.serverfault.com" to "meta.serverfault",
        "meta.superuser.com" to "meta.superuser",
        "meta.askubuntu.com" to "meta.askubuntu",
        "meta.mathoverflow.net" to "meta.mathoverflow",
    )

    fun isArxivUrl(url: String?): Boolean = url != null && arxivUrlRegex.matches(url)

    fun arxivId(url: String?): String? = url
        ?.takeIf(::isArxivUrl)
        ?.substringAfterLast('/')
        ?.removeSuffix(".pdf")

    fun isGitHubUrl(url: String?): Boolean = url != null && githubUrlRegex.matches(url)

    fun gitHubRepository(url: String?): GitHubRepository? {
        if (!isGitHubUrl(url)) return null
        val parts = url.orEmpty().substringAfter("github.com/").split('/')
        return if (parts.size >= 2) GitHubRepository(parts[0], parts[1]) else null
    }

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

    fun isStackExchangeUrl(url: String?): Boolean = stackExchangeRequest(url) != null

    fun stackExchangeRequest(url: String?): StackExchangeRequest? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        val siteParam = stackExchangeSiteParam(parsed.host) ?: return null
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        for (index in 0..<segments.lastIndex) {
            when (segments[index]) {
                "questions", "q" -> return StackExchangeRequest(
                    siteParam,
                    segments[index + 1],
                    false,
                )
                "a" -> return StackExchangeRequest(siteParam, segments[index + 1], true)
            }
        }
        return null
    }

    fun isWikipediaUrl(url: String?): Boolean = url != null && wikipediaUrlRegex.matches(url)

    fun wikipediaTitle(url: String?): String? = url
        ?.takeIf(::isWikipediaUrl)
        ?.substringAfter("en.wikipedia.org/wiki/", missingDelimiterValue = "")
        ?.takeIf(String::isNotEmpty)

    private fun stackExchangeSiteParam(host: String?): String? {
        val normalized = host?.lowercase()?.removePrefix("www.") ?: return null
        stackExchangeSites[normalized]?.let { return it }
        return normalized
            .takeIf { it.endsWith(".stackexchange.com") }
            ?.removeSuffix(".stackexchange.com")
            ?.takeIf(String::isNotEmpty)
    }
}

data class GitHubRepository(val owner: String, val name: String)

data class StackExchangeRequest(
    val siteParam: String,
    val id: String,
    val isAnswer: Boolean,
)

object LinkPreviewParsers {
    private const val UNSUPPORTED_WIKIPEDIA_ELEMENTS =
        "script, style, svg, wiki-chart, table, figure, iframe, canvas, noscript, object, embed"

    private val stackExchangeSiteNames = mapOf(
        "stackoverflow" to "Stack Overflow",
        "serverfault" to "Server Fault",
        "superuser" to "Super User",
        "askubuntu" to "Ask Ubuntu",
        "mathoverflow" to "MathOverflow",
        "stackapps" to "Stack Apps",
        "meta.stackoverflow" to "Meta Stack Overflow",
        "meta.serverfault" to "Meta Server Fault",
        "meta.superuser" to "Meta Super User",
        "meta.askubuntu" to "Meta Ask Ubuntu",
        "meta.mathoverflow" to "Meta MathOverflow",
        "meta" to "Meta Stack Exchange",
    )

    fun parseArxiv(response: String, arxivId: String): ArxivInfo? {
        val document = Ksoup.parseXml(response)
        val entry = document.getElementsByTag("entry").firstOrNull() ?: return null
        val abstractText = entry.getElementsByTag("summary").firstOrNull()?.wholeText().orEmpty()
        val authors = entry.getElementsByTag("author").mapNotNull { author ->
            author.getElementsByTag("name").firstOrNull()?.text()
        }
        val primaryCategory = (
            entry.getElementsByTag("arxiv:primary_category").firstOrNull()
                ?: entry.getElementsByTag("primary_category").firstOrNull()
            )?.attr("term").orEmpty()
        val secondaryCategories = entry.getElementsByTag("category")
            .map { it.attr("term") }
            .filter { it != primaryCategory && ArxivResolver.isArxivSubject(it) }
        val publishedDate = entry.getElementsByTag("published").firstOrNull()?.text().orEmpty()
        if (
            abstractText.isEmpty() || authors.isEmpty() || primaryCategory.isEmpty() ||
            publishedDate.isEmpty()
        ) return null

        return ArxivInfo().apply {
            arxivAbstract = abstractText
            this.authors = authors.toTypedArray()
            this.primaryCategory = primaryCategory
            this.secondaryCategories = secondaryCategories.toTypedArray()
            this.publishedDate = publishedDate
            arxivID = arxivId
        }
    }

    fun parseGitHub(response: String): RepoInfo {
        val json = JsonObject(response)
        return RepoInfo().apply {
            owner = json.optJSONObject("owner")?.nullableString("login")
            name = json.optString("name")
            about = json.nullableString("description")
            website = json.nullableString("homepage")
            license = json.optJSONObject("license")?.let {
                if (it.optString("name") == "Other") "Other" else it.nullableString("spdx_id")
            }
            language = json.nullableString("language")
            stars = json.optInt("stargazers_count")
            watching = json.optInt("subscribers_count")
            forks = json.optInt("forks_count")
        }
    }

    fun parseGitLab(response: String): GitLabInfo {
        val json = JsonObject(response)
        return GitLabInfo().apply {
            name = json.nullableString("name")
            namespace = json.optJSONObject("namespace")?.nullableString("full_path")
                ?: json.nullableString("namespace")
            description = json.nullableString("description")
            website = json.nullableString("web_url")
            visibility = json.nullableString("visibility")
            stars = json.optInt("star_count")
            forks = json.optInt("forks_count")
        }
    }

    fun parseStackExchange(
        response: String,
        request: StackExchangeRequest,
    ): StackExchangeInfo? {
        val items = JsonObject(response).getJSONArray("items")
        if (items.length() == 0) return null
        val item = items.getJSONObject(0)
        return StackExchangeInfo().apply {
            site = stackExchangeSiteName(request.siteParam)
            title = cleanHtmlText(item.optString("title"))
            questionText = cleanHtmlBody(item.optString("body"))
            score = item.optInt("score")
            answerCount = item.optInt("answer_count")
            viewCount = item.optInt("view_count")
            isAnswered = item.optBoolean("is_answered")
            hasAcceptedAnswer = item.has("accepted_answer_id")
            author = item.optJSONObject("owner")?.let { cleanHtmlText(it.optString("display_name")) }
            tags = item.optJSONArray("tags")?.let { tags ->
                Array<String?>(tags.length()) { index -> tags.getString(index) }
            }
        }
    }

    fun parseWikipedia(response: String): WikipediaInfo? {
        val json = JsonObject(response)
        val pages = json.getJSONObject("query").getJSONObject("pages")
        val pageKey = pages.keys().asSequence().firstOrNull() ?: return null
        val summary = pages.getJSONObject(pageKey).optString("extract")
        if (summary.isEmpty()) return null
        val document = sanitizeWikipediaHtml(summary)
        if (!document.body().hasText()) return null
        return WikipediaInfo().apply { this.summary = document.body().html() }
    }

    fun firstWikipediaParagraph(summaryHtml: String?): String {
        if (summaryHtml.isNullOrEmpty()) return ""
        val document = Ksoup.parse(summaryHtml)
        return document.selectFirst("p")?.text() ?: document.text()
    }

    fun parseArchiveUrl(response: String): String? {
        val snapshots = JsonObject(response).getJSONObject("archived_snapshots")
        val closest = snapshots.optJSONObject("closest") ?: return null
        return closest.takeIf { it.optBoolean("available") }?.nullableString("url")
    }

    private fun sanitizeWikipediaHtml(summaryHtml: String): Document {
        val document = Ksoup.parseBodyFragment(summaryHtml)
        document.select(UNSUPPORTED_WIKIPEDIA_ELEMENTS).remove()
        for (blockquote in document.select("blockquote")) blockquote.unwrap()
        for (element in document.select("p, ul, ol")) {
            if (!element.hasText()) element.remove()
        }
        return document
    }

    private fun stackExchangeSiteName(siteParam: String): String =
        stackExchangeSiteNames[siteParam] ?: siteParam
            .split('.')
            .filter(String::isNotEmpty)
            .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) }

    private fun cleanHtmlText(text: String?): String? =
        text?.takeUnless(String::isEmpty)?.let { Ksoup.parse(it).text() }

    private fun cleanHtmlBody(html: String?): String? {
        if (html.isNullOrEmpty()) return null
        val document = Ksoup.parse(html)
        document.outputSettings(Document.OutputSettings().prettyPrint(false))
        document.select("br").append("\\n")
        document.select("p, pre, blockquote, ul, ol").before("\\n")
        document.select("li").before("\\n")
        return document.wholeText()
            .replace("\\n", "\n")
            .replace("[ \\t\\x0B\\f\\r]+".toRegex(), " ")
            .replace(" *\\n *".toRegex(), "\n")
            .replace("\\n{3,}".toRegex(), "\n\n")
            .trim()
    }

    private fun JsonObject.nullableString(key: String): String? =
        optString(key, null)?.takeUnless(String::isEmpty)
}
