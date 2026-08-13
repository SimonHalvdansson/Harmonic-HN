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
import com.simon.harmonichackernews.summary.LocalSummaryAvailability
import com.simon.harmonichackernews.summary.LocalSummaryPreparation
import com.simon.harmonichackernews.summary.StorySummaryBackend
import com.simon.harmonichackernews.summary.StorySummaryEvent
import com.simon.harmonichackernews.summary.StorySummaryInput
import com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager
import com.simon.harmonichackernews.summary.local.LocalModelInference
import com.simon.harmonichackernews.summary.local.LocalModelManager
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Play-distribution readiness checks for Gemini Nano and downloadable local models. */
internal object LocalSummaryManager {
    private const val TAG = "LocalSummaryManager"

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
                resolvedAvailability(status)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                LocalSummaryAvailability(
                    false,
                    false,
                    "Gemini Nano availability check was interrupted",
                )
            } catch (error: Throwable) {
                Log.w(TAG, "Gemini Nano availability check failed", unwrap(error))
                cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE
                downloadableModelAvailability()
            } finally {
                summarizer?.close()
            }
        }
    }

    fun isLocalSummaryReady(context: Context?): Boolean {
        if (context == null) return false
        val selected = LocalModelManager.getSelectedModel(context)
        if (selected.id == LocalModelManager.MODEL_GEMINI_NANO) {
            return isLocalFeatureUsable(cachedLocalFeatureStatus)
        }
        return LocalModelManager.isModelSupported(selected) &&
            LocalModelManager.isSelectedModelDownloaded(context) &&
            LocalAiRuntimeManager.isRuntimeInstalled(context, selected.runtime)
    }

    private fun resolvedAvailability(status: Int): LocalSummaryAvailability =
        if (isLocalFeatureUsable(status)) {
            LocalSummaryAvailability(true, false, localFeatureStatusMessage(status))
        } else {
            downloadableModelAvailability()
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
internal class AndroidLocalSummaryBackend(context: Context) : StorySummaryBackend {
    private val appContext = context.applicationContext

    override fun summarize(input: StorySummaryInput): Flow<StorySummaryEvent> = channelFlow {
        val content = LocalSummaryPreparation.prepareManagedText(input.articleText.orEmpty())
        if (!LocalSummaryPreparation.isLongEnough(content)) {
            send(StorySummaryEvent.Failure("Article is too short for local summarization"))
            return@channelFlow
        }
        try {
            val selected = LocalModelManager.getSelectedModel(appContext)
            val result = if (selected.id == LocalModelManager.MODEL_GEMINI_NANO) {
                send(StorySummaryEvent.DebugInfo("Gemini Nano · load —"))
                summarizeWithGeminiNano(content)
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
            val detail = LocalSummaryManager.unwrap(error).message?.takeIf(String::isNotBlank)
                ?: "Unknown error"
            send(StorySummaryEvent.Failure("Local summarization failed: $detail"))
        }
    }

    private suspend fun summarizeWithDownloadedModel(
        content: String,
        onProgress: (String) -> Unit,
        onLoaded: (Long) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val selected = LocalModelManager.getSelectedModel(appContext)
        check(LocalModelManager.isModelSupported(selected)) {
            LocalModelManager.getModelUnsupportedReason(selected)
        }
        check(LocalModelManager.isSelectedModelDownloaded(appContext)) {
            "Download the selected local model before using it"
        }
        check(LocalAiRuntimeManager.isRuntimeInstalled(appContext, selected.runtime)) {
            "Install the selected model runtime before using it"
        }
        LocalModelInference.summarize(
            appContext,
            content,
            LocalModelInference.ProgressCallback(onProgress),
            LocalModelInference.LoadCallback(onLoaded),
        )
    }

    private suspend fun summarizeWithGeminiNano(content: String): String =
        withContext(Dispatchers.IO) {
            val summarizer = Summarization.getClient(LocalSummaryManager.createOptions(appContext))
            try {
                val status = summarizer.checkFeatureStatus().get()
                if (!LocalSummaryManager.isLocalFeatureUsable(status)) {
                    error(
                        "Gemini Nano is unavailable. Select another local model in AI " +
                            "summarization settings.",
                    )
                }
                if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
                    awaitDownload(summarizer)
                }
                val request = SummarizationRequest.builder(content).build()
                summarizer.runInference(request).get().summary
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
