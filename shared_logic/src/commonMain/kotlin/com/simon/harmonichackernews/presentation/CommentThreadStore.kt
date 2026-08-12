package com.simon.harmonichackernews.presentation

import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.CommentListDiff
import com.simon.harmonichackernews.CommentThreadFilter
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.utils.CommentSorter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CommentThreadUiState(
    val story: Story? = null,
    val allComments: List<Comment> = emptyList(),
    val displayedComments: List<Comment> = emptyList(),
    val sorting: String = CommentSorter.DEFAULT,
    val commentsByOp: Boolean = false,
    val hasCommentsByOp: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Comment> = emptyList(),
    val visibleComments: List<VisibleComment> = emptyList(),
    val revision: Long = 0,
)

data class VisibleComment(
    val sourceIndex: Int,
    val comment: Comment,
    val hiddenReplyCount: Int,
)

data class PortableCommentItem(
    val comment: CommentSnapshot,
    val presentation: CommentPresentationSnapshot,
)

data class PortableVisibleComment(
    val sourceIndex: Int,
    val item: PortableCommentItem,
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

    private val searchableTextById = mutableMapOf<Int, SearchableCommentText>()
    private val mutableState = MutableStateFlow(PortableCommentThreadState())
    val state: StateFlow<PortableCommentThreadState> = mutableState.asStateFlow()

    /** Temporary mutable-model view for Android and shared UI migration only. */
    private val mutableLegacyState = MutableStateFlow(CommentThreadUiState())
    val legacyState: StateFlow<CommentThreadUiState> = mutableLegacyState.asStateFlow()

    /** Source-compatible name for callers already migrated to immutable snapshots. */
    val portableState: StateFlow<PortableCommentThreadState> get() = state

    fun reset(story: Story?, header: Comment = Comment(), sorting: String = CommentSorter.DEFAULT) {
        allComments.clear()
        allComments.add(header)
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
        val existingById = allComments.drop(1).associateBy(Comment::id)
        val nextComments = ArrayList<Comment>(parsedComments.size + 1)
        nextComments.add(header)
        parsedComments.forEach { parsed ->
            val existing = existingById[parsed.id]
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
        val comment = allComments.firstOrNull { it.id == commentId } ?: return false
        comment.expanded = !comment.expanded
        publish()
        return comment.expanded
    }

    fun expandParents(commentId: Int): Boolean {
        val byId = allComments.associateBy(Comment::id)
        var parentId = byId[commentId]?.parent ?: return false
        var expandedAny = false
        val visited = mutableSetOf<Int>()
        while (parentId > 0 && visited.add(parentId)) {
            val parent = byId[parentId] ?: break
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

    fun findComment(commentId: Int): Comment? =
        displayedComments.firstOrNull { it.id == commentId }
            ?: allComments.firstOrNull { it.id == commentId }

    fun showCommentsByOp(): Boolean {
        val story = legacyState.value.story
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
        searchableTextById.keys.retainAll(allComments.mapTo(mutableSetOf(), Comment::id))
        rebuildDisplayedComments()
        publish(story = story, sorting = sorting)
    }

    private fun rebuildDisplayedComments(commentsByOp: Boolean = state.value.commentsByOp) {
        val shouldFilterByOp = commentsByOp &&
            CommentThreadFilter.hasCommentsByOp(legacyState.value.story, allComments)
        val next = if (shouldFilterByOp) {
            CommentThreadFilter.buildCommentsByOpThreadList(legacyState.value.story, allComments)
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
        story: Story? = legacyState.value.story,
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
        val nextLegacyState = CommentThreadUiState(
            story = story,
            allComments = allComments.toList(),
            displayedComments = displayedComments.toList(),
            sorting = sorting,
            commentsByOp = actualCommentsByOp,
            hasCommentsByOp = hasCommentsByOp,
            searchQuery = searchQuery,
            searchResults = results,
            visibleComments = visible,
            revision = revision,
        )
        val allSnapshots = allComments.associate { comment ->
            comment.id to comment.toPortableItem()
        }
        val displayedSnapshots = displayedComments.map { it.toPortableItem() }
        val nextState = PortableCommentThreadState(
            story = story?.toSnapshot(),
            allComments = allComments.map { it.toPortableItem() },
            displayedComments = displayedSnapshots,
            sorting = sorting,
            commentsByOp = actualCommentsByOp,
            hasCommentsByOp = hasCommentsByOp,
            searchQuery = searchQuery,
            searchResults = results.map { it.toPortableItem() },
            searchResultIds = results.map(Comment::id),
            visibleComments = visible.map { item ->
                PortableVisibleComment(
                    sourceIndex = item.sourceIndex,
                    item = allSnapshots.getValue(item.comment.id),
                    hiddenReplyCount = item.hiddenReplyCount,
                )
            },
            revision = revision,
        )
        // Publish the detached snapshot before making mutable compatibility objects observable.
        mutableState.value = nextState
        mutableLegacyState.value = nextLegacyState
    }

    private fun Comment.toPortableItem(): PortableCommentItem = PortableCommentItem(
        comment = toSnapshot(),
        presentation = presentationSnapshot(),
    )

    private fun buildVisibleComments(source: List<Comment>): List<VisibleComment> {
        if (source.size <= 1) return emptyList()
        val byId = source.associateBy(Comment::id)
        return source.mapIndexedNotNull { index, comment ->
            if (index == 0 || !isVisible(comment, byId)) return@mapIndexedNotNull null
            var lastChild = index
            for (candidate in index + 1 until source.size) {
                if (source[candidate].depth <= comment.depth) break
                lastChild = candidate
            }
            VisibleComment(index, comment, lastChild - index)
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

    private data class SearchableCommentText(val source: String, val text: String)
}
