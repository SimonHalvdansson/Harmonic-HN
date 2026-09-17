package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.serialization.JsonArray
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder

internal object UsgsLinkPreview {
    internal fun usgsEventId(url: String?): String? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        if (parsed.host.lowercase() != "earthquake.usgs.gov") return null
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        val index = segments.indexOf("eventpage")
        return segments.getOrNull(index + 1)?.takeIf(String::isNotEmpty)
    }

    fun parseUsgs(response: String, eventId: String, url: String): LinkPreviewInfo {
        val root = JsonObject(response)
        val properties = root.getJSONObject("properties")
        return LinkPreviewInfo(
            LinkPreviewType.USGS_EARTHQUAKE,
            properties.nonBlankString("title") ?: properties.optString("place").requiredPreviewTitle(
                LinkPreviewType.USGS_EARTHQUAKE,
            ),
            "USGS event $eventId",
            properties.nonBlankString("place"),
            null,
            url,
            details(
                "Magnitude" to properties.numberString("mag"),
                "Depth" to root.optJSONObject("geometry")?.optJSONArray("coordinates")
                    ?.numberString(2)?.let { "$it km" },
                "Type" to properties.nonBlankString("type")?.titleCase(),
                "Significance" to properties.optLong("sig").toString(),
                "Tsunami" to if (properties.optInt("tsunami") == 1) "Warning" else "No warning",
                "Status" to properties.nonBlankString("status")?.titleCase(),
            ),
        )
    }

    private fun JsonObject.numberString(key: String): String? = numberString(opt(key))

    private fun JsonArray.numberString(index: Int): String? = numberString(get(index))

    private fun numberString(value: Any?): String? = when (value) {
        is Long -> value.toString()
        is Int -> value.toString()
        is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        is Float -> if (value % 1f == 0f) value.toLong().toString() else value.toString()
        else -> null
    }
}

internal suspend fun HttpClient.loadUsgsPreview(url: String): LinkPreviewInfo {
    val eventId = UsgsLinkPreview.usgsEventId(url)
        ?: throw LinkPreviewException("Invalid USGS earthquake URL")
    val endpoint = URLBuilder("https://earthquake.usgs.gov/fdsnws/event/1/query").apply {
        parameters.append("format", "geojson")
        parameters.append("eventid", eventId)
    }.buildString()
    return UsgsLinkPreview.parseUsgs(getTextOrThrow(endpoint), eventId, url)
}
