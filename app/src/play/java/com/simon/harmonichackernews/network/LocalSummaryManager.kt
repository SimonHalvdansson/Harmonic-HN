package com.simon.harmonichackernews.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelService
import com.simon.harmonichackernews.summary.LocalSummaryAvailability
import com.simon.harmonichackernews.summary.LocalSummaryPreparation
import com.simon.harmonichackernews.summary.StorySummaryBackend
import com.simon.harmonichackernews.summary.StorySummaryEvent
import com.simon.harmonichackernews.summary.StorySummaryInput
import com.simon.harmonichackernews.summary.local.LocalModelInference
import com.simon.harmonichackernews.platform.LocalSummaryEngine
import com.simon.harmonichackernews.platform.SummaryRequest
import com.simon.harmonichackernews.platform.SummaryResult
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Application-owned readiness checks for Gemini Nano and downloadable local models. */
private class PlayLocalSummaryStatus {
    private val tag = "PlayLocalSummaryStatus"

    @Volatile
    private var cachedLocalFeatureStatus: Int = Int.MIN_VALUE

    fun canAttemptLocalSummarization(): Boolean = true

    suspend fun checkLocalSummaryAvailability(context: Context?): LocalSummaryAvailability {
        if (context == null) {
            return LocalSummaryAvailability(false, false, "Local AI context is unavailable")
        }
        return withContext(Dispatchers.IO) {
            var summarizer: Summarizer? = null
            try {
                summarizer = Summarization.getClient(createOptions(context.applicationContext))
                val status = summarizer.checkFeatureStatus().get()
                cachedLocalFeatureStatus = status
                resolvedAvailability(status, readBaseModelName(summarizer, status))
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                LocalSummaryAvailability(
                    false,
                    false,
                    "Gemini Nano availability check was interrupted",
                )
            } catch (error: Throwable) {
                Log.w(tag, "Gemini Nano availability check failed", unwrap(error))
                cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE
                downloadableModelAvailability()
            } finally {
                summarizer?.close()
            }
        }
    }

    fun isLocalSummaryReady(models: LocalModelService): Boolean {
        val selected = models.selectedModel
        if (selected.id == LocalModelCatalog.MODEL_GEMINI_NANO) {
            return isLocalFeatureUsable(cachedLocalFeatureStatus)
        }
        return models.isSupported(selected) && models.isDownloaded(selected) &&
            models.isRuntimeInstalled(selected.runtime)
    }

    private fun resolvedAvailability(
        status: Int,
        baseModelName: String?,
    ): LocalSummaryAvailability =
        if (isLocalFeatureUsable(status)) {
            LocalSummaryAvailability(
                available = true,
                downloadableFallbackRequired = false,
                statusMessage = localFeatureStatusMessage(status),
                baseModelName = baseModelName,
            )
        } else {
            downloadableModelAvailability()
        }

    private fun readBaseModelName(summarizer: Summarizer, featureStatus: Int): String? {
        if (!isLocalFeatureUsable(featureStatus)) return null
        return try {
            summarizer.getBaseModelName().get().trim().takeIf(String::isNotEmpty)
        } catch (error: InterruptedException) {
            throw error
        } catch (error: Throwable) {
            Log.w(tag, "Gemini Nano model name lookup failed", unwrap(error))
            null
        }
    }

    private fun downloadableModelAvailability(): LocalSummaryAvailability =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LocalSummaryAvailability(true, true, "Gemini Nano isn't available on this device")
        } else {
            LocalSummaryAvailability(
                false,
                false,
                "Gemini Nano is unavailable; downloadable models require Android 12 or newer",
            )
        }

    internal fun createOptions(context: Context): SummarizerOptions =
        SummarizerOptions.builder(context)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .setLongInputAutoTruncationEnabled(true)
            .build()

    internal fun isLocalFeatureUsable(status: Int): Boolean = when (status) {
        FeatureStatus.AVAILABLE, FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> true
        else -> false
    }

    private fun localFeatureStatusMessage(status: Int): String = when (status) {
        FeatureStatus.AVAILABLE -> ""
        FeatureStatus.DOWNLOADABLE -> "Gemini Nano will download before the first local summary"
        FeatureStatus.DOWNLOADING -> "Gemini Nano is downloading"
        else -> "Gemini Nano not available on this device"
    }

    internal fun unwrap(error: Throwable): Throwable =
        if (error is ExecutionException) error.cause ?: error else error
}

