package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommentThreadStoreTest {
    @Test
    fun delayedPlaceholderCommentsCanBeHiddenWithoutRemovingTheirReplies() {
        val store = CommentThreadStore()
        store.reset(story = story())
        store.appendLoadedComments(
            story = story(),
            loadedComments = listOf(
                comment(1, -1, 0, "[delayed]").also { it.expanded = true },
                comment(2, 1, 1, "visible reply"),
                comment(3, -1, 0, "Not [delayed]"),
            ),
            sorting = "Default",
            collapseTopLevel = false,
        )

        store.setHideDelayedComments(true)

        assertEquals(listOf(2, 3), store.state.value.displayedComments.drop(1).map { it.id })
        assertEquals(listOf(2, 3), store.state.value.visibleComments.map { it.comment.id })
        assertEquals(listOf(2, 3), store.state.value.searchResults.map { it.id })

        store.setHideDelayedComments(false)
        assertEquals(listOf(1, 2, 3), store.state.value.displayedComments.drop(1).map { it.id })
    }

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
    fun visibleReplyCountsStopAtTheNextSibling() {
        val store = CommentThreadStore()
        store.reset(story = story())
        store.replaceParsedComments(
            story = story(),
            parsedComments = listOf(
                comment(1, -1, 0, "first root").also { it.expanded = true },
                comment(2, 1, 1, "first child").also { it.expanded = true },
                comment(3, 2, 2, "grandchild"),
                comment(4, 1, 1, "second child"),
                comment(5, -1, 0, "second root").also { it.expanded = true },
                comment(6, 5, 1, "second root child"),
            ),
            sorting = "Default",
            collapseTopLevel = false,
        )

        assertEquals(
            listOf(1 to 3, 2 to 1, 3 to 0, 4 to 0, 5 to 1, 6 to 0),
            store.state.value.visibleComments.map { it.comment.id to it.hiddenReplyCount },
        )
    }

    @Test
    fun portableListsReuseTheSameImmutableCommentSnapshots() {
        val store = CommentThreadStore()
        store.reset(story = story())
        store.appendLoadedComments(
            story = story(),
            loadedComments = listOf(comment(1, -1, 0, "comment")),
            sorting = "Default",
            collapseTopLevel = false,
        )

        val state = store.state.value
        val snapshot = state.allComments[1]
        assertSame(snapshot, state.displayedComments[1])
        assertSame(snapshot, state.searchResults[0])
        assertSame(snapshot, state.visibleComments[0].comment)
    }

    @Test
    fun searchOnlyPublicationReusesUnchangedThreadCollections() {
        val store = CommentThreadStore()
        store.reset(story = story())
        store.appendLoadedComments(
            story = story(),
            loadedComments = listOf(comment(1, -1, 0, "comment")),
            sorting = "Default",
            collapseTopLevel = false,
        )
        val before = store.state.value

        store.setSearchQuery("comment")

        val after = store.state.value
        assertSame(before.allComments, after.allComments)
        assertSame(before.displayedComments, after.displayedComments)
        assertSame(before.searchResults, after.searchResults)
        assertSame(before.visibleComments, after.visibleComments)
    }

    @Test
    fun expansionOnlyReplacesTheChangedPortableComment() {
        val store = CommentThreadStore()
        store.reset(story = story())
        store.appendLoadedComments(
            story = story(),
            loadedComments = listOf(
                comment(1, -1, 0, "first"),
                comment(2, -1, 0, "second"),
            ),
            sorting = "Default",
            collapseTopLevel = false,
        )
        val before = store.state.value

        store.toggleExpanded(1)

        val after = store.state.value
        assertTrue(before.allComments[1] !== after.allComments[1])
        assertSame(before.allComments[2], after.allComments[2])
        assertSame(before.displayedComments[2], after.displayedComments[2])
        assertSame(before.searchResults[1], after.searchResults[1])
    }

    @Test
    fun commentIndexDropsRemovedAndResetComments() {
        val store = CommentThreadStore()
        store.reset(story = story())
        store.appendLoadedComments(
            story = story(),
            loadedComments = listOf(
                comment(1, -1, 0, "removed"),
                comment(2, -1, 0, "retained"),
            ),
            sorting = "Default",
            collapseTopLevel = false,
        )

        store.replaceParsedComments(
            story = story(),
            parsedComments = listOf(comment(2, -1, 0, "updated")),
            sorting = "Default",
            collapseTopLevel = false,
        )

        assertNull(store.findComment(1))
        assertEquals("updated", store.findComment(2)?.text)
        store.reset(story = story())
        assertNull(store.findComment(2))
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

        assertEquals(listOf(1), store.state.value.searchResults.map { it.comment.id })
        store.setSearchQuery("")
        assertEquals(listOf(1, 2), store.state.value.searchResults.map { it.comment.id })
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

    @Test
    fun portableStateDoesNotExposeLaterLegacyMutations() {
        val store = CommentThreadStore()
        val sourceStory = story()
        val source = comment(1, -1, 0, "original").also { it.expanded = true }
        store.reset(sourceStory)
        store.appendLoadedComments(sourceStory, listOf(source), "Default", false)

        val portable = store.portableState.value
        sourceStory.title = "mutated story"
        source.text = "mutated"
        source.expanded = false

        assertEquals("Story", portable.story?.title)
        assertEquals("original", portable.displayedComments.last().comment.text)
        assertTrue(portable.displayedComments.last().presentation.expanded)
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
