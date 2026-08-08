package com.simon.harmonichackernews.linkpreview

import android.content.Context
import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.data.ArxivInfo
import com.simon.harmonichackernews.network.QueueRequest as Request
import com.simon.harmonichackernews.network.StringRequest
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.utils.ArxivResolver

object ArxivAbstractGetter {
    private val arxivUrlRegex = Regex(
        "^https?://arxiv\\.org/(abs|pdf)/((\\d{4}\\.\\d{4,5}(v\\d+)?)|" +
            "([a-z\\-]+/\\d{2}\\d{4}))(\\.pdf)?$",
    )

    fun isValidArxivUrl(url: String): Boolean = arxivUrlRegex.matches(url)

    fun getAbstract(url: String, ctx: Context, callback: GetterCallback) {
        val arxivId = url.substringAfterLast('/').removeSuffix(".pdf")
        val request = StringRequest(
            Request.Method.GET,
            "https://export.arxiv.org/api/query?id_list=$arxivId",
            { response ->
                try {
                    parseResponse(response.orEmpty(), arxivId)?.let(callback::onSuccess)
                        ?: callback.onFailure("Data not found")
                } catch (error: IllegalArgumentException) {
                    error.printStackTrace()
                    callback.onFailure("Failed to parse ArXiv API response")
                }
            },
            { error ->
                error?.printStackTrace()
                callback.onFailure("Couldn't connect to ArXiv API")
            },
        )
        NetworkComponent.getRequestQueueInstance(ctx).add(request)
    }

    private fun parseResponse(response: String, arxivId: String): ArxivInfo? {
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
            .filter { it != primaryCategory }
        val publishedDate = entry.getElementsByTag("published").firstOrNull()?.text().orEmpty()

        if (
            abstractText.isEmpty() ||
            authors.isEmpty() ||
            primaryCategory.isEmpty() ||
            publishedDate.isEmpty()
        ) {
            return null
        }

        return ArxivInfo().apply {
            arxivAbstract = abstractText
            this.authors = authors.toTypedArray()
            this.primaryCategory = primaryCategory
            this.secondaryCategories = secondaryCategories
                .filter(ArxivResolver::isArxivSubject)
                .toTypedArray()
            this.publishedDate = publishedDate
            arxivID = arxivId
        }
    }

    interface GetterCallback {
        fun onSuccess(arxivInfo: ArxivInfo)

        fun onFailure(reason: String)
    }
}
