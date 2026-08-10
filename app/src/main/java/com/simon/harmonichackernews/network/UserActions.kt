package com.simon.harmonichackernews.network

import android.content.Context
import android.text.TextUtils
import android.widget.Toast
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.Utils

/**
 * Android adapter for Hacker News web actions.
 *
 * Credentials, local preference updates, Toasts, and dialogs remain in the app shell. The HN
 * protocol, HTML parsing, pagination, cookie-backed login, and captcha state live in shared code.
 */
object UserActions {
    private const val FAVORITES_PATH = "favorites"
    private const val UPVOTED_PATH = "upvoted"
    private const val ACTIVE_PATH = "active"
    private const val VOTE_DIR_UP = "up"
    private const val VOTE_DIR_DOWN = "down"
    private const val VOTE_DIR_UN = "un"

    fun voteWithDir(ctx: Context, id: Int, dir: String) {
        voteWithDir(ctx, id, dir, null, null)
    }

    private fun voteWithDir(
        ctx: Context,
        id: Int,
        dir: String,
        successMessage: String?,
        cb: ActionCallback? = null,
    ) {
        vote(id.toString(), dir, ctx, object : ActionCallback {
            override fun onSuccess() {
                if (cb == null) {
                    val message = successMessage ?: when (dir) {
                        VOTE_DIR_UP -> "Upvote successful"
                        VOTE_DIR_DOWN -> "Downvote successful"
                        VOTE_DIR_UN -> "Removed vote successfully"
                        else -> "Vote successful"
                    }
                    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
                } else {
                    cb.onSuccess()
                }
            }

            override fun onFailure(summary: String?, response: String?) {
                MainActivity.showFailureDetailForActiveUi(summary, response)
                Toast.makeText(
                    ctx,
                    "Vote unsuccessful, see dialog for response",
                    Toast.LENGTH_SHORT,
                ).show()
                cb?.onFailure(summary, response)
            }

            override fun onCaptchaRequired(challenge: HackerNewsCaptchaChallenge) {
                if (cb != null) {
                    cb.onCaptchaRequired(challenge)
                } else {
                    onFailure(
                        "Captcha required",
                        "HN requires a captcha for this action. Please try again in a browser.",
                    )
                }
            }
        })
    }

    fun upvote(ctx: Context, id: Int) = voteWithDir(ctx, id, VOTE_DIR_UP)

    fun upvote(ctx: Context, id: Int, cb: ActionCallback?) =
        voteWithDir(ctx, id, VOTE_DIR_UP, null, cb)

    fun votePollOption(ctx: Context, id: Int) =
        voteWithDir(ctx, id, VOTE_DIR_UP, "Poll vote successful")

    fun downvote(ctx: Context, id: Int) = voteWithDir(ctx, id, VOTE_DIR_DOWN)

    fun downvote(ctx: Context, id: Int, cb: ActionCallback?) =
        voteWithDir(ctx, id, VOTE_DIR_DOWN, null, cb)

    fun unvote(ctx: Context, id: Int) = voteWithDir(ctx, id, VOTE_DIR_UN)

    fun unvote(ctx: Context, id: Int, cb: ActionCallback?) =
        voteWithDir(ctx, id, VOTE_DIR_UN, null, cb)

