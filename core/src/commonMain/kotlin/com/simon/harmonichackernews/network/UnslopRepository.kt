package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.utils.HackerNewsLinks
import io.ktor.client.HttpClient

/** Reads Unslop's published RSS feed; story details still come from the HN API. */
class UnslopRepository(private val client: suspend () -> HttpClient) {
    constructor(client: HttpClient) : this({ client })

    suspend fun getStoryIds(): List<Int> =
        parseStoryIds(client().getTextOrThrow(FEED_URL))

    companion object {
        const val FEED_URL = "https://www.unslop.news/rss.xml"

        internal fun parseStoryIds(xml: String): List<Int> {
            val channel = Ksoup.parseXml(xml).selectFirst("rss > channel")
                ?: throw ApiDecodingException(
                    "Invalid Unslop RSS feed",
                    IllegalArgumentException("Missing RSS channel"),
                )
            val items = channel.select("item")
            val ids = items.mapNotNull { item ->
                HackerNewsLinks.parseItemLink(item.selectFirst("guid")?.text()?.trim())?.itemId
            }.distinct()
            if (items.isNotEmpty() && ids.isEmpty()) {
                throw ApiDecodingException(
                    "Invalid Unslop RSS feed",
                    IllegalArgumentException("No Hacker News item IDs"),
                )
            }
            return ids
        }
    }
}
