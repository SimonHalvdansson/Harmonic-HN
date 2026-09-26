package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import com.simon.harmonichackernews.data.toSnapshot
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommentThreadStoreTest {
    @Test
    fun detachedRefreshReusesUnchangedContentWhileReplacingEditedComments() {
        val story = story()
        val store = CommentThreadStore().also {
            it.reset(story)
            it.appendLoadedComments(story, comments(), "Default", false)
        }
        val original = store.state.value
        val unchanged = CommentThreadStore.prepareUpdate(store.capturePreparationInput(),
            story.toSnapshot(), comments(), false, true)
        assertSame(original.allComments, unchanged.state.allComments)
        val edited = comments().also { it[1].text = "Edited" }
        val changed = CommentThreadStore.prepareUpdate(store.capturePreparationInput(),
            story.toSnapshot(), edited, false, true)
        assertSame(original.allComments[1], changed.state.allComments[1])
        assertSame(original.allComments[3], changed.state.allComments[3])
        assertEquals("Edited", changed.state.allComments[2].text)
        store.commitPreparedInitialComments(story, changed)
        assertEquals("Edited", store.findComment(2)?.text)
    }

    @Test
    fun expansionUpdatesMatchAFullRebuildAcrossSortingAndFilters() {
        for (sorting in listOf("Default", "Newest first", "Reply count")) {
            for (filtered in listOf(false, true)) {
                val story = story().apply { by = "op" }
                fun makeStore() = CommentThreadStore().also { store ->
                    store.reset(story)
                    store.appendLoadedComments(story, List(120) { index ->
                        val offset = index % 6
                        comment(index + 1, if (offset == 0) -1 else index,
                            offset, if (index % 13 == 0) "[delayed]" else "Text $index").apply {
                            expanded = true; by = if (index % 17 == 0) "op" else "reader"; time = index
                        }
                    }, sorting, false)
                    if (filtered) { store.setHideDelayedComments(true); store.enableOpThreadFilter() }
                }
                val optimized = makeStore()
                val full = makeStore()
                val random = kotlin.random.Random(91)
                repeat(100) {
                    val id = random.nextInt(1, 121)
                    optimized.toggleExpanded(id)
                    full.findComment(id)!!.let { it.expanded = !it.expanded }
                    full.notifyCommentsChanged()
                    assertEquals(full.state.value.copy(revision = 0), optimized.state.value.copy(revision = 0),
                        "sorting=$sorting filtered=$filtered id=$id")
                }
            }
        }
    }

    @Test
    fun collapseReusesUnchangedVisibleRowsAndPreservesNestedExpansion() {
        val store = CommentThreadStore().also { it.reset(story()) }
        val source = comments().map { it.apply { expanded = true } } +
            comment(4, -1, 0, "unaffected").apply { expanded = true }
        store.appendLoadedComments(story(), source, "Default", false)
        val before = store.state.value
        store.toggleExpanded(2)
        val collapsed = store.state.value
        assertEquals(listOf(1, 2, 4), collapsed.visibleComments.map { it.comment.id })
        assertSame(before.visibleComments[0], collapsed.visibleComments[0])
        assertSame(before.visibleComments.last(), collapsed.visibleComments.last())
        store.toggleExpanded(1)
        store.toggleExpanded(1)
        assertEquals(listOf(1, 2, 4), store.state.value.visibleComments.map { it.comment.id })
        store.toggleExpanded(2)
        assertEquals(listOf(1, 2, 3, 4), store.state.value.visibleComments.map { it.comment.id })
    }

    @Test
    fun detachedRefreshMatchesLiveMergeWithoutMutatingEitherInput() {
        val sourceStory = story().apply { by = "op" }
        fun store() = CommentThreadStore().also {
            it.reset(sourceStory)
            it.appendLoadedComments(sourceStory, comments().map { c -> c.apply { expanded = true; by = "op" } }, "Default", false)
            it.toggleExpanded(2)
            it.setSorting("Newest first")
            it.enableOpThreadFilter()
        }
        val detached = store()
        val direct = store()
        val input = detached.capturePreparationInput()
        val arrivals = comments() + comment(4, -1, 0, "arrival")
        val prepared = CommentThreadStore.prepareUpdate(input, sourceStory.toSnapshot(), arrivals, false, true)
        assertSame(input.state, detached.state.value)
        assertFalse(arrivals[1].expanded)
        detached.commitPreparedInitialComments(sourceStory, prepared)
        direct.replaceParsedComments(sourceStory, comments() + comment(4, -1, 0, "arrival"), "Newest first", false)
        assertEquals(direct.state.value.copy(revision = 0), detached.state.value.copy(revision = 0))
        assertEquals(setOf(4), detached.state.value.allComments.filter { it.isNew }.map { it.id }.toSet())
    }

    @Test
    fun refreshMarksOnlyArrivalsAndRetainsMarkersThroughSortingCollapseAndRepeatedRefresh() {
        val store = CommentThreadStore()
        store.reset(story())
        store.replaceParsedComments(story(), listOf(comment(1, -1, 0, "cached")), "Default", false)
        assertFalse(store.state.value.visibleComments.single().comment.isNew)
        val refreshed = listOf(
            comment(1, -1, 0, "edited cached"),
            comment(2, -1, 0, "new root"),
            comment(3, 2, 1, "new reply"),
        )
        store.replaceParsedComments(story(), refreshed, "Default", false)
        fun newIds() = store.state.value.allComments.filter { it.isNew }.map { it.id }.toSet()
        assertEquals(setOf(2, 3), newIds())
        store.toggleExpanded(2)
        store.setSorting("Newest first")
        assertEquals(setOf(2, 3), newIds())
        store.replaceParsedComments(story(), refreshed, "Default", false)
        assertEquals(setOf(2, 3), newIds())
        store.reset(story())
        store.replaceParsedComments(story(), refreshed, "Default", false)
        assertTrue(newIds().isEmpty())
    }

    @Test
    fun preparedEmptyCacheIsABaselineForTheFirstNetworkComment() {
        val store = CommentThreadStore()
        store.reset(story())
        val cached = store.prepareInitialParsedComments(story(), emptyList(), "Default", false)
        store.commitPreparedInitialComments(story(), cached)
        assertTrue(store.hasLoadedComments)
        store.replaceParsedComments(story(), listOf(comment(1, -1, 0, "first reply")), "Default", false)
        assertTrue(store.state.value.visibleComments.single().comment.isNew)
    }

    @Test
    fun officialRefreshUsesTheSameBaselineAsCachedComments() {
        val store = CommentThreadStore()
        store.reset(story())
        store.appendLoadedComments(story(), listOf(comment(1, -1, 0, "cached")), "Default", false)
        store.appendLoadedComments(story(), listOf(
            comment(1, -1, 0, "cached"), comment(2, -1, 0, "new"),
        ), "Default", false)
        assertEquals(listOf(false, true), store.state.value.visibleComments.map { it.comment.isNew })
    }

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

        assertEquals(listOf(2, 3), store.state.value.filteredComments.drop(1).map { it.id })
        assertEquals(listOf(2, 3), store.state.value.visibleComments.map { it.comment.id })
        assertEquals(listOf(2, 3), store.state.value.searchResults.map { it.id })

        store.setHideDelayedComments(false)
        assertEquals(listOf(1, 2, 3), store.state.value.filteredComments.drop(1).map { it.id })
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
        assertEquals(2, store.state.value.visibleComments.single().subtreeReplyCount)

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
            store.state.value.visibleComments.map { it.comment.id to it.subtreeReplyCount },
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
        assertSame(snapshot, state.filteredComments[1])
        assertSame(snapshot, state.searchResults[0])
        assertSame(snapshot, state.visibleComments[0].comment)
    }

    @Test
    fun searchOnlyPublicationReusesUnchangedThreadCollections() = runTest {
        val store = CommentThreadStore()
        store.reset(story = story())
        store.appendLoadedComments(
            story = story(),
            loadedComments = listOf(comment(1, -1, 0, "comment")),
            sorting = "Default",
            collapseTopLevel = false,
        )
        CommentSearchSession(backgroundScope, store, StandardTestDispatcher(testScheduler)).setActive(true)
        runCurrent()
        val before = store.state.value

        store.setSearchQuery("comment")

        val after = store.state.value
        assertSame(before.allComments, after.allComments)
        assertSame(before.filteredComments, after.filteredComments)
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
        assertSame(before.filteredComments[2], after.filteredComments[2])
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
    fun searchUsesVisibleTextInsteadOfHtmlMarkup() = runTest {
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

        CommentSearchSession(backgroundScope, store, StandardTestDispatcher(testScheduler)).setActive(true)
        store.setSearchQuery("kotlin multiplatform")
        runCurrent()

        assertEquals(listOf(1), store.state.value.searchResults.map { it.comment.id })
        store.setSearchQuery("")
        assertEquals(listOf(1, 2), store.state.value.searchResults.map { it.comment.id })
    }

    @Test
    fun searchPreservesOrderAndFilteringWithMissingOrStaleIndexEntries() {
        val store = CommentThreadStore()
        store.reset(story(), comment(99, -1, 0, "Kotlin header"))
        store.appendLoadedComments(
            story(),
            listOf(
                comment(9, -1, 0, " \n[delayed]\t "),
                comment(7, -1, 0, "Kotlin first"),
                comment(3, -1, 0, "Kotlin missing index"),
                comment(11, -1, 0, "Kotlin changed text"),
                comment(5, -1, 0, "Kotlin last"),
            ),
            sorting = "Default",
            collapseTopLevel = true,
        )
        store.setSearchQuery("kotlin")
        assertEquals(emptyList(), store.state.value.searchResultIds)

        val source = store.state.value.allComments
        store.installSearchIndex(source, source.filterNot { it.id == 3 }.associate { item ->
            val html = item.expandedAnchorText.orEmpty()
            item.id to if (item.id == 11) {
                SearchableCommentText("Kotlin old text", "kotlin old text")
            } else {
                SearchableCommentText(html, html.lowercase())
            }
        })

        for (hideDelayed in listOf(false, true)) {
            store.setHideDelayedComments(hideDelayed)
            val allIds = if (hideDelayed) listOf(7, 3, 11, 5) else listOf(9, 7, 3, 11, 5)
            val delayedIds = if (hideDelayed) emptyList() else listOf(9)
            for ((query, expected) in listOf(
                "  KoTLiN\n" to listOf(7, 5),
                "missing" to emptyList(),
                "old text" to emptyList(),
                "absent" to emptyList(),
                "[delayed]" to delayedIds,
                "" to allIds,
                " \t\n" to allIds,
            )) {
                store.setSearchQuery(query)
                val state = store.state.value
                assertEquals(query, state.searchQuery)
                assertEquals(expected, state.searchResultIds, "hideDelayed=$hideDelayed, query=$query")
                assertEquals(expected, state.searchResults.map { it.id })
            }
        }
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

        assertFalse(store.enableOpThreadFilter())
        assertFalse(store.state.value.opThreadFilterEnabled)
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
        assertEquals("original", portable.filteredComments.last().comment.text)
        assertTrue(portable.filteredComments.last().presentation.expanded)
    }

    @Test
    fun preparedInitialCommentsMatchDirectReplacementAndDoNotPublishBeforeCommit() {
        val sourceStory = story()
        val preparedStore = CommentThreadStore().also { it.reset(sourceStory) }
        val before = preparedStore.state.value

        val prepared = preparedStore.prepareInitialParsedComments(
            story = sourceStory,
            parsedComments = comments(),
            sorting = "Default",
            collapseTopLevel = true,
        )

        assertSame(before, preparedStore.state.value)
        preparedStore.commitPreparedInitialComments(sourceStory, prepared)

        val directStore = CommentThreadStore().also { it.reset(sourceStory) }
        directStore.replaceParsedComments(
            story = sourceStory,
            parsedComments = comments(),
            sorting = "Default",
            collapseTopLevel = true,
        )
        assertEquals(
            directStore.state.value.copy(revision = 0),
            preparedStore.state.value.copy(revision = 0),
        )
        assertEquals(listOf(1), preparedStore.state.value.visibleComments.map { it.comment.id })
        assertEquals("child", preparedStore.findComment(2)?.text)
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
