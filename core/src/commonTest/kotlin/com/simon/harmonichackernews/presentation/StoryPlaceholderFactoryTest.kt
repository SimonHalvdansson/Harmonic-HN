package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StoryPlaceholderFactoryTest {
    @Test
    fun placeholdersApplyHistoryCommentAndExistingItemPolicies() {
        val stories = StoryPlaceholderFactory.createNew(
            existingStories = listOf(Story("Existing", 1, true, false)),
            itemIds = listOf(1, 2, 3, 4),
            commentIds = setOf(3),
            clickedIds = setOf(2),
            hideClicked = true,
        )

        assertEquals(listOf(3, 4), stories.map(Story::id))
        assertTrue(stories.first().isComment)
        assertFalse(stories.last().isComment)
    }

    @Test
    fun hydratedRowsCanBeFilteredWithoutPlatformListLogic() {
        val stories = StoryPlaceholderFactory.create(
            itemIds = listOf(1, 2),
            hydrateCachedStory = { story ->
                story.loaded = story.id == 2
                story.loaded
            },
            shouldHideHydratedStory = { it.id == 2 },
        )

        assertEquals(listOf(1), stories.map(Story::id))
    }

    @Test
    fun refreshedFeedRetainsEveryDetailOfMatchingLiveStories() {
        val retained = Story("Already loaded", 2, true, false)

        val stories = StoryPlaceholderFactory.reconcile(
            existingStories = listOf(Story("Old first", 1, true, false), retained),
            itemIds = listOf(3, 2, 4),
            commentIds = setOf(2),
        )

        assertEquals(listOf(3, 2, 4), stories.map(Story::id))
        assertSame(retained, stories[1])
        assertEquals("Already loaded", stories[1].title)
        assertTrue(stories[1].loaded)
        assertTrue(stories[1].isComment)
        assertFalse(stories[0].loaded)
    }

    @Test
    fun creationRetainsCachedReferencesAndHydrationOrderWithDuplicateIds() {
        val cached = Story("Cached", 2, true, false).apply { isComment = true }
        val events = mutableListOf<String>()
        val stories = StoryPlaceholderFactory.create(
            itemIds = listOf(1, 2, 3, 2, 4, 5),
            commentIds = setOf(3, 5),
            clickedIds = setOf(1, 2),
            cachedStories = mapOf(2 to cached),
            hydrateCachedStory = {
                events += "hydrate:${it.id}:${it.clicked}:${it.isComment}"
                it.loaded = it.id != 5
                it.loaded
            },
            shouldHideHydratedStory = {
                events += "filter:${it.id}"
                it.id == 4
            },
        )

        assertEquals(listOf(1, 2, 3, 2, 5), stories.map(Story::id))
        assertSame(cached, stories[1])
        assertSame(cached, stories[3])
        assertTrue(cached.clicked)
        assertFalse(cached.isComment)
        assertEquals(
            listOf(
                "hydrate:1:true:false", "filter:1", "filter:2",
                "hydrate:3:false:true", "filter:3", "filter:2",
                "hydrate:4:false:false", "filter:4", "hydrate:5:false:true",
            ),
            events,
        )
    }

    @Test
    fun hiddenClickedItemsNeverReadOrHydrateCache() {
        val events = mutableListOf<Int>()
        val stories = StoryPlaceholderFactory.create(
            itemIds = listOf(1, 2, 3),
            clickedIds = setOf(1, 3),
            hideClicked = true,
            hydrateCachedStory = { events += it.id; false },
        )
        assertEquals(listOf(2), stories.map(Story::id))
        assertEquals(listOf(2), events)
        stories += Story("Append", 4, false, false)
        assertEquals(listOf(2, 4), stories.map(Story::id))
    }

    @Test
    fun reconcilePreservesExistingClickedStateAndOnlyHydratesNewRows() {
        val clicked = Story("Clicked", 1, true, true)
        val unclicked = Story("Unclicked", 2, true, false)
        val cached = Story("Cached", 3, true, false)
        val events = mutableListOf<String>()
        val stories = StoryPlaceholderFactory.reconcile(
            existingStories = listOf(clicked, unclicked),
            itemIds = listOf(2, 3, 1, 4, 2),
            clickedIds = setOf(2, 3, 4),
            commentIds = setOf(2, 4),
            cachedStories = mapOf(3 to cached),
            hydrateCachedStory = {
                events += "hydrate:${it.id}:${it.clicked}:${it.isComment}"
                true
            },
            shouldHideHydratedStory = { events += "filter:${it.id}"; false },
        )

        assertEquals(listOf(2, 3, 1, 4, 2), stories.map(Story::id))
        assertSame(unclicked, stories[0])
        assertSame(unclicked, stories[4])
        assertSame(clicked, stories[2])
        assertSame(cached, stories[1])
        assertTrue(clicked.clicked)
        assertFalse(unclicked.clicked)
        assertTrue(unclicked.isComment)
        assertTrue(cached.clicked)
        assertTrue(stories[3].clicked)
        assertEquals(listOf("filter:3", "hydrate:4:true:true", "filter:4"), events)
    }

    @Test
    fun reconcileHiddenClickedRowsPreservesSurvivorState() {
        val retained = Story("Retained", 1, true, true)
        val cached = Story("Cached", 3, true, true)
        val events = mutableListOf<String>()
        val stories = StoryPlaceholderFactory.reconcile(
            existingStories = listOf(retained, Story("Hidden", 2, true, false)),
            itemIds = listOf(1, 2, 3, 4),
            clickedIds = setOf(2),
            hideClicked = true,
            cachedStories = mapOf(3 to cached),
            hydrateCachedStory = { events += "hydrate:${it.id}:${it.clicked}"; false },
            shouldHideHydratedStory = { events += "filter:${it.id}"; false },
        )
        assertEquals(listOf(1, 3, 4), stories.map(Story::id))
        assertSame(retained, stories[0])
        assertTrue(retained.clicked)
        assertSame(cached, stories[1])
        assertFalse(cached.clicked)
        assertFalse(stories[2].clicked)
        assertEquals(listOf("filter:3", "hydrate:4:false"), events)
    }
}
