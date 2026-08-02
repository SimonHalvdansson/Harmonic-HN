package com.simon.harmonichackernews.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizationResult
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager
import com.simon.harmonichackernews.summary.local.LocalModelInference
import com.simon.harmonichackernews.summary.local.LocalModelManager
import com.simon.harmonichackernews.network.SummaryManager.LocalSummaryAvailabilityCallback
import com.simon.harmonichackernews.network.SummaryManager.SummaryCallback
import com.simon.harmonichackernews.summary.local.LocalModelManager.ModelInfo
import java.util.concurrent.ExecutionException

/** Play distribution implementation for Gemini Nano and downloadable local models.  */
internal object LocalSummaryManager {
    private val TAG = "LocalSummaryManager"
    private const val LOCAL_SUMMARY_MIN_CHARS = 400
    private const val LOCAL_SUMMARY_MAX_WORDS = 3000

    @kotlin.concurrent.Volatile
    private var cachedLocalFeatureStatus: Int = Int.MIN_VALUE

    fun canAttemptLocalSummarization(): Boolean {
        return true
    }

    fun checkLocalSummaryAvailability(
        context: Context?, callback: LocalSummaryAvailabilityCallback?
    ) {
        if (context == null || callback == null) {
            SummaryManager.postLocalAvailability(callback, false, false, "Local AI context is unavailable")
            return
        }
        val appContext: Context = context.getApplicationContext()
        Thread({
            var summarizer: Summarizer? = null
            try {
                summarizer = Summarization.getClient(createLocalSummarizerOptions(appContext))
                val featureStatus: Int = summarizer.checkFeatureStatus().get()
                cachedLocalFeatureStatus = featureStatus
                postResolvedLocalAvailability(callback, featureStatus)
            } catch (exception: ExecutionException) {
                Log.w(TAG, "Gemini Nano availability check failed", exception.cause)
                cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE
                postDownloadableModelAvailability(callback)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                SummaryManager.postLocalAvailability(
                    callback, false, false,
                    "Gemini Nano availability check was interrupted"
                )
            } catch (exception: Exception) {
                Log.w(TAG, "Gemini Nano availability check failed", exception)
                cachedLocalFeatureStatus = FeatureStatus.UNAVAILABLE
                postDownloadableModelAvailability(callback)
            } finally {
                if (summarizer != null) {
                    summarizer.close()
                }
            }
        }).start()
    }

    fun summarizeArticle(
        context: Context?, articleUrl: String?, callback: SummaryCallback?
    ) {
        if (context == null || callback == null) return
        val appContext: Context = context.getApplicationContext()
        Thread({
            try {
                summarizePreparedTextLocally(
                    appContext,
                    prepareLocalSummaryInput(SummaryManager.extractMainContent(articleUrl.orEmpty())),
                    callback
                )
            } catch (exception: ExecutionException) {
                SummaryManager.postFailure(
                    callback, "Local summarization failed: "
                            + SummaryManager.getThrowableMessage(exception.cause)
                )
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                SummaryManager.postFailure(callback, "Local summarization was interrupted")
            } catch (exception: Exception) {
                SummaryManager.postFailure(
                    callback, "Local summarization failed: "
                            + SummaryManager.getThrowableMessage(exception)
                )
            }
        }).start()
    }

    fun summarizeText(
        context: Context?, text: String?, callback: SummaryCallback?
    ) {
        if (context == null || callback == null) return
        val appContext: Context = context.getApplicationContext()
        Thread({
            try {
                summarizePreparedTextLocally(
                    appContext, prepareLocalSummaryInput(text.orEmpty()), callback
                )
            } catch (exception: ExecutionException) {
                SummaryManager.postFailure(
                    callback, "Local summarization failed: "
                            + SummaryManager.getThrowableMessage(exception.cause)
                )
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                SummaryManager.postFailure(callback, "Local summarization was interrupted")
            } catch (exception: Exception) {
                SummaryManager.postFailure(
                    callback, "Local summarization failed: "
                            + SummaryManager.getThrowableMessage(exception)
                )
            }
        }).start()
    }

