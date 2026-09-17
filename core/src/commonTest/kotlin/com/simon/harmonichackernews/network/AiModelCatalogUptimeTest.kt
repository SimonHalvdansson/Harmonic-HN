package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AiModelCatalogUptimeTest {
    @Test
    fun usesBestActiveEndpointAndFiltersDirectProviders() = runTest {
        var requestCount = 0
        val transport = HttpClient(MockEngine { request ->
            requestCount++
            assertEquals(
                "https://openrouter.ai/api/v1/models/google/test-model/endpoints",
                request.url.toString(),
            )
            respond(
                content = """
                    {
                      "data": {
                        "endpoints": [
                          {
                            "provider_name": "Google",
                            "status": 0,
                            "uptime_last_1d": 99.5
                          },
                          {
                            "provider_name": "Google AI Studio",
                            "status": 0,
                            "uptime_last_1d": 99.8
                          },
                          {
                            "provider_name": "Unavailable provider",
                            "status": -5,
                            "uptime_last_1d": 100
                          }
                        ]
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        })
        val repository = KtorAiModelCatalogRepository(KtorHttpClient(transport))

        assertEquals(
            99.8,
            repository.fetchUptimeLastDay(
                AiSummaryProviders.defaultProvider,
                "google/test-model",
            ),
        )
        assertEquals(
            99.8,
            repository.fetchUptimeLastDay(AiSummaryProviders.GOOGLE, "google/test-model"),
        )
        assertEquals(
            99.8,
            repository.fetchUptimeLastDay(AiSummaryProviders.GOOGLE, "google/test-model"),
        )
        assertEquals(2, requestCount)

        transport.close()
    }
}
