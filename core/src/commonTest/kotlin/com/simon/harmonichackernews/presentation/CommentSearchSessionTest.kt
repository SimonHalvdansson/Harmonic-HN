package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Comment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommentSearchSessionTest {
    @Test
    fun preparationIsLazyAndReopeningReusesUnchangedText() = runTest {
        val thread = thread("first")
        var parses = 0
        val search = CommentSearchSession(backgroundScope, thread, StandardTestDispatcher(testScheduler)) {
            parses++
            it
        }
        runCurrent()
        assertEquals(0, parses)
        search.setActive(true)
        runCurrent()
        assertEquals(1, parses)
        search.setActive(false)
        search.setActive(true)
        runCurrent()
        assertEquals(1, parses)
        assertFalse(thread.state.value.searchPreparing)
    }

    @Test
    fun latestQueryIsAppliedWhenBackgroundPreparationFinishes() = runTest {
        val thread = thread("second")
        val release = CompletableDeferred<Unit>()
        val search = CommentSearchSession(backgroundScope, thread, StandardTestDispatcher(testScheduler)) {
            release.await()
            it
        }
        search.setActive(true)
        runCurrent()
        thread.setSearchQuery("first")
        thread.setSearchQuery("second")
        assertTrue(thread.state.value.searchPreparing)
        release.complete(Unit)
        runCurrent()
        assertEquals("second", thread.state.value.searchQuery)
        assertEquals(listOf(1), thread.state.value.searchResultIds)
        assertFalse(thread.state.value.searchPreparing)
    }

    @Test
    fun replacedThreadCancelsOldPreparationAndSearchesNewText() = runTest {
        val thread = thread("old")
        val blocked = CompletableDeferred<Unit>()
        val search = CommentSearchSession(backgroundScope, thread, StandardTestDispatcher(testScheduler)) {
            if (it == "old") blocked.await()
            it
        }
        search.setActive(true)
        runCurrent()
        thread.setSearchQuery("new")
        thread.replaceParsedComments(null, listOf(comment("new")), "Default", false)
        runCurrent()
        blocked.complete(Unit)
        runCurrent()
        assertEquals(listOf(1), thread.state.value.searchResultIds)
        thread.setSearchQuery("old")
        assertTrue(thread.state.value.searchResults.isEmpty())
    }

    @Test
    fun closingCancelsIndexPublicationAndDoesNotIndexLaterRefreshes() = runTest {
        val thread = thread("old")
        val release = CompletableDeferred<Unit>()
        var parses = 0
        val search = CommentSearchSession(backgroundScope, thread, StandardTestDispatcher(testScheduler)) {
            parses++
            release.await()
            it
        }
        search.setActive(true)
        runCurrent()
        thread.setSearchQuery("old")
        search.setActive(false)
        thread.replaceParsedComments(null, listOf(comment("new")), "Default", false)
        release.complete(Unit)
        runCurrent()
        assertEquals(1, parses)
        assertEquals("", thread.state.value.searchQuery)
        assertFalse(thread.state.value.searchPreparing)
    }

    private fun thread(text: String) = CommentThreadStore().apply {
        reset(null)
        appendLoadedComments(null, listOf(comment(text)), "Default", false)
    }

    private fun comment(html: String) = Comment().apply {
        id = 1
        parent = -1
        by = "author"
        text = html
    }
}
