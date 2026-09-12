@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.CommentListDiff
import com.simon.harmonichackernews.CommentThreadFilter
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.presentation.CommentThreadStore
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.PortableCommentThreadState
import com.simon.harmonichackernews.presentation.PortableVisibleComment
import com.simon.harmonichackernews.utils.CommentSorter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Paired full-store operations; the before store below freezes the original production code. */
@RunWith(AndroidJUnit4::class)
class CommentInteractionMicroOptimizationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var result: Any? = null

    @Test fun toggleComment10Before() = toggle(10, before = true)
    @Test fun toggleComment10After() = toggle(10, before = false)
    @Test fun toggleComment500Before() = toggle(500, before = true)
    @Test fun toggleComment500After() = toggle(500, before = false)
    @Test fun toggleFilteredComment500Before() = toggle(500, before = true, filtered = true)
    @Test fun toggleFilteredComment500After() = toggle(500, before = false, filtered = true)
    @Test fun hideShowDelayed500Before() = hideShow(before = true)
    @Test fun hideShowDelayed500After() = hideShow(before = false)
    @Test fun openComments500Before() = open(before = true)
    @Test fun openComments500After() = open(before = false)
    @Test fun preparedOpen500Before() = preparedOpen(before = true)
    @Test fun preparedOpen500After() = preparedOpen(before = false)
    @Test fun firstToggleAfterPreparedOpen500Before() = firstToggleAfterPreparedOpen(before = true)
    @Test fun firstToggleAfterPreparedOpen500After() = firstToggleAfterPreparedOpen(before = false)

    private fun toggle(count: Int, before: Boolean, filtered: Boolean = false) {
        val original = beforeStore(count)
        val optimized = afterStore(count)
        original.setHideDelayedComments(filtered)
        optimized.setHideDelayedComments(filtered)
        assertEquals(original.state.value, optimized.state.value)
        repeat(4) {
            assertEquals(original.toggleExpanded(FIRST_ID), optimized.toggleExpanded(FIRST_ID))
            assertEquals(original.state.value, optimized.state.value)
        }
        val operation: () -> Any = if (before) {
            { original.toggleExpanded(FIRST_ID); original.state.value }
        } else {
            { optimized.toggleExpanded(FIRST_ID); optimized.state.value }
        }
        benchmarkRule.measureRepeated { result = operation() }
    }

    private fun hideShow(before: Boolean) {
        val original = beforeStore(500)
        val optimized = afterStore(500)
        for (hide in listOf(true, false)) {
            original.setHideDelayedComments(hide)
            optimized.setHideDelayedComments(hide)
            assertEquals(original.state.value, optimized.state.value)
        }
        var hide = false
        val operation: () -> Any = if (before) {
            { hide = !hide; original.setHideDelayedComments(hide); original.state.value }
        } else {
            { hide = !hide; optimized.setHideDelayedComments(hide); optimized.state.value }
        }
        benchmarkRule.measureRepeated { result = operation() }
    }

    private fun open(before: Boolean) {
        assertEquals(beforeStore(500).state.value, afterStore(500).state.value)
        val comments = comments(500)
        val story = story()
        val operation: () -> Any = if (before) {
            {
                BeforeCommentThreadStore().also {
                    it.reset(story)
                    it.appendLoadedComments(story, comments, CommentSorter.DEFAULT, false)
                }.state.value
            }
        } else {
            {
                CommentThreadStore().also {
                    it.reset(story)
                    it.appendLoadedComments(story, comments, CommentSorter.DEFAULT, false)
                }.state.value
            }
        }
        benchmarkRule.measureRepeated { result = operation() }
    }

    private fun preparedOpen(before: Boolean) {
        val fixture = comments(500)
        val sourceStory = story()
        assertEquals(
            beforePreparedStore(fixture, sourceStory).state.value,
            afterPreparedStore(fixture, sourceStory).state.value,
        )
        val operation: () -> Any = if (before) {
            { beforePreparedStore(fixture, sourceStory).state.value }
        } else {
            { afterPreparedStore(fixture, sourceStory).state.value }
        }
        benchmarkRule.measureRepeated { result = operation() }
    }

    private fun firstToggleAfterPreparedOpen(before: Boolean) {
        val sourceStory = story()
        val original = beforePreparedStore(comments(500), sourceStory)
        val optimized = afterPreparedStore(comments(500), sourceStory)
        assertEquals(original.state.value, optimized.state.value)
        assertEquals(original.toggleExpanded(FIRST_ID), optimized.toggleExpanded(FIRST_ID))
        assertEquals(original.state.value, optimized.state.value)
        // Each iteration starts with a newly committed prepared thread. This exercises the first
        // user interaction, including any visibility work not retained across the preparation hop.
        if (before) {
            benchmarkRule.measureRepeated {
                val store = runWithMeasurementDisabled {
                    beforePreparedStore(comments(500), sourceStory)
                }
                store.toggleExpanded(FIRST_ID)
                result = store.state.value
            }
        } else {
            benchmarkRule.measureRepeated {
                val store = runWithMeasurementDisabled {
                    afterPreparedStore(comments(500), sourceStory)
                }
                store.toggleExpanded(FIRST_ID)
                result = store.state.value
            }
        }
    }

    private fun beforePreparedStore(fixture: List<Comment>, sourceStory: Story) =
        BeforeCommentThreadStore().also {
            it.reset(sourceStory)
            val prepared = it.prepareInitialParsedComments(sourceStory, fixture, CommentSorter.DEFAULT, false)
            it.commitPreparedInitialComments(sourceStory, prepared)
        }

    private fun afterPreparedStore(fixture: List<Comment>, sourceStory: Story) =
        CommentThreadStore().also {
            it.reset(sourceStory)
            val prepared = it.prepareInitialParsedComments(sourceStory, fixture, CommentSorter.DEFAULT, false)
            it.commitPreparedInitialComments(sourceStory, prepared)
        }

    private fun beforeStore(count: Int) = BeforeCommentThreadStore().also {
        val story = story()
        it.reset(story)
        it.appendLoadedComments(story, comments(count), CommentSorter.DEFAULT, false)
    }

    private fun afterStore(count: Int) = CommentThreadStore().also {
        val story = story()
        it.reset(story)
        it.appendLoadedComments(story, comments(count), CommentSorter.DEFAULT, false)
    }

    private fun story() = Story("Interaction benchmark", 99, true, false).also { it.by = "op" }

    private fun comments(count: Int): List<Comment> = List(count) { index ->
        val groupIndex = index % 10
        val depth = if (groupIndex == 0) 0 else (groupIndex - 1) % 3 + 1
        Comment().also {
            it.id = FIRST_ID + index
            it.parent = when (depth) {
                0 -> -1
                1 -> FIRST_ID + index - groupIndex
                else -> FIRST_ID + index - 1
            }
            it.depth = depth
            it.expanded = groupIndex != 4
            it.by = if (index % 19 == 0) "op" else "reader"
            it.text = if (groupIndex == 1) " [delayed] " else "Comment $index with nested replies"
        }
    }

    private companion object { const val FIRST_ID = 1_000_000 }
}

