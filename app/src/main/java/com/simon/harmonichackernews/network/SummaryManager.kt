package com.simon.harmonichackernews.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import androidx.preference.PreferenceManager
import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.simon.harmonichackernews.network.AiSummaryProviders.defaultBaseUrl
import com.simon.harmonichackernews.network.AiSummaryProviders.getModelForRequest
import com.simon.harmonichackernews.network.AiSummaryProviders.isAnthropicBaseUrl
import com.simon.harmonichackernews.network.AiSummaryProviders.normalizeUrl
import com.simon.harmonichackernews.network.NetworkComponent.okHttpClientInstance
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup

object SummaryManager {
    private const val CLOUD_SUMMARY_MAX_OUTPUT_TOKENS = 1000
    private const val PREF_STREAM_RESPONSES = "pref_ai_summary_stream_responses"
    private const val DEFAULT_SYSTEM_PROMPT =
        "You are a helpful assistant that is an expert on summarizing articles into an information-dense, concise and brief bullet-point list. Focus on key takeaways and most important/note-worthy points in the article. Keep the summary under 500 characters where possible. Respond in markdown format. Respond with only the summarized content - nothing else before or after."
    private val MAIN_HANDLER = Handler(Looper.getMainLooper())

    fun fetchModels(ctx: Context, queue: RequestQueue, callback: SummaryCallback) {
        val baseUrl = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getString("pref_ai_summary_base_url", defaultBaseUrl)
        val apiKey = AiSummaryApiKeyStore.getApiKey(ctx)
        val url = joinUrl(baseUrl, "models")

        val request: JsonObjectRequest = object : JsonObjectRequest(
            com.android.volley.Request.Method.GET, url, null,
            com.android.volley.Response.Listener { response: JSONObject? ->
                try {
                    val models = response?.getJSONArray("data")
                        ?: throw JSONException("Missing models response")
                    val modelNames = mutableListOf<String>()
                    for (i in 0..<models.length()) {
                        modelNames.add(models.getJSONObject(i).getString("id"))
                    }
                    callback.onSuccess(modelNames.joinToString(","))
                } catch (e: JSONException) {
                    callback.onFailure("Failed to parse models")
                }
            },
            com.android.volley.Response.ErrorListener { error: VolleyError? -> callback.onFailure(error?.message ?: "Unknown error") }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = mutableMapOf<String, String>()
                if (apiKey.isNotEmpty()) {
                    headers["Authorization"] = "Bearer $apiKey"
                }
                return headers
            }
        }