/** Direct implementation of the shared streaming backend; no callback compatibility layer. */
internal class AndroidLocalSummaryBackend(
    context: Context,
    private val models: LocalModelService,
) : StorySummaryBackend, LocalSummaryEngine {
    private val appContext = context.applicationContext
    private val status = PlayLocalSummaryStatus()
    private val inference = LocalModelInference(appContext, models)

    override fun canAttempt(): Boolean = status.canAttemptLocalSummarization()

    override suspend fun availability(): LocalSummaryAvailability =
        status.checkLocalSummaryAvailability(appContext)

    override suspend fun isAvailable(): Boolean =
        availability().available

    override fun isReady(): Boolean = status.isLocalSummaryReady(models)

    override suspend fun summarize(request: SummaryRequest): SummaryResult {
        var result: SummaryResult? = null
        var debugInfo: String? = null
        summarize(StorySummaryInput(articleUrl = "", articleText = request.text)).collect { event ->
            when (event) {
                is StorySummaryEvent.DebugInfo -> debugInfo = event.value
                is StorySummaryEvent.Progress -> Unit
                is StorySummaryEvent.Success -> result = SummaryResult(event.text, debugInfo)
                is StorySummaryEvent.Failure -> error(event.message)
            }
        }
        return result ?: error("Local summary provider completed without a result")
    }

    override fun summarizeEvents(request: SummaryRequest): Flow<StorySummaryEvent> =
        summarize(StorySummaryInput(articleUrl = "", articleText = request.text))

    override fun summarize(input: StorySummaryInput): Flow<StorySummaryEvent> = channelFlow {
        val content = LocalSummaryPreparation.prepareManagedText(input.articleText.orEmpty())
        if (!LocalSummaryPreparation.isLongEnough(content)) {
            send(StorySummaryEvent.Failure("Article is too short for local summarization"))
            return@channelFlow
        }
        try {
            val selected = models.selectedModel
            val result = if (selected.id == LocalModelCatalog.MODEL_GEMINI_NANO) {
                send(StorySummaryEvent.DebugInfo("Gemini Nano · load —"))
                summarizeWithGeminiNano(
                    content = content,
                    onProgress = { trySend(StorySummaryEvent.Progress(it)) },
                )
            } else {
                summarizeWithDownloadedModel(
                    content = content,
                    onProgress = { trySend(StorySummaryEvent.Progress(it)) },
                    onLoaded = { loadMillis ->
                        trySend(
                            StorySummaryEvent.DebugInfo(
                                SummaryFormatting.formatLoadInfo(selected.displayName, loadMillis),
                            ),
                        )
                    },
                )
            }
            send(StorySummaryEvent.Success(result))
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            send(StorySummaryEvent.Failure("Local summarization was interrupted"))
        } catch (error: Throwable) {
            val detail = status.unwrap(error).message?.takeIf(String::isNotBlank)
                ?: "Unknown error"
            send(StorySummaryEvent.Failure("Local summarization failed: $detail"))
        }
    }

    private suspend fun summarizeWithDownloadedModel(
        content: String,
        onProgress: (String) -> Unit,
        onLoaded: (Long) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val selected = models.selectedModel
        check(models.isSupported(selected)) {
            models.unsupportedReason(selected)
        }
        check(models.isDownloaded(selected)) {
            "Download the selected local model before using it"
        }
        check(models.isRuntimeInstalled(selected.runtime)) {
            "Install the selected model runtime before using it"
        }
        inference.summarize(
            content,
            LocalModelInference.ProgressCallback(onProgress),
            LocalModelInference.LoadCallback(onLoaded),
        )
    }

    private suspend fun summarizeWithGeminiNano(
        content: String,
        onProgress: (String) -> Unit,
    ): String =
        withContext(Dispatchers.IO) {
            val summarizer = Summarization.getClient(status.createOptions(appContext))
            try {
                val status = summarizer.checkFeatureStatus().get()
                if (!this@AndroidLocalSummaryBackend.status.isLocalFeatureUsable(status)) {
                    error(
                        "Gemini Nano is unavailable. Select another local model in AI " +
                            "summarization settings.",
                    )
                }
                if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
                    awaitDownload(summarizer)
                }
                val request = SummarizationRequest.builder(content).build()
                val streamedSummary = StringBuilder()
                summarizer.runInference(request) { additionalText ->
                    if (additionalText.isNotEmpty()) {
                        streamedSummary.append(additionalText)
                        onProgress(streamedSummary.toString())
                    }
                }.get().summary
            } finally {
                summarizer.close()
            }
        }

    private suspend fun awaitDownload(summarizer: Summarizer) =
        suspendCancellableCoroutine { continuation ->
            summarizer.downloadFeature(object : DownloadCallback {
                override fun onDownloadCompleted() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onDownloadFailed(exception: GenAiException) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }

                override fun onDownloadProgress(totalBytesDownloaded: Long) = Unit

                override fun onDownloadStarted(bytesToDownload: Long) = Unit
            })
        }
}

internal fun createAndroidLocalSummaryEngine(
    context: Context,
    models: LocalModelService,
): LocalSummaryEngine = AndroidLocalSummaryBackend(context, models)
