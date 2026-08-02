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
import kotlin.Boolean
import kotlin.Exception
import kotlin.Long
import kotlin.Throwable
import kotlin.Throws
import kotlin.also
import kotlin.plus
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

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
                    val models = response!!.getJSONArray("data")
                    val modelNames: MutableList<String> = ArrayList()
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
            override fun getHeaders(): MutableMap<kotlin.String?, kotlin.String?> {
                val headers: MutableMap<kotlin.String?, kotlin.String?> =
                    HashMap<kotlin.String?, kotlin.String?>()
                if (!apiKey.isEmpty()) {
                    headers.put("Authorization", "Bearer " + apiKey)
                }
                return headers
            }
        }

        queue.add<JSONObject?>(request)
    }

    fun summarizeArticle(
        ctx: Context,
        queue: RequestQueue?,
        articleUrl: kotlin.String,
        callback: SummaryCallback
    ) {
        Thread(Runnable {
            try {
                summarizeText(ctx, queue, extractMainContent(articleUrl), callback)
            } catch (e: Exception) {
                postFailure(callback, "Extraction failed: " + getThrowableMessage(e))
            }
        }).start()
    }

    fun summarizeText(
        ctx: Context,
        queue: RequestQueue?,
        text: kotlin.String?,
        callback: SummaryCallback
    ) {
        summarizeWithLLM(ctx, queue, prepareCloudSummaryInput(text), callback)
    }

    fun canAttemptLocalSummarization(): Boolean {
        return LocalSummaryManager.canAttemptLocalSummarization()
    }

    fun checkLocalSummaryAvailability(ctx: Context?, callback: LocalSummaryAvailabilityCallback?) {
        LocalSummaryManager.checkLocalSummaryAvailability(ctx, callback)
    }

    fun summarizeArticleWithGeminiNano(
        ctx: Context?,
        articleUrl: kotlin.String?,
        callback: SummaryCallback?
    ) {
        LocalSummaryManager.summarizeArticle(ctx, articleUrl, callback)
    }

    fun summarizeTextWithGeminiNano(
        ctx: Context?,
        text: kotlin.String?,
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
    fun extractMainContent(url: kotlin.String): kotlin.String {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .timeout(10000)
            .get()
        val body = doc.body()
        return body.text()
    }

    private fun prepareCloudSummaryInput(text: kotlin.String?): kotlin.String {
        val normalized = if (text == null) "" else text.trim { it <= ' ' }
        if (normalized.length > 15000) {
            return normalized.substring(0, 15000)
        }
        return normalized
    }

    fun getThrowableMessage(throwable: Throwable?): kotlin.String? {
        if (throwable == null || throwable.message == null || throwable.message!!.isEmpty()) {
            return "Unknown error"
        }
        return throwable.message
    }

    private fun summarizeWithLLM(
        ctx: Context,
        queue: RequestQueue?,
        text: kotlin.String?,
        callback: SummaryCallback
    ) {
        val apiKey = AiSummaryApiKeyStore.getApiKey(ctx)
        val baseUrl = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getString("pref_ai_summary_base_url", defaultBaseUrl)
        val model = getModelForRequest(
            baseUrl,
            PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString("pref_ai_summary_model", "")
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

        val prompt: kotlin.String = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getString("pref_ai_summary_system_prompt", SummaryManager.DEFAULT_SYSTEM_PROMPT)!!
        val streamResponses = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(PREF_STREAM_RESPONSES, true)
        if (isAnthropicBaseUrl(baseUrl)) {
            summarizeWithAnthropic(
                queue, baseUrl, apiKey, model, prompt, text,
                streamResponses, callback
            )
        } else {
            summarizeWithChatCompletions(
                queue, baseUrl, apiKey, model, prompt, text,
                streamResponses, callback
            )
        }
    }

    private fun summarizeWithChatCompletions(
        queue: RequestQueue?,
        baseUrl: kotlin.String?,
        apiKey: kotlin.String?,
        model: kotlin.String?,
        prompt: kotlin.String?,
        text: kotlin.String?,
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
        queue: RequestQueue?,
        baseUrl: kotlin.String?,
        apiKey: kotlin.String,
        model: kotlin.String?,
        prompt: kotlin.String?,
        text: kotlin.String?,
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
        url: kotlin.String,
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
        val client = okHttpClientInstance!!.newBuilder()
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
                            val errorBody = if (body == null) "" else body.string()
                            postFailure(
                                callback, "API error: "
                                        + getApiErrorMessage(errorBody, response.message)
                            )
                            return
                        }
                        if (body == null) {
                            postFailure(callback, "API response error")
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
            var line: kotlin.String?
            while ((reader.readLine().also { line = it }) != null) {
                if (line!!.isEmpty()) {
                    if (eventData.length > 0) {
                        sawSseData = true
                        if (appendStreamEvent(eventData.toString(), anthropic, summary, callback)) {
                            eventData.setLength(0)
                            break
                        }
                        eventData.setLength(0)
                    }
                } else if (line.startsWith("data:")) {
                    if (eventData.length > 0) {
                        eventData.append('\n')
                    }
                    eventData.append(line.substring(5).trim { it <= ' ' })
                } else if (!line.startsWith(":")) {
                    if (plainResponse.length > 0) {
                        plainResponse.append('\n')
                    }
                    plainResponse.append(line)
                }
            }
        }
        if (eventData.length > 0) {
            sawSseData = true
            appendStreamEvent(eventData.toString(), anthropic, summary, callback)
        }

        if (!sawSseData && summary.length == 0 && plainResponse.length > 0) {
            appendNonStreamingResponse(plainResponse.toString(), anthropic, summary, callback)
        }

        if (summary.length == 0) {
            postFailure(callback, "API response error")
        } else {
            postSuccess(callback, summary.toString())
        }
    }

    @Throws(IOException::class)
    private fun appendStreamEvent(
        data: kotlin.String,
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

            val chunk: kotlin.String?
            if (anthropic) {
                val delta = event.optJSONObject("delta")
                chunk = if (delta == null) "" else delta.optString("text", "")
            } else {
                val choices = event.optJSONArray("choices")
                val choice = if (choices == null) null else choices.optJSONObject(0)
                val delta = if (choice == null) null else choice.optJSONObject("delta")
                chunk = if (delta == null) "" else delta.optString("content", "")
            }

            appendSummaryChunk(summary, chunk, callback)
            return false
        } catch (e: JSONException) {
            throw IOException("Invalid streaming response", e)
        }
    }

    @Throws(IOException::class)
    private fun appendNonStreamingResponse(
        responseBody: kotlin.String,
        anthropic: Boolean,
        summary: StringBuilder,
        callback: SummaryCallback
    ) {
        appendSummaryChunk(summary, parseNonStreamingResponse(responseBody, anthropic), callback)
    }

    @Throws(IOException::class)
    private fun parseNonStreamingResponse(
        responseBody: kotlin.String,
        anthropic: Boolean
    ): kotlin.String? {
        try {
            val response = JSONObject(responseBody)
            if (anthropic) {
                return parseAnthropicSummary(response)
            }
            val choices = response.optJSONArray("choices")
            val choice = if (choices == null) null else choices.optJSONObject(0)
            val message = if (choice == null) null else choice.optJSONObject("message")
            return if (message == null) "" else message.optString("content", "")
        } catch (e: JSONException) {
            throw IOException("Invalid API response", e)
        }
    }

    private fun appendSummaryChunk(
        summary: StringBuilder,
        chunk: kotlin.String?,
        callback: SummaryCallback
    ) {
        if (TextUtils.isEmpty(chunk)) {
            return
        }
        summary.append(chunk)
        postProgress(callback, summary.toString())
    }

    private fun parseAnthropicSummary(response: JSONObject): kotlin.String? {
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
            if (!text.isEmpty()) {
                if (summary.length > 0) {
                    summary.append("\n")
                }
                summary.append(text)
            }
        }
        return summary.toString()
    }

    private fun getApiErrorMessage(body: kotlin.String?, fallback: kotlin.String): kotlin.String? {
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
                if (errorObject is kotlin.String) {
                    return errorObject
                }
            }
            return errorJson.optString("message", fallback)
        } catch (ignored: JSONException) {
            return fallback
        }
    }

    private fun joinUrl(baseUrl: kotlin.String?, path: kotlin.String?): kotlin.String {
        return normalizeUrl(baseUrl) + "/" + path
    }

    fun postSuccess(callback: SummaryCallback?, summary: kotlin.String?) {
        if (callback == null) return
        MAIN_HANDLER.post(Runnable { callback.onSuccess(summary) })
    }

    fun postProgress(callback: SummaryCallback?, summary: kotlin.String?) {
        if (callback == null) return
        MAIN_HANDLER.post(Runnable { callback.onProgress(summary) })
    }

    fun postDebugInfo(callback: SummaryCallback?, debugInfo: kotlin.String?) {
        if (callback == null) return
        MAIN_HANDLER.post(Runnable { callback.onDebugInfo(debugInfo) })
    }

    fun formatLoadInfo(modelName: kotlin.String?, loadMillis: Long): kotlin.String {
        if (loadMillis < 1000L) {
            return modelName + " · " + loadMillis + " ms load"
        }
        return (modelName + " · "
                + kotlin.String.format(Locale.US, "%.1f s", loadMillis / 1000.0) + " load")
    }

    fun postFailure(callback: SummaryCallback?, error: kotlin.String?) {
        if (callback == null) return
        MAIN_HANDLER.post(Runnable { callback.onFailure(error) })
    }

    fun postLocalAvailability(
        callback: LocalSummaryAvailabilityCallback?,
        available: Boolean,
        downloadableFallbackRequired: Boolean,
        statusMessage: kotlin.String?
    ) {
        if (callback == null) return
        MAIN_HANDLER.post(Runnable {
            callback.onResult(
                available, downloadableFallbackRequired, statusMessage
            )
        })
    }

    interface SummaryCallback {
        fun onProgress(summary: kotlin.String?) {
        }

        fun onDebugInfo(debugInfo: kotlin.String?) {
        }

        fun onSuccess(summary: kotlin.String?)
        fun onFailure(error: kotlin.String?)
    }

    fun interface LocalSummaryAvailabilityCallback {
        fun onResult(
            available: Boolean, downloadableFallbackRequired: Boolean,
            statusMessage: kotlin.String?
        )
    }
}
