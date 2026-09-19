package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.StoryTypeMenuPolicy
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FrontpageSettingsTest {
    @Test
    fun existingSettingsKeepTheirOrderAndAccountLists() {
        val expected = StoryTypeMenuPolicy.baseFrontpages + StoryType.CLASSIC + StoryType.UNSLOP
        val enabled = setOf("Classic", "unslop.news")
        assertEquals(
            expected + listOf(StoryType.BOOKMARKS, StoryType.HISTORY),
            StoryTypeMenuPolicy.availableTypes(enabled, hasAccount = false),
        )
        assertEquals(
            expected + listOf(StoryType.BOOKMARKS, StoryType.FAVORITES, StoryType.HISTORY, StoryType.UPVOTED),
            StoryTypeMenuPolicy.availableTypes(enabled, hasAccount = true),
        )
    }

    @Test
    fun partialOrStaleOrderCannotDuplicateHideOrEnableFeeds() {
        val order = listOf("UNSLOP", "ASK_HN", "ASK_HN", "obsolete", "BOOKMARKS", "CLASSIC")
        val pages = StoryTypeMenuPolicy.frontpages(setOf("Classic"), order)
        assertEquals(listOf(StoryType.ASK_HN, StoryType.CLASSIC), pages.take(2))
        assertEquals((StoryTypeMenuPolicy.baseFrontpages + StoryType.CLASSIC).toSet(), pages.toSet())
        assertEquals(pages.size, pages.distinct().size)
        assertFalse(StoryType.UNSLOP in pages)
    }

    @Test
    fun orderAndDefaultSurviveReopeningIndependently() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        repository.setAdditionalFrontpages(setOf("Classic"))
        repository.setPreferredStoryType("Best Stories")
        val order = listOf(StoryType.CLASSIC, StoryType.NEW_STORIES) +
            StoryTypeMenuPolicy.baseFrontpages.filterNot { it == StoryType.NEW_STORIES }
        repository.setFrontpageOrder(order.map { it.name } + listOf("CLASSIC", "unknown"))

        val reopened = AppSettingsRepository(store, emptyFlow()).snapshot().story
        assertEquals("Best Stories", reopened.preferredStoryType)
        assertEquals(order.map { it.name }, reopened.frontpageOrder)
        assertEquals(
            order,
            StoryTypeMenuPolicy.frontpages(reopened.additionalFrontpages, reopened.frontpageOrder),
        )
    }

    @Test
    fun removingDefaultFallsBackAndAddingItAgainDoesNotChangeDefault() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        repository.setAdditionalFrontpages(setOf("Classic", "Active"))
        repository.setPreferredStoryType("Classic")
        repository.setAdditionalFrontpages(setOf("Active"))
        assertEquals("Top Stories", repository.snapshot().story.preferredStoryType)
        repository.setAdditionalFrontpages(setOf("Classic", "Active"))
        assertEquals("Top Stories", repository.snapshot().story.preferredStoryType)
    }
}