    fun isLocalSummaryReady(context: Context?): Boolean {
        if (context == null) return false
        val selected: ModelInfo =
            LocalModelManager.getSelectedModel(context)
        if (LocalModelManager.MODEL_GEMINI_NANO.equals(selected.id)) {
            return isLocalFeatureUsable(cachedLocalFeatureStatus)
        }
        return LocalModelManager.isModelSupported(selected)
                && LocalModelManager.isSelectedModelDownloaded(context)
                && LocalAiRuntimeManager.isRuntimeInstalled(context, selected.runtime)
    }

    fun isLocalSummaryConfigurationKnown(context: Context?): Boolean {
        if (context == null) return false
        val selected: ModelInfo =
            LocalModelManager.getSelectedModel(context)
        return !LocalModelManager.MODEL_GEMINI_NANO.equals(selected.id)
                || cachedLocalFeatureStatus != Int.MIN_VALUE
    }

    private fun createLocalSummarizerOptions(context: Context): SummarizerOptions {
        return SummarizerOptions.builder(context)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .build()
    }

    @kotlin.Throws(ExecutionException::class, InterruptedException::class)
    private fun summarizePreparedTextLocally(
        appContext: Context, content: String, callback: SummaryCallback
    ) {
        val selected: ModelInfo =
            LocalModelManager.getSelectedModel(appContext)
        if (!LocalModelManager.MODEL_GEMINI_NANO.equals(selected.id)) {
            summarizeWithDownloadedLocalModel(appContext, content, callback)
            return
        }
        SummaryManager.postDebugInfo(callback, "Gemini Nano · load —")

        var availabilityChecker: Summarizer? = null
        try {
            availabilityChecker =
                Summarization.getClient(createLocalSummarizerOptions(appContext))
            val featureStatus: Int = availabilityChecker.checkFeatureStatus().get()
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
            if (availabilityChecker != null) {
                availabilityChecker.close()
            }
        }

        SummaryManager.postFailure(
            callback,
            "Gemini Nano is unavailable. Select another local model in AI summarization settings."
        )
    }

    private fun summarizeWithDownloadedLocalModel(
        appContext: Context, content: String, callback: SummaryCallback
    ) {
        val selected: ModelInfo =
            LocalModelManager.getSelectedModel(appContext)
        if (!LocalModelManager.isModelSupported(selected)) {
            SummaryManager.postFailure(
                callback, LocalModelManager.getModelUnsupportedReason(selected)
            )
            return
        }
        if (!LocalModelManager.isSelectedModelDownloaded(appContext)) {
            SummaryManager.postFailure(
                callback, "Download the selected local model before using it"
            )
            return
        }
        if (!LocalAiRuntimeManager.isRuntimeInstalled(appContext, selected.runtime)) {
            SummaryManager.postFailure(
                callback, "Install the selected model runtime before using it"
            )
            return
        }
        if (content.length < LOCAL_SUMMARY_MIN_CHARS) {
            SummaryManager.postFailure(
                callback, "Article is too short for local summarization"
            )
            return
        }

        try {
            SummaryManager.postSuccess(
                callback, LocalModelInference.summarize(
                    appContext,
                    content,
                    { summary -> SummaryManager.postProgress(callback, summary) },
                    { loadMillis ->
                        SummaryManager.postDebugInfo(
                            callback,
                            SummaryManager.formatLoadInfo(
                                selected.displayName, loadMillis
                            )
                        )
                    })
            )
        } catch (exception: Exception) {
            SummaryManager.postFailure(
                callback, "Local model failed: "
                        + SummaryManager.getThrowableMessage(exception)
            )
        }
    }

    @kotlin.Throws(ExecutionException::class, InterruptedException::class)
    private fun summarizePreparedLocalText(
        appContext: Context, content: String, callback: SummaryCallback
    ) {
        var summarizer: Summarizer? = null
        var summarizerReleased = false
        try {
            if (content.length < LOCAL_SUMMARY_MIN_CHARS) {
                SummaryManager.postFailure(
                    callback, "Article is too short for Gemini Nano summarization"
                )
                return
            }

            summarizer = Summarization.getClient(createLocalSummarizerOptions(appContext))
            val featureStatus: Int = summarizer.checkFeatureStatus().get()
            if (featureStatus == FeatureStatus.UNAVAILABLE) {
                SummaryManager.postFailure(
                    callback, "Gemini Nano summarization is not available on this device"
                )
            } else if (featureStatus == FeatureStatus.DOWNLOADABLE
                || featureStatus == FeatureStatus.DOWNLOADING
            ) {
                downloadLocalFeatureAndSummarize(content, summarizer, callback)
                summarizerReleased = true
            } else if (featureStatus == FeatureStatus.AVAILABLE) {
                runLocalInference(content, summarizer, callback)
                summarizerReleased = true
            } else {
                SummaryManager.postFailure(
                    callback, "Gemini Nano summarization is not available on this device"
                )
            }
        } finally {
            if (summarizer != null && !summarizerReleased) {
                summarizer.close()
            }
        }
    }

