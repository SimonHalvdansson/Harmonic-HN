package com.simon.harmonichackernews.network

import android.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.util.Pair
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.NetworkComponent.httpClientInstance
import com.simon.harmonichackernews.network.NetworkComponent.httpClientInstanceWithCookies
import com.simon.harmonichackernews.network.NetworkComponent.resetHttpClientCookieInstance
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.Utils
import java.io.IOException
import java.util.Objects
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.Triple
import org.jetbrains.annotations.NotNull
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element

private typealias Call = HttpCall
private typealias Callback = HttpCallback
private typealias HttpUrl = NetworkUrl
private typealias Request = HttpRequest
private typealias Response = HttpResponse

private fun String.toHttpUrlOrNull(): HttpUrl? = toNetworkUrlOrNull()

object UserActions {
    private const val BASE_WEB_URL = "https://news.ycombinator.com"
    private const val LOGIN_PATH = "login"
    private const val VOTE_PATH = "vote"
    private const val FAVE_PATH = "fave"
    private const val FAVORITES_PATH = "favorites"
    private const val UPVOTED_PATH = "upvoted"
    private const val ACTIVE_PATH = "active"
    private const val COMMENT_PATH = "comment"
    private const val SUBMIT_PATH = "submit"
    private const val ITEM_PATH = "item"
    private const val SUBMIT_POST_PATH = "r"
    private const val LOGIN_PARAM_ACCT = "acct"
    private const val LOGIN_PARAM_PW = "pw"
    private const val LOGIN_PARAM_CREATING = "creating"
    private const val LOGIN_PARAM_GOTO = "goto"
    private const val ITEM_PARAM_ID = "id"
    private const val AUTH_PARAM = "auth"
    private const val UNFAVORITE_PARAM = "un"
    private const val COMMENTS_PARAM = "comments"
    private const val VOTE_PARAM_ID = "id"
    private const val VOTE_PARAM_HOW = "how"
    private const val COMMENT_PARAM_PARENT = "parent"
    private const val COMMENT_PARAM_TEXT = "text"
    private const val SUBMIT_PARAM_TITLE = "title"
    private const val SUBMIT_PARAM_URL = "url"
    private const val SUBMIT_PARAM_TEXT = "text"
    private const val SUBMIT_PARAM_FNID = "fnid"
    private const val SUBMIT_PARAM_FNOP = "fnop"
    private const val VOTE_DIR_UP = "up"
    private const val VOTE_DIR_DOWN = "down"
    private const val VOTE_DIR_UN = "un"
    private const val DEFAULT_REDIRECT = "news"
    private const val CREATING_TRUE = "t"
    private const val DEFAULT_FNOP = "submit-page"
    private const val TRUE_VALUE = "t"
    private const val DEFAULT_SUBMIT_REDIRECT = "newest"
    private const val HEADER_LOCATION = "location"
    private const val HEADER_COOKIE = "cookie"
    private const val HEADER_SET_COOKIE = "set-cookie"
    private const val CAPTCHA_VALIDATION_TEXT =
        "Validation required. If this doesn't work, you can email"
    private const val CAPTCHA_RESPONSE_PARAM = "g-recaptcha-response"
    private val MAX_RESPONSE_PREVIEW_BYTES = (1024 * 1024).toLong()
    private const val MAX_USER_ITEM_LIST_PAGES = 50
    private val MAIN_HANDLER = Handler(Looper.getMainLooper())
    private val FNID_INPUT_PATTERN: Pattern = Pattern.compile(
        "<input[^>]*name=\"fnid\"[^>]*value=\"([^\"]+)\""
    )
    private val HACKER_NEWS_LIST_PATHS = arrayOf(
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
        "launches"
    )

    fun voteWithDir(ctx: Context, id: Int, dir: String) {
        voteWithDir(ctx, id, dir, null, null)
    }

    private fun voteWithDir(
        ctx: Context,
        id: Int,
        dir: String,
        successMessage: String?,
        cb: ActionCallback? = null
    ) {
        vote(id.toString(), dir, ctx, object : ActionCallback {
            override fun onSuccess(response: Response) {
                if (cb == null) {
                    var message = successMessage
                    if (TextUtils.isEmpty(message)) {
                        message = "Vote successful"
                        when (dir) {
                            VOTE_DIR_UP -> message = "Upvote successful"
                            VOTE_DIR_DOWN -> message = "Downvote successful"
                            VOTE_DIR_UN -> message = "Removed vote successfully"
                        }
                    }
                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                }
                if (cb != null) {
                    cb.onSuccess(response)
                }
            }

            override fun onFailure(summary: String?, response: String?) {
                showFailureDetailDialog(ctx, summary, response)
                Toast.makeText(
                    ctx,
                    "Vote unsuccessful, see dialog for response",
                    Toast.LENGTH_SHORT
                ).show()
                if (cb != null) {
                    cb.onFailure(summary, response)
                }
            }

            override fun onCaptchaRequired(challenge: CaptchaChallenge) {
                if (cb != null) {
                    cb.onCaptchaRequired(challenge)
                } else {
                    onFailure(
                        "Captcha required",
                        "HN requires a captcha for this action. Please try again in a browser."
                    )
                }
            }
        })
    }

