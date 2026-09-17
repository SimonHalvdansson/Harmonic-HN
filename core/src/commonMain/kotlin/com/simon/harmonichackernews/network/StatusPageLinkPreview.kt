package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLPathPart

internal object StatusPageLinkPreview {
    internal fun statusPageIncident(url: String?): Pair<String, String>? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        val host = parsed.host.lowercase()
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        if (!host.endsWith(".statuspage.io") || segments.size < 2 || segments[0] != "incidents") {
            return null
        }
        return host to segments[1]
    }

    fun parseStatusPage(response: String, url: String): LinkPreviewInfo {
        val root = JsonObject(response)
        val incident = root.optJSONObject("incident") ?: root
        val page = root.optJSONObject("page")
        val latest = incident.optJSONArray("incident_updates")?.optJSONObject(0)
        return LinkPreviewInfo(
            LinkPreviewType.STATUS_PAGE,
            incident.optString("name").requiredPreviewTitle(LinkPreviewType.STATUS_PAGE),
            page?.nonBlankString("name"),
            latest?.nonBlankString("body"),
            null,
            url,
            details(
                "Status" to incident.nonBlankString("status")?.humanize(),
                "Impact" to incident.nonBlankString("impact")?.humanize(),
                "Started" to incident.nonBlankString("started_at")?.dateOnly(),
                "Updated" to incident.nonBlankString("updated_at")?.dateOnly(),
            ),
        )
    }
}

internal suspend fun HttpClient.loadStatusPagePreview(url: String): LinkPreviewInfo {
    val (host, incident) = StatusPageLinkPreview.statusPageIncident(url)
        ?: throw LinkPreviewException("Invalid Statuspage incident URL")
    return StatusPageLinkPreview.parseStatusPage(
        getTextOrThrow("https://$host/api/v2/incidents/${incident.encodeURLPathPart()}.json"),
        url,
    )
}
