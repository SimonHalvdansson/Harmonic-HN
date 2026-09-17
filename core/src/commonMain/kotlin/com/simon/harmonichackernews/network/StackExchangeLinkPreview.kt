package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.simon.harmonichackernews.data.StackExchangeInfo
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder

data class StackExchangeRequest(
    val siteParam: String,
    val id: String,
    val isAnswer: Boolean,
)

internal object StackExchangeLinkPreview {
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

    private fun stackExchangeSiteParam(host: String?): String? {
        val normalized = host?.lowercase()?.removePrefix("www.") ?: return null
        stackExchangeSites[normalized]?.let { return it }
        return normalized
            .takeIf { it.endsWith(".stackexchange.com") }
            ?.removeSuffix(".stackexchange.com")
            ?.takeIf(String::isNotEmpty)
    }

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

    fun parseStackExchange(
        response: String,
        request: StackExchangeRequest,
    ): StackExchangeInfo? {
        val items = JsonObject(response).getJSONArray("items")
        if (items.length() == 0) return null
        val item = items.getJSONObject(0)
        return StackExchangeInfo(
            site = stackExchangeSiteName(request.siteParam),
            title = cleanHtmlText(item.optString("title")),
            questionText = cleanHtmlBody(item.optString("body")),
            score = item.optInt("score"),
            answerCount = item.optInt("answer_count"),
            viewCount = item.optInt("view_count"),
            isAnswered = item.optBoolean("is_answered"),
            hasAcceptedAnswer = item.has("accepted_answer_id"),
            author = item.optJSONObject("owner")?.let { cleanHtmlText(it.optString("display_name")) },
            tags = item.optJSONArray("tags")?.let { tags ->
                List(tags.length()) { index -> tags.getString(index) }
            }.orEmpty(),
        )
    }

    private fun stackExchangeSiteName(siteParam: String): String =
        stackExchangeSiteNames[siteParam] ?: siteParam
            .split('.')
            .filter(String::isNotEmpty)
            .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) }

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
        (opt(key) as? String)?.takeUnless(String::isEmpty)
}

internal suspend fun HttpClient.loadStackExchangeInfo(url: String): StackExchangeInfo {
    val request = StackExchangeLinkPreview.stackExchangeRequest(url)
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
    return StackExchangeLinkPreview.parseStackExchange(
        getTextOrThrow(endpoint),
        request,
    ) ?: throw LinkPreviewException("Stack Exchange question not found")
}
