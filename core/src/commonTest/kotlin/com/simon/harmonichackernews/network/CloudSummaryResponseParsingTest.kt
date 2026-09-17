package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class CloudSummaryResponseParsingTest {
    @Test
    fun ignoresNullContentInOpenAiStreamingChunk() {
        val event = Json.parseToJsonElement(
            """{"choices":[{"delta":{"role":"assistant","content":null}}]}""",
        ).jsonObject

        assertEquals("", parseCloudSummaryStreamChunk(event, anthropic = false))
    }

    @Test
    fun ignoresNullContentInOpenAiResponse() {
        val response = Json.parseToJsonElement(
            """{"choices":[{"message":{"content":null}}]}""",
        ).jsonObject

        assertEquals("", parseCloudSummaryResponseText(response, anthropic = false))
    }
}
