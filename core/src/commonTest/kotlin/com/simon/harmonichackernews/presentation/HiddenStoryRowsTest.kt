package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiddenStoryRowsTest {
    private fun stories(vararg ids: Int) = ids.map { Story("Story $it", it, true, false) }

    @Test
    fun rapidHidesUndoInEitherOrderAndDisablingRestoresOriginalOrder() {
        val filter = HiddenStoryRows()
        var rows = filter.apply(stories(1, 2, 3, 4), setOf(2))
        rows = filter.apply(rows, setOf(2, 3))
        assertEquals(listOf(1, 4), rows.map(Story::id))
        rows = filter.apply(rows, setOf(3))
        assertEquals(listOf(1, 2, 4), rows.map(Story::id))
        rows = filter.apply(rows, emptySet())
        assertEquals(listOf(1, 2, 3, 4), rows.map(Story::id))
        rows = filter.apply(rows, setOf(2, 3))
        rows = filter.apply(rows, setOf(2))
        assertEquals(listOf(1, 3, 4), rows.map(Story::id))
    }

    @Test
    fun allHiddenRowsCanBeRestoredButNeverInsertedIntoAnotherFeed() {
        val filter = HiddenStoryRows()
        assertTrue(filter.apply(stories(1, 2, 3), setOf(1, 2, 3)).isEmpty())
        assertEquals(listOf(1, 2, 3), filter.apply(emptyList(), emptySet()).map(Story::id))
        filter.apply(stories(1, 2, 3), setOf(1, 2, 3))
        filter.reset()
        assertEquals(listOf(4, 5), filter.apply(stories(4, 5), emptySet()).map(Story::id))
    }

    @Test
    fun restoringRespectsOtherFiltersAndKeepsCommentRows() {
        val filter = HiddenStoryRows()
        val comment = Story("Comment", 3, true, false).apply { isComment = true }
        val rows = filter.apply(stories(1, 2) + comment, setOf(1, 2, 3))
        assertEquals(listOf(3), rows.map(Story::id))
        assertEquals(listOf(2, 3), filter.apply(rows, emptySet()) { it.id != 1 }.map(Story::id))
        assertTrue(HiddenStoryPolicy.supports(StoryType.LAST_WEEK))
        assertTrue(HiddenStoryPolicy.supports(StoryType.UNSLOP))
        listOf(StoryType.BOOKMARKS, StoryType.HISTORY, StoryType.FAVORITES,
            StoryType.UPVOTED, StoryType.BEST_COMMENTS, StoryType.HIGHLIGHTS).forEach {
            assertFalse(HiddenStoryPolicy.supports(it))
        }
    }
}
