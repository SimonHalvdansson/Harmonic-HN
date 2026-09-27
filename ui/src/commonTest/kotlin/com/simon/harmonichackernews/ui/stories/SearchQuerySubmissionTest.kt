package com.simon.harmonichackernews.ui.stories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchQuerySubmissionTest {
    @Test
    fun correctionBeforeSearchUsesLatestTextWithoutWaitingForRecomposition() {
        val submission = SearchQuerySubmission("kotln")
        assertNull(submission.updateDraft("kotlin"))
        assertEquals("kotlin", submission.submit())
    }

    @Test
    fun correctionAfterSearchReplacesQueryAndIgnoresDuplicateCommit() {
        val submission = SearchQuerySubmission("kotln")
        assertEquals("kotln", submission.submit())
        assertEquals("kotlin", submission.updateDraft("kotlin"))
        submission.synchronizeDraft("kotlin")
        assertNull(submission.updateDraft("kotlin"))
    }

    @Test
    fun refocusingOrClearingDoesNotSearchOnEveryKeystroke() {
        val submission = SearchQuerySubmission("kotlin")
        submission.submit()
        submission.startEditing()
        assertNull(submission.updateDraft(""))
        assertNull(submission.updateDraft("swift"))
        assertEquals("swift", submission.submit())
    }

    @Test
    fun closingSearchIgnoresLateImeCommit() {
        val submission = SearchQuerySubmission("kotln")
        submission.submit()
        submission.startEditing()
        assertNull(submission.updateDraft("kotlin"))
    }

    @Test
    fun restoredDraftCanBeSubmittedWithoutTyping() {
        val submission = SearchQuerySubmission("")
        submission.synchronizeDraft("restored query")
        assertEquals("restored query", submission.submit())
    }
}
