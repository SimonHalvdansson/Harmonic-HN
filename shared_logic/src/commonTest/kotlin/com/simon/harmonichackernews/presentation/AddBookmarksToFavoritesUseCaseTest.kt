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
