package com.simon.harmonichackernews.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalSummaryGenerationPolicyTest {
    @Test
    fun qwenGenerationStartsWithBulletPrefix() {
        val configuration = LocalSummaryGenerationPolicy.configuration(
            LocalModelCatalog.MODEL_QWEN_08B,
        )

        assertEquals("- ", configuration.responsePrefix)
        assertEquals(256, configuration.maxOutputTokens)
    }

    @Test
    fun otherModelsDoNotReceiveResponsePrefix() {
        assertEquals(
            "",
            LocalSummaryGenerationPolicy.configuration(
                LocalModelCatalog.MODEL_BONSAI_17B,
            ).responsePrefix,
        )
    }

    @Test
    fun reasoningIsHiddenUntilClosingTagArrives() {
        assertNull(LocalSummaryGenerationPolicy.visibleOutput(""))
        assertNull(LocalSummaryGenerationPolicy.visibleOutput("<thi"))
        assertNull(LocalSummaryGenerationPolicy.visibleOutput("  <think>working"))
        assertEquals(
            "- First point",
            LocalSummaryGenerationPolicy.visibleOutput(
                "<think>private reasoning</think>\n- First point",
            ),
        )
    }

    @Test
    fun ordinaryOutputIsVisibleImmediately() {
        assertEquals(
            "Summary text",
            LocalSummaryGenerationPolicy.visibleOutput("  Summary text"),
        )
    }
}