// Frozen before implementation: includes publication and immutable snapshot work in both timings.
private data class BeforePreparedInitialCommentThread(
    val allComments: List<Comment>,
    val displayedComments: List<Comment>,
    val state: PortableCommentThreadState,
)

/** Canonical portable workflow for comment sorting, filtering, expansion and search. */
private class BeforeCommentThreadStore {
    val allComments: MutableList<Comment> = mutableListOf()
    val displayedComments: MutableList<Comment> = mutableListOf()

    private val commentsById = mutableMapOf<Int, Comment>()
    private val searchableTextById = mutableMapOf<Int, BeforeSearchableCommentText>()
    private val portableItemsById = mutableMapOf<Int, PortableCommentItem>()
    private val mutableState = MutableStateFlow(PortableCommentThreadState())
    val state: StateFlow<PortableCommentThreadState> = mutableState.asStateFlow()
    private var currentStory: Story? = null
    private var hideDelayedComments = false

    /** Source-compatible name for callers already migrated to immutable snapshots. */
    val portableState: StateFlow<PortableCommentThreadState> get() = state

    fun reset(story: Story?, header: Comment = Comment(), sorting: String = CommentSorter.DEFAULT) {
        allComments.clear()
        allComments.add(header)
        commentsById.clear()
        commentsById[header.id] = header
        displayedComments.clear()
        displayedComments.add(header)
        searchableTextById.clear()
        portableItemsById.clear()
        publish(
            story = story,
            sorting = sorting,
            commentsByOp = false,
            searchQuery = "",
            rebuildSearch = true,
            rebuildVisibility = true,
        )
    }

