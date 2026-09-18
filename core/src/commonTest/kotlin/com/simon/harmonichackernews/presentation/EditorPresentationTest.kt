package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.navigation.EditorType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorPresentationTest {
    @Test
    fun incompleteAndInvalidPostsStillContainADraft() {
        for (submission in listOf(
            EditorSubmission(title = "Unfinished title"),
            EditorSubmission(url = "https://example.com"),
            EditorSubmission(text = "A body without a title"),
            EditorSubmission(title = "x".repeat(81), text = "Body"),
        )) {
            assertFalse(submission.validate(EditorType.POST).canSubmit)
            assertTrue(submission.hasDraft(EditorType.POST))
        }
        assertFalse(EditorSubmission().hasDraft(EditorType.POST))
        assertTrue(EditorSubmission(comment = "Reply").hasDraft(EditorType.COMMENT_REPLY))
        assertFalse(EditorSubmission(title = "Context title").hasDraft(EditorType.COMMENT_REPLY))
    }

    @Test
    fun postNeedsATitleAndEitherUrlOrText() {
        assertFalse(EditorSubmission().validate(EditorType.POST, 80).canSubmit)
        assertFalse(
            EditorSubmission(title = "Title").validate(EditorType.POST, 80).canSubmit,
        )
        assertTrue(
            EditorSubmission(title = "Title", url = "https://example.com")
                .validate(EditorType.POST, 80).canSubmit,
        )
        assertTrue(
            EditorSubmission(title = "Ask HN", text = "Question")
                .validate(EditorType.POST, 80).canSubmit,
        )
    }

    @Test
    fun titleLengthIsReportedSeparatelyFromSubmissionValidity() {
        val validation = EditorSubmission(title = "123456", text = "body")
            .validate(EditorType.POST, titleMaxLength = 5)

        assertTrue(validation.titleTooLong)
        assertFalse(validation.canSubmit)
    }

    @Test
    fun commentAndReplyOnlyRequireCommentText() {
        assertFalse(
            EditorSubmission().validate(EditorType.TOP_LEVEL_COMMENT, 80).canSubmit,
        )
        assertTrue(
            EditorSubmission(comment = "Reply")
                .validate(EditorType.COMMENT_REPLY, 80).canSubmit,
        )
    }
}
