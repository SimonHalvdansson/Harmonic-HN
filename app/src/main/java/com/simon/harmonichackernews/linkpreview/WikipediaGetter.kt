package com.simon.harmonichackernews.linkpreview

import android.content.Context
import android.text.TextUtils
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.simon.harmonichackernews.data.WikipediaInfo
import com.simon.harmonichackernews.network.NetworkComponent
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object WikipediaGetter {
    private const val UNSUPPORTED_PREVIEW_ELEMENTS =
        "script, style, svg, wiki-chart, table, figure, iframe, canvas, noscript, object, embed"

    fun isValidWikipediaUrl(url: String): Boolean {
        val regex = "^(https|http)://en.wikipedia.org/wiki/.+"
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(url)
        return matcher.matches()
    }

    fun getInfo(wikipediaUrl: String, ctx: Context, callback: GetterCallback): Request<*>? {
        try {
            val title = wikipediaUrl.split("en.wikipedia.org/wiki/".toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()[1]

            val apiUrl =
                "https://en.wikipedia.org/w/api.php?format=json&action=query&prop=extracts&exintro&titles=" + title

            val stringRequest = StringRequest(
                Request.Method.GET, apiUrl,
                Response.Listener { response: String? ->
                    try {
                        val jsonResponse = JSONObject(response)
                        val pages = jsonResponse.getJSONObject("query").getJSONObject("pages")
                        val pageId = pages.keys().next()
                        val summary = pages.getJSONObject(pageId).optString("extract")

                        if (!TextUtils.isEmpty(summary)) {
                            val doc = sanitizeSummaryHtml(summary)

                            if (doc.body().hasText()) {
                                val wikiInfo = WikipediaInfo()
                                wikiInfo.summary = doc.body().html()
                                callback.onSuccess(wikiInfo)
                            } else {
                                callback.onFailure("Wikipedia did not return a visible summary")
                            }
                        } else {
                            callback.onFailure("Failed to retrieve Wikipedia summary")
                        }
                    } catch (e: Exception) {
                        callback.onFailure("Failed to parse Wikipedia API response")
                        e.printStackTrace()
                    }
                },
                Response.ErrorListener { error: VolleyError? ->
                    error!!.printStackTrace()
                    callback.onFailure("Couldn't connect to Wikipedia API")
                })

            val queue = NetworkComponent.getRequestQueueInstance(ctx)
            queue.add<String?>(stringRequest)
            return stringRequest
        } catch (e: Exception) {
            callback.onFailure("Invalid Wikipedia URL")
            return null
        }
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
        if (TextUtils.isEmpty(summaryHtml)) {
            return ""
        }

        val document = Jsoup.parse(summaryHtml)
        val firstParagraph = document.selectFirst("p")
        return if (firstParagraph == null) document.text() else firstParagraph.text()
    }

    interface GetterCallback {
        fun onSuccess(wikiInfo: WikipediaInfo)

        fun onFailure(reason: String)
    }
}
