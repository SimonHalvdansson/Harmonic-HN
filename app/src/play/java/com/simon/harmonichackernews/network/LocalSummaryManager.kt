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
import com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager
import com.simon.harmonichackernews.summary.local.LocalModelInference
import com.simon.harmonichackernews.summary.local.LocalModelManager
import java.util.concurrent.ExecutionException

/** Play distribution implementation for Gemini Nano and downloadable local models.  */
internal object LocalSummaryManager {
    private const val TAG = "LocalSummaryManager"
    private const val LOCAL_SUMMARY_MIN_CHARS = 400
    private const val LOCAL_SUMMARY_MAX_WORDS = 3000
    private val ConsecutiveWhitespace = Regex("\\s+")

    @Volatile
    private var cachedLocalFeatureStatus: Int = Int.MIN_VALUE

    fun canAttemptLocalSummarization(): Boolean = true

    fun checkLocalSummaryAvailability(
        context: Context?,
        callback: LocalSummaryAvailabilityCallback?,
    ) {
        if (context == null || callback == null) {
            LocalSummaryCallbacks.availability(
                callback, false, false, "Local AI context is unavailable"
            )
            return
        }
        val appContext = context.applicationContext
        Thread {
            var summarizer: Summarizer? = null
            try {
                summarizer = Summarization.getClient(createLocalSummarizerOptions(appContext))
                val featureStatus = summarizer.checkFeatureStatus().get()
                cachedLocalFeatureStatus = featureStatus
                postResolvedLocalAvailability(callback, featureStatus)
            } catch (exception: ExecutionException) {
                Log.w(TAG, "Gemini Nano availability check failed", exception.cause)
                cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE
                postDownloadableModelAvailability(callback)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                LocalSummaryCallbacks.availability(
                    callback, false, false,
                    "Gemini Nano availability check was interrupted"
                )
            } catch (exception: Exception) {
                Log.w(TAG, "Gemini Nano availability check failed", exception)
                cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE
                postDownloadableModelAvailability(callback)
            } finally {
                summarizer?.close()
            }
        }.start()
    }

