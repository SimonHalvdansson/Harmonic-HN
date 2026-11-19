package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSummaryRequestTest {
    @Test
    fun invalidKeysProduceReadableErrorsForEveryProviderInBothModes() = runTest {
        // Response shapes observed from real requests with the deliberately invalid key 234673.
        val cases = listOf(
            Triple(AiSummaryProviders.OPENAI,
                """{"error":{"message":"Incorrect API key provided: 234673. You can find your API key at https://platform.openai.com/account/api-keys.","type":"invalid_request_error","param":null,"code":"invalid_api_key"}}""",
                "Incorrect API key provided: 234673. You can find your API key at https://platform.openai.com/account/api-keys."),
            Triple(AiSummaryProviders.ANTHROPIC,
                """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"},"request_id":"test-request"}""",
                "invalid x-api-key"),
            Triple(AiSummaryProviders.defaultProvider,
                """{"error":{"message":"Missing Authentication header","code":401}}""",
                "Missing Authentication header"),
            Triple(AiSummaryProviders.GOOGLE,
                """[{"error":{"code":400,"message":"Please pass a valid API key","status":"INVALID_ARGUMENT"}}]""",
                "Please pass a valid API key"),
        )
        for (stream in listOf(false, true)) for ((provider, body, message) in cases) {
            val status = if (provider == AiSummaryProviders.GOOGLE) 400 else 401
            var requestedUrl: String? = null
            var sentKey: String? = null
            var sentVersion: String? = null
            val anthropic = provider == AiSummaryProviders.ANTHROPIC
            val transport = HttpClient(MockEngine { request ->
                requestedUrl = request.url.toString()
                sentKey = request.headers[if (anthropic) "x-api-key" else "Authorization"]
                sentVersion = request.headers["anthropic-version"]
                respond(body, HttpStatusCode(status, ""))
            })
            try {
                val error = assertFailsWith<CloudSummaryException> {
                    KtorCloudSummaryRepository(KtorHttpClient(transport), "test", StandardTestDispatcher(testScheduler))
                        .summarize(CloudSummaryConfig(provider.baseUrl, "234673", "model", streamResponses = stream), "Article").toList()
                }
                assertEquals("API error (HTTP $status): $message", error.message)
                assertEquals(provider.baseUrl + if (anthropic) "/messages" else "/chat/completions", requestedUrl)
                assertEquals(if (anthropic) "234673" else "Bearer 234673", sentKey)
                if (anthropic) assertEquals("2023-06-01", sentVersion)
            } finally { transport.close() }
        }
    }

    @Test
    fun httpErrorsPreserveProviderMessagesAndAlwaysIncludeStatus() = runTest {
        val cases = listOf(
            """[{"error":{"message":"API key not valid."}}]""" to "API key not valid.",
            """[null,{},{"error":{"message":"API key not valid."}}]""" to "API key not valid.",
            """{"error":{"message":"Invalid authentication"}}""" to "Invalid authentication",
            """{"error":"Rate limited"}""" to "Rate limited",
            """{"message":"Unavailable"}""" to "Unavailable",
            """{"error":{"message":"  "},"message":"Fallback message"}""" to "Fallback message",
            """{"error":{"message":""}}""" to "Request failed",
            "[]" to "Request failed",
            "<html>Error</html>" to "Request failed",
            "" to "Request failed",
        )
        for (stream in listOf(false, true)) for ((body, expected) in cases) {
            val transport = HttpClient(MockEngine { respond(body, HttpStatusCode(400, "")) })
            try {
                val error = assertFailsWith<CloudSummaryException> {
                    KtorCloudSummaryRepository(KtorHttpClient(transport), "test", StandardTestDispatcher(testScheduler))
                        .summarize(CloudSummaryConfig(AiSummaryProviders.GOOGLE.baseUrl, "fake", "model", streamResponses = stream), "Article").toList()
                }
                assertEquals("API error (HTTP 400): $expected", error.message)
            } finally { transport.close() }
        }
    }

    @Test
    fun onlyAnthropicReceivesAnExplicitTokenLimit() = runTest {
        for (provider in AiSummaryProviders.PROVIDERS) {
            var payload = ""
            val transport = HttpClient(MockEngine { request ->
                payload = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                respond("", HttpStatusCode.BadRequest)
            })
            try {
                assertFailsWith<CloudSummaryException> {
                    KtorCloudSummaryRepository(KtorHttpClient(transport), "test", StandardTestDispatcher(testScheduler))
                        .summarize(CloudSummaryConfig(provider.baseUrl, "fake", "model"), "Article").toList()
                }
                val json = Json.parseToJsonElement(payload).jsonObject
                if (provider == AiSummaryProviders.ANTHROPIC) {
                    assertEquals("6000", json["max_tokens"]?.jsonPrimitive?.content)
                } else {
                    assertFalse("max_tokens" in json)
                }
                assertFalse("max_completion_tokens" in json)
                assertFalse("reasoning_effort" in json)
            } finally { transport.close() }
        }
    }
}
