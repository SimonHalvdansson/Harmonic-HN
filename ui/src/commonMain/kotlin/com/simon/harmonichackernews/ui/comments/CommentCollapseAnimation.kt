package com.simon.harmonichackernews.ui.comments

import androidx.collection.MutableIntObjectMap
import androidx.collection.MutableIntSet
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.simon.harmonichackernews.presentation.PortableVisibleComment
import com.simon.harmonichackernews.ui.content.contentTween
import kotlin.math.roundToInt

internal data class CommentCollapsePlan(
    val rows: List<PortableVisibleComment>,
    val exitingIds: Set<Int> = emptySet(),
    val exitingGroups: List<List<Int>> = emptyList(),
)

/** Retain only removed rows already on screen, regardless of the full subtree's size. */
internal fun commentCollapsePlan(
    previous: List<PortableVisibleComment>,
    current: List<PortableVisibleComment>,
    visibleIds: Set<Int>,
): CommentCollapsePlan {
    if (previous === current || visibleIds.isEmpty()) return CommentCollapsePlan(current)
    val currentIds = MutableIntSet(current.size)
    val collapsedIds = MutableIntSet()
    current.forEach { row ->
        currentIds += row.comment.id
        if (!row.comment.expanded) collapsedIds += row.comment.id
    }
    val exitingIds = hashSetOf<Int>()
    val groups = mutableListOf<MutableList<Int>>()
    val exitsAfter = MutableIntObjectMap<MutableList<PortableVisibleComment>>()
    var precedingId = 0
    var hasPreceding = false
    var collapsedDepth: Int? = null
    previous.forEach { row ->
        if (row.comment.id in currentIds) {
            precedingId = row.comment.id
            hasPreceding = true
        }
        if (collapsedDepth != null && row.comment.depth <= collapsedDepth) collapsedDepth = null
        if (row.comment.id in collapsedIds && row.comment.expanded && collapsedDepth == null) {
            collapsedDepth = row.comment.depth
            groups += mutableListOf<Int>()
        } else if (collapsedDepth != null && row.comment.id !in currentIds && row.comment.id in visibleIds) {
            exitingIds += row.comment.id
            groups.last() += row.comment.id
            if (hasPreceding) exitsAfter.getOrPut(precedingId) { mutableListOf() }.add(row)
        }
    }
    if (exitingIds.isEmpty()) return CommentCollapsePlan(current)
    return CommentCollapsePlan(
        buildList(current.size + exitingIds.size) {
            current.forEach { row ->
                add(row)
                exitsAfter[row.comment.id]?.let(::addAll)
            }
        },
        exitingIds,
        groups.filter { it.isNotEmpty() },
    )
}

/** Bottom-aligned shrink of one subtree: every child travels the same distance. */
internal data class CollapsingCommentRow(val offset: Int, val height: Int, val groupHeight: Int) {
    fun clippedTop(progress: Float): Int =
        ((groupHeight * (1f - progress)).roundToInt() - offset).coerceIn(0, height)
}

internal fun CommentCollapsePlan.rowGeometry(heights: Map<Int, Int>): Map<Int, CollapsingCommentRow> = buildMap {
    exitingGroups.forEach { group ->
        val total = group.sumOf { heights.getValue(it) }
        var offset = 0
        group.forEach { id ->
            val height = heights.getValue(id)
            put(id, CollapsingCommentRow(offset, height, total))
            offset += height
        }
    }
}

internal data class AnimatedCommentRows(
    val rows: List<PortableVisibleComment>,
    val exitingIds: Set<Int>,
    val exitGeometry: Map<Int, CollapsingCommentRow>,
    val exitProgress: () -> Float,
)

/** Compare immutable row snapshots without allocating a boxed triple for every comment. */
internal class CommentTreeStructure(
    private val rows: List<PortableVisibleComment>,
    private val idsOnly: Boolean = false,
) {
    fun isNotEmpty(): Boolean = rows.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommentTreeStructure || idsOnly != other.idsOnly || rows.size != other.rows.size) return false
        return rows.indices.all { index ->
            val before = rows[index].comment
            val after = other.rows[index].comment
            before.id == after.id && (idsOnly ||
                (before.depth == after.depth && before.expanded == after.expanded))
        }
    }

    override fun hashCode(): Int = rows.fold(1) { hash, row ->
        if (idsOnly) 31 * hash + row.comment.id
        else 31 * (31 * (31 * hash + row.comment.id) + row.comment.depth) + row.comment.expanded.hashCode()
    }
}

@Composable
internal fun rememberAnimatedCommentRows(
    current: List<PortableVisibleComment>,
    listState: LazyListState,
    enabled: Boolean,
): AnimatedCommentRows {
    var previous by remember { mutableStateOf(current) }
    // Metadata refreshes must not discard retained children or restart their animation.
    // Only changes to the visible tree begin a new transition.
    val structure = remember(current) {
        CommentTreeStructure(current)
    }
    val (plan, geometry, plannedCurrent) = remember(structure, enabled) {
        val heights = listState.layoutInfo.visibleItemsInfo.mapNotNull {
            (it.key as? Int)?.let { id -> id to it.size }
        }.toMap()
        val plan = if (enabled) commentCollapsePlan(
            previous,
            current,
            heights.keys,
        ) else CommentCollapsePlan(current)
        Triple(plan, plan.rowGeometry(heights), current)
    }
    SideEffect { previous = current }
    val rows = remember(plan, current) {
        if (plan.exitingIds.isEmpty()) current
        else if (current === plannedCurrent) plan.rows
        else {
            val updated = current.associateBy { it.comment.id }
            plan.rows.map { updated[it.comment.id] ?: it }
        }
    }
    val progress = remember(plan) { Animatable(1f) }
    LaunchedEffect(plan) {
        if (plan.exitingIds.isNotEmpty()) progress.animateTo(0f, contentTween())
    }
    val finished by remember(progress) { derivedStateOf { progress.value == 0f } }
    return if (finished) AnimatedCommentRows(current, emptySet(), emptyMap()) { 0f }
    else AnimatedCommentRows(rows, plan.exitingIds, geometry) { progress.value }
}
