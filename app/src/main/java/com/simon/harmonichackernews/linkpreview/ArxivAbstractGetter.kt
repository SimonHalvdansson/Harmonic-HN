package com.simon.harmonichackernews.linkpreview

import android.content.Context
import android.util.Xml
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.simon.harmonichackernews.data.ArxivInfo
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.utils.ArxivResolver
import java.io.IOException
import java.io.StringReader
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

object ArxivAbstractGetter {
    fun isValidArxivUrl(url: String): Boolean {
        val arxivUrlPattern =
            "^https?:\\/\\/arxiv\\.org\\/(abs|pdf)\\/((\\d{4}\\.\\d{4,5}(v\\d+)?)|([a-z\\-]+\\/\\d{2}\\d{4}))(\\.pdf)?$"

        val pattern = Pattern.compile(arxivUrlPattern)
        val matcher = pattern.matcher(url)

        return matcher.matches()
    }

    fun getAbstract(url: String, ctx: Context, callback: GetterCallback) {
        val arxivID = url.substring(url.lastIndexOf('/') + 1).replace(".pdf", "")

        val stringRequest = StringRequest(
            Request.Method.GET, "https://export.arxiv.org/api/query?id_list=" + arxivID,
            Response.Listener { response: String? ->
                try {
                    val parser = Xml.newPullParser()
                    parser.setInput(StringReader(response))
                    var eventType = parser.getEventType()

                    var abstractText = ""
                    val authorList: MutableList<String?> = ArrayList<String?>()
                    var primaryCategoryText = ""
                    val secondaryCategoryList: MutableList<String?> = ArrayList<String?>()
                    var publishedDateText = ""

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        val tagName = parser.getName()

                        // Parsing the summary
                        if (eventType == XmlPullParser.START_TAG && "summary" == tagName) {
                            parser.next()
                            abstractText = parser.getText()
                        }

                        // Parsing the authors
                        if (eventType == XmlPullParser.START_TAG && "name" == tagName) {
                            parser.next()
                            authorList.add(parser.getText())
                        }

                        // Parsing the primary category
                        if (eventType == XmlPullParser.START_TAG && "primary_category" == tagName) {
                            primaryCategoryText = parser.getAttributeValue(null, "term")
                        }

                        // Parsing secondary categories
                        if (eventType == XmlPullParser.START_TAG && "category" == tagName) {
                            val category = parser.getAttributeValue(null, "term")
                            if (category != primaryCategoryText) {
                                secondaryCategoryList.add(category)
                            }
                        }

                        // Parsing the published date
                        if (eventType == XmlPullParser.START_TAG && "published" == tagName) {
                            parser.next()
                            publishedDateText = parser.getText()
                        }

                        eventType = parser.next()
                    }

                    // Convert author list to array
                    val authorsArray = authorList.toTypedArray<String?>()

                    // API 23 does not support Java 8 so we do this by hand
                    val secondaryCategoriesFiltered: MutableList<String?> = ArrayList<String?>()
                    for (category in secondaryCategoryList) {
                        if (ArxivResolver.isArxivSubject(category)) {
                            secondaryCategoriesFiltered.add(category)
                        }
                    }

                    val secondaryCategoriesFilteredArray =
                        secondaryCategoriesFiltered.toTypedArray<String?>()

                    // Create ArxivInfo object
                    val info = ArxivInfo()
                    info.arxivAbstract = abstractText
                    info.authors = authorsArray
                    info.primaryCategory = primaryCategoryText
                    info.secondaryCategories = secondaryCategoriesFilteredArray
                    info.publishedDate = publishedDateText
                    info.arxivID = arxivID

                    if (!abstractText.isEmpty() && authorsArray.size > 0 && !primaryCategoryText.isEmpty() && !publishedDateText.isEmpty()) {
                        callback.onSuccess(info)
                    } else {
                        callback.onFailure("Data not found")
                    }
                } catch (e: XmlPullParserException) {
                    callback.onFailure("Failed to parse ArXiv API response")
                    e.printStackTrace()
                } catch (e: IOException) {
                    callback.onFailure("Failed to parse ArXiv API response")
                    e.printStackTrace()
                }
            },
            Response.ErrorListener { error: VolleyError? ->
                error!!.printStackTrace()
                callback.onFailure("Couldn't connect to ArXiv API")
            })

        val queue = NetworkComponent.getRequestQueueInstance(ctx)
        queue.add<String?>(stringRequest)
    }

    interface GetterCallback {
        fun onSuccess(arxivInfo: ArxivInfo?)

        fun onFailure(reason: String?)
    }
}
