package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.CommentListDiff
import com.simon.harmonichackernews.CommentThreadFilter
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.data.ItemTimeFormatter
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.data.applySnapshot
import com.simon.harmonichackernews.utils.CommentSorter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PortableCommentItem(
    val comment: CommentSnapshot,
    val presentation: CommentPresentationSnapshot,
    val isNew: Boolean = false,
) {
    val id: Int get() = comment.id
    val by: String? get() = comment.author
    val parent: Int get() = comment.parentId
    val text: String? get() = comment.text
    val time: Int get() = comment.createdAtEpochSeconds
    val timeFormatted: String get() = ItemTimeFormatter.formatNow(time)
    val kidsIds: List<Int> get() = comment.childIds
    val expandedAnchorText: String? get() = comment.expandedAnchorText
    val expanded: Boolean get() = presentation.expanded
    val depth: Int get() = presentation.depth
    val children: Int get() = presentation.childCount
    val totalReplies: Int get() = presentation.totalReplies
    val sortOrder: Int get() = presentation.sortOrder
}

data class PortableVisibleComment(
    val sourceIndex: Int,
    val comment: PortableCommentItem,
    val subtreeReplyCount: Int,
)

data class PortableCommentThreadState(
    val story: StorySnapshot? = null,
    val allComments: List<PortableCommentItem> = emptyList(),
    val filteredComments: List<PortableCommentItem> = emptyList(),
    val sorting: String = CommentSorter.DEFAULT,
    val opThreadFilterEnabled: Boolean = false,
    val hasCommentsByOp: Boolean = false,
    val searchQuery: String = "",
    val searchPreparing: Boolean = false,
    val searchResults: List<PortableCommentItem> = emptyList(),
    val searchResultIds: List<Int> = emptyList(),
    val visibleComments: List<PortableVisibleComment> = emptyList(),
    val revision: Long = 0,
)

internal data class PreparedInitialCommentThread(
    val allComments: List<Comment>,
    val filteredComments: List<Comment>,
    val state: PortableCommentThreadState,
    val visibilityTopology: CommentThreadStore.CommentVisibilityTopology?,
    val initialCommentIds: Set<Int>,
    val searchIndex: Map<Int, SearchableCommentText> = emptyMap(),
)

internal data class CommentThreadPreparationInput(
    val state: PortableCommentThreadState,
    val initialCommentIds: Set<Int>?,
    val hideDelayedComments: Boolean,
    val searchIndex: Map<Int, SearchableCommentText>,
)

/** Canonical portable workflow for comment sorting, filtering, expansion and search. */
class CommentThreadStore {
    val allComments: MutableList<Comment> = mutableListOf()
    val filteredComments: MutableList<Comment> = mutableListOf()

    private val commentsById = mutableMapOf<Int, Comment>()
    private val searchableTextById = mutableMapOf<Int, SearchableCommentText>()
    private val portableItemsById = mutableMapOf<Int, PortableCommentItem>()
    // Only populated on a detached preparation store. Compare content on the worker so the
    // owner can publish unchanged rows by identity without repeating large text comparisons.
    private var snapshotReuseCandidates: Map<Int, PortableCommentItem> = emptyMap()
    private val mutableState = MutableStateFlow(PortableCommentThreadState())
    val state: StateFlow<PortableCommentThreadState> = mutableState.asStateFlow()
    private var currentStory: Story? = null
    // The first successful load (including an empty cached thread) is this visit's baseline.
    private var initialCommentIds: Set<Int>? = null
    internal val hasLoadedComments: Boolean get() = initialCommentIds != null
    private var hideDelayedComments = false
    private var visibilityTopology: CommentVisibilityTopology? = null
    private var previousVisibilityTopology: CommentVisibilityTopology? = null

    /** Source-compatible name for callers already migrated to immutable snapshots. */
    val portableState: StateFlow<PortableCommentThreadState> get() = state

