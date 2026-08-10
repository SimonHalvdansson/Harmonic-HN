package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.simon.harmonichackernews.data.Story
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import kotlin.time.Clock

data class HackerNewsListPage(
    val itemIds: List<Int>,
    val commentIds: List<Int>,
    val nextPageUrl: String?,
)

data class HackerNewsUserItems(
    val itemIds: List<Int>,
    val commentIds: List<Int>,
)

interface HackerNewsWebRepository {
    suspend fun getStoryList(
        path: String,
        commentsPage: Boolean = false,
        day: String? = null,
    ): HackerNewsListPage

    suspend fun getStoryListPage(url: String, commentsPage: Boolean): HackerNewsListPage
    suspend fun getListDirectory(): List<Story>
    suspend fun getUserItems(path: String, username: String): HackerNewsUserItems
}

class KtorHackerNewsWebRepository(
    private val client: HttpClient,
) : HackerNewsWebRepository {
    override suspend fun getStoryList(
        path: String,
        commentsPage: Boolean,
        day: String?,
    ): HackerNewsListPage {
        require(path.isNotBlank()) { "A Hacker News path is required" }
        val url = URLBuilder(BASE_WEB_URL).apply {
            appendPathSegments(path)
            if (!day.isNullOrEmpty()) parameters.append("day", day)
        }.buildString()
        return getStoryListPage(url, commentsPage)
    }

    override suspend fun getStoryListPage(
        url: String,
        commentsPage: Boolean,
    ): HackerNewsListPage = HackerNewsWebParser.parseStoryListPage(
        client.getTextOrThrow(url),
        commentsPage,
    )

    override suspend fun getListDirectory(): List<Story> =
        HackerNewsWebParser.parseListDirectory(client.getTextOrThrow("$BASE_WEB_URL/lists"))

    override suspend fun getUserItems(path: String, username: String): HackerNewsUserItems {
        require(path.isNotBlank()) { "A Hacker News user-list path is required" }
        require(username.isNotBlank()) { "A Hacker News username is required" }
        val itemIds = linkedSetOf<Int>()
        val commentIds = linkedSetOf<Int>()
        loadUserPages(path, username, comments = false, itemIds, commentIds)
        loadUserPages(path, username, comments = true, itemIds, commentIds)
        return HackerNewsUserItems(itemIds.toList(), commentIds.toList())
    }

    private suspend fun loadUserPages(
        path: String,
        username: String,
        comments: Boolean,
        itemIds: MutableSet<Int>,
        commentIds: MutableSet<Int>,
    ) {
        var nextUrl: String? = userItemsUrl(path, username, comments)
        var page = 0
        while (!nextUrl.isNullOrEmpty() && page < MAX_USER_ITEM_LIST_PAGES) {
            page++
            val parsed = HackerNewsWebParser.parseStoryListPage(
                client.getTextOrThrow(nextUrl),
                commentsPage = comments,
            )
            itemIds += parsed.itemIds
            if (comments) commentIds += parsed.commentIds
            nextUrl = parsed.nextPageUrl
        }
    }

    private fun userItemsUrl(path: String, username: String, comments: Boolean): String =
        URLBuilder(BASE_WEB_URL).apply {
            appendPathSegments(path)
            parameters.append("id", username)
            if (comments) parameters.append("comments", "t")
        }.buildString()

    private companion object {
        const val BASE_WEB_URL = "https://news.ycombinator.com"
        const val MAX_USER_ITEM_LIST_PAGES = 50
    }
}

object HackerNewsWebParser {
    private const val BASE_WEB_URL = "https://news.ycombinator.com"
    private val listPaths = setOf(
        "front",
        "pool",
        "invited",
        "highlights",
        "shownew",
        "asknew",
        "best",
        "bestcomments",
        "active",
        "noobstories",
        "noobcomments",
        "classic",
        "leaders",
        "topcolors",
        "whoishiring",
        "launches",
    )

    fun parseStoryListPage(body: String, commentsPage: Boolean): HackerNewsListPage {
        val document = Ksoup.parse(body, baseUri = "$BASE_WEB_URL/")
        val itemIds = linkedSetOf<Int>()
        val commentIds = linkedSetOf<Int>()
        for (item in document.select("tr.athing[id]")) {
            val id = item.attr("id").toIntOrNull()?.takeIf { it > 0 } ?: continue
            itemIds += id
            if (commentsPage) commentIds += id
        }
        if (commentsPage) {
            for (ageLink in document.select("span.comhead span.age a[href]")) {
                hackerNewsItemId(ageLink)?.let {
                    itemIds += it
                    commentIds += it
                }
            }
        }
        return HackerNewsListPage(
            itemIds = itemIds.toList(),
            commentIds = commentIds.toList(),
            nextPageUrl = document.selectFirst("a.morelink[href]")
                ?.absUrl("href")
                ?.takeIf(String::isNotEmpty),
        )
    }

    fun parseListDirectory(body: String): List<Story> {
        val document = Ksoup.parse(body, baseUri = "$BASE_WEB_URL/")
        val seenPaths = mutableSetOf<String>()
        val nowSeconds = (Clock.System.now().toEpochMilliseconds() / 1_000L).toInt()
        return buildList {
            for (link in document.select("a[href]")) {
                val path = listPath(link) ?: continue
                if (path !in listPaths || !seenPaths.add(path)) continue
                add(
                    Story(listTitle(link), -1 - (path.hashCode() and 0x7fffffff), true, false)
                        .apply {
                            isFrontpageLink = true
                            isLink = true
                            url = link.absUrl("href")
                            by = "Hacker News"
                            time = nowSeconds
                        },
                )
            }
        }
    }

    private fun hackerNewsItemId(link: Element): Int? {
        val url = link.absUrl("href").toNetworkUrlOrNull() ?: return null
        if (url.encodedPath != "/item") return null
        return url.queryParameter("id")?.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun listPath(link: Element): String? {
        val url = link.absUrl("href").toNetworkUrlOrNull() ?: return null
        if (url.host != "news.ycombinator.com") return null
        return url.encodedPath.removePrefix("/").takeUnless { it.contains('/') || it.isEmpty() }
    }

    private fun listTitle(link: Element): String {
        val title = link.text().trim()
        val rowText = link.closest("tr")?.text()?.trim().orEmpty()
        val description = rowText
            .takeIf { it.startsWith(title) }
            ?.substring(title.length)
            ?.trim()
            .orEmpty()
        return if (description.isEmpty()) title else "$title - $description"
    }
}
