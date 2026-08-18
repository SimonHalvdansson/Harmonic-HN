package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenRouterLinkPreviewTest {
    @Test
    fun recognizesModelPagesAndIgnoresOtherOpenRouterRoutes() {
        assertEquals(
            OpenRouterModel("openai", "gpt-5.6-sol"),
            LinkPreviewUrls.openRouterModel("https://openrouter.ai/openai/gpt-5.6-sol"),
        )
        assertEquals(
            OpenRouterModel("openai", "gpt-5.6-sol"),
            LinkPreviewUrls.openRouterModel("https://www.openrouter.ai/openai/gpt-5.6-sol/"),
        )
        assertNull(LinkPreviewUrls.openRouterModel("https://openrouter.ai/models"))
        assertNull(LinkPreviewUrls.openRouterModel("https://openrouter.ai/openai"))
        assertFalse(LinkPreviewUrls.isOpenRouterUrl("https://example.com/openai/gpt-5.6-sol"))
    }

    @Test
    fun parsesAndFormatsModelApiFields() {
        val info = LinkPreviewParsers.parseOpenRouter(
            """
            {
              "data": {
                "id":"openai/gpt-5.6-sol",
                "name":"OpenAI: GPT-5.6 Sol",
                "description":"GPT-5.6 Sol is the flagship model in OpenAI's GPT-5.6 series.",
                "context_length":1050000,
                "architecture":{
                  "input_modalities":["file","image","text"],
                  "output_modalities":["text"]
                },
                "pricing":{
                  "prompt":"0.0000025",
                  "completion":"0.000015"
                },
                "top_provider":{
                  "max_completion_tokens":128000
                },
                "knowledge_cutoff":"2026-02-16"
              }
            }
            """.trimIndent(),
        )

        assertEquals("OpenAI", info.provider)
        assertEquals("GPT-5.6 Sol", info.name)
        assertEquals("https://openrouter.ai/openai/gpt-5.6-sol", info.website)
        assertEquals("https://openrouter.ai/images/icons/OpenAI.svg", info.providerIconUrl)
        assertEquals("\$2.50/M input", info.formatPromptPrice())
        assertEquals("\$15/M output", info.formatCompletionPrice())
        assertEquals("1.05M context", info.formatContext())
        assertEquals("128K max output", info.formatMaxOutput())
        assertEquals("Text + image + file → text", info.formatModalities())
        assertEquals("Knowledge Feb 2026", info.formatKnowledgeCutoff())
        assertTrue(LinkPreviewUrls.isOpenRouterUrl(info.website))
    }
}
