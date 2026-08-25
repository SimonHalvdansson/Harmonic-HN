package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToLong

data class CloudSummaryConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String = CloudSummaryDefaults.SYSTEM_PROMPT,
    val streamResponses: Boolean = true,
)

sealed interface CloudSummaryEvent {
    data class DebugInfo(val value: String) : CloudSummaryEvent
    data class Progress(val summary: String) : CloudSummaryEvent
    data class Success(val summary: String) : CloudSummaryEvent
}

interface CloudSummaryRepository {
    suspend fun fetchModelIds(baseUrl: String, apiKey: String): List<String>
    suspend fun extractMainContent(url: String): String
    fun summarize(config: CloudSummaryConfig, text: String?): Flow<CloudSummaryEvent>
}

class KtorCloudSummaryRepository(
    private val client: KtorHttpClient,
    private val articleUserAgent: String,
) : CloudSummaryRepository {
    override suspend fun fetchModelIds(baseUrl: String, apiKey: String): List<String> {
        val requestBuilder = HttpRequest.Builder().url(joinUrl(baseUrl, "models"))
        if (apiKey.isNotBlank()) requestBuilder.header("Authorization", "Bearer $apiKey")
        val response = client.execute(requestBuilder.get().build())
        return try {
            val body = response.body.readText(MAX_MODEL_CATALOG_BYTES)
            if (!response.isSuccessful) throw CloudSummaryException(
                "Could not load models (HTTP ${response.code})",
            )
            runCatching {
                jsonParser.parseToJsonElement(body).jsonObject["data"]
                    ?.jsonArray
                    .orEmpty()
                    .mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
            }.getOrElse { throw CloudSummaryException("Failed to parse models", it) }
        } finally {
            response.close()
        }
    }

    override suspend fun extractMainContent(url: String): String {
        val request = HttpRequest.Builder()
            .url(url)
            .header("User-Agent", articleUserAgent)
            .get()
            .build()
        val response = client.newBuilder().readTimeoutMillis(10_000).build().execute(request)
        return try {
            if (!response.isSuccessful) {
                throw CloudSummaryException("Article returned HTTP ${response.code}")
            }
            Ksoup.parse(response.body.readText(MAX_ARTICLE_BYTES), baseUri = url).body().text()
        } finally {
            response.close()
        }
    }

    override fun summarize(
        config: CloudSummaryConfig,
        text: String?,
    ): Flow<CloudSummaryEvent> = flow {
        if (config.apiKey.isBlank()) throw CloudSummaryException("API Key missing")
        val model = AiSummaryProviders.getModelForRequest(config.baseUrl, config.model)
        if (model.isBlank()) {
            throw CloudSummaryException(
                "Model missing. Open AI summarization settings and choose a model.",
            )
        }
        emit(CloudSummaryEvent.DebugInfo("$model · load —"))

        val anthropic = AiSummaryProviders.isAnthropicBaseUrl(config.baseUrl)
        val payload = buildPayload(
            anthropic = anthropic,
            model = model,
            prompt = config.systemPrompt,
            text = prepareInput(text),
            stream = config.streamResponses,
        )
        val requestBuilder = HttpRequest.Builder()
            .url(joinUrl(config.baseUrl, if (anthropic) "messages" else "chat/completions"))
            .header(
                "Accept",
                if (config.streamResponses) "text/event-stream" else "application/json",
            )
        if (anthropic) {
            requestBuilder
                .header("x-api-key", config.apiKey)
                .header("anthropic-version", "2023-06-01")
        } else {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
        }
        val request = requestBuilder.post(
            payload.toString().toHttpRequestBody(
                "application/json; charset=utf-8".toHttpMediaType(),
            ),
        ).build()
        val response = client.newBuilder().readTimeoutMillis(120_000).build().execute(request)
        try {
            if (!response.isSuccessful) {
                val errorBody = response.body.readText(MAX_ERROR_BODY_BYTES)
                throw CloudSummaryException(
                    "API error: ${apiErrorMessage(errorBody, response.message)}",
                )
            }
            if (config.streamResponses) {
                readStream(response.body, anthropic) { summary ->
                    emit(CloudSummaryEvent.Progress(summary))
                }.also { emit(CloudSummaryEvent.Success(it)) }
            } else {
                val summary = parseNonStreamingResponse(
                    response.body.readText(MAX_SUMMARY_RESPONSE_BYTES),
                    anthropic,
                )
                if (summary.isBlank()) throw CloudSummaryException("API response error")
                emit(CloudSummaryEvent.Success(summary))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: CloudSummaryException) {
            throw error
        } catch (error: Throwable) {
            throw CloudSummaryException("API error: ${error.readableMessage()}", error)
        } finally {
            response.close()
        }
    }

    private suspend fun readStream(
        body: HttpResponseBody,
        anthropic: Boolean,
        onProgress: suspend (String) -> Unit,
    ): String {
        val summary = StringBuilder()
        val eventData = StringBuilder()
        val plainResponse = StringBuilder()
        var sawSseData = false
        var complete = false
        while (!complete) {
            val line = body.readUtf8Line() ?: break
            when {
                line.isEmpty() -> if (eventData.isNotEmpty()) {
                    sawSseData = true
                    complete = appendStreamEvent(
                        eventData.toString(),
                        anthropic,
                        summary,
                        onProgress,
                    )
                    eventData.clear()
                }

                line.startsWith("data:") -> {
                    if (eventData.isNotEmpty()) eventData.append('\n')
                    eventData.append(line.substring(5).trim())
                    if (eventData.length > MAX_STREAM_EVENT_CHARS) {
                        throw CloudSummaryException("Streaming event exceeded the response limit")
                    }
                }

                !line.startsWith(":") -> {
                    if (plainResponse.isNotEmpty()) plainResponse.append('\n')
                    plainResponse.append(line)
                    if (plainResponse.length > MAX_SUMMARY_RESPONSE_BYTES) {
                        throw CloudSummaryException("API response exceeded the response limit")
                    }
                }
            }
        }
        if (!complete && eventData.isNotEmpty()) {
            sawSseData = true
            appendStreamEvent(eventData.toString(), anthropic, summary, onProgress)
        }
        if (!sawSseData && summary.isEmpty() && plainResponse.isNotEmpty()) {
            appendChunk(
                summary,
                parseNonStreamingResponse(plainResponse.toString(), anthropic),
                onProgress,
            )
        }
        if (summary.isEmpty()) throw CloudSummaryException("API response error")
        return summary.toString()
    }

    private suspend fun appendStreamEvent(
        data: String,
        anthropic: Boolean,
        summary: StringBuilder,
        onProgress: suspend (String) -> Unit,
    ): Boolean {
        if (data == "[DONE]") return true
        val event = parseCloudSummaryObject(data, "Invalid streaming response")
        if (event.string("type") == "error" || "error" in event) {
            throw CloudSummaryException(apiErrorMessage(data, "Streaming request failed"))
        }
        val chunk = parseCloudSummaryStreamChunk(event, anthropic)
        appendChunk(summary, chunk, onProgress)
        return false
    }

    private fun parseNonStreamingResponse(responseBody: String, anthropic: Boolean): String =
        parseCloudSummaryResponseText(
            parseCloudSummaryObject(responseBody, "Invalid API response"),
            anthropic,
        )

    private suspend fun appendChunk(
        summary: StringBuilder,
        chunk: String,
        onProgress: suspend (String) -> Unit,
    ) {
        if (chunk.isEmpty()) return
        if (summary.length + chunk.length > MAX_SUMMARY_RESPONSE_BYTES) {
            throw CloudSummaryException("Summary exceeded the response limit")
        }
        summary.append(chunk)
        onProgress(summary.toString())
    }

    private fun buildPayload(
        anthropic: Boolean,
        model: String,
        prompt: String,
        text: String,
        stream: Boolean,
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("stream", stream)
        if (anthropic) {
            put("max_tokens", CLOUD_SUMMARY_MAX_OUTPUT_TOKENS)
            put("system", prompt)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", text)
                })
            })
        } else {
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", prompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", text)
                })
            })
        }
    }

    private fun apiErrorMessage(body: String?, fallback: String): String {
        if (body.isNullOrBlank()) return fallback
        return runCatching {
            val responseJson = jsonParser.parseToJsonElement(body).jsonObject
            when (val error = responseJson["error"]) {
                is JsonObject -> error.string("message") ?: fallback
                is JsonPrimitive -> error.contentOrNull ?: fallback
                else -> responseJson.string("message") ?: fallback
            }
        }.getOrDefault(fallback)
    }

    private companion object {
        const val CLOUD_SUMMARY_MAX_OUTPUT_TOKENS = 1000
        const val MAX_MODEL_CATALOG_BYTES = 2 * 1024 * 1024
        const val MAX_ARTICLE_BYTES = 4 * 1024 * 1024
        const val MAX_ERROR_BODY_BYTES = 128 * 1024
        const val MAX_SUMMARY_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_STREAM_EVENT_CHARS = 256 * 1024
    }
}

