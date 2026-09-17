package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.serialization.JsonArray
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLPathPart

internal object CrossrefLinkPreview {
    internal fun crossrefDoi(url: String?): String? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        val host = parsed.host.lowercase().removePrefix("www.")
        if (host !in setOf("doi.org", "dx.doi.org")) return null
        return parsed.pathSegments.filter(String::isNotEmpty).joinToString("/").takeIf(String::isNotEmpty)
    }

    fun parseCrossref(response: String, doi: String, url: String): LinkPreviewInfo {
        val message = JsonObject(response).getJSONObject("message")
        val title = message.optJSONArray("title")?.optString(0).orEmpty()
        val authors = message.optJSONArray("author")?.let { values ->
            (0..<values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { author ->
                    listOf(author.nonBlankString("given"), author.nonBlankString("family"))
                        .filterNotNull().joinToString(" ").takeIf(String::isNotBlank)
                }
            }
        }.orEmpty()
        val published = message.optJSONObject("published")
            ?.optJSONArray("date-parts")
            ?.optJSONArray(0)
            ?.let(::dateParts)
        return LinkPreviewInfo(
            LinkPreviewType.CROSSREF_ARTICLE,
            title.requiredPreviewTitle(LinkPreviewType.CROSSREF_ARTICLE),
            message.optJSONArray("container-title")?.optString(0),
            null,
            null,
            url,
            details(
                "Authors" to authors.take(3).joinToString(", ").takeIf(String::isNotEmpty),
                "Published" to published,
                "Type" to message.nonBlankString("type")?.humanize(),
                "Publisher" to message.nonBlankString("publisher"),
                "Citations" to message.optLong("is-referenced-by-count").toString(),
                "DOI" to doi,
            ),
        )
    }

    private fun dateParts(parts: JsonArray): String? {
        val year = parts.optInt(0).takeIf { it > 0 } ?: return null
        val month = parts.optInt(1).takeIf { it > 0 }
        val day = parts.optInt(2).takeIf { it > 0 }
        return listOfNotNull(year.toString(), month?.toString()?.padStart(2, '0'), day?.toString()?.padStart(2, '0'))
            .joinToString("-")
    }
}

internal suspend fun HttpClient.loadCrossrefPreview(url: String): LinkPreviewInfo {
    val doi = CrossrefLinkPreview.crossrefDoi(url)
        ?: throw LinkPreviewException("Invalid Crossref DOI URL")
    return CrossrefLinkPreview.parseCrossref(
        getTextOrThrow("https://api.crossref.org/works/${doi.encodeURLPathPart()}"),
        doi,
        url,
    )
}
