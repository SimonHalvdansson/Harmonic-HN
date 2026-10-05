package com.simon.harmonichackernews.ui.comments

import kotlin.test.Test
import kotlin.test.assertEquals

class CommentDiscussionErrorTest {
    @Test
    fun setupErrorsOfferSettingsInsteadOfRetry() {
        listOf(
            "API Key missing",
            "Model missing. Open AI summarization settings and choose a model.",
            "No local AI model is available. Configure AI in Settings.",
            "Local summarization failed: Download the selected local model before using it",
            "Local summarization failed: Install the selected model runtime before using it",
            "Local summarization failed: Gemini Nano's custom prompt feature is unavailable on this device.",
        ).forEach { error ->
            assertEquals(DiscussionErrorAction.OpenSettings, discussionErrorPresentation(error, false).action, error)
        }
    }

    @Test
    fun contextLimitOffersReset() {
        assertEquals(DiscussionErrorAction.Reset, discussionErrorPresentation("Too many tokens", true).action)
    }

    @Test
    fun otherFailuresPreserveTheirDetailsAndOfferRetry() {
        listOf("API error: Service unavailable", "Invalid streaming response",
            "Local summarization failed: Inference interrupted").forEach { error ->
            val presentation = discussionErrorPresentation(error, false)
            assertEquals(error, presentation.message)
            assertEquals(DiscussionErrorAction.Retry, presentation.action)
        }
    }
}