/** Extracts a provider response chunk, treating an explicit JSON null as no content. */
internal fun parseCloudSummaryStreamChunk(event: JsonObject, anthropic: Boolean): String =
    if (anthropic) {
        event.objectValue("delta")?.string("text").orEmpty()
    } else {
        event.arrayValue("choices")
            ?.firstOrNull()
            ?.asObject()
            ?.objectValue("delta")
            ?.string("content")
            .orEmpty()
    }

/** Extracts a complete provider response, treating an explicit JSON null as no content. */
internal fun parseCloudSummaryResponseText(response: JsonObject, anthropic: Boolean): String =
    if (anthropic) {
        response.arrayValue("content")
            .orEmpty()
            .mapNotNull { it.asObject()?.string("text")?.takeIf(String::isNotEmpty) }
            .joinToString("\n")
    } else {
        response.arrayValue("choices")
            ?.firstOrNull()
            ?.asObject()
            ?.objectValue("message")
            ?.string("content")
            .orEmpty()
    }

private fun parseCloudSummaryObject(value: String, errorMessage: String): JsonObject = runCatching {
    jsonParser.parseToJsonElement(value).jsonObject
}.getOrElse { throw CloudSummaryException(errorMessage, it) }

private val jsonParser = Json { ignoreUnknownKeys = true }