    fun setStory(story: Story?) {
        publish(story = story, rebuildVisibility = true)
    }

    fun replaceParsedComments(
        story: Story?,
        parsedComments: List<Comment>,
        sorting: String,
        collapseTopLevel: Boolean,
    ) {
        val header = allComments.firstOrNull() ?: Comment()
        val nextComments = ArrayList<Comment>(parsedComments.size + 1)
        nextComments.add(header)
        parsedComments.forEach { parsed ->
            val existing = commentsById[parsed.id]
            if (existing == null) {
                nextComments.add(parsed)
            } else {
                CommentListDiff.updateExistingComment(existing, parsed)
                nextComments.add(existing)
            }
        }
        prepareAndReplace(story, nextComments, sorting, collapseTopLevel)
    }

    /** Builds the initial immutable thread snapshot without mutating the live screen store. */
    internal fun prepareInitialParsedComments(
        story: Story?,
        parsedComments: List<Comment>,
        sorting: String,
        collapseTopLevel: Boolean,
    ): BeforePreparedInitialCommentThread {
        val prepared = BeforeCommentThreadStore().also { store ->
            store.hideDelayedComments = hideDelayedComments
            store.reset(story, allComments.firstOrNull() ?: Comment(), sorting)
            store.replaceParsedComments(story, parsedComments, sorting, collapseTopLevel)
        }
        return BeforePreparedInitialCommentThread(
            allComments = prepared.allComments.toList(),
            displayedComments = prepared.displayedComments.toList(),
            state = prepared.state.value,
        )
    }

    /** Atomically installs a background-prepared initial thread into the live store. */
    internal fun commitPreparedInitialComments(
        story: Story?,
        prepared: BeforePreparedInitialCommentThread,
    ) {
        allComments.clear()
        allComments.addAll(prepared.allComments)
        displayedComments.clear()
        displayedComments.addAll(prepared.displayedComments)
        commentsById.clear()
        allComments.forEach { comment -> commentsById[comment.id] = comment }
        searchableTextById.clear()
        portableItemsById.clear()
        prepared.state.allComments.forEach { item -> portableItemsById[item.id] = item }
        currentStory = story
        mutableState.value = prepared.state.copy(
            story = story?.toSnapshot(),
            revision = mutableState.value.revision + 1,
        )
    }

    fun appendLoadedComments(
        story: Story?,
        loadedComments: List<Comment>,
        sorting: String,
        collapseTopLevel: Boolean,
    ) {
        val header = allComments.firstOrNull() ?: Comment()
        val nextComments = ArrayList<Comment>(loadedComments.size + 1)
        nextComments.add(header)
        nextComments.addAll(loadedComments)
        prepareAndReplace(story, nextComments, sorting, collapseTopLevel)
    }

    fun setSorting(sortType: String) {
        CommentSorter.sort(allComments, sortType)
        portableItemsById.clear()
        rebuildDisplayedComments()
        publish(sorting = sortType, rebuildSearch = true, rebuildVisibility = true)
    }