    fun upvote(ctx: Context, id: Int) {
        voteWithDir(ctx, id, VOTE_DIR_UP)
    }

    fun upvote(ctx: Context, id: Int, cb: ActionCallback?) {
        voteWithDir(ctx, id, VOTE_DIR_UP, null, cb)
    }

    fun votePollOption(ctx: Context, id: Int) {
        voteWithDir(ctx, id, VOTE_DIR_UP, "Poll vote successful")
    }

    fun downvote(ctx: Context, id: Int) {
        voteWithDir(ctx, id, VOTE_DIR_DOWN)
    }

    fun downvote(ctx: Context, id: Int, cb: ActionCallback?) {
        voteWithDir(ctx, id, VOTE_DIR_DOWN, null, cb)
    }

    fun unvote(ctx: Context, id: Int) {
        voteWithDir(ctx, id, VOTE_DIR_UN)
    }

    fun unvote(ctx: Context, id: Int, cb: ActionCallback?) {
        voteWithDir(ctx, id, VOTE_DIR_UN, null, cb)
    }

    fun setFavorite(ctx: Context, id: Int, favorite: Boolean) {
        setFavorite(ctx, id, favorite, object : ActionCallback {
            override fun onSuccess(response: Response) {
                Toast.makeText(
                    ctx,
                    if (favorite) "Added favorite" else "Removed favorite",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onFailure(summary: String?, response: String?) {
                showFailureDetailDialog(ctx, summary, response)
                Toast.makeText(ctx, "Couldn't update favorite", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun setFavorite(ctx: Context, id: Int, favorite: Boolean, cb: ActionCallback) {
        val account = AccountUtils.getAccountDetails(ctx)
        if (AccountUtils.handlePossibleError(account, true, ctx)) {
            return
        }

        login(ctx, object : ActionCallback {
            override fun onSuccess(response: Response) {
                response.close()
                fetchFavoriteActionLink(ctx, id, favorite, cb)
            }

            override fun onFailure(summary: String?, response: String?) {
                cb.onFailure(summary, response)
            }

            override fun onCaptchaRequired(challenge: CaptchaChallenge) {
                cb.onCaptchaRequired(challenge)
            }
        })
    }

    fun fetchFavorites(ctx: Context, cb: UserItemListCallback) {
        fetchUserItemList(ctx, FAVORITES_PATH, "favorites", false, cb)
    }

    fun fetchUpvoted(ctx: Context, cb: UserItemListCallback) {
        fetchUserItemList(ctx, UPVOTED_PATH, "upvoted", true, cb)
    }

    fun fetchActiveStoryIds(ctx: Context, cb: StoryIdsCallback) {
        fetchStoryListIds(
            ctx,
            ACTIVE_PATH,
            "active stories",
            false,
            null,
            object : StoryListCallback {
                override fun onSuccess(
                    itemIds: MutableList<Int>,
                    commentIds: MutableList<Int>,
                    nextPageUrl: String?
                ) {
                    cb.onSuccess(itemIds)
                }

                override fun onFailure(summary: String?, response: String?) {
                    cb.onFailure(summary, response)
                }
            })
    }

    fun fetchStoryListIds(
        ctx: Context,
        path: String?,
        listName: String?,
        commentsPage: Boolean,
        day: String?,
        cb: StoryListCallback
    ) {
        if (TextUtils.isEmpty(path)) {
            cb.onFailure("Couldn't fetch " + listName, "Missing Hacker News path")
            return
        }

        val main = MAIN_HANDLER
        UserActions.fetchStoryListPage(
            httpClientInstance,
            UserActions.buildStoryListUrl(path.orEmpty(), day),
            listName,
            commentsPage,
            main,
            cb
        )
    }

    fun fetchStoryListPage(
        ctx: Context,
        url: String?,
        listName: String?,
        commentsPage: Boolean,
        cb: StoryListCallback
    ) {
        if (TextUtils.isEmpty(url)) {
            cb.onFailure("Couldn't fetch " + listName, "Missing Hacker News page URL")
            return
        }

        val main = MAIN_HANDLER
        UserActions.fetchStoryListPage(
            httpClientInstance,
            url.orEmpty(),
            listName,
            commentsPage,
            main,
            cb
        )
    }

    private fun buildStoryListUrl(path: String, day: String? = null): String {
        val builder = BASE_WEB_URL.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegment(path)
        if (!TextUtils.isEmpty(day)) {
            builder.addQueryParameter("day", day)
        }
        return builder.build().toString()
    }

    fun fetchHackerNewsListLinks(ctx: Context, cb: StoryRowsCallback) {
        val main = MAIN_HANDLER
        val request: Request = HttpRequest.Builder()
            .url(buildStoryListUrl("lists"))
            .build()

        httpClientInstance.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post(Runnable { cb.onFailure("Couldn't fetch HN lists", e.message) })
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val failure = response.toString()
                    response.close()
                    main.post(Runnable { cb.onFailure("Couldn't fetch HN lists", failure) })
                    return
                }

                try {
                    val body = if (response.body == null) "" else response.body.string()
                    val document = Ksoup.parse(body, baseUri = BASE_WEB_URL + "/")
                    val linkRows = parseHackerNewsListLinks(document)
                    main.post(Runnable { cb.onSuccess(linkRows) })
                } catch (e: Exception) {
                    main.post(Runnable { cb.onFailure("Couldn't parse HN lists", e.message) })
                }
            }
        })
    }

    private fun parseHackerNewsListLinks(document: Document): ArrayList<Story> {
        val linkRows = ArrayList<Story>()
        val seenPaths: MutableSet<String?> = HashSet<String?>()
        for (link in document.select("a[href]")) {
            val path = getHackerNewsListPath(link)
            if (TextUtils.isEmpty(path)
                || !isKnownHackerNewsListPath(path) || seenPaths.contains(path)
            ) {
                continue
            }

            seenPaths.add(path)
            val story = Story(
                buildHackerNewsListTitle(link),
                UserActions.getFrontpageLinkStoryId(path!!),
                true,
                false
            )
            story.isFrontpageLink = true
            story.isLink = true
            story.url = link.absUrl("href")
            story.by = "Hacker News"
            story.time = (System.currentTimeMillis() / 1000).toInt()
            linkRows.add(story)
        }
        return linkRows
    }

    private fun buildHackerNewsListTitle(link: Element): String {
        val title = link.text().trim { it <= ' ' }
        val row = link.closest("tr")
        if (row == null) {
            return title
        }

        val rowText = row.text().trim { it <= ' ' }
        if (rowText.startsWith(title)) {
            val description = rowText.substring(title.length).trim { it <= ' ' }
            if (!TextUtils.isEmpty(description)) {
                return title + " - " + description
            }
        }
        return title
    }

    private fun getHackerNewsListPath(link: Element): String? {
        val url = link.absUrl("href").toHttpUrlOrNull()
        if (url == null || "news.ycombinator.com" != url.host) {
            return null
        }

        var path = url.encodedPath
        if (path.startsWith("/")) {
            path = path.substring(1)
        }
        return if (path.contains("/")) null else path
    }

    private fun isKnownHackerNewsListPath(path: String?): Boolean {
        for (knownPath in HACKER_NEWS_LIST_PATHS) {
            if (knownPath == path) {
                return true
            }
        }
        return false
    }

    private fun getFrontpageLinkStoryId(path: String): Int {
        return -1 - (path.hashCode() and 0x7fffffff)
    }

    private fun fetchStoryListPage(
        client: KtorHttpClient,
        url: String,
        listName: String?,
        commentsPage: Boolean,
        main: Handler,
        cb: StoryListCallback
    ) {
        val request = HttpRequest.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post(Runnable { cb.onFailure("Couldn't fetch " + listName, e.message) })
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val failure = response.toString()
                    response.close()
                    main.post(Runnable { cb.onFailure("Couldn't fetch " + listName, failure) })
                    return
                }

                try {
                    val itemIds = ArrayList<Int>()
                    val commentIds = ArrayList<Int>()
                    val body = if (response.body == null) "" else response.body.string()
                    val document = Ksoup.parse(body, baseUri = BASE_WEB_URL + "/")
                    addHackerNewsItemIds(document, itemIds, commentIds, commentsPage)

                    val moreLink = document.selectFirst("a.morelink[href]")
                    val nextPage = if (moreLink == null) null else moreLink.absUrl("href")
                    main.post(Runnable { cb.onSuccess(itemIds, commentIds, nextPage) })
                } catch (e: Exception) {
                    main.post(Runnable { cb.onFailure("Couldn't parse " + listName, e.message) })
                }
            }
        })
    }

    private fun fetchUserItemList(
        ctx: Context,
        path: String,
        listName: String?,
        loginRequired: Boolean,
        cb: UserItemListCallback
    ) {
        val account = AccountUtils.getAccountDetails(ctx)
        if (AccountUtils.handlePossibleError(account, false, ctx)) {
            cb.onFailure(
                "Login required",
                "Save your Hacker News login before syncing " + listName + "."
            )
            return
        }

        val main = MAIN_HANDLER
        val fetch = Runnable {
            val client = if (loginRequired)
                httpClientInstanceWithCookies
            else
                httpClientInstance
            val itemIds = ArrayList<Int>()
            val commentIds = ArrayList<Int>()
            UserActions.fetchUserItemListPage(
                client,
                buildUserItemListUrl(path, account.first, false),
                itemIds,
                commentIds,
                1,
                false,
                listName,
                main,
                object : UserItemListCallback {
                    override fun onSuccess(ids: MutableList<Int>, comments: MutableList<Int>) {
                        UserActions.fetchUserItemListPage(
                            client,
                            buildUserItemListUrl(path, account.first, true),
                            ids,
                            comments,
                            1,
                            true,
                            listName,
                            main,
                            cb
                        )
                    }

                    override fun onFailure(summary: String?, response: String?) {
                        cb.onFailure(summary, response)
                    }
                })
        }

        if (!loginRequired) {
            fetch.run()
            return
        }

        login(ctx, object : ActionCallback {
            override fun onSuccess(response: Response) {
                response.close()
                fetch.run()
            }

            override fun onFailure(summary: String?, response: String?) {
                cb.onFailure(summary, response)
            }

            override fun onCaptchaRequired(challenge: CaptchaChallenge) {
                cb.onFailure(
                    "Captcha required",
                    "HN asked for a captcha before syncing " + listName + "."
                )
            }
        })
    }

    private fun buildUserItemListUrl(path: String, username: String?, comments: Boolean): String {
        val builder = BASE_WEB_URL.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegment(path)
            .addQueryParameter("id", username)

        if (comments) {
            builder.addQueryParameter(COMMENTS_PARAM, TRUE_VALUE)
        }

        return builder.build().toString()
    }

    private fun fetchUserItemListPage(
        client: KtorHttpClient,
        url: String,
        itemIds: MutableList<Int>,
        commentIds: MutableList<Int>,
        page: Int,
        commentsPage: Boolean,
        listName: String?,
        main: Handler,
        cb: UserItemListCallback
    ) {
        val request = HttpRequest.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post(Runnable { cb.onFailure("Couldn't sync " + listName, e.message) })
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val failure = response.toString()
                    response.close()
                    main.post(Runnable { cb.onFailure("Couldn't sync " + listName, failure) })
                    return
                }

                try {
                    val body = if (response.body == null) "" else response.body.string()
                    val document = Ksoup.parse(body, baseUri = BASE_WEB_URL + "/")
                    val pageItemIds: MutableList<Int> = ArrayList<Int>()
                    addHackerNewsItemIds(document, pageItemIds)
                    for (id in pageItemIds) {
                        if (!itemIds.contains(id)) {
                            itemIds.add(id)
                        }
                        if (commentsPage && !commentIds.contains(id)) {
                            commentIds.add(id)
                        }
                    }

                    val moreLink = document.selectFirst("a.morelink[href]")
                    val nextPage = if (moreLink == null) null else moreLink.absUrl("href")
                    if (!TextUtils.isEmpty(nextPage) && page < MAX_USER_ITEM_LIST_PAGES) {
                        UserActions.fetchUserItemListPage(
                            client,
                            nextPage!!,
                            itemIds,
                            commentIds,
                            page + 1,
                            commentsPage,
                            listName,
                            main,
                            cb
                        )
                    } else {
                        main.post(Runnable { cb.onSuccess(itemIds, commentIds) })
                    }
                } catch (e: Exception) {
                    main.post(Runnable { cb.onFailure("Couldn't parse " + listName, e.message) })
                }
            }
        })
    }

    private fun addHackerNewsItemIds(
        document: Document,
        itemIds: MutableList<Int>,
        commentIds: MutableList<Int>? = null,
        commentsPage: Boolean = false
    ) {
        for (item in document.select("tr.athing[id]")) {
            val idString = item.attr("id")
            if (!TextUtils.isDigitsOnly(idString)) {
                continue
            }

            val id = idString.toInt()
            addHackerNewsItemId(itemIds, commentIds, id, commentsPage)
        }

        if (commentsPage) {
            for (ageLink in document.select("span.comhead span.age a[href]")) {
                val id = getHackerNewsItemIdFromHref(ageLink)
                if (id > 0) {
                    addHackerNewsItemId(itemIds, commentIds, id, true)
                }
            }
        }
    }

    private fun addHackerNewsItemId(
        itemIds: MutableList<Int>,
        commentIds: MutableList<Int>?,
        id: Int,
        isComment: Boolean
    ) {
        if (!itemIds.contains(id)) {
            itemIds.add(id)
        }
        if (isComment && commentIds != null && !commentIds.contains(id)) {
            commentIds.add(id)
        }
    }

    private fun getHackerNewsItemIdFromHref(link: Element): Int {
        val url = link.absUrl("href").toHttpUrlOrNull()
        if (url == null || ITEM_PATH != url.encodedPath.replaceFirst("^/".toRegex(), "")) {
            return -1
        }

        val idString = url.queryParameter(ITEM_PARAM_ID)
        if (!TextUtils.isDigitsOnly(idString)) {
            return -1
        }

        return idString!!.toInt()
    }

    private fun fetchFavoriteActionLink(
        ctx: Context,
        id: Int,
        favorite: Boolean,
        cb: ActionCallback
    ) {
        val main = MAIN_HANDLER
        val url = BASE_WEB_URL.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegment(ITEM_PATH)
            .addQueryParameter(ITEM_PARAM_ID, id.toString())
            .build()

        val request = HttpRequest.Builder()
            .url(url)
            .build()

        httpClientInstanceWithCookies.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post(Runnable { cb.onFailure("Couldn't load HN item", e.message) })
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val failure = response.toString()
                    response.close()
                    main.post(Runnable { cb.onFailure("Couldn't load HN item", failure) })
                    return
                }

                try {
                    val body = if (response.body == null) "" else response.body.string()
                    if (body.contains("Bad login.")) {
                        AccountUtils.deleteAccountDetails(ctx)
                        main.post(Runnable {
                            cb.onFailure(
                                "Bad login",
                                "Your session has expired or credentials are invalid. Logged out."
                            )
                        })
                        return
                    }

                    if (isCaptchaRequired(body)) {
                        val challenge = parseCaptchaChallenge(body, true)
                        if (challenge != null) {
                            main.post(Runnable { cb.onCaptchaRequired(challenge) })
                        } else {
                            main.post(Runnable {
                                cb.onFailure(
                                    "Captcha parsing error",
                                    "HN asked for a captcha, but Harmonic could not read the challenge form."
                                )
                            })
                        }
                        return
                    }

                    val document = Ksoup.parse(body, baseUri = BASE_WEB_URL + "/")
                    val itemTitle = findHackerNewsItemTitle(document, id)
                    if (!TextUtils.isEmpty(itemTitle)) {
                        main.post(Runnable { cb.onItemTitleLoaded(id, itemTitle) })
                    }

                    val linkResult = findFavoriteLink(document, id, favorite)
                    if (linkResult.alreadyDesiredState) {
                        if (favorite) {
                            Utils.addFavorite(ctx, id)
                        } else {
                            Utils.removeFavorite(ctx, id)
                        }
                        main.post(Runnable { cb.onSuccess(response) })
                        return
                    }

                    if (TextUtils.isEmpty(linkResult.actionUrl)) {
                        main.post(Runnable {
                            cb.onFailure(
                                "Favorite unavailable",
                                "HN did not return a favorite action for this item."
                            )
                        })
                        return
                    }

                    val favoriteRequest = HttpRequest.Builder()
                        .url(linkResult.actionUrl!!)
                        .build()

                    executeRequest(ctx, favoriteRequest, object : ActionCallback {
                        override fun onSuccess(response: Response) {
                            response.close()
                            verifyFavoriteState(ctx, id, favorite, cb)
                        }

                        override fun onFailure(summary: String?, response: String?) {
                            cb.onFailure(summary, response)
                        }

                        override fun onCaptchaRequired(challenge: CaptchaChallenge) {
                            cb.onCaptchaRequired(challenge)
                        }
                    }, true)
                } catch (e: Exception) {
                    main.post(Runnable {
                        cb.onFailure(
                            "Couldn't parse favorite action",
                            e.message
                        )
                    })
                }
            }
        })
    }

    private fun verifyFavoriteState(ctx: Context, id: Int, favorite: Boolean, cb: ActionCallback) {
        val main = MAIN_HANDLER
        val url = BASE_WEB_URL.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegment(ITEM_PATH)
            .addQueryParameter(ITEM_PARAM_ID, id.toString())
            .build()

        val request = HttpRequest.Builder()
            .url(url)
            .build()

        httpClientInstanceWithCookies.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post(Runnable { cb.onFailure("Couldn't verify favorite", e.message) })
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val failure = response.toString()
                    response.close()
                    main.post(Runnable { cb.onFailure("Couldn't verify favorite", failure) })
                    return
                }

                try {
                    val body = if (response.body == null) "" else response.body.string()
                    if (body.contains("Bad login.")) {
                        AccountUtils.deleteAccountDetails(ctx)
                        response.close()
                        main.post(Runnable {
                            cb.onFailure(
                                "Bad login",
                                "Your session has expired or credentials are invalid. Logged out."
                            )
                        })
                        return
                    }

                    if (isCaptchaRequired(body)) {
                        val challenge = parseCaptchaChallenge(body, true)
                        response.close()
                        if (challenge != null) {
                            main.post(Runnable { cb.onCaptchaRequired(challenge) })
                        } else {
                            main.post(Runnable {
                                cb.onFailure(
                                    "Captcha parsing error",
                                    "HN asked for a captcha, but Harmonic could not read the challenge form."
                                )
                            })
                        }
                        return
                    }

                    val linkResult = findFavoriteLink(body, id, favorite)
                    if (!linkResult.alreadyDesiredState) {
                        response.close()
                        main.post(Runnable {
                            cb.onFailure(
                                "Favorite update not confirmed",
                                if (favorite)
                                    "HN still reports this item as not favorited."
                                else
                                    "HN still reports this item as favorited."
                            )
                        })
                        return
                    }

                    if (favorite) {
                        Utils.addFavorite(ctx, id)
                    } else {
                        Utils.removeFavorite(ctx, id)
                    }
                    main.post(Runnable { cb.onSuccess(response) })
                } catch (e: Exception) {
                    response.close()
                    main.post(Runnable { cb.onFailure("Couldn't verify favorite", e.message) })
                }
            }
        })
    }

    private fun findFavoriteLink(body: String, id: Int, favorite: Boolean): FavoriteLinkResult {
        val document = Ksoup.parse(body, baseUri = BASE_WEB_URL + "/")
        return findFavoriteLink(document, id, favorite)
    }

    private fun findFavoriteLink(
        document: Document,
        id: Int,
        favorite: Boolean
    ): FavoriteLinkResult {
        val result = FavoriteLinkResult()

        for (link in document.select("a[href]")) {
            val actionUrl = link.absUrl("href").toHttpUrlOrNull()
            if (actionUrl == null || ("https" != actionUrl.scheme) || (BASE_WEB_URL.toHttpUrlOrNull()!!.host != actionUrl.host) || (("/" + FAVE_PATH) != actionUrl.encodedPath) || (id.toString() != actionUrl.queryParameter(
                    ITEM_PARAM_ID
                )) || TextUtils.isEmpty(actionUrl.queryParameter(AUTH_PARAM))
            ) {
                continue
            }

            val unfavoriteValue = actionUrl.queryParameter(UNFAVORITE_PARAM)
            if (unfavoriteValue != null && TRUE_VALUE != unfavoriteValue) {
                continue
            }

            val removalAction = TRUE_VALUE == unfavoriteValue
            val currentlyFavorited = removalAction
            if (currentlyFavorited == favorite) {
                result.alreadyDesiredState = true
            } else {
                result.actionUrl = actionUrl.toString()
            }
            return result
        }

        return result
    }

    private fun findHackerNewsItemTitle(document: Document, id: Int): String {
        val item = document.getElementById(id.toString())
        val titleLink = if (item == null) null else item.selectFirst("span.titleline > a")
        return if (titleLink == null) "" else titleLink.text().trim { it <= ' ' }
    }


    fun vote(itemId: String, direction: String, ctx: Context, cb: ActionCallback) {
        Utils.log("Attempting to vote")
        val account = AccountUtils.getAccountDetails(ctx)

        if (AccountUtils.handlePossibleError(account, true, ctx)) {
            return
        }

        val request = HttpRequest.Builder()
            .url(
                BASE_WEB_URL.toHttpUrlOrNull()!!.newBuilder()
                    .addPathSegment(VOTE_PATH)
                    .build()
            )
            .post(
                FormRequestBody.Builder()
                    .add(LOGIN_PARAM_ACCT, account.first!!)
                    .add(LOGIN_PARAM_PW, account.second!!)
                    .add(VOTE_PARAM_ID, itemId)
                    .add(VOTE_PARAM_HOW, direction)
                    .build()
            )
            .build()

        executeRequest(ctx, request, cb)
    }


    fun comment(itemId: String, text: String, ctx: Context, cb: ActionCallback) {
        val account = AccountUtils.getAccountDetails(ctx)

        if (AccountUtils.handlePossibleError(account, false, ctx)) {
            return
        }

        val request = HttpRequest.Builder()
            .url(
                BASE_WEB_URL.toHttpUrlOrNull()!!.newBuilder()
                    .addPathSegment(COMMENT_PATH)
                    .build()
            )
            .post(
                FormRequestBody.Builder()
                    .add(LOGIN_PARAM_ACCT, account.first!!)
                    .add(LOGIN_PARAM_PW, account.second!!)
                    .add(COMMENT_PARAM_PARENT, itemId)
                    .add(COMMENT_PARAM_TEXT, text)
                    .build()
            )
            .build()

        executeRequest(ctx, request, cb)
    }

    /**
     * Performs a login POST to Hacker News and verifies credentials by checking for the fnid
     * input in the resulting /submit page. Calls onSuccess only if login succeeded and the
     * submit form is present.
     */
    fun login(ctx: Context, cb: ActionCallback) {
        // always redirect to submit page to verify login
        val gotoPath = SUBMIT_PATH

        // Retrieve stored account details
        val account = AccountUtils.getAccountDetails(ctx)
        if (AccountUtils.handlePossibleError(account, false, ctx)) {
            cb.onFailure("Couldn't read credentials", "Check your saved login.")
            return
        }

        // Build login form
        val form = FormRequestBody.Builder()
            .add(LOGIN_PARAM_ACCT, account.first!!)
            .add(LOGIN_PARAM_PW, account.second!!)
            .add(LOGIN_PARAM_GOTO, gotoPath)
            .build()

        val request = HttpRequest.Builder()
            .url(BASE_WEB_URL + "/" + LOGIN_PATH)
            .post(form)
            .build()

        resetHttpClientCookieInstance()
        executeLoginRequest(ctx, request, cb)
    }

    private fun executeLoginRequest(ctx: Context, request: Request, cb: ActionCallback) {
        val main = MAIN_HANDLER
        val client = httpClientInstanceWithCookies
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post(Runnable { cb.onFailure("Login failed", e.message) })
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    main.post(Runnable {
                        cb.onFailure(
                            "Login failed: HTTP " + response.code, response.toString()
                        )
                    })
                    return
                }
                try {
                    // Peek at a small part of the body to find fnid without consuming full stream
                    val preview = response.peekBody(MAX_RESPONSE_PREVIEW_BYTES).string()
                    val matcher = FNID_INPUT_PATTERN.matcher(preview)
                    if (preview.contains("Bad login.")) {
                        main.post(Runnable {
                            cb.onFailure(
                                "Bad login",
                                "Your credentials are invalid."
                            )
                        })
                    } else if (isCaptchaRequired(preview)) {
                        val challenge = parseCaptchaChallenge(preview, true)
                        response.close()
                        if (challenge != null) {
                            main.post(Runnable { cb.onCaptchaRequired(challenge) })
                        } else {
                            main.post(Runnable {
                                cb.onFailure(
                                    "Captcha parsing error",
                                    "HN asked for a captcha, but Harmonic could not read the challenge form."
                                )
                            })
                        }
                    } else if (!matcher.find()) {
                        main.post(Runnable { cb.onFailure("Bad login", "Submit form not found") })
                    } else {
                        main.post(Runnable { cb.onSuccess(response) })
                    }
                } catch (e: IOException) {
                    main.post(Runnable { cb.onFailure("Login parsing error", e.message) })
                }
            }
        })
    }

    fun continueLoginWithCaptcha(
        ctx: Context,
        challenge: CaptchaChallenge,
        captchaResponse: String,
        cb: ActionCallback
    ) {
        executeLoginRequest(ctx, buildCaptchaRequest(challenge, captchaResponse), cb)
    }

    /**
     * Submits a story to Hacker News: logs in (checked above), then parses fnid and posts submission.
     */
    fun submit(
        title: String,
        text: String,
        url: String,
        ctx: Context,
        cb: ActionCallback
    ) {
        val main = MAIN_HANDLER

        // Login first (will check credentials and readiness of submit page)
        login(ctx, object : ActionCallback {
            override fun onFailure(summary: String?, response: String?) {
                main.post(Runnable { cb.onFailure(summary, response) })
            }

            override fun onCaptchaRequired(challenge: CaptchaChallenge) {
                main.post(Runnable { cb.onCaptchaRequired(challenge) })
            }

            override fun onSuccess(loginResp: Response) {
                main.post(Runnable {
                    submitAfterSuccessfulLogin(
                        title,
                        text,
                        url,
                        ctx,
                        cb,
                        loginResp
                    )
                })
            }
        })
    }

    fun submitAfterLoginCaptcha(
        title: String,
        text: String,
        url: String,
        ctx: Context,
        challenge: CaptchaChallenge,
        captchaResponse: String,
        cb: ActionCallback
    ) {
        continueLoginWithCaptcha(ctx, challenge, captchaResponse, object : ActionCallback {
            override fun onSuccess(response: Response) {
                submitAfterSuccessfulLogin(title, text, url, ctx, cb, response)
            }

            override fun onFailure(summary: String?, response: String?) {
                cb.onFailure(summary, response)
            }

            override fun onCaptchaRequired(challenge: CaptchaChallenge) {
                cb.onCaptchaRequired(challenge)
            }
        })
    }

    private fun submitAfterSuccessfulLogin(
        title: String,
        text: String,
        url: String,
        ctx: Context,
        cb: ActionCallback,
        loginResp: Response
    ) {
        val html: String?
        try {
            html = loginResp.body.string()
        } catch (e: IOException) {
            cb.onFailure("Error reading login response", e.message)
            return
        }
        val m = FNID_INPUT_PATTERN.matcher(html)
        if (!m.find()) {
            cb.onFailure("HN submit form parsing error", "No fnid found on /submit")
            return
        }
        val fnid = m.group(1)
        val submitForm = FormRequestBody.Builder()
            .add("fnid", fnid!!)
            .add("fnop", "submit-page")
            .add("title", title)
            .add("url", url)
            .add("text", text)
        val submitReq = HttpRequest.Builder()
            .url(BASE_WEB_URL + "/" + SUBMIT_POST_PATH)
            .post(submitForm.build())
            .build()
        executeRequest(ctx, submitReq, cb, true)
    }

    @JvmOverloads
    fun executeRequest(
        ctx: Context,
        request: Request,
        cb: ActionCallback,
        cookies: Boolean = false
    ) {
        val client = if (cookies) httpClientInstanceWithCookies else httpClientInstance

        client.newCall(request).enqueue(object : Callback {
            val mainHandler: Handler = MAIN_HANDLER

            override fun onResponse(call: Call, response: Response) {
                mainHandler.post(Runnable responseTask@ {
                    if (!response.isSuccessful) {
                        cb.onFailure("Unsuccessful response", response.toString())
                        return@responseTask
                    }
                    var body = ""
                    try {
                        body = response.body.string()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    if (body.contains("Unknown or expired link.")) {
                        cb.onFailure("Unknown or expired link", body)
                    } else if (body.contains("Bad login.")) {
                        AccountUtils.deleteAccountDetails(ctx)
                        cb.onFailure(
                            "Bad login",
                            "Your session has expired or credentials are invalid. Logged out."
                        )
                    } else if (isCaptchaRequired(body)) {
                        val challenge = parseCaptchaChallenge(body, cookies)
                        if (challenge != null) {
                            cb.onCaptchaRequired(challenge)
                        } else {
                            cb.onFailure(
                                "Captcha parsing error",
                                "HN asked for a captcha, but Harmonic could not read the challenge form."
                            )
                        }
                    } else {
                        // HN sends a 302 to the new post; Ktor follows redirects by default.
                        cb.onSuccess(response)
                    }
                })
            }

            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post(Runnable { cb.onFailure("Couldn't connect to HN", e.message) })
            }
        })
    }


    fun continueCaptchaAction(
        ctx: Context,
        challenge: CaptchaChallenge,
        captchaResponse: String,
        cb: ActionCallback
    ) {
        executeRequest(
            ctx,
            buildCaptchaRequest(challenge, captchaResponse),
            cb,
            challenge.useCookies()
        )
    }

    private fun buildCaptchaRequest(challenge: CaptchaChallenge, captchaResponse: String): Request {
        val formBuilder = FormRequestBody.Builder()
        for (field in challenge.getFormFields()!!) {
            formBuilder.add(field.first!!, (if (field.second == null) "" else field.second)!!)
        }
        formBuilder.add(CAPTCHA_RESPONSE_PARAM, captchaResponse)

        return HttpRequest.Builder()
            .url(challenge.actionUrl)
            .post(formBuilder.build())
            .build()
    }

    private fun isCaptchaRequired(body: String?): Boolean {
        return body != null && body.contains(CAPTCHA_VALIDATION_TEXT) && body.contains("g-recaptcha")
    }

    private fun parseCaptchaChallenge(body: String, cookies: Boolean): CaptchaChallenge? {
        val document = Ksoup.parse(body, baseUri = BASE_WEB_URL + "/")
        val form = document.selectFirst("form[action]")
        val captcha = document.selectFirst(".g-recaptcha[data-sitekey]")

        if (form == null || captcha == null) {
            return null
        }

        var actionUrl = form.absUrl("action")
        if (TextUtils.isEmpty(actionUrl)) {
            val parsedBase = BASE_WEB_URL.toHttpUrlOrNull()
            if (parsedBase == null) {
                return null
            }
            actionUrl = parsedBase.newBuilder()
                .addPathSegment(form.attr("action"))
                .build()
                .toString()
        }

        val siteKey = captcha.attr("data-sitekey")
        if (TextUtils.isEmpty(siteKey)) {
            return null
        }

        val formFields = ArrayList<Pair<String?, String?>>()
        for (input in form.select("input[name], textarea[name]")) {
            val name = input.attr("name")
            val type = input.attr("type")
            if (TextUtils.isEmpty(name)
                || CAPTCHA_RESPONSE_PARAM == name
                || "submit".equals(type, ignoreCase = true)
                || "button".equals(type, ignoreCase = true)
            ) {
                continue
            }

            formFields.add(Pair<String?, String?>(name, input.value()))
        }

        return CaptchaChallenge(actionUrl, siteKey, formFields, cookies)
    }

    @JvmOverloads
    fun showFailureDetailDialog(
        ctx: Context,
        summary: String?,
        response: String?,
        clipboardText: String? = null
    ) {
        // We need to try-catch this because it is called asynchronously and if the app has been
        // closed we cannot show a dialog. Instead of checking for this, we can just try-catch! :)
        try {
            val builder = MaterialAlertDialogBuilder(ctx)
                .setTitle(summary)
                .setMessage(response)
                .setPositiveButton("Done", null)

            if (clipboardText != null) {
                builder.setNeutralButton("Copy comment", object : DialogInterface.OnClickListener {
                    override fun onClick(dialogInterface: DialogInterface?, i: Int) {
                        val clipboard =
                            ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Hacker News comment", clipboardText)
                        clipboard.setPrimaryClip(clip)

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(ctx, "Comment copied to clipboard", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                })
            }

            val dialog = builder.create()

            dialog.show()

            val messageView = dialog.findViewById<TextView?>(R.id.message)
            if (messageView != null) {
                messageView.setTextIsSelectable(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    class CaptchaChallenge(
        val actionUrl: String,
        private val siteKey: String?,
        private val formFields: ArrayList<Pair<String?, String?>>?,
        private val cookies: Boolean
    ) {
        val captchaHtml: String
            get() {
                val safeSiteKey = TextUtils.htmlEncode(siteKey)
                return ("<!doctype html><html><head>"
                        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                        + "<script src=\"https://www.google.com/recaptcha/api.js\" async defer></script>"
                        + "<style>body{margin:0;padding:16px;background:#fff;font-family:sans-serif;} .wrap{min-height:420px;}</style>"
                        + "</head><body><div class=\"wrap\"><div class=\"g-recaptcha\" data-sitekey=\""
                        + safeSiteKey
                        + "\"></div></div></body></html>")
            }

        fun getFormFields(): MutableList<Pair<String?, String?>>? {
            return formFields
        }

        fun useCookies(): Boolean {
            return cookies
        }

        val isLoginChallenge: Boolean
            get() {
                val url = actionUrl.toHttpUrlOrNull()
                return url != null && url.pathSegments.size == 1 && LOGIN_PATH == url.pathSegments.get(
                    0
                )
            }
    }


    interface ActionCallback {
        fun onSuccess(response: Response)
        fun onFailure(summary: String?, response: String?)

        fun onItemTitleLoaded(itemId: Int, title: String?) {
        }

        fun onCaptchaRequired(challenge: CaptchaChallenge) {
            onFailure(
                "Captcha required",
                "HN requires a captcha for this action. Please try again in a browser."
            )
        }
    }

    interface UserItemListCallback {
        fun onSuccess(itemIds: MutableList<Int>, commentIds: MutableList<Int>)
        fun onFailure(summary: String?, response: String?)
    }

    interface StoryIdsCallback {
        fun onSuccess(itemIds: MutableList<Int>)
        fun onFailure(summary: String?, response: String?)
    }

    interface StoryListCallback {
        fun onSuccess(
            itemIds: MutableList<Int>,
            commentIds: MutableList<Int>,
            nextPageUrl: String?
        )

        fun onFailure(summary: String?, response: String?)
    }

    interface StoryRowsCallback {
        fun onSuccess(stories: MutableList<Story>)
        fun onFailure(summary: String?, response: String?)
    }

    private class FavoriteLinkResult {
        var actionUrl: String? = null
        var alreadyDesiredState: Boolean = false
    }
}
