package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.PortableVisibleComment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertSame

class CommentCollapseAnimationTest {
    private fun row(id: Int, depth: Int, expanded: Boolean = true) = PortableVisibleComment(
        id, PortableCommentItem(CommentSnapshot(id), CommentPresentationSnapshot(expanded = expanded, depth = depth)), 0,
    )

    @Test
    fun collapsingChildrenKeepTheirSpacingAndShrinkAsOneBottomAlignedBlock() {
        val before = listOf(row(1, 0), row(2, 1), row(3, 1), row(4, 0))
        val plan = commentCollapsePlan(before, listOf(row(1, 0, false), row(4, 0)), (1..4).toSet())
        val geometry = plan.rowGeometry(mapOf(2 to 80, 3 to 120))
        assertEquals(listOf(listOf(2, 3)), plan.exitingGroups)
        // After a 50px movement, the first child is partially clipped, while the second child
        // still has its full height. Both contents moved exactly 50px, with no accordion effect.
        assertEquals(50, geometry.getValue(2).clippedTop(0.75f))
        assertEquals(0, geometry.getValue(3).clippedTop(0.75f))
        assertEquals(30, 80 - geometry.getValue(2).clippedTop(0.75f))
        assertEquals(80, geometry.getValue(2).clippedTop(0f))
        assertEquals(120, geometry.getValue(3).clippedTop(0f))
    }

    @Test
    fun largeCollapseRetainsOnlyVisibleChildrenAndKeepsTheNextRootInOrder() {
        val before = listOf(row(1, 0)) + (2..201).map { row(it, 1) } + row(202, 0)
        val after = listOf(row(1, 0, false), row(202, 0))
        val plan = commentCollapsePlan(before, after, (1..7).toSet())
        assertEquals((2..7).toSet(), plan.exitingIds)
        assertEquals((1..7).toList() + 202, plan.rows.map { it.comment.id })
        assertEquals(after.first(), plan.rows.first())
    }

    @Test
    fun filteringAndRefreshingDoNotUseCollapseMotion() {
        val before = listOf(row(1, 0), row(2, 1), row(3, 0))
        assertTrue(commentCollapsePlan(before, listOf(before.last()), setOf(1, 2, 3)).exitingIds.isEmpty())
        assertTrue(commentCollapsePlan(before, before, setOf(1, 2, 3)).exitingIds.isEmpty())
    }

    @Test
    fun collapsingNestedBranchDoesNotRetainUnrelatedRemovedComments() {
        val before = listOf(row(1, 0), row(2, 1), row(3, 2), row(4, 1), row(5, 0))
        val after = listOf(before.first(), row(2, 1, false), before.last())
        val plan = commentCollapsePlan(before, after, (1..5).toSet())
        assertEquals(setOf(3), plan.exitingIds)
        assertEquals(listOf(1, 2, 3, 5), plan.rows.map { it.comment.id })
    }

    @Test
    fun simultaneousCollapsesKeepSeparateGroupsAndCurrentMetadata() {
        val before = listOf(row(1, 0), row(2, 1), row(3, 0), row(4, 1), row(5, 0))
        val after = listOf(row(1, 0, false), row(3, 0, false), before.last().copy(subtreeReplyCount = 9))
        val plan = commentCollapsePlan(before, after, setOf(1, 2, 3, 4, 5))
        assertEquals(listOf(listOf(2), listOf(4)), plan.exitingGroups)
        assertEquals(listOf(1, 2, 3, 4, 5), plan.rows.map { it.comment.id })
        assertSame(after[0], plan.rows[0])
        assertSame(after[1], plan.rows[2])
        assertSame(after[2], plan.rows[4])
    }

    @Test
    fun offscreenCollapseUsesTheCurrentListWithoutRetainedRows() {
        val before = listOf(row(1, 0), row(2, 1), row(3, 0))
        val after = listOf(row(1, 0, false), row(3, 0))
        assertSame(after, commentCollapsePlan(before, after, emptySet()).rows)
        assertSame(after, commentCollapsePlan(before, after, setOf(3)).rows)
    }

    @Test
    fun structuralKeysIgnoreMetadataButDetectOrderingDepthAndExpansion() {
        val before = listOf(row(1, 0), row(2, 1))
        val metadata = before.map { it.copy(subtreeReplyCount = 5, comment = it.comment.copy(isNew = true)) }
        val key = CommentTreeStructure(before)
        assertEquals(key, CommentTreeStructure(metadata))
        assertEquals(key.hashCode(), CommentTreeStructure(metadata).hashCode())
        assertFalse(key == CommentTreeStructure(before.reversed()))
        assertFalse(key == CommentTreeStructure(listOf(row(1, 0, false), row(2, 1))))
        assertFalse(key == CommentTreeStructure(listOf(row(1, 0), row(2, 2))))
        assertFalse(key == CommentTreeStructure(before.take(1)))
        assertEquals(CommentTreeStructure(before, idsOnly = true),
            CommentTreeStructure(listOf(row(1, 0, false), row(2, 2)), idsOnly = true))
        assertFalse(CommentTreeStructure(before, idsOnly = true) ==
            CommentTreeStructure(before.reversed(), idsOnly = true))
    }
}