    fun setHideDelayedComments(hide: Boolean) {
        if (hideDelayedComments == hide) return
        hideDelayedComments = hide
        rebuildDisplayedComments()
        publish(rebuildSearch = true, rebuildVisibility = true)
    }

    fun toggleExpanded(commentId: Int): Boolean {
        val comment = commentsById[commentId] ?: return false
        comment.expanded = !comment.expanded
        portableItemsById.remove(commentId)
        publish(rebuildVisibility = true)
        return comment.expanded
    }

    fun expandParents(commentId: Int): Boolean {
        var parentId = commentsById[commentId]?.parent ?: return false
        var expandedAny = false
        val visited = mutableSetOf<Int>()
        while (parentId > 0 && visited.add(parentId)) {
            val parent = commentsById[parentId] ?: break
            if (!parent.expanded) {
                parent.expanded = true
                portableItemsById.remove(parent.id)
                expandedAny = true
            }
            parentId = parent.parent
        }
        if (expandedAny) publish(rebuildVisibility = true)
        return expandedAny
    }

    fun restoreCollapsedComments(collapsedIds: Set<Int>) {
        allComments.forEach { comment ->
            val expanded = comment.id !in collapsedIds
            if (comment.expanded != expanded) {
                comment.expanded = expanded
                portableItemsById.remove(comment.id)
            }
        }
        publish(rebuildVisibility = true)
    }

    fun findComment(commentId: Int): Comment? = commentsById[commentId]

    fun showCommentsByOp(): Boolean {
        val story = currentStory
        if (!CommentThreadFilter.hasCommentsByOp(story, allComments)) return false
        rebuildDisplayedComments(commentsByOp = true)
        publish(commentsByOp = true, rebuildVisibility = true)
        return true
    }

    fun resetCommentsByOp() {
        if (!state.value.commentsByOp) return
        rebuildDisplayedComments(commentsByOp = false)
        publish(commentsByOp = false, rebuildVisibility = true)
    }

    fun setSearchQuery(query: String) {
        publishSearch(query)
    }

    internal fun setSearchPreparing(preparing: Boolean) {
        mutableState.value = state.value.copy(searchPreparing = preparing)
    }

    internal fun installSearchIndex(
        source: List<PortableCommentItem>,
        index: Map<Int, BeforeSearchableCommentText>,
    ) {
        if (state.value.allComments !== source) return
        searchableTextById.clear()
        searchableTextById.putAll(index)
        publishSearch(state.value.searchQuery, preparing = false)
    }

    private fun publishSearch(query: String, preparing: Boolean = state.value.searchPreparing) {
        val previous = state.value
        val ids = searchResultIds(query)
        mutableState.value = previous.copy(
            searchQuery = query,
            searchPreparing = preparing,
            searchResultIds = ids,
            searchResults = snapshotIds(ids, previous.searchResults),
        )
    }

    fun notifyCommentsChanged() {
        portableItemsById.clear()
        rebuildDisplayedComments()
        publish(rebuildSearch = true, rebuildVisibility = true)
    }

    private fun prepareAndReplace(
        story: Story?,
        comments: MutableList<Comment>,
        sorting: String,
        collapseTopLevel: Boolean,
    ) {
        for (index in 1..<comments.size) comments[index].sortOrder = index
        CommentSorter.sort(comments, sorting)
        if (collapseTopLevel) {
            comments.drop(1).filter { it.depth == 0 }.forEach { it.expanded = false }
        }
        allComments.clear()
        allComments.addAll(comments)
        commentsById.clear()
        allComments.forEach { comment -> commentsById[comment.id] = comment }
        searchableTextById.keys.retainAll(allComments.mapTo(mutableSetOf(), Comment::id))
        portableItemsById.clear()
        rebuildDisplayedComments()
        publish(
            story = story,
            sorting = sorting,
            rebuildSearch = true,
            rebuildVisibility = true,
        )
    }

