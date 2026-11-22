package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.utils.HackerNewsLinks
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

data class HackerNewsListPage(
    val itemIds: List<Int>,
    val commentIds: List<Int>,
    val nextPageUrl: String?,
    val storyHeaders: Map<Int, StoryListingHeader> = emptyMap(),
)

data class StoryListingHeader(
    val title: String,
    val url: String,
    val author: String,
    val score: Int,
    val time: Int,
    val descendants: Int,
) {
    fun applyTo(row: Story) {
        row.update(author, row.id, score, time, title)
        row.url = url
        row.isLink = true
        row.descendants = descendants
        row.loaded = true
        row.loadingFailed = false
        StoryTextProcessor.applyTitleBadges(row)
    }
}

data class HackerNewsUserItems(
    val itemIds: List<Int>,
    val commentIds: List<Int>,
)

class HackerNewsUserItemsCaptchaException(val challenge: HackerNewsCaptchaChallenge) :
    Exception("HN requires a CAPTCHA before returning the complete saved list")

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
    private val client: suspend () -> HttpClient,
    private val requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val useCookies: Boolean = false,
) : HackerNewsWebRepository {
    constructor(client: HttpClient, requestDispatcher: CoroutineDispatcher = Dispatchers.Default, useCookies: Boolean = false) :
        this({ client }, requestDispatcher, useCookies)
    override suspend fun getStoryList(
        path: String,
        commentsPage: Boolean,
        day: String?,
    ): HackerNewsListPage = withContext(requestDispatcher) {
        require(path.isNotBlank()) { "A Hacker News path is required" }
        val url = URLBuilder(HackerNewsLinks.BASE_URL).apply {
            appendPathSegments(path)
            if (!day.isNullOrEmpty()) parameters.append("day", day)
        }.buildString()
        getStoryListPage(url, commentsPage)
    }

    override suspend fun getStoryListPage(
        url: String,
        commentsPage: Boolean,
    ): HackerNewsListPage = withContext(requestDispatcher) {
        HackerNewsWebParser.parseStoryListPage(client().getTextOrThrow(url), commentsPage)
    }

    override suspend fun getListDirectory(): List<Story> = withContext(requestDispatcher) {
        HackerNewsWebParser.parseListDirectory(
            client().getTextOrThrow(HackerNewsLinks.absolutePath("lists")),
        )
    }

    override suspend fun getUserItems(path: String, username: String): HackerNewsUserItems = withContext(requestDispatcher) {
        require(path.isNotBlank()) { "A Hacker News user-list path is required" }
        require(username.isNotBlank()) { "A Hacker News username is required" }
        // Each pagination chain is sequential, but story and comment pages are independent.
        val stories = async { loadUserPages(path, username, comments = false) }
        val comments = async { loadUserPages(path, username, comments = true) }
        HackerNewsUserItems(
            itemIds = (stories.await().itemIds + comments.await().itemIds).distinct(),
            commentIds = comments.await().commentIds,
        )
    }

    private suspend fun loadUserPages(
        path: String,
        username: String,
        comments: Boolean,
    ): HackerNewsUserItems {
        val itemIds = linkedSetOf<Int>()
        val commentIds = linkedSetOf<Int>()
        var nextUrl: String? = userItemsUrl(path, username, comments)
        var page = 0
        while (!nextUrl.isNullOrEmpty() && page < MAX_USER_ITEM_LIST_PAGES) {
            page++
            val body = client().getTextOrThrow(nextUrl)
            if (HackerNewsActionParser.isCaptchaRequired(body)) {
                val challenge = HackerNewsActionParser.parseCaptchaChallenge(body, useCookies)
                    ?: error("HN returned a CAPTCHA that could not be read. Existing saved items are preserved.")
                throw HackerNewsUserItemsCaptchaException(challenge)
            }
            val parsed = HackerNewsWebParser.parseStoryListPage(body, commentsPage = comments, includeHeaders = false)
            if (parsed.itemIds.isEmpty()) {
                val document = Ksoup.parse(body, baseUri = HackerNewsLinks.ROOT_URL)
                check(document.selectFirst("form[action] input[name=pw]") == null) {
                    "HN asked you to log in again. Existing saved items are preserved."
                }
                check(document.selectFirst("table#hnmain, table.itemlist") != null) {
                    "HN did not return a recognizable saved list. Existing saved items are preserved."
                }
            }
            itemIds += parsed.itemIds
            if (comments) commentIds += parsed.commentIds
            nextUrl = parsed.nextPageUrl
        }
        return HackerNewsUserItems(itemIds.toList(), commentIds.toList())
    }

    private fun userItemsUrl(path: String, username: String, comments: Boolean): String =
        URLBuilder(HackerNewsLinks.BASE_URL).apply {
            appendPathSegments(path)
            parameters.append("id", username)
            if (comments) parameters.append("comments", "t")
        }.buildString()

    private companion object {
        const val MAX_USER_ITEM_LIST_PAGES = 50
    }
}

