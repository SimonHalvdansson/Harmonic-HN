package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals

class AiSummaryProvidersTest {
    @Test
    fun anthropicModelIdsAreFullyDashDelimited() {
        assertEquals(
            "claude-haiku-5-5",
            AiSummaryProviders.toProviderModelId(
                AiSummaryProviders.ANTHROPIC,
                "anthropic/claude-haiku-5.5",
            ),
        )
        assertEquals(
            "claude-haiku-5-5",
            AiSummaryProviders.getModelForRequest(
                "https://api.anthropic.com/v1",
                "anthropic/claude-haiku-5.5:free",
            ),
        )
    }

    @Test
    fun anthropicModelIdsMapBackToOpenRouterDots() {
        assertEquals(
            "anthropic/claude-haiku-5.5",
            AiSummaryProviders.toOpenRouterModelId(AiSummaryProviders.ANTHROPIC, "claude-haiku-5-5"),
        )
        assertEquals(
            "anthropic/claude-sonnet-4.5-20250929",
            AiSummaryProviders.toOpenRouterModelId(
                AiSummaryProviders.ANTHROPIC,
                "claude-sonnet-4-5-20250929",
            ),
        )
        assertEquals(
            "anthropic/claude-opus-4",
            AiSummaryProviders.toOpenRouterModelId(AiSummaryProviders.ANTHROPIC, "claude-opus-4"),
        )
    }

    @Test
    fun switchingFromAnthropicToOpenRouterRestoresCanonicalId() {
        assertEquals(
            "anthropic/claude-haiku-5.5",
            AiSummaryProviders.translateModelId(
                AiSummaryProviders.ANTHROPIC,
                AiSummaryProviders.defaultProvider,
                "claude-haiku-5-5",
            ),
        )
    }

    @Test
    fun otherProvidersKeepDotsInModelIds() {
        assertEquals(
            "gpt-5.1",
            AiSummaryProviders.toProviderModelId(AiSummaryProviders.OPENAI, "openai/gpt-5.1"),
        )
        assertEquals(
            "claude-haiku-5.5",
            AiSummaryProviders.getModelForRequest(
                AiSummaryProviders.defaultBaseUrl,
                "claude-haiku-5.5",
            ),
        )
    }
}