        queue.add<JSONObject?>(request)
    }

    fun summarizeArticle(
        ctx: Context,
        queue: RequestQueue?,
        articleUrl: String,
        callback: SummaryCallback
    ) {
        Thread {
            try {
                summarizeText(ctx, queue, extractMainContent(articleUrl), callback)
            } catch (e: Exception) {
                postFailure(callback, "Extraction failed: " + getThrowableMessage(e))
            }
        }.start()
    }

    fun summarizeText(
        ctx: Context,
        queue: RequestQueue?,
        text: String?,
        callback: SummaryCallback
    ) {
        summarizeWithLLM(ctx, prepareCloudSummaryInput(text), callback)
    }

    fun canAttemptLocalSummarization(): Boolean {
        return LocalSummaryManager.canAttemptLocalSummarization()
    }

    fun checkLocalSummaryAvailability(ctx: Context?, callback: LocalSummaryAvailabilityCallback?) {
        LocalSummaryManager.checkLocalSummaryAvailability(ctx, callback)
    }

    fun summarizeArticleWithGeminiNano(
        ctx: Context?,
        articleUrl: String?,
        callback: SummaryCallback?
    ) {
        LocalSummaryManager.summarizeArticle(ctx, articleUrl, callback)
    }

    fun summarizeTextWithGeminiNano(
        ctx: Context?,
        text: String?,
        callback: SummaryCallback?
    ) {
        LocalSummaryManager.summarizeText(ctx, text, callback)
    }

    fun isLocalSummaryReady(context: Context?): Boolean {
        return LocalSummaryManager.isLocalSummaryReady(context)
    }

    fun isLocalSummaryConfigurationKnown(context: Context?): Boolean {
        return LocalSummaryManager.isLocalSummaryConfigurationKnown(context)
    }

    @Throws(IOException::class)
    fun extractMainContent(url: String): String {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .timeout(10000)
            .get()
        val body = doc.body()
        return body.text()
    }

    private fun prepareCloudSummaryInput(text: String?): String {
        val normalized = text.orEmpty().trim { it <= ' ' }
        if (normalized.length > 15000) {
            return normalized.substring(0, 15000)
        }
        return normalized
    }

    fun getThrowableMessage(throwable: Throwable?): String {
        return throwable?.message?.takeUnless(String::isEmpty) ?: "Unknown error"
    }

    private fun summarizeWithLLM(
        ctx: Context,
        text: String?,
        callback: SummaryCallback
    ) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        val apiKey = AiSummaryApiKeyStore.getApiKey(ctx)
        val baseUrl = preferences.getString("pref_ai_summary_base_url", defaultBaseUrl)
        val model = getModelForRequest(
            baseUrl,
            preferences.getString("pref_ai_summary_model", "")
        )

        if (apiKey.isEmpty()) {
            postFailure(callback, "API Key missing")
            return
        }
        if (model.isEmpty()) {
            postFailure(
                callback,
                "Model missing. Open AI summarization settings and choose a model."
            )
            return
        }
        postDebugInfo(callback, model + " · load —")

        val prompt = preferences.getString(
            "pref_ai_summary_system_prompt",
            DEFAULT_SYSTEM_PROMPT
        ) ?: DEFAULT_SYSTEM_PROMPT
        val streamResponses = preferences.getBoolean(PREF_STREAM_RESPONSES, true)
        if (isAnthropicBaseUrl(baseUrl)) {
            summarizeWithAnthropic(
                baseUrl, apiKey, model, prompt, text,
                streamResponses, callback
            )
        } else {
            summarizeWithChatCompletions(
                baseUrl, apiKey, model, prompt, text,
                streamResponses, callback
            )
        }
    }

    private fun summarizeWithChatCompletions(
        baseUrl: String?,
        apiKey: String,
        model: String,
        prompt: String,
        text: String?,
        streamResponses: Boolean,
        callback: SummaryCallback
    ) {
        val url = joinUrl(baseUrl, "chat/completions")

        val payload = JSONObject()
        try {
            payload.put("model", model)
            payload.put("stream", streamResponses)
            val messages = JSONArray()

            val systemMsg = JSONObject()
            systemMsg.put("role", "system")
            systemMsg.put("content", prompt)

            val userMsg = JSONObject()
            userMsg.put("role", "user")
            userMsg.put("content", text)

            messages.put(systemMsg)
            messages.put(userMsg)
            payload.put("messages", messages)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        requestSummary(
            url, payload, Request.Builder()
                .header("Authorization", "Bearer " + apiKey), false,
            streamResponses, callback
        )
    }

    private fun summarizeWithAnthropic(
        baseUrl: String?,
        apiKey: String,
        model: String,
        prompt: String,
        text: String?,
        streamResponses: Boolean,
        callback: SummaryCallback
    ) {
        val url = joinUrl(baseUrl, "messages")

        val payload = JSONObject()
        try {
            payload.put("model", model)
            payload.put("max_tokens", CLOUD_SUMMARY_MAX_OUTPUT_TOKENS)
            payload.put("system", prompt)
            payload.put("stream", streamResponses)

            val messages = JSONArray()
            val userMsg = JSONObject()
            userMsg.put("role", "user")
            userMsg.put("content", text)
            messages.put(userMsg)
            payload.put("messages", messages)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        requestSummary(
            url, payload, Request.Builder()
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01"), true,
            streamResponses, callback
        )
    }

    private fun requestSummary(
        url: String,
        payload: JSONObject,
        requestBuilder: Request.Builder,
        anthropic: Boolean,
        streamResponses: Boolean,
        callback: SummaryCallback
    ) {
        val requestBody: RequestBody =
            payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = requestBuilder
            .url(url)
            .header("Accept", if (streamResponses) "text/event-stream" else "application/json")
            .post(requestBody)
            .build()
        val client = okHttpClientInstance.newBuilder()
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                postFailure(callback, "API error: " + getThrowableMessage(e))
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                try {
                    response.body.use { body ->
                        if (!response.isSuccessful) {
                            val errorBody = body.string()
                            postFailure(
                                callback, "API error: "
                                        + getApiErrorMessage(errorBody, response.message)
                            )
                            return
                        }
                        if (streamResponses) {
                            readSummaryStream(body, anthropic, callback)
                        } else {
                            readSummaryResponse(body, anthropic, callback)
                        }
                    }
                } catch (e: IOException) {
                    postFailure(callback, "API error: " + getThrowableMessage(e))
                }
            }
        })
    }

    @Throws(IOException::class)
    private fun readSummaryResponse(
        body: ResponseBody,
        anthropic: Boolean,
        callback: SummaryCallback
    ) {
        val summary = parseNonStreamingResponse(body.string(), anthropic)
        if (TextUtils.isEmpty(summary)) {
            postFailure(callback, "API response error")
        } else {
            postSuccess(callback, summary)
        }
    }

    @Throws(IOException::class)
    private fun readSummaryStream(
        body: ResponseBody,
        anthropic: Boolean,
        callback: SummaryCallback
    ) {
        val summary = StringBuilder()
        val eventData = StringBuilder()
        val plainResponse = StringBuilder()
        var sawSseData = false

        BufferedReader(
            InputStreamReader(body.byteStream(), StandardCharsets.UTF_8)
        ).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) {
                    if (eventData.isNotEmpty()) {
                        sawSseData = true
                        if (appendStreamEvent(eventData.toString(), anthropic, summary, callback)) {
                            eventData.setLength(0)
                            break
                        }
                        eventData.setLength(0)
                    }
                } else if (line.startsWith("data:")) {
                    if (eventData.isNotEmpty()) {
                        eventData.append('\n')
                    }
                    eventData.append(line.substring(5).trim { it <= ' ' })
                } else if (!line.startsWith(":")) {
                    if (plainResponse.isNotEmpty()) {
                        plainResponse.append('\n')
                    }
                    plainResponse.append(line)
                }
            }
        }
        if (eventData.isNotEmpty()) {
            sawSseData = true
            appendStreamEvent(eventData.toString(), anthropic, summary, callback)
        }

        if (!sawSseData && summary.isEmpty() && plainResponse.isNotEmpty()) {
            appendNonStreamingResponse(plainResponse.toString(), anthropic, summary, callback)
        }

        if (summary.isEmpty()) {
            postFailure(callback, "API response error")
        } else {
            postSuccess(callback, summary.toString())
        }
    }

    @Throws(IOException::class)
    private fun appendStreamEvent(
        data: String,
        anthropic: Boolean,
        summary: StringBuilder,
        callback: SummaryCallback
    ): Boolean {
        if ("[DONE]" == data) {
            return true
        }

        try {
            val event = JSONObject(data)
            if ("error" == event.optString("type") || event.has("error")) {
                throw IOException(getApiErrorMessage(data, "Streaming request failed"))
            }

            val chunk: String?
            if (anthropic) {
                chunk = event.optJSONObject("delta")?.optString("text", "").orEmpty()
            } else {
                chunk = event.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("delta")
                    ?.optString("content", "")
                    .orEmpty()
            }

            appendSummaryChunk(summary, chunk, callback)
            return false
        } catch (e: JSONException) {
            throw IOException("Invalid streaming response", e)
        }
    }

    @Throws(IOException::class)
    private fun appendNonStreamingResponse(
        responseBody: String,
        anthropic: Boolean,
        summary: StringBuilder,
        callback: SummaryCallback
    ) {
        appendSummaryChunk(summary, parseNonStreamingResponse(responseBody, anthropic), callback)
    }

    @Throws(IOException::class)
    private fun parseNonStreamingResponse(
        responseBody: String,
        anthropic: Boolean
    ): String? {
        try {
            val response = JSONObject(responseBody)
            if (anthropic) {
                return parseAnthropicSummary(response)
            }
            return response.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                .orEmpty()
        } catch (e: JSONException) {
            throw IOException("Invalid API response", e)
        }
    }

    private fun appendSummaryChunk(
        summary: StringBuilder,
        chunk: String?,
        callback: SummaryCallback
    ) {
        if (TextUtils.isEmpty(chunk)) {
            return
        }
        summary.append(chunk)
        postProgress(callback, summary.toString())
    }

    private fun parseAnthropicSummary(response: JSONObject): String? {
        val content = response.optJSONArray("content")
        if (content == null) {
            return null
        }

        val summary = StringBuilder()
        for (i in 0..<content.length()) {
            val block = content.optJSONObject(i)
            if (block == null) {
                continue
            }
            val text = block.optString("text", "")
            if (text.isNotEmpty()) {
                if (summary.isNotEmpty()) {
                    summary.append("\n")
                }
                summary.append(text)
            }
        }
        return summary.toString()
    }

    private fun getApiErrorMessage(body: String?, fallback: String): String? {
        if (TextUtils.isEmpty(body)) {
            return fallback
        }
        try {
            val errorJson = JSONObject(body)
            if (errorJson.has("error")) {
                val errorObject = errorJson.get("error")
                if (errorObject is JSONObject) {
                    return errorObject.optString("message", fallback)
                }
                if (errorObject is String) {
                    return errorObject
                }
            }
            return errorJson.optString("message", fallback)
        } catch (ignored: JSONException) {
            return fallback
        }
    }

    private fun joinUrl(baseUrl: String?, path: String): String {
        return normalizeUrl(baseUrl) + "/" + path
    }

    fun postSuccess(callback: SummaryCallback?, summary: String?) {
        if (callback == null) return
        MAIN_HANDLER.post { callback.onSuccess(summary) }
    }

    fun postProgress(callback: SummaryCallback?, summary: String?) {
        if (callback == null) return
        MAIN_HANDLER.post { callback.onProgress(summary) }
    }

    fun postDebugInfo(callback: SummaryCallback?, debugInfo: String?) {
        if (callback == null) return
        MAIN_HANDLER.post { callback.onDebugInfo(debugInfo) }
    }

    fun formatLoadInfo(modelName: String?, loadMillis: Long): String {
        if (loadMillis < 1000L) {
            return modelName + " · " + loadMillis + " ms load"
        }
        return (modelName + " · "
                + String.format(Locale.US, "%.1f s", loadMillis / 1000.0) + " load")
    }

    fun postFailure(callback: SummaryCallback?, error: String?) {
        if (callback == null) return
        MAIN_HANDLER.post { callback.onFailure(error) }
    }

    fun postLocalAvailability(
        callback: LocalSummaryAvailabilityCallback?,
        available: Boolean,
        downloadableFallbackRequired: Boolean,
        statusMessage: String?
    ) {
        if (callback == null) return
        MAIN_HANDLER.post {
            callback.onResult(
                available, downloadableFallbackRequired, statusMessage
            )
        }
    }

    interface SummaryCallback {
        fun onProgress(summary: String?) {
        }

        fun onDebugInfo(debugInfo: String?) {
        }

        fun onSuccess(summary: String?)
        fun onFailure(error: String?)
    }

    fun interface LocalSummaryAvailabilityCallback {
        fun onResult(
            available: Boolean, downloadableFallbackRequired: Boolean,
            statusMessage: String?
        )
    }
}
