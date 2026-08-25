package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CloudSummaryRepositoryCancellationTest {
    @Test
    fun responseReadCancellationIsNotWrappedAsProviderFailure() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val transport = HttpClient(MockEngine {
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })
        val repository = KtorCloudSummaryRepository(KtorHttpClient(transport), "test")

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(1) {
                repository.summarize(
                    CloudSummaryConfig(
                        baseUrl = "https://api.openai.com/v1",
                        apiKey = "test-key",
                        model = "test-model",
                        streamResponses = false,
                    ),
                    text = "Summarize this",
                ).collect()
            }
        }

        transport.close()
    }
}