    fun summarizeText(
        context: Context?,
        text: String?,
        callback: LocalSummaryCallback?,
    ) {
        if (context == null || callback == null) return
        val appContext = context.applicationContext
        Thread {
            try {
                summarizePreparedTextLocally(
                    appContext, prepareLocalSummaryInput(text.orEmpty()), callback
                )
            } catch (exception: ExecutionException) {
                LocalSummaryCallbacks.failure(
                    callback,
                    "Local summarization failed: ${LocalSummaryCallbacks.errorMessage(exception.cause)}",
                )
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                LocalSummaryCallbacks.failure(callback, "Local summarization was interrupted")
            } catch (exception: Exception) {
                LocalSummaryCallbacks.failure(
                    callback,
                    "Local summarization failed: ${LocalSummaryCallbacks.errorMessage(exception)}",
                )
            }
        }.start()
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

    fun isLocalSummaryConfigurationKnown(context: Context?): Boolean {
        if (context == null) return false
        val selected = LocalModelManager.getSelectedModel(context)
        return selected.id != LocalModelManager.MODEL_GEMINI_NANO ||
            cachedLocalFeatureStatus != Int.MIN_VALUE
    }

    private fun createLocalSummarizerOptions(context: Context): SummarizerOptions =
        SummarizerOptions.builder(context)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .build()

    @Throws(ExecutionException::class, InterruptedException::class)
    private fun summarizePreparedTextLocally(
        appContext: Context, content: String, callback: LocalSummaryCallback
    ) {
        val selected = LocalModelManager.getSelectedModel(appContext)
        if (selected.id != LocalModelManager.MODEL_GEMINI_NANO) {
            summarizeWithDownloadedLocalModel(appContext, content, callback)
            return
        }
        LocalSummaryCallbacks.debugInfo(callback, "Gemini Nano · load —")

        var availabilityChecker: Summarizer? = null
        try {
            availabilityChecker =
                Summarization.getClient(createLocalSummarizerOptions(appContext))
            val featureStatus = availabilityChecker.checkFeatureStatus().get()
            cachedLocalFeatureStatus = featureStatus
            if (isLocalFeatureUsable(featureStatus)) {
                summarizePreparedLocalText(appContext, content, callback)
                return
            }
        } catch (exception: ExecutionException) {
            cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE
            throw exception
        } catch (exception: RuntimeException) {
            cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE
            throw exception
        } finally {
            availabilityChecker?.close()
        }

        LocalSummaryCallbacks.failure(
            callback,
            "Gemini Nano is unavailable. Select another local model in AI summarization settings."
        )
    }

    private fun summarizeWithDownloadedLocalModel(
        appContext: Context, content: String, callback: LocalSummaryCallback
    ) {
        val selected = LocalModelManager.getSelectedModel(appContext)
        if (!LocalModelManager.isModelSupported(selected)) {
            LocalSummaryCallbacks.failure(
                callback, LocalModelManager.getModelUnsupportedReason(selected)
            )
            return
        }
        if (!LocalModelManager.isSelectedModelDownloaded(appContext)) {
            LocalSummaryCallbacks.failure(
                callback, "Download the selected local model before using it"
            )
            return
        }
        if (!LocalAiRuntimeManager.isRuntimeInstalled(appContext, selected.runtime)) {
            LocalSummaryCallbacks.failure(
                callback, "Install the selected model runtime before using it"
            )
            return
        }
        if (content.length < LOCAL_SUMMARY_MIN_CHARS) {
            LocalSummaryCallbacks.failure(
                callback, "Article is too short for local summarization"
            )
            return
        }

        try {
            LocalSummaryCallbacks.success(
                callback, LocalModelInference.summarize(
                    appContext,
                    content,
                    { summary -> LocalSummaryCallbacks.progress(callback, summary) },
                    { loadMillis ->
                        LocalSummaryCallbacks.debugInfo(
                            callback,
                            SummaryFormatting.formatLoadInfo(
                                selected.displayName, loadMillis
                            )
                        )
                    })
            )
        } catch (exception: Exception) {
            LocalSummaryCallbacks.failure(
                callback,
                "Local model failed: ${LocalSummaryCallbacks.errorMessage(exception)}",
            )
        }
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    private fun summarizePreparedLocalText(
        appContext: Context, content: String, callback: LocalSummaryCallback
    ) {
        var summarizer: Summarizer? = null
        var summarizerReleased = false
        try {
            if (content.length < LOCAL_SUMMARY_MIN_CHARS) {
                LocalSummaryCallbacks.failure(
                    callback, "Article is too short for Gemini Nano summarization"
                )
                return
            }

            summarizer = Summarization.getClient(createLocalSummarizerOptions(appContext))
            when (summarizer.checkFeatureStatus().get()) {
                FeatureStatus.DOWNLOADABLE,
                FeatureStatus.DOWNLOADING,
                -> {
                    downloadLocalFeatureAndSummarize(content, summarizer, callback)
                    summarizerReleased = true
                }
                FeatureStatus.AVAILABLE -> {
                    runLocalInference(content, summarizer, callback)
                    summarizerReleased = true
                }
                else -> LocalSummaryCallbacks.failure(
                    callback,
                    "Gemini Nano summarization is not available on this device",
                )
            }
        } finally {
            if (summarizer != null && !summarizerReleased) {
                summarizer.close()
            }
        }
    }

    private fun downloadLocalFeatureAndSummarize(
        text: String, summarizer: Summarizer, callback: LocalSummaryCallback
    ) {
        summarizer.downloadFeature(object : DownloadCallback {
            override fun onDownloadCompleted() {
                runLocalInference(text, summarizer, callback)
            }

            override fun onDownloadFailed(exception: GenAiException) {
                summarizer.close()
                LocalSummaryCallbacks.failure(
                    callback,
                    "Gemini Nano download failed: ${LocalSummaryCallbacks.errorMessage(exception)}",
                )
            }

            override fun onDownloadProgress(totalBytesDownloaded: Long) {
            }

            override fun onDownloadStarted(bytesToDownload: Long) {
            }
        })
    }

    private fun runLocalInference(
        text: String, summarizer: Summarizer, callback: LocalSummaryCallback
    ) {
        Thread {
            try {
                val request: SummarizationRequest = SummarizationRequest.builder(text).build()
                val result = summarizer.runInference(request).get()
                LocalSummaryCallbacks.success(callback, result.summary)
            } catch (exception: ExecutionException) {
                LocalSummaryCallbacks.failure(
                    callback,
                    "Gemini Nano failed: ${LocalSummaryCallbacks.errorMessage(exception.cause)}",
                )
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                LocalSummaryCallbacks.failure(
                    callback, "Gemini Nano summarization was interrupted"
                )
            } catch (exception: Exception) {
                LocalSummaryCallbacks.failure(
                    callback,
                    "Gemini Nano failed: ${LocalSummaryCallbacks.errorMessage(exception)}",
                )
            } finally {
                summarizer.close()
            }
        }.start()
    }

    private fun prepareLocalSummaryInput(text: String): String {
        val normalized = text.trim().replace(ConsecutiveWhitespace, " ")
        if (normalized.isEmpty()) {
            return normalized
        }

        val words = normalized.split(ConsecutiveWhitespace)
        if (words.size <= LOCAL_SUMMARY_MAX_WORDS) {
            return normalized
        }

        return words.take(LOCAL_SUMMARY_MAX_WORDS).joinToString(" ")
    }

    private fun isLocalFeatureUsable(featureStatus: Int): Boolean = when (featureStatus) {
        FeatureStatus.AVAILABLE,
        FeatureStatus.DOWNLOADABLE,
        FeatureStatus.DOWNLOADING,
        -> true
        else -> false
    }

    private fun getLocalFeatureStatusMessage(featureStatus: Int): String = when (featureStatus) {
        FeatureStatus.AVAILABLE -> ""
        FeatureStatus.DOWNLOADABLE -> "Gemini Nano will download before the first local summary"
        FeatureStatus.DOWNLOADING -> "Gemini Nano is downloading"
        else -> "Gemini Nano not available on this device"
    }

    private fun postResolvedLocalAvailability(
        callback: LocalSummaryAvailabilityCallback,
        featureStatus: Int,
    ) {
        if (isLocalFeatureUsable(featureStatus)) {
            LocalSummaryCallbacks.availability(
                callback, true, false, getLocalFeatureStatusMessage(featureStatus)
            )
        } else {
            postDownloadableModelAvailability(callback)
        }
    }

    private fun postDownloadableModelAvailability(
        callback: LocalSummaryAvailabilityCallback,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LocalSummaryCallbacks.availability(
                callback, true, true, "Gemini Nano isn't available on this device"
            )
        } else {
            LocalSummaryCallbacks.availability(
                callback, false, false,
                "Gemini Nano is unavailable; downloadable models require Android 12 or newer"
            )
        }
    }
}
