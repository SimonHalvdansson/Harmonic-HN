package com.simon.harmonichackernews.linkpreview

import android.content.Context
import android.util.Xml
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.simon.harmonichackernews.data.ArxivInfo
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.utils.ArxivResolver
import java.io.IOException
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

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
                    parseResponse(response, arxivId)?.let(callback::onSuccess)
                        ?: callback.onFailure("Data not found")
                } catch (error: XmlPullParserException) {
                    error.printStackTrace()
                    callback.onFailure("Failed to parse ArXiv API response")
                } catch (error: IOException) {
                    error.printStackTrace()
                    callback.onFailure("Failed to parse ArXiv API response")
                }
            },
            { error ->
                error.printStackTrace()
                callback.onFailure("Couldn't connect to ArXiv API")
            },
        )
        NetworkComponent.getRequestQueueInstance(ctx).add(request)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseResponse(response: String, arxivId: String): ArxivInfo? {
        val parser = Xml.newPullParser().apply {
            setInput(StringReader(response))
        }
        var eventType = parser.eventType
        var abstractText = ""
        val authors = mutableListOf<String?>()
        var primaryCategory = ""
        val secondaryCategories = mutableListOf<String?>()
        var publishedDate = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "summary" -> {
                        parser.next()
                        abstractText = parser.text
                    }
                    "name" -> {
                        parser.next()
                        authors += parser.text
                    }
                    "primary_category" -> {
                        primaryCategory = parser.getAttributeValue(null, "term")
                    }
                    "category" -> {
                        val category = parser.getAttributeValue(null, "term")
                        if (category != primaryCategory) secondaryCategories += category
                    }
                    "published" -> {
                        parser.next()
                        publishedDate = parser.text
                    }
                }
            }
            eventType = parser.next()
        }

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
