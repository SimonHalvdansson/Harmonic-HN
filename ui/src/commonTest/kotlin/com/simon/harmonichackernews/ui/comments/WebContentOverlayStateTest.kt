package com.simon.harmonichackernews.ui.comments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebContentOverlayStateTest {
    @Test
    fun aNewNavigationResetsProgressAndThePreviousDownload() {
        val state = WebContentOverlayState()
        state.beginLoad()
        state.updateProgress(95)
        state.showDownload { error("Previous download must not survive navigation") }
        val previousLoad = state.loadId
        state.beginLoad()
        assertNotEquals(previousLoad, state.loadId)
        assertEquals(0, state.progress)
        assertTrue(state.progressVisible)
        assertNull(state.onDownload)
    }

    @Test
    fun completionAndImmediateRestartCannotReuseTheCompletedAnimation() {
        val state = WebContentOverlayState()
        state.beginLoad()
        val previousLoad = state.loadId
        state.finishLoad(completeProgress = true)
        assertEquals(100, state.progress)
        assertFalse(state.progressVisible)
        state.beginLoad()
        assertNotEquals(previousLoad, state.loadId)
        assertEquals(0, state.progress)
        assertTrue(state.progressVisible)
    }

    @Test
    fun settlingACommittedPageKeepsActualProgressAndDismissesTheIndicator() {
        val state = WebContentOverlayState()
        state.beginLoad()
        state.updateProgress(60)
        state.finishLoad(completeProgress = false)
        assertEquals(60, state.progress)
        assertFalse(state.progressVisible)
        // A late progress update must not reopen the settled indicator.
        state.updateProgress(90)
        assertFalse(state.progressVisible)
    }

    @Test
    fun browserDisposalDropsDownloadCallbacksAndLoadingState() {
        val state = WebContentOverlayState()
        var clicks = 0
        state.beginLoad()
        state.showDownload { clicks++ }
        state.onDownload?.invoke()
        assertEquals(1, clicks)
        state.reset()
        state.onDownload?.invoke()
        assertEquals(1, clicks)
        assertNull(state.onDownload)
        assertFalse(state.progressVisible)
        assertEquals(0, state.progress)
    }
}
