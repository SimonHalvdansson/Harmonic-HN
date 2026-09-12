package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.utils.CommentSorter
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentVisibilityTopologyTest {
    @Test
    fun nestedExpansionFilteringSortingAndReplacementMatchUncachedVisibility() {
        val story = story()
        val store = store(story, nestedComments())
        assertMatchesUncached(store)
        for (id in listOf(1, 2, 1, 2, 5, 5)) {
            store.toggleExpanded(id)
            assertMatchesUncached(store)
        }
        store.restoreCollapsedComments(setOf(1, 2, 5))
        assertMatchesUncached(store)
        store.expandParents(4)
        assertMatchesUncached(store)
        store.setHideDelayedComments(true)
        assertMatchesUncached(store)
        store.toggleExpanded(1)
        assertMatchesUncached(store)
        store.setHideDelayedComments(false)
        assertMatchesUncached(store)
        store.showCommentsByOp()
        assertMatchesUncached(store)
        store.toggleExpanded(1)
        assertMatchesUncached(store)
        store.resetCommentsByOp()
        for (sorting in listOf(CommentSorter.NEWEST_FIRST, CommentSorter.REPLY_COUNT, CommentSorter.DEFAULT)) {
            store.setSorting(sorting)
            assertMatchesUncached(store)
        }
        store.replaceParsedComments(story, nestedComments().take(3), CommentSorter.DEFAULT, false)
        assertMatchesUncached(store)
        store.reset(story)
        assertMatchesUncached(store)
        store.appendLoadedComments(story, nestedComments(), CommentSorter.DEFAULT, true)
        assertMatchesUncached(store)
    }

    @Test
    fun cachedTopologyChecksMutableIdsDepthsObjectsOrderAndSize() {
        val story = story()
        val store = store(story, nestedComments())
        assertMatchesUncached(store)

        // Legacy callers can alter the mutable objects without replacing a list. Visibility used
        // those live fields before caching, so rebuilding visibility must continue to see them.
        store.displayedComments[2].depth = 0
        store.setStory(story)
        assertMatchesUncached(store)
        store.displayedComments[3].parent = 5
        store.setStory(story)
        assertMatchesUncached(store)
        val renamedComment = store.displayedComments[2]
        val originalId = renamedComment.id
        renamedComment.id = 77
        store.setStory(story)
        assertMatchesUncached(store)
        // Restore the ID before rebuilding search: the existing comment lookup is keyed by the
        // original ID until a thread replacement. Visibility must handle the mutation both ways.
        renamedComment.id = originalId
        store.setStory(story)
        assertMatchesUncached(store)

        val replacement = comment(1, -1, 0).also { it.expanded = false }
        store.displayedComments[1] = replacement
        store.setStory(story)
        assertMatchesUncached(store)
        val first = store.displayedComments[1]
        store.displayedComments[1] = store.displayedComments[5]
        store.displayedComments[5] = first
        store.setStory(story)
        assertMatchesUncached(store)
        store.displayedComments.removeAt(2)
        store.setStory(story)
        assertMatchesUncached(store)
        store.notifyCommentsChanged()
        assertMatchesUncached(store)
    }

    @Test
    fun inactiveFullTopologyRevalidatesDepthChangesWhenDelayedCommentsReturn() {
        val store = mutateInactiveFullTopology { it.allComments[2].depth = 0 }
        assertEquals(0, store.state.value.visibleComments.first().hiddenReplyCount)
    }

    @Test
    fun inactiveFullTopologyRevalidatesIdChangesWhenDelayedCommentsReturn() {
        // Use another existing ID so the unchanged search snapshot lookup stays defined. The
        // old parent ID disappears from the displayed thread and its children become visible.
        val store = mutateInactiveFullTopology { it.allComments[2].id = 7 }
        assertTrue(store.state.value.visibleComments.any { it.comment.id == 3 })
    }

    @Test
    fun inactiveFullTopologyRevalidatesReplacementObjectsWhenDelayedCommentsReturn() {
        val store = mutateInactiveFullTopology {
            it.allComments[2] = comment(2, 1, 1).also { replacement ->
                replacement.text = " [delayed] "
                replacement.expanded = true
            }
        }
        assertTrue(store.state.value.visibleComments.any { it.comment.id == 3 })
    }

    private fun mutateInactiveFullTopology(mutate: (CommentThreadStore) -> Unit): CommentThreadStore {
        val story = story()
        val store = store(story, nestedComments() + comment(7, -1, 0))
        store.setHideDelayedComments(true)
        assertMatchesUncached(store)
        // Comment 2 is absent from the active filtered variant, so its mutation cannot invalidate
        // that variant. Switching back must independently validate the inactive full topology.
        mutate(store)
        store.setStory(story)
        assertMatchesUncached(store)
        store.setHideDelayedComments(false)
        assertMatchesUncached(store)
        repeat(3) {
            store.setHideDelayedComments(true)
            assertMatchesUncached(store)
            store.setHideDelayedComments(false)
            assertMatchesUncached(store)
        }
        return store
    }

    @Test
    fun duplicateMissingOutOfOrderAndCyclicParentsMatchUncachedVisibility() {
        val random = Random(821)
        repeat(30) {
            val comments = List(40) { index ->
                comment(
                    id = if (index % 11 == 0) 7 else index + 1,
                    parent = random.nextInt(-1, 45),
                    depth = random.nextInt(-1, 8),
                ).also { it.expanded = random.nextBoolean() }
            }
            val story = story()
            val store = store(story, comments)
            assertMatchesUncached(store)
            repeat(12) {
                store.toggleExpanded(random.nextInt(1, 41))
                assertMatchesUncached(store)
            }
            store.restoreCollapsedComments(emptySet())
            assertMatchesUncached(store)
        }
    }

    @Test
    fun preparedCommitReplacesThePreviouslyCachedThread() {
        val story = story()
        val store = store(story, nestedComments())
        val prepared = store.prepareInitialParsedComments(
            story,
            listOf(comment(91, -1, 0), comment(92, 91, 1)),
            CommentSorter.DEFAULT,
            false,
        )
        store.commitPreparedInitialComments(story, prepared)
        store.toggleExpanded(91)
        assertMatchesUncached(store)
        store.toggleExpanded(91)
        assertMatchesUncached(store)
    }

    @Test
    fun preparedTopologyRevalidatesMutableDepthsAndParentsBeforeFirstToggle() {
        val story = story()
        val store = store(story, nestedComments())
        val comments = listOf(
            comment(91, -1, 0),
            comment(92, 91, 1),
            comment(93, 92, 2),
            comment(94, -1, 0),
        )
        val prepared = store.prepareInitialParsedComments(
            story,
            comments,
            CommentSorter.DEFAULT,
            false,
        )
        // Prepared snapshots intentionally stay immutable, but visibility has always read the
        // legacy objects at its next publication. The transferred topology must honor that too.
        comments[1].depth = 0
        comments[2].parent = 94
        comments[3].expanded = false
        store.commitPreparedInitialComments(story, prepared)
        store.toggleExpanded(91)
        assertMatchesUncached(store)
        store.toggleExpanded(94)
        assertMatchesUncached(store)
    }

    @Test
    fun preparedTopologyReadsLiveParentAndExpansionChangesWithoutDepthChanges() {
        val story = story()
        val store = store(story, nestedComments())
        val comments = listOf(
            comment(91, -1, 0),
            comment(92, 91, 1),
            comment(93, -1, 0),
        )
        val prepared = store.prepareInitialParsedComments(
            story,
            comments,
            CommentSorter.DEFAULT,
            false,
        )
        comments[1].parent = 93
        comments[2].expanded = false
        store.commitPreparedInitialComments(story, prepared)
        store.toggleExpanded(91)
        assertMatchesUncached(store)
        store.toggleExpanded(93)
        assertMatchesUncached(store)
    }

    private fun assertMatchesUncached(store: CommentThreadStore) {
        assertEquals(
            uncachedVisibility(store.displayedComments),
            store.state.value.visibleComments.map {
                Triple(it.sourceIndex, it.comment.id, it.hiddenReplyCount)
            },
        )
    }

    // Original visibility algorithm, kept independent of the cached implementation.
    private fun uncachedVisibility(source: List<Comment>): List<Triple<Int, Int, Int>> {
        if (source.size <= 1) return emptyList()
        val byId = HashMap<Int, Comment>(source.size)
        source.forEach { byId[it.id] = it }
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
        return buildList {
            for (index in 1..<source.size) {
                if (visibleByIndex[index]) {
                    add(Triple(index, source[index].id, subtreeEndExclusive[index] - index - 1))
                }
            }
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

    private fun store(story: Story, comments: List<Comment>) = CommentThreadStore().also {
        it.reset(story)
        it.appendLoadedComments(story, comments, CommentSorter.DEFAULT, false)
    }

    private fun nestedComments() = listOf(
        comment(1, -1, 0),
        comment(2, 1, 1).also { it.text = " [delayed] "; it.expanded = false },
        comment(3, 2, 2),
        comment(4, 3, 3).also { it.by = "op" },
        comment(5, -1, 0),
        comment(6, 5, 1),
    )

    private fun story() = Story("Story", 99, true, false).also { it.by = "op" }

    private fun comment(id: Int, parent: Int, depth: Int) = Comment().also {
        it.id = id
        it.parent = parent
        it.depth = depth
        it.expanded = true
        it.text = "comment $id"
        it.by = "reader"
        it.time = id
    }
}
