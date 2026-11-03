package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.network.HackerNewsFavoriteService
import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AddBookmarksToFavoritesUseCaseTest {
    @Test
    fun restoredBatchRevisionCannotAttachToADifferentAccountBeforeFirstRequest() = runTest {
        var account = "original-user"
        val savedItems = SavedItemsRepository(TestKeyValueStore()).also { it.bindAccountScope { account } }
        val capturedRevision = savedItems.currentAccountRevision
        account = "replacement-user"
        var remoteCalls = 0
        val restored = AddBookmarksToFavoritesUseCase(
            favorites = HackerNewsFavoriteService { _, _ -> remoteCalls++; HackerNewsActionResult.Success() },
            savedItems = savedItems,
            initialAccountRevision = capturedRevision,
        )
        assertFalse(restored.add(42, "Fixture").successful)
        assertEquals(0, remoteCalls)
        assertFalse(savedItems.contains(SavedItemSource.FAVORITES, 42))
    }

    @Test
    fun accountChangeWhileAwaitingRemoteFavoriteCannotWriteToReplacementAccount() = runTest {
        var account = "original-user"
        var remoteCalls = 0
        val savedItems = SavedItemsRepository(TestKeyValueStore()).also { it.bindAccountScope { account } }
        val useCase = AddBookmarksToFavoritesUseCase(
            favorites = HackerNewsFavoriteService { _, _ ->
                remoteCalls++
                account = "replacement-user"
                HackerNewsActionResult.Success()
            },
            savedItems = savedItems,
        )
        val result = useCase.add(42, "Fixture")
        assertFalse(result.successful)
        assertTrue(result.message.contains("account changed"))
        assertFalse(savedItems.contains(SavedItemSource.FAVORITES, 42))
        assertFalse(useCase.add(43, "Next fixture").successful)
        assertEquals(1, remoteCalls)
        assertFalse(savedItems.contains(SavedItemSource.FAVORITES, 43))
        account = "original-user"
        assertFalse(savedItems.contains(SavedItemSource.FAVORITES, 42))
    }

    @Test
    fun successPersistsTimestampAndUsesReturnedTitle() = runTest {
        val savedItems = SavedItemsRepository(TestKeyValueStore())
        val useCase = AddBookmarksToFavoritesUseCase(
            favorites = HackerNewsFavoriteService { itemId, favorite ->
                assertEquals(42, itemId)
                assertTrue(favorite)
                HackerNewsActionResult.Success(itemTitle = "Resolved title")
            },
            savedItems = savedItems,
            nowMillis = { 123_456L },
        )

        val result = useCase.add(42, "Fallback")

        assertTrue(result.successful)
        assertEquals("Resolved title", result.title)
        assertEquals("In HN favorites", result.message)
        assertEquals(42, savedItems.loadItems(SavedItemSource.FAVORITES).single().id)
        assertEquals(
            123_456L,
            savedItems.loadItems(SavedItemSource.FAVORITES).single().created,
        )
    }

    @Test
    fun failuresAndCaptchaAreMappedWithoutChangingMembership() = runTest {
        val savedItems = SavedItemsRepository(TestKeyValueStore())
        var result: HackerNewsActionResult = HackerNewsActionResult.Failure(
            summary = "Rejected",
            detail = "Try again",
        )
        val useCase = AddBookmarksToFavoritesUseCase(
            favorites = HackerNewsFavoriteService { _, _ -> result },
            savedItems = savedItems,
            nowMillis = { 1L },
        )

        val failure = useCase.add(7, "Story #7")
        assertFalse(failure.successful)
        assertEquals("Rejected: Try again", failure.message)

        result = HackerNewsActionResult.Captcha(
            HackerNewsCaptchaChallenge("https://news.ycombinator.com/login", "key", emptyList(), false),
        )
        val captcha = useCase.add(7, "Story #7")
        assertFalse(captcha.successful)
        assertEquals("HN requires a captcha before adding this favorite", captcha.message)
        assertFalse(savedItems.contains(SavedItemSource.FAVORITES, 7))
    }
}
