package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.settings.StoredUserSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

class WidgetFeedUseCaseTest {
    @Test
    fun loadsUpToVisibleCountAndKeepsPartialSuccesses() = runTest {
        val repository = FakeRepository(
            ids = listOf(1, 2, 3, 4),
            stories = mapOf(1 to story(1), 3 to story(3), 4 to story(4)),
        )

        val result = WidgetFeedUseCase(repository).load(
            WidgetFeedRequest(
                storyType = StoryType.TOP_STORIES,
                fetchCount = 4,
                visibleCount = 2,
            ),
        )

        val loaded = assertIs<WidgetFeedResult.Loaded>(result)
        assertEquals(listOf(1, 3), loaded.stories.map(Story::id))
        assertEquals(4, loaded.availableStoryCount)
        assertEquals(1, loaded.failedStoryCount)
        assertFalse(loaded.timedOut)
    }

    @Test
    fun reportsFailureWhenNoStoryCanBeLoaded() = runTest {
        val result = WidgetFeedUseCase(
            FakeRepository(ids = listOf(1), stories = emptyMap()),
        ).load(
            WidgetFeedRequest(
                storyType = StoryType.NEW_STORIES,
                fetchCount = 1,
                visibleCount = 1,
            ),
        )

        assertIs<WidgetFeedResult.Failed>(result)
    }

    @Test
    fun mapsPersistedWidgetUrlsToTypedFeeds() {
        assertEquals(
            StoryType.BEST_STORIES,
            widgetStoryTypeForUrl(StoryType.BEST_STORIES.hackerNewsUrl),
        )
        assertEquals(StoryType.TOP_STORIES, widgetStoryTypeForUrl("legacy-or-invalid"))
        assertEquals(StoryType.TOP_STORIES, widgetStoryTypeForUrl(null))
        assertEquals(StoryType.UNSLOP, widgetStoryTypeForUrl("UNSLOP"))
        assertEquals(StoryType.LAST_WEEK, widgetStoryTypeForUrl("LAST_WEEK"))
    }

    @Test
    fun discoveryIsIncludedInTimeoutAndErrorSurvivesRefreshRuntime() = runTest {
        val repository = FakeRepository(emptyList(), emptyMap())
        val timedOut = WidgetFeedUseCase(repository) { _, _ ->
            delay(100)
            StoryFeedResult.ItemIds(listOf(1))
        }.load(WidgetFeedRequest(StoryType.TOP_STORIES, 1, 1, totalTimeoutMillis = 50))
        assertTrue(assertIs<WidgetFeedResult.Failed>(timedOut).cause!!.message!!.contains("Timed out"))

        val cause = IllegalStateException("HTTP 503")
        val widgets = WidgetConfigurationService(InMemoryKeyValueStore(), InMemoryKeyValueStore(), repository) { _, _ -> throw cause }
        val failed = WidgetRefreshRuntime(widgets) { 123 }.refresh(7, false)
        assertEquals(cause.message, assertIs<IllegalStateException>(assertIs<WidgetRefreshResult.Failed>(failed).cause).message)
        assertEquals(0L, widgets.runtime(7).lastUpdatedMillis)
    }

    @Test
    fun parallelItemsKeepFeedOrderAndPartialResultsAtDeadline() = runTest {
        val repository = object : HackerNewsRepository {
            override suspend fun getStoryIds(type: StoryType) = listOf(1, 2, 3, 4)
            override suspend fun getComment(id: Int): Comment? = null
            override suspend fun getStory(id: Int): Story {
                delay(if (id == 2) 200 else (5 - id) * 10L)
                return story(id)
            }
        }
        val loaded = assertIs<WidgetFeedResult.Loaded>(WidgetFeedUseCase(repository).load(
            WidgetFeedRequest(StoryType.TOP_STORIES, 4, 4, totalTimeoutMillis = 100),
        ))
        assertEquals(listOf(1, 3, 4), loaded.stories.map { it.id })
        assertTrue(loaded.timedOut)
    }

