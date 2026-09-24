package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.network.LinkSummary
import com.simon.harmonichackernews.network.ReferenceLinkPreviewState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceSummaryUiStateTest {
    @Test
    fun knownSummaryIsVisibleBeforeAndDuringCacheLookup() {
        val known = LinkSummary(title = "Known title", imageUrl = "", description = "")

        val initial = referenceSummaryUiState(ReferenceLinkPreviewState(), known)
        val loading = referenceSummaryUiState(
            ReferenceLinkPreviewState(url = "https://example.com", loading = true),
            known,
        )

        assertEquals(known, initial.result)
        assertEquals(known, loading.result)
        assertFalse(initial.loading)
        assertFalse(loading.loading)
    }

    @Test
    fun runtimeResultOrErrorReplacesKnownSummary() {
        val known = LinkSummary(title = "Known title")
        val updated = LinkSummary(title = "Updated title")

        assertEquals(
            updated,
            referenceSummaryUiState(
                ReferenceLinkPreviewState(url = "https://example.com", summary = updated),
                known,
            ).result,
        )
        val failed = referenceSummaryUiState(
            ReferenceLinkPreviewState(url = "https://example.com", error = "Failed"),
            known,
        )
        assertNull(failed.result)
        assertEquals("Failed", failed.error)
        assertTrue(referenceSummaryUiState(
            ReferenceLinkPreviewState(url = "https://example.com", loading = true),
            null,
        ).loading)
    }
}
