package com.simon.harmonichackernews.linkpreview

import android.content.Context
import android.net.Uri
import com.simon.harmonichackernews.network.QueueRequest as Request
import com.simon.harmonichackernews.network.StringRequest
import com.simon.harmonichackernews.data.StackExchangeInfo
import com.simon.harmonichackernews.network.NetworkComponent
import java.util.Locale
import com.simon.harmonichackernews.serialization.JsonObject as JSONObject
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document

object StackExchangeGetter {
    private val siteParams = mapOf(
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
    private val siteNames = mapOf(
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

    fun isValidStackExchangeUrl(url: String?): Boolean = getRequestInfo(url) != null

    fun getInfo(stackExchangeUrl: String, ctx: Context, callback: GetterCallback) {
        val requestInfo = getRequestInfo(stackExchangeUrl)
        if (requestInfo == null) {
            callback.onFailure("Invalid Stack Exchange URL")
            return
        }

        val endpoint = if (requestInfo.isAnswer) {
            "https://api.stackexchange.com/2.3/answers/${requestInfo.id}/questions"
        } else {
            "https://api.stackexchange.com/2.3/questions/${requestInfo.id}"
        }
        val apiUrl = "$endpoint?site=${Uri.encode(requestInfo.siteParam)}&filter=withbody"
        val request = StringRequest(
            Request.Method.GET,
            apiUrl,
            { response ->
                try {
                    val items = JSONObject(response.orEmpty()).getJSONArray("items")
                    if (items.length() == 0) {
                        callback.onFailure("Stack Exchange question not found")
                    } else {
                        callback.onSuccess(parseResponseItem(items.getJSONObject(0), requestInfo))
                    }
                } catch (error: Exception) {
                    error.printStackTrace()
                    callback.onFailure("Failed to parse Stack Exchange API response")
                }
            },
            { error ->
                error?.printStackTrace()
                callback.onFailure("Couldn't connect to Stack Exchange API")
            },
        )
        NetworkComponent.getRequestQueueInstance(ctx).add(request)
    }

    private fun getRequestInfo(url: String?): RequestInfo? {
        url ?: return null

        try {
            val uri = Uri.parse(url)
            val siteParam = getSiteParam(uri.host) ?: return null

            val segments = uri.pathSegments
            for (i in 0..<segments.size - 1) {
                val segment = segments[i]
                if (segment == "questions" || segment == "q") {
                    return RequestInfo(siteParam, segments[i + 1], false)
                } else if (segment == "a") {
                    return RequestInfo(siteParam, segments[i + 1], true)
                }
            }

            return null
        } catch (_: Exception) {
            return null
        }
    }

    private fun getSiteParam(host: String?): String? {
        val normalizedHost = host
            ?.lowercase(Locale.getDefault())
            ?.removePrefix("www.")
            ?: return null
        siteParams[normalizedHost]?.let { return it }

        if (normalizedHost.endsWith(".stackexchange.com")) {
            return normalizedHost
                .removeSuffix(".stackexchange.com")
                .takeUnless(String::isEmpty)
        }

        return null
    }

    private fun getSiteName(siteParam: String): String = siteNames[siteParam] ?: siteParam
        .split('.')
        .filter(String::isNotEmpty)
        .joinToString(" ") { part ->
            part.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }

    private fun cleanText(text: String?): String? =
        text?.takeUnless(String::isEmpty)?.let { Ksoup.parse(it).text() }

    private fun cleanBodyText(html: String?): String? {
        if (html.isNullOrEmpty()) return null

        val document = Ksoup.parse(html)
        document.outputSettings(Document.OutputSettings().prettyPrint(false))
        document.select("br").append("\\n")
        document.select("p, pre, blockquote, ul, ol").before("\\n")
        document.select("li").before("\\n")
        val text = document.wholeText()
        return text
            .replace("\\n", "\n")
            .replace("[ \\t\\x0B\\f\\r]+".toRegex(), " ")
            .replace(" *\\n *".toRegex(), "\n")
            .replace("\\n{3,}".toRegex(), "\n\n")
            .trim { it <= ' ' }
    }

    private fun parseResponseItem(item: JSONObject, requestInfo: RequestInfo): StackExchangeInfo =
        StackExchangeInfo().apply {
            site = getSiteName(requestInfo.siteParam)
            title = cleanText(item.optString("title"))
            questionText = cleanBodyText(item.optString("body"))
            score = item.optInt("score")
            answerCount = item.optInt("answer_count")
            viewCount = item.optInt("view_count")
            isAnswered = item.optBoolean("is_answered")
            hasAcceptedAnswer = item.has("accepted_answer_id")
            author = item.optJSONObject("owner")?.let {
                cleanText(it.optString("display_name"))
            }
            tags = item.optJSONArray("tags")?.let { tags ->
                Array<String?>(tags.length()) { index -> tags.getString(index) }
            }
        }

    private data class RequestInfo(val siteParam: String, val id: String, val isAnswer: Boolean)

    interface GetterCallback {
        fun onSuccess(stackExchangeInfo: StackExchangeInfo)

        fun onFailure(reason: String)
    }
}