    @Test
    fun loadsCommentFrontpagesAndAlreadyLoadedAlgoliaStories() = runTest {
        val comment = story(5).apply { isComment = true; text = "Comment" }
        val repository = FakeRepository(emptyList(), mapOf(5 to comment))
        val loaded = assertIs<WidgetFeedResult.Loaded>(WidgetFeedUseCase(repository) { _, _ ->
            StoryFeedResult.Scraped(HackerNewsListPage(listOf(5), listOf(5), null))
        }.load(WidgetFeedRequest(StoryType.BEST_COMMENTS, 8, 8)))
        assertEquals(listOf(comment), loaded.stories)
        val algolia = assertIs<WidgetFeedResult.Loaded>(WidgetFeedUseCase(repository) { _, _ ->
            StoryFeedResult.LinkDirectory(listOf(story(1)))
        }.load(WidgetFeedRequest(StoryType.LAST_WEEK, 8, 8)))
        assertEquals(listOf(1), algolia.stories.map { it.id })
    }

    @Test
    fun appearanceAndAdditionalFeedsAreIndependentPerWidgetAndLegacySettingsMigrate() {
        val store = InMemoryKeyValueStore().apply {
            putString("feed_type_1", StoryType.BEST_STORIES.hackerNewsUrl)
            putInt("story_count_1", 20)
        }
        val widgets = WidgetConfigurationService(store, InMemoryKeyValueStore(), FakeRepository(emptyList(), emptyMap()))
        assertEquals(WidgetConfiguration(StoryType.BEST_STORIES, visibleStoryCount = 20), widgets.configuration(1))
        val configured = WidgetConfiguration(StoryType.UNSLOP, "unslop.news", 24, StoryPreviewMode.MEDIUM, DisplayStyle.OUTLINED, true)
        widgets.save(2, configured)
        assertEquals(configured, widgets.configuration(2))
        assertEquals(StoryPreviewMode.OFF, widgets.configuration(1).previewImageMode)
        widgets.clear(2)
        assertEquals(WidgetConfiguration(), widgets.configuration(2))
    }

    @Test
    fun newWidgetsInheritAppearanceWhileSavedChoicesStayIndependent() {
        val story = StoredUserSettings(InMemoryKeyValueStore(), kotlinx.coroutines.flow.emptyFlow()).story.copy(
            previewImageMode = StoryPreviewMode.MEDIUM, displayStyle = DisplayStyle.OUTLINED,
            tintCardsFromImages = true,
        )
        val defaults = WidgetConfiguration.fromStoryPreferences(story)
        val widgets = WidgetConfigurationService(InMemoryKeyValueStore(), InMemoryKeyValueStore(), FakeRepository(emptyList(), emptyMap()))
        assertEquals(12, defaults.visibleStoryCount)
        assertEquals(defaults, widgets.configuration(1, defaults))
        assertEquals(StoryPreviewMode.MEDIUM, WidgetConfiguration.fromStoryPreferences(story.copy(previewImageMode = StoryPreviewMode.LARGE)).previewImageMode)
        widgets.save(1, defaults.copy(tint = false, previewImageMode = StoryPreviewMode.OFF))
        assertFalse(widgets.configuration(1, defaults).tint)
        assertEquals(StoryPreviewMode.OFF, widgets.configuration(1, defaults).previewImageMode)
    }

    @Test
    fun sliderCountsRoundTripAcrossTheWholeRangeAndClampOldValues() {
        val widgets = WidgetConfigurationService(InMemoryKeyValueStore(), InMemoryKeyValueStore(), FakeRepository(emptyList(), emptyMap()))
        for (count in 8..24) {
            widgets.save(1, WidgetConfiguration(visibleStoryCount = count))
            assertEquals(count, widgets.configuration(1).visibleStoryCount)
            assertTrue(widgets.configuration(1).fetchStoryCount >= count)
        }
        widgets.save(1, WidgetConfiguration(visibleStoryCount = 40))
        assertEquals(24, widgets.configuration(1).visibleStoryCount)
        widgets.save(1, WidgetConfiguration(visibleStoryCount = 1))
        assertEquals(8, widgets.configuration(1).visibleStoryCount)
    }

    private class FakeRepository(
        private val ids: List<Int>,
        private val stories: Map<Int, Story>,
    ) : HackerNewsRepository {
        override suspend fun getStory(id: Int): Story? = stories[id]

        override suspend fun getComment(id: Int): Comment? = null

        override suspend fun getStoryIds(type: StoryType): List<Int> = ids
    }

    private companion object {
        fun story(id: Int) = Story().apply {
            this.id = id
            title = "Story $id"
            loaded = true
        }
    }
}