    private fun rebuildDisplayedComments(commentsByOp: Boolean = state.value.commentsByOp) {
        val shouldFilterByOp = commentsByOp &&
            CommentThreadFilter.hasCommentsByOp(currentStory, allComments)
        val filteredByOp = if (shouldFilterByOp) {
            CommentThreadFilter.buildCommentsByOpThreadList(currentStory, allComments)
        } else {
            allComments
        }
        val next = if (hideDelayedComments) {
            filteredByOp.filterNot { it.isDelayedPlaceholder() }
        } else {
            filteredByOp
        }
        displayedComments.clear()
        displayedComments.addAll(next)
    }

    private fun searchResultIds(query: String): List<Int> {
        if (allComments.size <= 1) return emptyList()
        val normalizedQuery = query.trim().lowercase()
        // Build only the IDs consumed by both callers, without copying or filtering the thread
        // into intermediate lists on each query. Blank queries can reserve their maximum size.
        val ids = if (normalizedQuery.isEmpty()) {
            ArrayList<Int>(allComments.size - 1)
        } else {
            ArrayList<Int>()
        }
        for (index in 1..<allComments.size) {
            val comment = allComments[index]
            if (hideDelayedComments && comment.isDelayedPlaceholder()) continue
            if (normalizedQuery.isNotEmpty()) {
                val source = comment.expandedAnchorText.orEmpty()
                val cached = searchableTextById[comment.id]
                val searchableText = cached?.takeIf { it.source == source }?.text ?: continue
                if (normalizedQuery !in searchableText) continue
            }
            ids.add(comment.id)
        }
        return ids
    }

    private fun publish(
        story: Story? = currentStory,
        sorting: String = state.value.sorting,
        commentsByOp: Boolean = state.value.commentsByOp,
        searchQuery: String = state.value.searchQuery,
        rebuildSearch: Boolean = false,
        rebuildVisibility: Boolean = false,
    ) {
        val hasCommentsByOp = CommentThreadFilter.hasCommentsByOp(story, allComments)
        val actualCommentsByOp = commentsByOp && hasCommentsByOp
        if (actualCommentsByOp != commentsByOp) rebuildDisplayedComments(commentsByOp = false)
        val previous = state.value
        val resultIds = if (rebuildSearch || searchQuery != previous.searchQuery) {
            searchResultIds(searchQuery)
        } else {
            previous.searchResultIds
        }
        val revision = previous.revision + 1
        currentStory = story
        portableItemsById.keys.retainAll(commentsById.keys)
        val allSnapshots = snapshotList(allComments, previous.allComments)
        val displayedSnapshots = snapshotList(displayedComments, previous.displayedComments)
        val searchSnapshots = snapshotIds(resultIds, previous.searchResults)
        val visibleSnapshots = if (rebuildVisibility) {
            buildVisibleComments(displayedComments)
        } else {
            refreshVisibleSnapshots(previous.visibleComments)
        }
        val nextState = PortableCommentThreadState(
            story = story?.toSnapshot(),
            allComments = allSnapshots,
            displayedComments = displayedSnapshots,
            sorting = sorting,
            commentsByOp = actualCommentsByOp,
            hasCommentsByOp = hasCommentsByOp,
            searchQuery = searchQuery,
            searchPreparing = previous.searchPreparing,
            searchResults = searchSnapshots,
            searchResultIds = resultIds,
            visibleComments = visibleSnapshots,
            revision = revision,
        )
        mutableState.value = nextState
    }

    private fun snapshotList(
        comments: List<Comment>,
        previous: List<PortableCommentItem>,
    ): List<PortableCommentItem> = identityPreservingMap(comments, previous, ::portableItem)

    private fun snapshotIds(
        ids: List<Int>,
        previous: List<PortableCommentItem>,
    ): List<PortableCommentItem> = identityPreservingMap(ids, previous) { id ->
        portableItem(commentsById.getValue(id))
    }