    private fun downloadLocalFeatureAndSummarize(
        text: String, summarizer: Summarizer, callback: SummaryCallback
    ) {
        summarizer.downloadFeature(object : DownloadCallback {
            override fun onDownloadCompleted() {
                runLocalInference(text, summarizer, callback)
            }

            override fun onDownloadFailed(exception: GenAiException) {
                summarizer.close()
                SummaryManager.postFailure(
                    callback, "Gemini Nano download failed: "
                            + SummaryManager.getThrowableMessage(exception)
                )
            }

            override fun onDownloadProgress(totalBytesDownloaded: Long) {
            }

            override fun onDownloadStarted(bytesToDownload: Long) {
            }
        })
    }

    private fun runLocalInference(
        text: String, summarizer: Summarizer, callback: SummaryCallback
    ) {
        Thread({
            try {
                val request: SummarizationRequest = SummarizationRequest.builder(text).build()
                val result: SummarizationResult = summarizer.runInference(request).get()
                SummaryManager.postSuccess(callback, result.getSummary())
            } catch (exception: ExecutionException) {
                SummaryManager.postFailure(
                    callback, "Gemini Nano failed: "
                            + SummaryManager.getThrowableMessage(exception.cause)
                )
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                SummaryManager.postFailure(
                    callback, "Gemini Nano summarization was interrupted"
                )
            } catch (exception: Exception) {
                SummaryManager.postFailure(
                    callback, "Gemini Nano failed: "
                            + SummaryManager.getThrowableMessage(exception)
                )
            } finally {
                summarizer.close()
            }
        }).start()
    }

    private fun prepareLocalSummaryInput(text: String): String {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) {
            return normalized
        }

        val words = normalized.split(Regex("\\s+"))
        if (words.size <= LOCAL_SUMMARY_MAX_WORDS) {
            return normalized
        }

        val truncated: StringBuilder = StringBuilder()
        for (i in 0..<LOCAL_SUMMARY_MAX_WORDS) {
            if (i > 0) {
                truncated.append(' ')
            }
            truncated.append(words[i])
        }
        return truncated.toString()
    }

    private fun isLocalFeatureUsable(featureStatus: Int): Boolean {
        return featureStatus == FeatureStatus.AVAILABLE || featureStatus == FeatureStatus.DOWNLOADABLE || featureStatus == FeatureStatus.DOWNLOADING
    }

    private fun getLocalFeatureStatusMessage(featureStatus: Int): String {
        if (featureStatus == FeatureStatus.AVAILABLE) {
            return ""
        } else if (featureStatus == FeatureStatus.DOWNLOADABLE) {
            return "Gemini Nano will download before the first local summary"
        } else if (featureStatus == FeatureStatus.DOWNLOADING) {
            return "Gemini Nano is downloading"
        }
        return "Gemini Nano not available on this device"
    }

    private fun postResolvedLocalAvailability(
        callback: LocalSummaryAvailabilityCallback, featureStatus: Int
    ) {
        if (isLocalFeatureUsable(featureStatus)) {
            SummaryManager.postLocalAvailability(
                callback, true, false, getLocalFeatureStatusMessage(featureStatus)
            )
        } else {
            postDownloadableModelAvailability(callback)
        }
    }

    private fun postDownloadableModelAvailability(
        callback: LocalSummaryAvailabilityCallback
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SummaryManager.postLocalAvailability(
                callback, true, true, "Gemini Nano isn't available on this device"
            )
        } else {
            SummaryManager.postLocalAvailability(
                callback, false, false,
                "Gemini Nano is unavailable; downloadable models require Android 12 or newer"
            )
        }
    }
}
