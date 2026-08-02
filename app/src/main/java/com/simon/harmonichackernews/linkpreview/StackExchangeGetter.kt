package com.simon.harmonichackernews.linkpreview

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.simon.harmonichackernews.data.StackExchangeInfo
import com.simon.harmonichackernews.network.NetworkComponent
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object StackExchangeGetter {
    private val SITE_PARAMS: MutableMap<String?, String?> = HashMap<String?, String?>()
    private val SITE_NAMES: MutableMap<String?, String?> = HashMap<String?, String?>()

    init {
        SITE_PARAMS.put("stackoverflow.com", "stackoverflow")
        SITE_PARAMS.put("serverfault.com", "serverfault")
        SITE_PARAMS.put("superuser.com", "superuser")
        SITE_PARAMS.put("askubuntu.com", "askubuntu")
        SITE_PARAMS.put("mathoverflow.net", "mathoverflow")
        SITE_PARAMS.put("stackapps.com", "stackapps")
        SITE_PARAMS.put("meta.stackoverflow.com", "meta.stackoverflow")
        SITE_PARAMS.put("meta.serverfault.com", "meta.serverfault")
        SITE_PARAMS.put("meta.superuser.com", "meta.superuser")
        SITE_PARAMS.put("meta.askubuntu.com", "meta.askubuntu")
        SITE_PARAMS.put("meta.mathoverflow.net", "meta.mathoverflow")

        SITE_NAMES.put("stackoverflow", "Stack Overflow")
        SITE_NAMES.put("serverfault", "Server Fault")
        SITE_NAMES.put("superuser", "Super User")
        SITE_NAMES.put("askubuntu", "Ask Ubuntu")
        SITE_NAMES.put("mathoverflow", "MathOverflow")
        SITE_NAMES.put("stackapps", "Stack Apps")
        SITE_NAMES.put("meta.stackoverflow", "Meta Stack Overflow")
        SITE_NAMES.put("meta.serverfault", "Meta Server Fault")
        SITE_NAMES.put("meta.superuser", "Meta Super User")
        SITE_NAMES.put("meta.askubuntu", "Meta Ask Ubuntu")
        SITE_NAMES.put("meta.mathoverflow", "Meta MathOverflow")
        SITE_NAMES.put("meta", "Meta Stack Exchange")
    }

    fun isValidStackExchangeUrl(url: String?): Boolean {
        return getRequestInfo(url) != null
    }

    fun getInfo(stackExchangeUrl: String, ctx: Context, callback: GetterCallback) {
        try {
            val requestInfo = getRequestInfo(stackExchangeUrl)
            if (requestInfo == null) {
                callback.onFailure("Invalid Stack Exchange URL")
                return
            }

            val endpoint = if (requestInfo.isAnswer)
                "https://api.stackexchange.com/2.3/answers/" + requestInfo.id + "/questions"
            else
                "https://api.stackexchange.com/2.3/questions/" + requestInfo.id
            val apiUrl =
                endpoint + "?site=" + Uri.encode(requestInfo.siteParam) + "&filter=withbody"

            val stringRequest = StringRequest(
                Request.Method.GET, apiUrl,
                Response.Listener { response: String? ->
                    try {
                        val jsonResponse = JSONObject(response)
                        val items = jsonResponse.getJSONArray("items")

                        if (items.length() == 0) {
                            callback.onFailure("Stack Exchange question not found")
                            return@Listener
                        }

                        val item = items.getJSONObject(0)
                        val stackExchangeInfo = StackExchangeInfo()

                        stackExchangeInfo.site = getSiteName(requestInfo.siteParam)
                        stackExchangeInfo.title = cleanText(item.optString("title"))
                        stackExchangeInfo.questionText = cleanBodyText(item.optString("body"))
                        stackExchangeInfo.score = item.optInt("score")
                        stackExchangeInfo.answerCount = item.optInt("answer_count")
                        stackExchangeInfo.viewCount = item.optInt("view_count")
                        stackExchangeInfo.isAnswered = item.optBoolean("is_answered")
                        stackExchangeInfo.hasAcceptedAnswer = item.has("accepted_answer_id")

                        if (item.has("owner") && item.get("owner").toString() != "null") {
                            val owner = item.getJSONObject("owner")
                            stackExchangeInfo.author = cleanText(owner.optString("display_name"))
                        }

                        if (item.has("tags")) {
                            val tags = item.getJSONArray("tags")
                            stackExchangeInfo.tags = arrayOfNulls<String>(tags.length())
                            for (i in 0..<tags.length()) {
                                stackExchangeInfo.tags!![i] = tags.getString(i)
                            }
                        }

                        callback.onSuccess(stackExchangeInfo)
                    } catch (e: Exception) {
                        callback.onFailure("Failed to parse Stack Exchange API response")
                        e.printStackTrace()
                    }
                },
                Response.ErrorListener { error: VolleyError? ->
                    error!!.printStackTrace()
                    callback.onFailure("Couldn't connect to Stack Exchange API")
                })

            val queue = NetworkComponent.getRequestQueueInstance(ctx)
            queue.add<String?>(stringRequest)
        } catch (e: Exception) {
            callback.onFailure("Invalid Stack Exchange URL")
        }
    }

    private fun getRequestInfo(url: String?): RequestInfo? {
        try {
            val uri = Uri.parse(url)
            val siteParam = getSiteParam(uri.getHost())
            if (siteParam == null) {
                return null
            }

            val segments = uri.getPathSegments()
            for (i in 0..<segments.size - 1) {
                val segment = segments.get(i)
                if (segment == "questions" || segment == "q") {
                    return RequestInfo(siteParam, segments.get(i + 1), false)
                } else if (segment == "a") {
                    return RequestInfo(siteParam, segments.get(i + 1), true)
                }
            }

            return null
        } catch (e: Exception) {
            return null
        }
    }

    private fun getSiteParam(host: String?): String? {
        var host = host
        if (host == null) {
            return null
        }

        host = host.lowercase(Locale.getDefault())
        if (host.startsWith("www.")) {
            host = host.substring(4)
        }

        if (SITE_PARAMS.containsKey(host)) {
            return SITE_PARAMS.get(host)
        }

        if (host.endsWith(".stackexchange.com")) {
            val subdomain = host.substring(0, host.length - ".stackexchange.com".length)
            if (subdomain == "meta") {
                return "meta"
            }
            return subdomain
        }

        return null
    }

    private fun getSiteName(siteParam: String): String? {
        if (SITE_NAMES.containsKey(siteParam)) {
            return SITE_NAMES.get(siteParam)
        }

        val parts: Array<String?> =
            siteParam.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val builder = StringBuilder()
        for (part in parts) {
            if (TextUtils.isEmpty(part)) {
                continue
            }
            if (builder.length > 0) {
                builder.append(" ")
            }
            builder.append(part!!.substring(0, 1).uppercase(Locale.getDefault()))
                .append(part.substring(1))
        }
        return builder.toString()
    }

    private fun cleanText(text: String?): String? {
        if (TextUtils.isEmpty(text)) {
            return null
        }
        return Jsoup.parse(text).text()
    }

    private fun cleanBodyText(html: String?): String? {
        if (TextUtils.isEmpty(html)) {
            return null
        }

        val document = Jsoup.parse(html)
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

    private class RequestInfo(val siteParam: String, val id: String, val isAnswer: Boolean)

    interface GetterCallback {
        fun onSuccess(stackExchangeInfo: StackExchangeInfo?)

        fun onFailure(reason: String?)
    }
}