    private fun <Source, Snapshot> identityPreservingMap(
        source: List<Source>,
        previous: List<Snapshot>,
        transform: (Source) -> Snapshot,
    ): List<Snapshot> {
        if (source.size != previous.size) {
            return source.mapTo(ArrayList(source.size), transform)
        }
        for (index in source.indices) {
            val item = transform(source[index])
            if (previous[index] !== item) {
                return ArrayList<Snapshot>(source.size).apply {
                    addAll(previous.subList(0, index))
                    add(item)
                    for (remainingIndex in index + 1 until source.size) {
                        add(transform(source[remainingIndex]))
                    }
                }
            }
        }
        return previous
    }

    private fun refreshVisibleSnapshots(
        previous: List<PortableVisibleComment>,
    ): List<PortableVisibleComment> {
        previous.forEachIndexed { index, visible ->
            val item = portableItem(commentsById.getValue(visible.comment.id))
            if (item !== visible.comment) {
                return ArrayList<PortableVisibleComment>(previous.size).apply {
                    addAll(previous.subList(0, index))
                    add(visible.copy(comment = item))
                    for (remainingIndex in index + 1 until previous.size) {
                        val remaining = previous[remainingIndex]
                        val remainingItem = portableItem(
                            commentsById.getValue(remaining.comment.id),
                        )
                        add(
                            if (remainingItem === remaining.comment) remaining
                            else remaining.copy(comment = remainingItem),
                        )
                    }
                }
            }
        }
        return previous
    }

    private fun portableItem(comment: Comment): PortableCommentItem =
        portableItemsById.getOrPut(comment.id) { comment.toPortableItem() }

    private fun Comment.toPortableItem(): PortableCommentItem = PortableCommentItem(
        comment = toSnapshot(),
        presentation = presentationSnapshot(),
    )

    private fun buildVisibleComments(source: List<Comment>): List<PortableVisibleComment> {
        if (source.size <= 1) return emptyList()

        val byId = HashMap<Int, Comment>(source.size)
        source.forEach { comment -> byId[comment.id] = comment }

        // The flattened thread is in parent-before-child order. Cache each parent's visibility so
        // descendants do not repeatedly walk the same ancestor chain.
        val visibilityById = HashMap<Int, Boolean>(source.size)
        val visibleByIndex = BooleanArray(source.size)
        var visibleCount = 0
        for (index in 1..<source.size) {
            val comment = source[index]
            val parent = byId[comment.parent]
            val visible = when {
                comment.parent == -1 || parent == null -> true
                !parent.expanded -> false
                else -> visibilityById[parent.id] ?: isVisible(parent, byId)
            }
            visibleByIndex[index] = visible
            visibilityById[comment.id] = visible
            if (visible) visibleCount++
        }

        // Find the first following item at the same or a shallower depth for every comment in one
        // pass. Previously, each visible comment scanned the rest of its subtree independently.
        val subtreeEndExclusive = IntArray(source.size) { source.size }
        val openAncestors = IntArray(source.size)
        var openCount = 0
        for (index in 1..<source.size) {
            val depth = source[index].depth
            while (openCount > 0 && source[openAncestors[openCount - 1]].depth >= depth) {
                subtreeEndExclusive[openAncestors[--openCount]] = index
            }
            openAncestors[openCount++] = index
        }

        val visibleComments = ArrayList<PortableVisibleComment>(visibleCount)
        for (index in 1..<source.size) {
            if (!visibleByIndex[index]) continue
            visibleComments += PortableVisibleComment(
                sourceIndex = index,
                comment = portableItem(source[index]),
                hiddenReplyCount = subtreeEndExclusive[index] - index - 1,
            )
        }
        return visibleComments
    }

    private fun isVisible(comment: Comment, byId: Map<Int, Comment>): Boolean {
        var current = comment
        repeat(byId.size) {
            if (current.parent == -1) return true
            val parent = byId[current.parent] ?: return true
            if (!parent.expanded) return false
            current = parent
        }
        return true
    }


    private fun Comment.isDelayedPlaceholder(): Boolean = text?.trim() == "[delayed]"
}

private data class BeforeSearchableCommentText(val source: String, val text: String)
