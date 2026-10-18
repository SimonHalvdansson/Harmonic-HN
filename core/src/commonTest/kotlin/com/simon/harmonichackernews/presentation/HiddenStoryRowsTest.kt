package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertSame

class HiddenStoryRowsTest {
    private fun stories(vararg ids: Int) = ids.map { Story("Story $it", it, true, false) }

    @Test
    fun unchangedFeedReusesInputButStillObservesMutableRowsAndRestorePolicy() {
        val filter = HiddenStoryRows()
        val source = stories(1, 2, 3)
        val hidden = setOf(2, 3)
        source[2].isComment = true
        val rows = filter.apply(source, hidden).toMutableList()
        repeat(3) { assertSame(rows, filter.apply(rows, hidden)) }
        // A placeholder/comment can be reclassified in place without changing its ID.
        source[2].isComment = false
        val reclassified = filter.apply(rows, hidden)
        assertEquals(listOf(1), reclassified.map(Story::id))
        // Other filters can invalidate a retained row even while hidden IDs are unchanged.
        filter.apply(reclassified, hidden) { it.id != 2 }
        assertEquals(listOf(1, 3), filter.apply(reclassified, emptySet()).map(Story::id))
    }

    @Test
    fun unchangedIdsDoNotReuseReplacedOrReorderedObjects() {
        val filter = HiddenStoryRows()
        filter.apply(stories(1, 2, 3), setOf(2))
        val replacement = stories(3, 1)
        val rows = filter.apply(replacement, setOf(2))
        assertSame(replacement[0], rows[0])
        assertSame(replacement[1], rows[1])
        assertEquals(listOf(2, 3, 1), filter.apply(rows, emptySet()).map(Story::id))
    }

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