    fun reset(story: Story?, header: Comment = Comment(), sorting: String = CommentSorter.DEFAULT) {
        initialCommentIds = null
        allComments.clear()
        allComments.add(header)
        commentsById.clear()
        commentsById[header.id] = header
        filteredComments.clear()
        filteredComments.add(header)
        searchableTextById.clear()
        portableItemsById.clear()
        publish(
            story = story,
            sorting = sorting,
            opThreadFilterEnabled = false,
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
    ): PreparedInitialCommentThread {
        val prepared = CommentThreadStore().also { store ->
            store.hideDelayedComments = hideDelayedComments
            store.reset(story, allComments.firstOrNull() ?: Comment(), sorting)
            store.replaceParsedComments(story, parsedComments, sorting, collapseTopLevel)
        }
        return PreparedInitialCommentThread(
            allComments = prepared.allComments.toList(),
            filteredComments = prepared.filteredComments.toList(),
            state = prepared.state.value,
            visibilityTopology = prepared.visibilityTopology,
            initialCommentIds = checkNotNull(prepared.initialCommentIds),
        )
    }

    /** Atomically installs a background-prepared initial thread into the live store. */
    internal fun commitPreparedInitialComments(
        story: Story?,
        prepared: PreparedInitialCommentThread,
        sorting: String = prepared.state.sorting,
    ) {
        // Reuse the worker's structural work on the first expansion. The next visibility build
        // still checks every source object, ID and depth in case they changed before commit.
        visibilityTopology = prepared.visibilityTopology
        previousVisibilityTopology = null
        allComments.clear()
        allComments.addAll(prepared.allComments)
        initialCommentIds = prepared.initialCommentIds
        filteredComments.clear()
        filteredComments.addAll(prepared.filteredComments)
        commentsById.clear()
        allComments.forEach { comment -> commentsById[comment.id] = comment }
        searchableTextById.clear()
        searchableTextById.putAll(prepared.searchIndex)
        portableItemsById.clear()
        prepared.state.allComments.forEach { item -> portableItemsById[item.id] = item }
        currentStory = story
        if (sorting != prepared.state.sorting) {
            // A user can change sorting while preparation is running on a worker.
            setSorting(sorting)
            return
        }
        mutableState.value = prepared.state.copy(
            story = story?.toSnapshot(),
            revision = mutableState.value.revision + 1,
        )
    }

    /** Capture on the store's owner; a worker receives no live mutable comments or maps. */
    internal fun capturePreparationInput() = CommentThreadPreparationInput(
        state.value, initialCommentIds, hideDelayedComments, searchableTextById.toMap(),
    )

    companion object {
        internal fun prepareUpdate(
            input: CommentThreadPreparationInput,
            story: StorySnapshot,
            parsedComments: List<Comment>,
            collapseTopLevel: Boolean,
            preserveExisting: Boolean,
        ): PreparedInitialCommentThread {
            val detachedStory = Story().applySnapshot(story)
            val prepared = CommentThreadStore()
            prepared.currentStory = detachedStory
            prepared.initialCommentIds = input.initialCommentIds
            prepared.hideDelayedComments = input.hideDelayedComments
            prepared.mutableState.value = input.state
            prepared.snapshotReuseCandidates = input.state.allComments.associateBy { it.id }
            prepared.searchableTextById.putAll(input.searchIndex)
            val retained = if (preserveExisting) input.state.allComments
                else input.state.allComments.take(1)
            retained.forEach { item ->
                val comment = item.detachedComment()
                prepared.allComments.add(comment)
                prepared.commentsById[comment.id] = comment
            }
            // Incoming objects can also be retained by cache preparation. Sort and collapse only
            // detached copies so retrying after a UI interaction cannot change that input.
            val incoming = parsedComments.map { comment ->
                PortableCommentItem(comment.toSnapshot(), comment.presentationSnapshot()).detachedComment()
            }
            prepared.replaceParsedComments(detachedStory, incoming, input.state.sorting, collapseTopLevel)
            return PreparedInitialCommentThread(
                prepared.allComments.toList(), prepared.filteredComments.toList(),
                prepared.state.value, prepared.visibilityTopology,
                checkNotNull(prepared.initialCommentIds), prepared.searchableTextById.toMap(),
            )
        }

        private fun PortableCommentItem.detachedComment() = Comment().apply {
            applySnapshot(comment)
            restorePreparedText(comment.text.orEmpty(), comment.expandedAnchorText.orEmpty())
            // Keep null text/expanded-text semantics for headers and deleted placeholders.
            text = comment.text
            expanded = presentation.expanded
            depth = presentation.depth
            children = presentation.childCount
            totalReplies = presentation.totalReplies
            sortOrder = presentation.sortOrder
        }
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
        rebuildFilteredComments()
        publish(sorting = sortType, rebuildSearch = true, rebuildVisibility = true)
    }

    fun setHideDelayedComments(hide: Boolean) {
        if (hideDelayedComments == hide) return
        hideDelayedComments = hide
        rebuildFilteredComments()
        publish(rebuildSearch = true, rebuildVisibility = true)
    }

    fun toggleExpanded(commentId: Int): Boolean {
        val comment = commentsById[commentId] ?: return false
        comment.expanded = !comment.expanded
        portableItemsById.remove(commentId)
        if (commentsById.size != allComments.size) {
            // Retain legacy mapping semantics for malformed threads containing duplicate IDs.
            publish(rebuildVisibility = true)
        } else {
            val previous = state.value
            val item = portableItem(comment)
            val all = replaceExpandedItem(previous.allComments, item)
            val filtered = replaceExpandedItem(previous.filteredComments, item)
            mutableState.value = previous.copy(
                story = currentStory?.toSnapshot(),
                allComments = all,
                filteredComments = filtered,
                searchResults = replaceExpandedItem(previous.searchResults, item),
                visibleComments = buildVisibleComments(filteredComments, filtered, previous.visibleComments),
                revision = previous.revision + 1,
            )
        }
        return comment.expanded
    }

    private fun replaceExpandedItem(
        previous: List<PortableCommentItem>,
        item: PortableCommentItem,
    ): List<PortableCommentItem> {
        val index = previous.indexOfFirst { it.id == item.id }
        if (index < 0) return previous
        return previous.toMutableList().apply { this[index] = item }
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

    fun enableOpThreadFilter(): Boolean {
        val story = currentStory
        if (!CommentThreadFilter.hasCommentsByOp(story, allComments)) return false
        rebuildFilteredComments(opThreadFilterEnabled = true)
        publish(opThreadFilterEnabled = true, rebuildVisibility = true)
        return true
    }

    fun resetOpThreadFilter() {
        if (!state.value.opThreadFilterEnabled) return
        rebuildFilteredComments(opThreadFilterEnabled = false)
        publish(opThreadFilterEnabled = false, rebuildVisibility = true)
    }

    fun setSearchQuery(query: String) {
        publishSearch(query)
    }

    internal fun setSearchPreparing(preparing: Boolean) {
        mutableState.value = state.value.copy(searchPreparing = preparing)
    }

    internal fun installSearchIndex(
        source: List<PortableCommentItem>,
        index: Map<Int, SearchableCommentText>,
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
        rebuildFilteredComments()
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
        if (initialCommentIds == null) {
            initialCommentIds = comments.drop(1).mapTo(mutableSetOf()) { it.id }
        }
        portableItemsById.clear()
        rebuildFilteredComments()
        publish(
            story = story,
            sorting = sorting,
            rebuildSearch = true,
            rebuildVisibility = true,
        )
    }

    private fun rebuildFilteredComments(opThreadFilterEnabled: Boolean = state.value.opThreadFilterEnabled) {
        val shouldFilterByOp = opThreadFilterEnabled &&
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
        filteredComments.clear()
        filteredComments.addAll(next)
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
        opThreadFilterEnabled: Boolean = state.value.opThreadFilterEnabled,
        searchQuery: String = state.value.searchQuery,
        rebuildSearch: Boolean = false,
        rebuildVisibility: Boolean = false,
    ) {
        val hasCommentsByOp = CommentThreadFilter.hasCommentsByOp(story, allComments)
        val opThreadFilterActive = opThreadFilterEnabled && hasCommentsByOp
        if (opThreadFilterActive != opThreadFilterEnabled) rebuildFilteredComments(opThreadFilterEnabled = false)
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
        val filteredSnapshots = snapshotList(filteredComments, previous.filteredComments)
        val searchSnapshots = snapshotIds(resultIds, previous.searchResults)
        val visibleSnapshots = if (rebuildVisibility) {
            buildVisibleComments(filteredComments, filteredSnapshots, previous.visibleComments)
        } else {
            refreshVisibleSnapshots(previous.visibleComments)
        }
        val nextState = PortableCommentThreadState(
            story = story?.toSnapshot(),
            allComments = allSnapshots,
            filteredComments = filteredSnapshots,
            sorting = sorting,
            opThreadFilterEnabled = opThreadFilterActive,
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
        portableItemsById.getOrPut(comment.id) {
            val item = comment.toPortableItem().copy(
                isNew = comment !== allComments.firstOrNull() &&
                    initialCommentIds?.let { comment.id !in it } == true,
            )
            snapshotReuseCandidates[comment.id]?.takeIf { it == item } ?: item
        }

    private fun Comment.toPortableItem(): PortableCommentItem = PortableCommentItem(
        comment = toSnapshot(),
        presentation = presentationSnapshot(),
    )

    private fun buildVisibleComments(
        source: List<Comment>,
        snapshots: List<PortableCommentItem>,
        previous: List<PortableVisibleComment>,
    ): List<PortableVisibleComment> {
        if (source.size <= 1) {
            visibilityTopology = null
            previousVisibilityTopology = null
            return emptyList()
        }

        // Keep the current and previous filtered variants so hiding/showing delayed comments can
        // reuse both. Validate either entry before reuse: legacy comments can still be mutated.
        val topology = visibilityTopology?.takeIf { it.matches(source) } ?: run {
            val next = previousVisibilityTopology?.takeIf { it.matches(source) }
                ?: CommentVisibilityTopology(source)
            previousVisibilityTopology = visibilityTopology
            visibilityTopology = next
            next
        }
        val byId = topology.byId

        // The flattened thread is in parent-before-child order. Cache each parent's visibility so
        // descendants do not repeatedly walk the same ancestor chain.
        val visibleByIndex = BooleanArray(source.size)
        var visibleCount = 0
        for (index in 1..<source.size) {
            val comment = source[index]
            val parent = topology.parents[index]
            val visible = when {
                comment.parent == -1 || parent == null -> true
                !parent.expanded -> false
                else -> {
                    val parentIndex = topology.parentIndexes[index]
                    if (parentIndex in 1..<index) visibleByIndex[parentIndex]
                    else isVisible(parent, byId)
                }
            }
            visibleByIndex[index] = visible
            if (visible) visibleCount++
        }

        val visibleComments = ArrayList<PortableVisibleComment>(visibleCount)
        var previousIndex = 0
        for (index in 1..<source.size) {
            if (!visibleByIndex[index]) continue
            while (previousIndex < previous.size && previous[previousIndex].sourceIndex < index) {
                previousIndex++
            }
            val old = previous.getOrNull(previousIndex)
            val item = snapshots[index]
            val replies = topology.subtreeEndExclusive[index] - index - 1
            visibleComments += if (old?.sourceIndex == index && old.comment === item &&
                old.subtreeReplyCount == replies
            ) old else PortableVisibleComment(index, item, replies)
        }
        return visibleComments
    }

    internal class CommentVisibilityTopology(source: List<Comment>) {
        private val comments = source.toList()
        private val ids = IntArray(source.size)
        private val depths = IntArray(source.size)
        private val parentIds = IntArray(source.size)
        val byId = HashMap<Int, Comment>(source.size)
        val parents: List<Comment?>
        val subtreeEndExclusive = IntArray(source.size) { source.size }
        val parentIndexes = IntArray(source.size) { -1 }

        init {
            for (index in source.indices) {
                val comment = source[index]
                ids[index] = comment.id
                depths[index] = comment.depth
                parentIds[index] = comment.parent
                byId[comment.id] = comment
            }
            parents = source.map { byId[it.parent] }
            val precedingIndexes = HashMap<Int, Int>(source.size)
            for (index in 1..<source.size) {
                parentIndexes[index] = precedingIndexes[source[index].parent] ?: -1
                precedingIndexes[source[index].id] = index
            }
            val openAncestors = IntArray(source.size)
            var openCount = 0
            for (index in 1..<source.size) {
                val depth = depths[index]
                while (openCount > 0 && depths[openAncestors[openCount - 1]] >= depth) {
                    subtreeEndExclusive[openAncestors[--openCount]] = index
                }
                openAncestors[openCount++] = index
            }
        }

        fun matches(source: List<Comment>): Boolean {
            if (source.size != comments.size) return false
            for (index in source.indices) {
                val comment = source[index]
                if (comment !== comments[index] || comment.id != ids[index] ||
                    comment.depth != depths[index] || comment.parent != parentIds[index]
                ) return false
            }
            return true
        }
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

internal data class SearchableCommentText(val source: String, val text: String)
