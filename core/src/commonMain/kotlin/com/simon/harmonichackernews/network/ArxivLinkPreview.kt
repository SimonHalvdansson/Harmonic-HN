package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.data.ArxivInfo
import com.simon.harmonichackernews.utils.ArxivResolver
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

internal object ArxivLinkPreview {
    fun isArxivUrl(url: String?): Boolean = url != null && arxivUrlRegex.matches(url)

    fun arxivId(url: String?): String? = url
        ?.takeIf(::isArxivUrl)
        ?.substringAfterLast('/')
        ?.removeSuffix(".pdf")

    private val arxivUrlRegex = Regex(
        "^https?://arxiv\\.org/(abs|pdf)/((\\d{4}\\.\\d{4,5}(v\\d+)?)|" +
            "([a-z\\-]+/\\d{2}\\d{4}))(\\.pdf)?$",
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

        return ArxivInfo(
            arxivAbstract = abstractText,
            authors = authors,
            primaryCategory = primaryCategory,
            secondaryCategories = secondaryCategories,
            publishedDate = publishedDate,
            arxivID = arxivId,
        )
    }

    fun parseArxivAbstractPage(response: String, arxivId: String): ArxivInfo? {
        val document = Ksoup.parse(response)
        fun citation(name: String) = document.selectFirst("meta[name=citation_$name]")
            ?.attr("content").orEmpty().trim()
        // Do not mistake an error page (or a redirect to another paper) for this article.
        if (citation("arxiv_id").substringBefore('v') != arxivId.substringBefore('v')) return null
        val abstractText = citation("abstract")
        val authors = document.select(".authors a").map { it.text() }.filter(String::isNotBlank)
        val publishedDate = citation("date").replace('/', '-')
        val categoryPattern = Regex("\\(([^()]+)\\)")
        fun categories(text: String) = categoryPattern.findAll(text)
            .map { it.groupValues[1] }.filter(ArxivResolver::isArxivSubject).toList()
        val primaryCategory = categories(document.selectFirst(".primary-subject")?.text().orEmpty())
            .firstOrNull() ?: return null
        if (abstractText.isBlank() || authors.isEmpty() ||
            !Regex("\\d{4}-\\d{2}-\\d{2}").matches(publishedDate)
        ) return null
        return ArxivInfo(
            arxivAbstract = abstractText,
            authors = authors,
            primaryCategory = primaryCategory,
            secondaryCategories = categories(document.selectFirst(".subjects")?.text().orEmpty())
                .filter { it != primaryCategory }.distinct(),
            publishedDate = publishedDate,
            arxivID = arxivId,
        )
    }

    fun parseArxivHtmlUrl(response: String): String? {
        val href = Ksoup.parse(response, baseUri = "https://arxiv.org")
            .select("a[href]")
            .firstOrNull { link ->
                val value = link.attr("href")
                value.startsWith("/html/") || value.startsWith("https://arxiv.org/html/")
            }
            ?.attr("href")
            ?: return null
        return if (href.startsWith('/')) "https://arxiv.org$href" else href
    }
}

internal suspend fun HttpClient.loadArxivInfo(url: String): ArxivInfo = coroutineScope {
    val arxivId = ArxivLinkPreview.arxivId(url)
        ?: throw LinkPreviewException("Invalid ArXiv URL")
    val endpoint = URLBuilder("https://export.arxiv.org/api/query").apply {
        parameters.append("id_list", arxivId)
    }.buildString()
    val abstractPage = async {
        try {
            withTimeoutOrNull(ARXIV_HTML_PROBE_TIMEOUT_MILLIS) {
                getTextOrThrow("https://arxiv.org/abs/$arxivId")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }
    // The API can fail or return an empty entry while the public abstract page is available.
    // Reuse the page we already fetch for the HTML button instead of discarding the preview.
    val apiInfo = try {
        ArxivLinkPreview.parseArxiv(getTextOrThrow(endpoint), arxivId)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
    val page = abstractPage.await()
    val info = apiInfo ?: page?.let { ArxivLinkPreview.parseArxivAbstractPage(it, arxivId) }
        ?: throw LinkPreviewException("ArXiv data not found")
    info.copy(htmlUrl = page?.let(ArxivLinkPreview::parseArxivHtmlUrl))
}

private const val ARXIV_HTML_PROBE_TIMEOUT_MILLIS = 10_000L
