package com.simon.harmonichackernews.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore
import kotlinx.coroutines.runBlocking

/** Android preferences/callback adapter around shared cloud summarization. */
object SummaryManager {
    private const val PREF_BASE_URL = "pref_ai_summary_base_url"
    private const val PREF_MODEL = "pref_ai_summary_model"
    private const val PREF_SYSTEM_PROMPT = "pref_ai_summary_system_prompt"
    private const val PREF_STREAM_RESPONSES = "pref_ai_summary_stream_responses"
    private val MAIN_HANDLER = Handler(Looper.getMainLooper())

    fun fetchModels(
        ctx: Context,
        @Suppress("UNUSED_PARAMETER") queue: RequestQueue,
        callback: SummaryCallback,
    ) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        val baseUrl = preferences.getString(PREF_BASE_URL, AiSummaryProviders.defaultBaseUrl)
            ?: AiSummaryProviders.defaultBaseUrl
        val apiKey = AiSummaryApiKeyStore.getApiKey(ctx)
        NetworkComponent.launchCallbackRequest(
            request = {
                NetworkComponent.cloudSummaryRepository.fetchModelIds(baseUrl, apiKey)
            },
            onSuccess = { callback.onSuccess(it.joinToString(",")) },
            onFailure = { callback.onFailure(getThrowableMessage(it)) },
        )
    }

    fun summarizeArticle(
        ctx: Context,
        @Suppress("UNUSED_PARAMETER") queue: RequestQueue?,
        articleUrl: String,
        callback: SummaryCallback,
    ) {
        NetworkComponent.launchCallbackRequest(
            request = { NetworkComponent.cloudSummaryRepository.extractMainContent(articleUrl) },
            onSuccess = { summarizeText(ctx, null, it, callback) },
            onFailure = { callback.onFailure("Extraction failed: ${getThrowableMessage(it)}") },
        )
    }

    fun summarizeText(
        ctx: Context,
        @Suppress("UNUSED_PARAMETER") queue: RequestQueue?,
        text: String?,
        callback: SummaryCallback,
    ) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        val config = CloudSummaryConfig(
            baseUrl = preferences.getString(
                PREF_BASE_URL,
                AiSummaryProviders.defaultBaseUrl,
            ) ?: AiSummaryProviders.defaultBaseUrl,
            apiKey = AiSummaryApiKeyStore.getApiKey(ctx),
            model = preferences.getString(PREF_MODEL, "").orEmpty(),
            systemPrompt = preferences.getString(
                PREF_SYSTEM_PROMPT,
                CloudSummaryDefaults.SYSTEM_PROMPT,
            ) ?: CloudSummaryDefaults.SYSTEM_PROMPT,
            streamResponses = preferences.getBoolean(PREF_STREAM_RESPONSES, true),
        )
        NetworkComponent.collectCallbackFlow(
            flow = NetworkComponent.cloudSummaryRepository.summarize(config, text),
            onEach = { event ->
                when (event) {
                    is CloudSummaryEvent.DebugInfo -> callback.onDebugInfo(event.value)
                    is CloudSummaryEvent.Progress -> callback.onProgress(event.summary)
                    is CloudSummaryEvent.Success -> callback.onSuccess(event.summary)
                }
            },
            onFailure = { callback.onFailure(getThrowableMessage(it)) },
        )
    }

    fun canAttemptLocalSummarization(): Boolean =
        LocalSummaryManager.canAttemptLocalSummarization()

    fun checkLocalSummaryAvailability(ctx: Context?, callback: LocalSummaryAvailabilityCallback?) {
        LocalSummaryManager.checkLocalSummaryAvailability(ctx, callback)
    }

    fun summarizeArticleWithGeminiNano(
        ctx: Context?,
        articleUrl: String?,
        callback: SummaryCallback?,
    ) {
        LocalSummaryManager.summarizeArticle(ctx, articleUrl, callback)
    }

    fun summarizeTextWithGeminiNano(
        ctx: Context?,
        text: String?,
        callback: SummaryCallback?,
    ) {
        LocalSummaryManager.summarizeText(ctx, text, callback)
    }

    fun isLocalSummaryReady(context: Context?): Boolean =
        LocalSummaryManager.isLocalSummaryReady(context)

    fun isLocalSummaryConfigurationKnown(context: Context?): Boolean =
        LocalSummaryManager.isLocalSummaryConfigurationKnown(context)

    /** Blocking compatibility bridge for the existing local-model worker thread. */
    fun extractMainContent(url: String): String = runBlocking {
        NetworkComponent.cloudSummaryRepository.extractMainContent(url)
    }

    fun getThrowableMessage(throwable: Throwable?): String =
        throwable?.message?.takeUnless(String::isEmpty) ?: "Unknown error"

    fun postSuccess(callback: SummaryCallback?, summary: String?) {
        callback ?: return
        MAIN_HANDLER.post { callback.onSuccess(summary) }
    }

    fun postProgress(callback: SummaryCallback?, summary: String?) {
        callback ?: return
        MAIN_HANDLER.post { callback.onProgress(summary) }
    }

    fun postDebugInfo(callback: SummaryCallback?, debugInfo: String?) {
        callback ?: return
        MAIN_HANDLER.post { callback.onDebugInfo(debugInfo) }
    }

    fun formatLoadInfo(modelName: String?, loadMillis: Long): String =
        SummaryFormatting.formatLoadInfo(modelName, loadMillis)

    fun postFailure(callback: SummaryCallback?, error: String?) {
        callback ?: return
        MAIN_HANDLER.post { callback.onFailure(error) }
    }

    fun postLocalAvailability(
        callback: LocalSummaryAvailabilityCallback?,
        available: Boolean,
        downloadableFallbackRequired: Boolean,
        statusMessage: String?,
    ) {
        callback ?: return
        MAIN_HANDLER.post {
            callback.onResult(available, downloadableFallbackRequired, statusMessage)
        }
    }

    interface SummaryCallback {
        fun onProgress(summary: String?) = Unit
        fun onDebugInfo(debugInfo: String?) = Unit
        fun onSuccess(summary: String?)
        fun onFailure(error: String?)
    }

    fun interface LocalSummaryAvailabilityCallback {
        fun onResult(
            available: Boolean,
            downloadableFallbackRequired: Boolean,
            statusMessage: String?,
        )
    }
}
