package com.simon.harmonichackernews.presentation

import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.CommentListDiff
import com.simon.harmonichackernews.CommentThreadFilter
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
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
    val revision: Long = 0,
)

/** Canonical portable workflow for comment sorting, filtering, expansion and search. */
class CommentThreadStore {
    val allComments: MutableList<Comment> = mutableListOf()
    val displayedComments: MutableList<Comment> = mutableListOf()

    private val searchableTextById = mutableMapOf<Int, SearchableCommentText>()
    private val mutableState = MutableStateFlow(CommentThreadUiState())
    val state: StateFlow<CommentThreadUiState> = mutableState.asStateFlow()

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

    fun showCommentsByOp(): Boolean {
        val story = state.value.story
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
            CommentThreadFilter.hasCommentsByOp(state.value.story, allComments)
        val next = if (shouldFilterByOp) {
            CommentThreadFilter.buildCommentsByOpThreadList(state.value.story, allComments)
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
        story: Story? = state.value.story,
        sorting: String = state.value.sorting,
        commentsByOp: Boolean = state.value.commentsByOp,
        searchQuery: String = state.value.searchQuery,
    ) {
        val hasCommentsByOp = CommentThreadFilter.hasCommentsByOp(story, allComments)
        val actualCommentsByOp = commentsByOp && hasCommentsByOp
        if (actualCommentsByOp != commentsByOp) rebuildDisplayedComments(commentsByOp = false)
        mutableState.value = CommentThreadUiState(
            story = story,
            allComments = allComments.toList(),
            displayedComments = displayedComments.toList(),
            sorting = sorting,
            commentsByOp = actualCommentsByOp,
            hasCommentsByOp = hasCommentsByOp,
            searchQuery = searchQuery,
            searchResults = searchResults(searchQuery),
            revision = state.value.revision + 1,
        )
    }

    private data class SearchableCommentText(val source: String, val text: String)
}