    fun setFavorite(ctx: Context, id: Int, favorite: Boolean) {
        setFavorite(ctx, id, favorite, object : ActionCallback {
            override fun onSuccess() {
                Toast.makeText(
                    ctx,
                    if (favorite) "Added favorite" else "Removed favorite",
                    Toast.LENGTH_SHORT,
                ).show()
            }

            override fun onFailure(summary: String?, response: String?) {
                MainActivity.showFailureDetailForActiveUi(summary, response)
                Toast.makeText(ctx, "Couldn't update favorite", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun setFavorite(ctx: Context, id: Int, favorite: Boolean, cb: ActionCallback) {
        val credentials = readCredentials(ctx, showError = true) ?: return
        NetworkComponent.resetHttpClientCookieInstance()
        launchAction(
            ctx = ctx,
            failureSummary = "Couldn't update favorite",
            cb = cb,
            request = {
                NetworkComponent.hackerNewsActionRepository.setFavorite(
                    credentials,
                    id,
                    favorite,
                )
            },
            onSuccess = {
                Utils.setFavorite(ctx, id, favorite)
            },
        )
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
                    nextPageUrl: String?,
                ) = cb.onSuccess(itemIds)

                override fun onFailure(summary: String?, response: String?) =
                    cb.onFailure(summary, response)
            },
        )
    }

    fun fetchStoryListIds(
        @Suppress("UNUSED_PARAMETER") ctx: Context,
        path: String?,
        listName: String?,
        commentsPage: Boolean,
        day: String?,
        cb: StoryListCallback,
    ) {
        if (path.isNullOrBlank()) {
            cb.onFailure("Couldn't fetch $listName", "Missing Hacker News path")
            return
        }
        NetworkComponent.launchCallbackRequest(
            request = {
                NetworkComponent.hackerNewsWebRepository.getStoryList(path, commentsPage, day)
            },
            onSuccess = { page ->
                cb.onSuccess(
                    page.itemIds.toMutableList(),
                    page.commentIds.toMutableList(),
                    page.nextPageUrl,
                )
            },
            onFailure = { cb.onFailure("Couldn't fetch $listName", it.message) },
        )
    }

    fun fetchStoryListPage(
        @Suppress("UNUSED_PARAMETER") ctx: Context,
        url: String?,
        listName: String?,
        commentsPage: Boolean,
        cb: StoryListCallback,
    ) {
        if (url.isNullOrBlank()) {
            cb.onFailure("Couldn't fetch $listName", "Missing Hacker News page URL")
            return
        }
        NetworkComponent.launchCallbackRequest(
            request = {
                NetworkComponent.hackerNewsWebRepository.getStoryListPage(url, commentsPage)
            },
            onSuccess = { page ->
                cb.onSuccess(
                    page.itemIds.toMutableList(),
                    page.commentIds.toMutableList(),
                    page.nextPageUrl,
                )
            },
            onFailure = { cb.onFailure("Couldn't fetch $listName", it.message) },
        )
    }

    fun fetchHackerNewsListLinks(
        @Suppress("UNUSED_PARAMETER") ctx: Context,
        cb: StoryRowsCallback,
    ) {
        NetworkComponent.launchCallbackRequest(
            request = { NetworkComponent.hackerNewsWebRepository.getListDirectory() },
            onSuccess = { cb.onSuccess(ArrayList(it)) },
            onFailure = { cb.onFailure("Couldn't fetch HN lists", it.message) },
        )
    }

    private fun fetchUserItemList(
        ctx: Context,
        path: String,
        listName: String,
        loginRequired: Boolean,
        cb: UserItemListCallback,
    ) {
        val credentials = readCredentials(ctx, showError = false)
        if (credentials == null) {
            cb.onFailure(
                "Login required",
                "Save your Hacker News login before syncing $listName.",
            )
            return
        }

        fun fetch() {
            val repository = if (loginRequired) {
                NetworkComponent.authenticatedHackerNewsWebRepository
            } else {
                NetworkComponent.hackerNewsWebRepository
            }
            NetworkComponent.launchCallbackRequest(
                request = { repository.getUserItems(path, credentials.username) },
                onSuccess = {
                    cb.onSuccess(it.itemIds.toMutableList(), it.commentIds.toMutableList())
                },
                onFailure = { cb.onFailure("Couldn't sync $listName", it.message) },
            )
        }

        if (!loginRequired) {
            fetch()
            return
        }
        login(ctx, object : ActionCallback {
            override fun onSuccess() = fetch()

            override fun onFailure(summary: String?, response: String?) =
                cb.onFailure(summary, response)

            override fun onCaptchaRequired(challenge: HackerNewsCaptchaChallenge) {
                cb.onFailure("Captcha required", "HN asked for a captcha before syncing $listName.")
            }
        })
    }

    fun vote(itemId: String, direction: String, ctx: Context, cb: ActionCallback) {
        Utils.log("Attempting to vote")
        val credentials = readCredentials(ctx, showError = true) ?: return
        launchAction(ctx, "Couldn't connect to HN", cb) {
            NetworkComponent.hackerNewsActionRepository.vote(credentials, itemId, direction)
        }
    }

    fun comment(itemId: String, text: String, ctx: Context, cb: ActionCallback) {
        val credentials = readCredentials(ctx, showError = false) ?: return
        launchAction(ctx, "Couldn't connect to HN", cb) {
            NetworkComponent.hackerNewsActionRepository.comment(credentials, itemId, text)
        }
    }

    fun login(ctx: Context, cb: ActionCallback) {
        val credentials = readCredentials(ctx, showError = false)
        if (credentials == null) {
            cb.onFailure("Couldn't read credentials", "Check your saved login.")
            return
        }
        NetworkComponent.resetHttpClientCookieInstance()
        launchAction(ctx, "Login failed", cb) {
            NetworkComponent.hackerNewsActionRepository.login(credentials)
        }
    }

    fun continueLoginWithCaptcha(
        ctx: Context,
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
        cb: ActionCallback,
    ) {
        launchAction(ctx, "Login failed", cb) {
            NetworkComponent.hackerNewsActionRepository.continueLoginWithCaptcha(
                challenge,
                captchaResponse,
            )
        }
    }

    fun submit(
        title: String,
        text: String,
        url: String,
        ctx: Context,
        cb: ActionCallback,
    ) {
        val credentials = readCredentials(ctx, showError = false)
        if (credentials == null) {
            cb.onFailure("Couldn't read credentials", "Check your saved login.")
            return
        }
        NetworkComponent.resetHttpClientCookieInstance()
        launchAction(ctx, "Couldn't connect to HN", cb) {
            NetworkComponent.hackerNewsActionRepository.submit(credentials, title, text, url)
        }
    }

    fun submitAfterLoginCaptcha(
        title: String,
        text: String,
        url: String,
        ctx: Context,
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
        cb: ActionCallback,
    ) {
        launchAction(ctx, "Couldn't connect to HN", cb) {
            NetworkComponent.hackerNewsActionRepository.submitAfterLoginCaptcha(
                challenge,
                captchaResponse,
                title,
                text,
                url,
            )
        }
    }

    fun continueCaptchaAction(
        ctx: Context,
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
        cb: ActionCallback,
    ) {
        launchAction(ctx, "Couldn't connect to HN", cb) {
            NetworkComponent.hackerNewsActionRepository.continueCaptchaAction(
                challenge,
                captchaResponse,
            )
        }
    }

    private fun readCredentials(ctx: Context, showError: Boolean): HackerNewsCredentials? {
        val account = AccountUtils.getAccountDetails(ctx)
        if (AccountUtils.handlePossibleError(account, showError, ctx)) return null
        return HackerNewsCredentials(account.first.orEmpty(), account.second.orEmpty())
    }

    private fun launchAction(
        ctx: Context,
        failureSummary: String,
        cb: ActionCallback,
        onSuccess: () -> Unit = {},
        request: suspend () -> HackerNewsActionResult,
    ) {
        NetworkComponent.launchCallbackRequest(
            request = request,
            onSuccess = { result ->
                when (result) {
                    is HackerNewsActionResult.Success -> {
                        result.itemTitle?.let { title ->
                            cb.onItemTitleLoaded(result.itemId ?: 0, title)
                        }
                        onSuccess()
                        cb.onSuccess()
                    }

                    is HackerNewsActionResult.Failure -> {
                        if (result.invalidCredentials) AccountUtils.deleteAccountDetails(ctx)
                        cb.onFailure(result.summary, result.detail)
                    }

                    is HackerNewsActionResult.Captcha -> cb.onCaptchaRequired(result.challenge)
                }
            },
            onFailure = { cb.onFailure(failureSummary, it.message) },
        )
    }

    interface ActionCallback {
        fun onSuccess()
        fun onFailure(summary: String?, response: String?)

        fun onItemTitleLoaded(itemId: Int, title: String?) = Unit

        fun onCaptchaRequired(challenge: HackerNewsCaptchaChallenge) {
            onFailure(
                "Captcha required",
                "HN requires a captcha for this action. Please try again in a browser.",
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
            nextPageUrl: String?,
        )

        fun onFailure(summary: String?, response: String?)
    }

    interface StoryRowsCallback {
        fun onSuccess(stories: MutableList<Story>)
        fun onFailure(summary: String?, response: String?)
    }
}
