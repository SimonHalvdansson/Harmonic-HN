package com.simon.harmonichackernews.presentation

import com.fleeksoft.ksoup.Ksoup
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
import com.simon.harmonichackernews.utils.CommentSorter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PortableCommentItem(
    val comment: CommentSnapshot,
    val presentation: CommentPresentationSnapshot,
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
    val hiddenReplyCount: Int,
)

data class PortableCommentThreadState(
    val story: StorySnapshot? = null,
    val allComments: List<PortableCommentItem> = emptyList(),
    val displayedComments: List<PortableCommentItem> = emptyList(),
    val sorting: String = CommentSorter.DEFAULT,
    val commentsByOp: Boolean = false,
    val hasCommentsByOp: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<PortableCommentItem> = emptyList(),
    val searchResultIds: List<Int> = emptyList(),
    val visibleComments: List<PortableVisibleComment> = emptyList(),
    val revision: Long = 0,
)

/** Canonical portable workflow for comment sorting, filtering, expansion and search. */
class CommentThreadStore {
    val allComments: MutableList<Comment> = mutableListOf()
    val displayedComments: MutableList<Comment> = mutableListOf()

    private val commentsById = mutableMapOf<Int, Comment>()
    private val searchableTextById = mutableMapOf<Int, SearchableCommentText>()
    private val mutableState = MutableStateFlow(PortableCommentThreadState())
    val state: StateFlow<PortableCommentThreadState> = mutableState.asStateFlow()
    private var currentStory: Story? = null

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
        publish(
            story = story,
            sorting = sorting,
            commentsByOp = false,
            searchQuery = "",
        )
    }

    fun setStory(story: Story?) {
        publish(story = story)
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
        rebuildDisplayedComments()
        publish(sorting = sortType)
    }

    fun toggleExpanded(commentId: Int): Boolean {
        val comment = commentsById[commentId] ?: return false
        comment.expanded = !comment.expanded
        publish()
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
                expandedAny = true
            }
            parentId = parent.parent
        }
        if (expandedAny) publish()
        return expandedAny
    }

    fun restoreCollapsedComments(collapsedIds: Set<Int>) {
        allComments.forEach { comment -> comment.expanded = comment.id !in collapsedIds }
        publish()
    }

    fun findComment(commentId: Int): Comment? = commentsById[commentId]

    fun showCommentsByOp(): Boolean {
        val story = currentStory
        if (!CommentThreadFilter.hasCommentsByOp(story, allComments)) return false
        rebuildDisplayedComments(commentsByOp = true)
        publish(commentsByOp = true)
        return true
    }

    fun resetCommentsByOp() {
        if (!state.value.commentsByOp) return
        rebuildDisplayedComments(commentsByOp = false)
        publish(commentsByOp = false)
    }

    fun setSearchQuery(query: String) {
        publish(searchQuery = query)
    }

    fun notifyCommentsChanged() {
        rebuildDisplayedComments()
        publish()
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
        rebuildDisplayedComments()
        publish(story = story, sorting = sorting)
    }

    private fun rebuildDisplayedComments(commentsByOp: Boolean = state.value.commentsByOp) {
        val shouldFilterByOp = commentsByOp &&
            CommentThreadFilter.hasCommentsByOp(currentStory, allComments)
        val next = if (shouldFilterByOp) {
            CommentThreadFilter.buildCommentsByOpThreadList(currentStory, allComments)
        } else {
            allComments
        }
        displayedComments.clear()
        displayedComments.addAll(next)
    }

    private fun searchResults(query: String): List<Comment> {
        val normalizedQuery = query.trim().lowercase()
        val comments = allComments.drop(1)
        if (normalizedQuery.isEmpty()) return comments
        return comments.filter { comment ->
            val source = comment.expandedAnchorText.orEmpty()
            val cached = searchableTextById[comment.id]
            val searchableText = if (cached?.source == source) {
                cached.text
            } else {
                Ksoup.parse(source).text().lowercase().also { text ->
                    searchableTextById[comment.id] = SearchableCommentText(source, text)
                }
            }
            normalizedQuery in searchableText
        }
    }

    private fun publish(
        story: Story? = currentStory,
        sorting: String = state.value.sorting,
        commentsByOp: Boolean = state.value.commentsByOp,
        searchQuery: String = state.value.searchQuery,
    ) {
        val hasCommentsByOp = CommentThreadFilter.hasCommentsByOp(story, allComments)
        val actualCommentsByOp = commentsByOp && hasCommentsByOp
        if (actualCommentsByOp != commentsByOp) rebuildDisplayedComments(commentsByOp = false)
        val results = searchResults(searchQuery)
        val visible = buildVisibleComments(displayedComments)
        val revision = state.value.revision + 1
        currentStory = story
        val snapshotsById = HashMap<Int, PortableCommentItem>(allComments.size)
        val allSnapshots = ArrayList<PortableCommentItem>(allComments.size)
        allComments.forEach { comment ->
            val snapshot = comment.toPortableItem()
            snapshotsById[comment.id] = snapshot
            allSnapshots += snapshot
        }
        val displayedSnapshots = displayedComments.map { comment ->
            snapshotsById.getValue(comment.id)
        }
        val nextState = PortableCommentThreadState(
            story = story?.toSnapshot(),
            allComments = allSnapshots,
            displayedComments = displayedSnapshots,
            sorting = sorting,
            commentsByOp = actualCommentsByOp,
            hasCommentsByOp = hasCommentsByOp,
            searchQuery = searchQuery,
            searchResults = results.map { comment -> snapshotsById.getValue(comment.id) },
            searchResultIds = results.map(Comment::id),
            visibleComments = visible.map { item ->
                PortableVisibleComment(
                    sourceIndex = item.sourceIndex,
                    comment = snapshotsById.getValue(item.comment.id),
                    hiddenReplyCount = item.hiddenReplyCount,
                )
            },
            revision = revision,
        )
        mutableState.value = nextState
    }

    private fun Comment.toPortableItem(): PortableCommentItem = PortableCommentItem(
        comment = toSnapshot(),
        presentation = presentationSnapshot(),
    )

    private fun buildVisibleComments(source: List<Comment>): List<MutableVisibleComment> {
        if (source.size <= 1) return emptyList()

        val byId = HashMap<Int, Comment>(source.size)
        source.forEach { comment -> byId[comment.id] = comment }

        // The flattened thread is in parent-before-child order. Cache each parent's visibility so
        // descendants do not repeatedly walk the same ancestor chain.
        val visibilityById = HashMap<Int, Boolean>(source.size)
        val visibleByIndex = BooleanArray(source.size)
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

        val visibleComments = ArrayList<MutableVisibleComment>(source.size - 1)
        for (index in 1..<source.size) {
            if (!visibleByIndex[index]) continue
            visibleComments += MutableVisibleComment(
                sourceIndex = index,
                comment = source[index],
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

    private data class SearchableCommentText(val source: String, val text: String)

    private data class MutableVisibleComment(
        val sourceIndex: Int,
        val comment: Comment,
        val hiddenReplyCount: Int,
    )
}
