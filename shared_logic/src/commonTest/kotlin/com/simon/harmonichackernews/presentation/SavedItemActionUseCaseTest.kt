package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SavedItemActionUseCaseTest {
    private val repository = SavedItemsRepository(TestKeyValueStore())
    private var voteResult: HackerNewsActionResult = HackerNewsActionResult.Success()
    private var favoriteResult: HackerNewsActionResult = HackerNewsActionResult.Success()
    private var requestedVote: Pair<Int, String>? = null
    private var requestedFavorite: Pair<Int, Boolean>? = null
    private val actions = SavedItemActionUseCase(
        repository = repository,
        nowMillis = { 1234L },
        voteRequest = { id, direction ->
            requestedVote = id to direction
            voteResult
        },
        favoriteRequest = { id, favorite ->
            requestedFavorite = id to favorite
            favoriteResult
        },
    )

    @Test
    fun toggleBookmarkPersistsPortableState() {
        assertTrue(actions.toggleBookmark(42))
        assertTrue(actions.isBookmarked(42))
        assertFalse(actions.toggleBookmark(42))
        assertFalse(actions.isBookmarked(42))
    }

    @Test
    fun successfulStoryVoteKeepsOptimisticState() = runTest {
        val pending = actions.beginVote(42, isComment = false, direction = "up")
        assertTrue(actions.isUpvoted(42, isComment = false))

        assertIs<SavedItemActionOutcome.Success>(actions.execute(pending))

        assertEquals(42 to "up", requestedVote)
        assertTrue(actions.isUpvoted(42, isComment = false))
    }

    @Test
    fun failedCommentVoteRestoresPreviousState() = runTest {
        repository.setCommentMembership(SavedItemSource.UPVOTED, 7, true)
        voteResult = HackerNewsActionResult.Failure("Nope")
        val pending = actions.beginVote(7, isComment = true, direction = "un")
        assertFalse(actions.isUpvoted(7, isComment = true))

        val outcome = assertIs<SavedItemActionOutcome.Failure>(actions.execute(pending))

        assertEquals(voteResult, outcome.result)
        assertTrue(actions.isUpvoted(7, isComment = true))
    }

    @Test
    fun failedFavoriteRestoresPreviousState() = runTest {
        favoriteResult = HackerNewsActionResult.Captcha(
            com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge(
                actionUrl = "https://news.ycombinator.com/favorite",
                siteKey = "site-key",
                formFields = emptyList(),
                useCookies = false,
            ),
        )
        val pending = actions.beginFavorite(99)
        assertTrue(actions.isFavorited(99))

        assertIs<SavedItemActionOutcome.Failure>(actions.execute(pending))

        assertEquals(99 to true, requestedFavorite)
        assertFalse(actions.isFavorited(99))
    }
}
