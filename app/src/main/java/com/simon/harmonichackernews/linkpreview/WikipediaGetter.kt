package com.simon.harmonichackernews.linkpreview

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.simon.harmonichackernews.data.WikipediaInfo
import com.simon.harmonichackernews.network.NetworkComponent
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object WikipediaGetter {
    private val wikipediaUrlRegex = Regex("^https?://en\\.wikipedia\\.org/wiki/.+")

    private const val UNSUPPORTED_PREVIEW_ELEMENTS =
        "script, style, svg, wiki-chart, table, figure, iframe, canvas, noscript, object, embed"

    fun isValidWikipediaUrl(url: String): Boolean = wikipediaUrlRegex.matches(url)

    fun getInfo(wikipediaUrl: String, ctx: Context, callback: GetterCallback): Request<*>? {
        val title = wikipediaUrl
            .substringAfter("en.wikipedia.org/wiki/", missingDelimiterValue = "")
            .takeIf(String::isNotEmpty)
        if (title == null) {
            callback.onFailure("Invalid Wikipedia URL")
            return null
        }

        val apiUrl = "https://en.wikipedia.org/w/api.php?format=json&action=query&" +
            "prop=extracts&exintro&titles=$title"
        val request = StringRequest(
            Request.Method.GET,
            apiUrl,
            { response ->
                try {
                    val json = JSONObject(response)
                    val pages = json.getJSONObject("query").getJSONObject("pages")
                    val page = pages.getJSONObject(pages.keys().next())
                    val summary = page.optString("extract")
                    if (summary.isEmpty()) {
                        callback.onFailure("Failed to retrieve Wikipedia summary")
                    } else {
                        val document = sanitizeSummaryHtml(summary)
                        if (document.body().hasText()) {
                            callback.onSuccess(
                                WikipediaInfo().apply { this.summary = document.body().html() },
                            )
                        } else {
                            callback.onFailure("Wikipedia did not return a visible summary")
                        }
                    }
                } catch (error: Exception) {
                    error.printStackTrace()
                    callback.onFailure("Failed to parse Wikipedia API response")
                }
            },
            { error ->
                error.printStackTrace()
                callback.onFailure("Couldn't connect to Wikipedia API")
            },
        )
        NetworkComponent.getRequestQueueInstance(ctx).add(request)
        return request
    }

    private fun sanitizeSummaryHtml(summaryHtml: String): Document {
        val document = Jsoup.parseBodyFragment(summaryHtml)

        // HtmlTextView cannot render rich MediaWiki content such as charts or tables. Remove
        // these elements with their contents so embedded SVG labels and CSS are not shown as
        // flattened preview text.
        document.select(UNSUPPORTED_PREVIEW_ELEMENTS).remove()

        // HtmlTextView gives blockquotes a hard-coded white background. Keep the quoted text and
        // inline formatting, but render it as ordinary preview content.
        for (blockquote in document.select("blockquote")) {
            blockquote.unwrap()
        }

        for (element in document.select("p, ul, ol")) {
            if (!element.hasText()) {
                element.remove()
            }
        }

        return document
    }

    fun getFirstParagraphText(summaryHtml: String?): String {
        if (summaryHtml.isNullOrEmpty()) return ""

        val document = Jsoup.parse(summaryHtml)
        val firstParagraph = document.selectFirst("p")
        return firstParagraph?.text() ?: document.text()
    }

    interface GetterCallback {
        fun onSuccess(wikiInfo: WikipediaInfo)

        fun onFailure(reason: String)
    }
}
