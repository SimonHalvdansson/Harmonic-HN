package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.NitterInfo
import com.simon.harmonichackernews.serialization.JsonObject

object NitterPreview {
    fun isNitterUrl(url: String?): Boolean =
        url?.toNetworkUrlOrNull()?.host?.lowercase()?.removePrefix("www.") == "nitter.net"

    fun isConvertibleUrl(url: String?): Boolean {
        val parsed = url?.toNetworkUrlOrNull() ?: return false
        val host = parsed.host.lowercase()
            .removePrefix("www.")
            .removePrefix("mobile.")
        if (host != "twitter.com" && host != "x.com") return false
        val path = "/" + parsed.pathSegments.filter(String::isNotEmpty).joinToString("/")
        return path.matches(
            "^/(?:[A-Za-z0-9_]{1,15}/status/\\d+|i/web/status/\\d+)(?:/[^/]*)*$".toRegex(),
        )
    }

    fun convertUrl(url: String): String {
        val parsed = url.toNetworkUrlOrNull()
            ?: throw IllegalArgumentException("Invalid X/Twitter URL")
        require(isConvertibleUrl(url)) { "URL is not a supported X/Twitter status" }
        val builder = parsed.newBuilder()
        return builder.host("nitter.net").build().toString()
    }

    fun parseJavascriptResult(json: String): NitterInfo {
        val value = JsonObject(json)
        return NitterInfo().apply {
            text = value.optString("text").replace("\n", "<br>")
            userName = value.optString("userName")
            userTag = value.optString("userTag")
            date = value.optString("date")
            replyCount = value.optString("replyCount")
            reposts = value.optString("reposts")
            likes = value.optString("likes")
            imgSrc = value.nullableString("imgSrc")
            hasVideo = value.optBoolean("hasVideo")
            beforeUserName = value.optString("beforeName")
            beforeUserTag = value.optString("beforeTag")
            beforeText = value.optString("beforeText")
            beforeDate = value.optString("beforeDate")
            beforeImgSrc = value.nullableString("beforeImgSrc")
        }
    }

    private fun JsonObject.nullableString(key: String): String? {
        val value: String? = optString(key, null)
        return value?.takeUnless { it.isEmpty() || it == "null" }
    }
}