object CloudSummaryDefaults {
    const val SYSTEM_PROMPT =
        "You are a helpful assistant that is an expert on summarizing articles into an information-dense, concise and brief bullet-point list. Focus on key takeaways and most important/note-worthy points in the article. Keep the summary under 500 characters where possible. Respond in markdown format. Respond with only the summarized content - nothing else before or after."
}

class CloudSummaryException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

object SummaryFormatting {
    fun formatLoadInfo(modelName: String?, loadMillis: Long): String = if (loadMillis < 1_000L) {
        "$modelName · $loadMillis ms load"
    } else {
        "$modelName · ${formatOneDecimal(loadMillis / 1_000.0)} s load"
    }

    private fun formatOneDecimal(value: Double): String =
        ((value * 10.0).roundToLong() / 10.0).toString()
}

private fun prepareInput(text: String?): String = text.orEmpty().trim().take(15_000)

private fun joinUrl(baseUrl: String?, path: String): String =
    AiSummaryProviders.normalizeUrl(baseUrl) + "/" + path

private fun Throwable.readableMessage(): String = message?.takeIf(String::isNotEmpty) ?: "Unknown error"

private fun JsonObject.string(key: String): String? =
    this[key]?.let { it as? JsonPrimitive }?.contentOrNull

private fun JsonObject.objectValue(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.arrayValue(key: String): JsonArray? = this[key] as? JsonArray

private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