object HackerNewsWebParser {
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

    fun parseStoryListPage(body: String, commentsPage: Boolean, includeHeaders: Boolean = true): HackerNewsListPage {
        val document = Ksoup.parse(body, baseUri = HackerNewsLinks.ROOT_URL)
        val itemIds = linkedSetOf<Int>()
        val commentIds = linkedSetOf<Int>()
        val headers = mutableMapOf<Int, StoryListingHeader>()
        for (item in document.select("tr.athing[id]")) {
            val id = item.attr("id").toIntOrNull()?.takeIf { it > 0 } ?: continue
            itemIds += id
            if (commentsPage) commentIds += id
            else if (includeHeaders) storyHeader(item, id)?.let { headers[id] = it }
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
            storyHeaders = headers,
        )
    }

    private val commentCount = Regex("""(\d+)\s+comments?""")

    private fun storyHeader(item: Element, id: Int): StoryListingHeader? {
        val link = item.selectFirst(".titleline > a[href]") ?: return null
        val title = link.text().takeIf { it.isNotBlank() } ?: return null
        val url = link.absUrl("href")
        val parsed = url.toNetworkUrlOrNull() ?: return null
        if ((parsed.scheme != "http" && parsed.scheme != "https") || parsed.host.isEmpty()) return null
        // Self posts and polls need item details which the listing does not carry.
        if (parsed.host == HackerNewsLinks.HOST && parsed.encodedPath == "/item") return null
        val subtext = item.nextElementSibling()?.selectFirst("td.subtext") ?: return null
        val author = subtext.selectFirst("a.hnuser")?.text()?.takeIf { it.isNotBlank() } ?: return null
        val score = subtext.selectFirst("span#score_$id")?.text()?.substringBefore(' ')?.toIntOrNull() ?: return null
        val age = subtext.selectFirst(".age[title]")?.attr("title")?.trim() ?: return null
        val time = age.substringAfterLast(' ').toIntOrNull() ?: run {
            val utc = age.substringBefore(' ').removeSuffix("Z")
            Instant.parseOrNull("${utc}Z")?.epochSeconds?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
        } ?: return null
        if (time <= 0) return null
        val count = subtext.select("a[href]").firstNotNullOfOrNull { anchor ->
            val label = anchor.text().replace('\u00a0', ' ')
            val number = if (label == "discuss") 0 else commentCount.matchEntire(label)?.groupValues?.get(1)?.toIntOrNull()
            number?.takeIf { hackerNewsItemId(anchor) == id }
        } ?: return null
        return StoryListingHeader(title, url, author, score, time, count)
    }

    fun parseListDirectory(body: String): List<Story> {
        val document = Ksoup.parse(body, baseUri = HackerNewsLinks.ROOT_URL)
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
                            createdAtEpochSeconds = nowSeconds
                        },
                )
            }
        }
    }

    private fun hackerNewsItemId(link: Element): Int? {
        val url = link.absUrl("href").toNetworkUrlOrNull() ?: return null
        if (url.host != HackerNewsLinks.HOST || url.encodedPath != "/item") return null
        return url.queryParameter("id")?.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun listPath(link: Element): String? {
        val url = link.absUrl("href").toNetworkUrlOrNull() ?: return null
        if (url.host != HackerNewsLinks.HOST) return null
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
