package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.PortableVisibleComment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
