package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentThreadStoreTest {
    @Test
    fun collapsedThreadsHideDescendantsUntilTheirParentsExpand() {
        val store = CommentThreadStore()
        store.reset(story = story())
        store.replaceParsedComments(
            story = story(),
            parsedComments = comments(),
            sorting = "Default",
            collapseTopLevel = true,
        )

        assertEquals(listOf(1), store.state.value.visibleComments.map { it.comment.id })
        assertEquals(2, store.state.value.visibleComments.single().hiddenReplyCount)

        assertTrue(store.toggleExpanded(1))
        assertEquals(listOf(1, 2), store.state.value.visibleComments.map { it.comment.id })

        assertTrue(store.expandParents(3))
        assertEquals(listOf(1, 2, 3), store.state.value.visibleComments.map { it.comment.id })
    }

    @Test
    fun searchUsesVisibleTextInsteadOfHtmlMarkup() {
        val store = CommentThreadStore()
        store.reset(story = story())
        store.appendLoadedComments(
            story = story(),
            loadedComments = listOf(
                comment(1, -1, 0, "Read <b>Kotlin Multiplatform</b> today"),
                comment(2, -1, 0, "Android only"),
            ),
            sorting = "Default",
            collapseTopLevel = false,
        )

        store.setSearchQuery("kotlin multiplatform")

        assertEquals(listOf(1), store.state.value.searchResults.map(Comment::id))
        store.setSearchQuery("")
        assertEquals(listOf(1, 2), store.state.value.searchResults.map(Comment::id))
    }

    @Test
    fun replacingParsedCommentsPreservesExistingUiStateById() {
        val store = CommentThreadStore()
        store.reset(story = story())
        store.appendLoadedComments(
            story = story(),
            loadedComments = listOf(comment(1, -1, 0, "old")),
            sorting = "Default",
            collapseTopLevel = false,
        )
        assertTrue(store.toggleExpanded(1))

        store.replaceParsedComments(
            story = story(),
            parsedComments = listOf(comment(1, -1, 0, "updated")),
            sorting = "Default",
            collapseTopLevel = false,
        )

        assertEquals("updated", store.findComment(1)?.text)
        assertTrue(store.findComment(1)?.expanded == true)
    }

    @Test
    fun opFilterIsUnavailableWhenTheStoryAuthorHasNoComments() {
        val store = CommentThreadStore()
        val story = story().also { it.by = "author" }
        store.reset(story)
        store.appendLoadedComments(
            story,
            listOf(comment(1, -1, 0, "reply").also { it.by = "someone-else" }),
            "Default",
            collapseTopLevel = false,
        )

        assertFalse(store.showCommentsByOp())
        assertFalse(store.state.value.commentsByOp)
    }

    private fun story() = Story("Story", 99, true, false)

    private fun comments() = listOf(
        comment(1, -1, 0, "parent"),
        comment(2, 1, 1, "child"),
        comment(3, 2, 2, "grandchild"),
    )

    private fun comment(id: Int, parent: Int, depth: Int, text: String) = Comment().also {
        it.id = id
        it.parent = parent
        it.depth = depth
        it.text = text
    }
}
