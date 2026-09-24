package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.StoryTypeMenuPolicy
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FrontpageSettingsTest {
    @Test
    fun personalPagesFollowSavedOrderAndVisibilityWithoutChangingPreferences() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        val personal = listOf(StoryType.FAVORITES, StoryType.HISTORY, StoryType.UPVOTED, StoryType.BOOKMARKS)
        repository.setFrontpageOrder(personal.map { it.name })
        repository.setPreferredStoryType(StoryType.FAVORITES.label)
        val reopened = AppSettingsRepository(store, emptyFlow())
        val story = reopened.snapshot().story
        for (hasAccount in listOf(false, true, false, true)) {
            for (bookmarksEnabled in listOf(false, true)) {
                val pages = StoryTypeMenuPolicy.availableTypes(
                    emptySet(), hasAccount, story.frontpageOrder, bookmarksEnabled,
                )
                assertEquals(
                    personal.filter { (!it.isUserItemList || hasAccount) && (!it.isBookmarks || bookmarksEnabled) } +
                        StoryTypeMenuPolicy.baseFrontpages,
                    pages,
                )
                assertEquals(
                    if (hasAccount) StoryType.FAVORITES else StoryType.TOP_STORIES,
                    StoryTypeMenuPolicy.preferred(story.preferredStoryType, pages),
                )
                assertEquals(story, reopened.snapshot().story)
            }
        }
    }

    @Test
    fun reorderingWhileLoggedOutAndBookmarksDisabledKeepsHiddenSlotsAfterReopening() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, emptyFlow())
        val initial = listOf(
            StoryType.FAVORITES, StoryType.TOP_STORIES, StoryType.UPVOTED,
            StoryType.NEW_STORIES, StoryType.BOOKMARKS, StoryType.HISTORY,
        ) + StoryTypeMenuPolicy.baseFrontpages.filterNot {
            it == StoryType.TOP_STORIES || it == StoryType.NEW_STORIES
        }
        repository.setFrontpageOrder(initial.map { it.name })
        repository.setGeneralBoolean(GeneralBooleanPreference.BOOKMARKS_ENABLED, false)
        val visible = StoryTypeMenuPolicy.availableTypes(
            emptySet(), false, repository.snapshot().story.frontpageOrder, bookmarksEnabled = false,
        )
        repository.setVisibleFrontpageOrder(visible.reversed())
        val reopened = AppSettingsRepository(store, emptyFlow()).snapshot().story
        val restored = StoryTypeMenuPolicy.availableTypes(emptySet(), true, reopened.frontpageOrder)
        for (hidden in listOf(StoryType.FAVORITES, StoryType.UPVOTED, StoryType.BOOKMARKS)) {
            assertEquals(initial.indexOf(hidden), restored.indexOf(hidden))
        }
        assertEquals(visible.reversed(), restored.filter { it in visible })
        assertEquals(restored.size, restored.distinct().size)
        // A later reorder with all pages visible can move the formerly hidden pages normally.
        repository.setVisibleFrontpageOrder(restored.reversed())
        assertEquals(restored.reversed().map { it.name }, repository.snapshot().story.frontpageOrder)
    }

    @Test
    fun addingAndRemovingOptionalFeedsDoesNotDropHiddenPersonalPages() {
        val repository = AppSettingsRepository(TestKeyValueStore(), emptyFlow())
        repository.setFrontpageOrder(listOf("FAVORITES", "UPVOTED", "BOOKMARKS", "HISTORY"))
        repository.setVisibleFrontpageOrder(listOf(StoryType.HISTORY, StoryType.CLASSIC))
        repository.setAdditionalFrontpages(setOf("Classic"))
        val order = repository.snapshot().story.frontpageOrder
        assertEquals(listOf("FAVORITES", "UPVOTED", "BOOKMARKS", "HISTORY"), order.take(4))
        assertEquals("CLASSIC", order.last())
        repository.setAdditionalFrontpages(emptySet())
        repository.setFrontpageOrder(order.filterNot { it == "CLASSIC" })
        assertEquals(order.dropLast(1), repository.snapshot().story.frontpageOrder)
    }

    @Test
    fun disabledBookmarksDefaultFallsBackWithoutErasingTheSelection() {
        val repository = AppSettingsRepository(TestKeyValueStore(), emptyFlow())
        repository.setPreferredStoryType("Bookmarks")
        for (enabled in listOf(false, true)) {
            repository.setGeneralBoolean(GeneralBooleanPreference.BOOKMARKS_ENABLED, enabled)
            val settings = repository.snapshot()
            val available = StoryTypeMenuPolicy.availableTypes(emptySet(), false, bookmarksEnabled = settings.general.bookmarksEnabled)
            assertEquals(
                if (enabled) StoryType.BOOKMARKS else StoryType.TOP_STORIES,
                StoryTypeMenuPolicy.preferred(settings.story.preferredStoryType, available),
            )
            assertEquals("Bookmarks", settings.story.preferredStoryType)
        }
    }

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
