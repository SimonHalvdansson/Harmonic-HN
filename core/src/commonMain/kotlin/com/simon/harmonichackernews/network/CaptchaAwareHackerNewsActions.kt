package com.simon.harmonichackernews.network

/** Per-window continuation of the original operation; a challenge never replays its initial POST. */
class CaptchaAwareHackerNewsActions(
    private val service: HackerNewsUserService,
    private val respond: suspend (HackerNewsCaptchaChallenge) -> String?,
) : HackerNewsVotingService, HackerNewsFavoriteService, HackerNewsUserItemsLoader {
    override suspend fun vote(itemId: String, direction: String): HackerNewsActionResult =
        voteForAccount(service.accountUsername(), itemId, direction)

    suspend fun voteForAccount(
        username: String?, itemId: String, direction: String,
        isAccountCurrent: () -> Boolean = { true },
    ): HackerNewsActionResult = resolve(service.voteForAccount(username, itemId, direction, isAccountCurrent)) {
        challenge, token -> service.continueCaptchaActionForAccount(username, challenge, token, isAccountCurrent)
    }

    override suspend fun setFavorite(itemId: Int, favorite: Boolean): HackerNewsActionResult =
        setFavoriteForAccount(service.accountUsername(), itemId, favorite)

    suspend fun setFavoriteForAccount(
        username: String?, itemId: Int, favorite: Boolean,
        isAccountCurrent: () -> Boolean = { true },
    ): HackerNewsActionResult = resolve(service.setFavoriteForAccount(username, itemId, favorite, isAccountCurrent)) {
        challenge, token -> service.continueFavoriteCaptchaForAccount(
            username, challenge, token, itemId, favorite, isAccountCurrent,
        )
    }

    private suspend fun resolve(
        initial: HackerNewsActionResult,
        continuation: suspend (HackerNewsCaptchaChallenge, String) -> HackerNewsActionResult,
    ): HackerNewsActionResult {
        var result = initial
        while (result is HackerNewsActionResult.Captcha) {
            val response = respond(result.challenge) ?: return HackerNewsActionResult.Failure(
                "CAPTCHA cancelled", "Your action was not completed. You can try again.",
            )
            result = continuation(result.challenge, response)
        }
        return result
    }

    override suspend fun getUserItems(path: String, loginRequired: Boolean): HackerNewsUserItemsResult {
        val username = service.accountUsername()
        var result = service.getUserItems(path, loginRequired)
        while (result is HackerNewsUserItemsResult.Captcha) {
            val response = respond(result.challenge) ?: return HackerNewsUserItemsResult.Failure(
                "Sync cancelled", "Complete the HN CAPTCHA to sync $path. Existing items are preserved.",
            )
            result = service.getUserItemsAfterCaptcha(username, result.challenge, response, path, loginRequired)
        }
        return result
    }
}
