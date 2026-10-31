package com.simon.harmonichackernews.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Feature
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import com.google.mlkit.genai.prompt.TypedCandidate
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelService
import com.simon.harmonichackernews.summary.LocalModelTransferState
import com.simon.harmonichackernews.summary.LocalRuntimeInstallState
import com.simon.harmonichackernews.summary.LocalSummaryAvailability
import com.simon.harmonichackernews.summary.LocalSummaryPreparation
import com.simon.harmonichackernews.summary.NanoPromptBudget
import com.simon.harmonichackernews.summary.LOCAL_SUMMARY_ARTICLE_TOO_SHORT
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Application-owned readiness checks for Gemini Nano and downloadable local models. */
internal data class NanoFeatureSnapshot(val status: Int, val baseModelName: String? = null)

internal class PlayLocalSummaryStatus(
    private val queryFeature: suspend (useSummarizationLora: Boolean) -> NanoFeatureSnapshot,
) {
    private val tag = "PlayLocalSummaryStatus"

    @Volatile
    private var promptFeatureStatus: Int = Int.MIN_VALUE
    @Volatile
    private var summarizationFeatureStatus: Int = Int.MIN_VALUE

    fun canAttemptLocalSummarization(): Boolean = true

    suspend fun checkLocalSummaryAvailability(useSummarizationLora: Boolean): LocalSummaryAvailability {
        return withContext(Dispatchers.IO) {
            val requested = query(useSummarizationLora)
            val alternate = query(!useSummarizationLora)
            when {
                isLocalFeatureUsable(requested.status) -> resolvedAvailability(requested.status, requested.baseModelName)
                isLocalFeatureUsable(alternate.status) -> LocalSummaryAvailability(
                    available = true,
                    downloadableFallbackRequired = false,
                    statusMessage = if (useSummarizationLora) {
                        "The 3-bullet summarizer is unavailable; choose System prompt in AI features settings"
                    } else {
                        "Custom prompts are unavailable; choose the 3-bullet summarizer in AI features settings"
                    },
                    baseModelName = alternate.baseModelName,
                )
                else -> downloadableModelAvailability()
            }
        }
    }

    private suspend fun query(useSummarizationLora: Boolean): NanoFeatureSnapshot =
        try {
            queryFeature(useSummarizationLora)
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            NanoFeatureSnapshot(FeatureStatus.UNAVAILABLE)
        } catch (error: Exception) {
            Log.w(tag, "Gemini Nano availability check failed", unwrap(error))
            NanoFeatureSnapshot(FeatureStatus.UNAVAILABLE)
        }.also { recordFeatureStatus(useSummarizationLora, it.status) }

    fun recordFeatureStatus(useSummarizationLora: Boolean, featureStatus: Int) {
        if (useSummarizationLora) summarizationFeatureStatus = featureStatus
        else promptFeatureStatus = featureStatus
    }

    fun isNanoFeatureUsable(useSummarizationLora: Boolean): Boolean =
        isLocalFeatureUsable(if (useSummarizationLora) summarizationFeatureStatus else promptFeatureStatus)

    fun isLocalSummaryReady(models: LocalModelService, useSummarizationLora: Boolean): Boolean {
        val selected = models.selectedModel
        if (selected.id == LocalModelCatalog.MODEL_GEMINI_NANO) {
            return isNanoFeatureUsable(useSummarizationLora)
        }
        val cachedModels = models.cachedState.value
        return models.isSupported(selected) &&
            cachedModels.statuses[selected.id]?.state == LocalModelTransferState.DOWNLOADED &&
            cachedModels.runtimeStatuses[selected.runtime]?.state ==
                LocalRuntimeInstallState.INSTALLED
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

private fun nanoSummarizerOptions(context: Context): SummarizerOptions =
    SummarizerOptions.builder(context)
        .setInputType(SummarizerOptions.InputType.ARTICLE)
        .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
        .setLanguage(SummarizerOptions.Language.ENGLISH)
        .setLongInputAutoTruncationEnabled(true)
        .build()

private suspend fun queryNanoFeature(context: Context, useSummarizationLora: Boolean): NanoFeatureSnapshot {
    suspend fun snapshot(status: Int, readName: suspend () -> String): NanoFeatureSnapshot {
        val name = if (status == FeatureStatus.UNAVAILABLE) null else try {
            readName().trim().takeIf(String::isNotEmpty)
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            throw error
        } catch (error: Exception) {
            Log.w("PlayLocalSummaryStatus", "Gemini Nano model name lookup failed", error)
            null
        }
        return NanoFeatureSnapshot(status, name)
    }
    return if (useSummarizationLora) {
        val summarizer = Summarization.getClient(nanoSummarizerOptions(context))
        try {
            snapshot(summarizer.checkFeatureStatus().get()) { summarizer.getBaseModelName().get() }
        } finally {
            summarizer.close()
        }
    } else {
        val model = Generation.getClient()
        try {
            snapshot(model.checkStatus()) { model.getBaseModelName() }
        } finally {
            model.close()
        }
    }
}

@Generable
data class NanoQuestionSuggestions(
    @Guide(description = "Two distinct, specific questions, each under 180 characters and ending with a question mark",
        minItems = 2, maxItems = 2)
    val questions: List<String>,
)

internal fun nanoSummaryResult(text: String, finishReason: Int?): SummaryResult {
    check(finishReason != Candidate.FinishReason.OTHER) { "Gemini Nano could not finish the response. Please retry." }
    return SummaryResult(text, truncated = finishReason == Candidate.FinishReason.MAX_TOKENS)
}

/** Direct implementation of the shared streaming backend; no callback compatibility layer. */
internal class AndroidLocalSummaryBackend(
    context: Context,
    private val models: LocalModelService,
) : StorySummaryBackend, LocalSummaryEngine {
    private val appContext = context.applicationContext
    private val status = PlayLocalSummaryStatus { queryNanoFeature(appContext, it) }
    private val inference = LocalModelInference(appContext, models)

    override fun canAttempt(): Boolean = status.canAttemptLocalSummarization()

    override suspend fun availability(): LocalSummaryAvailability =
        availability(useGeminiNanoSummarizationLora = false)

    override suspend fun availability(useGeminiNanoSummarizationLora: Boolean): LocalSummaryAvailability =
        status.checkLocalSummaryAvailability(useGeminiNanoSummarizationLora)

    override suspend fun isAvailable(): Boolean =
        availability().available

    override fun isReady(): Boolean = isReady(useGeminiNanoSummarizationLora = false)

    override fun isReady(useGeminiNanoSummarizationLora: Boolean): Boolean =
        status.isLocalSummaryReady(models, useGeminiNanoSummarizationLora)

    override suspend fun summarize(request: SummaryRequest): SummaryResult {
        var result: SummaryResult? = null
        var debugInfo: String? = null
        summarizeEvents(request).collect { event ->
            when (event) {
                is StorySummaryEvent.DebugInfo -> debugInfo = event.value
                is StorySummaryEvent.SourceInput -> Unit
                is StorySummaryEvent.Progress -> Unit
                is StorySummaryEvent.Success -> result = SummaryResult(event.text, debugInfo, event.truncated)
                is StorySummaryEvent.Failure -> error(event.message)
            }
        }
        return result ?: error("Local summary provider completed without a result")
    }

    override fun summarizeEvents(request: SummaryRequest): Flow<StorySummaryEvent> =
        summarizeInput(
            input = StorySummaryInput(articleUrl = "", articleText = request.text),
            request = request,
        )

    override fun summarize(input: StorySummaryInput): Flow<StorySummaryEvent> =
        summarizeInput(input, SummaryRequest(text = input.articleText.orEmpty()))

    override suspend fun suggestQuestions(text: String, prompt: String): List<String> {
        if (models.selectedModel.id == LocalModelCatalog.MODEL_GEMINI_NANO) {
            val structured = withContext(Dispatchers.IO) {
                val model = Generation.getClient()
                try {
                    preparePromptModel(model)
                    val features = model.getAvailableFeatures()
                    if (Feature.STRUCTURED_OUTPUT in features) {
                        generateStructuredSuggestions(model, text, prompt, Feature.SYSTEM_PROMPT in features)
                    } else null
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w("AndroidLocalSummary", "Structured suggestions unavailable; using text fallback", error)
                    null
                } finally {
                    model.close()
                }
            }
            if (structured != null) return structured.questions
        }
        return super<LocalSummaryEngine>.suggestQuestions(text, prompt)
    }

    private suspend fun generateStructuredSuggestions(
        model: GenerativeModel,
        text: String,
        prompt: String,
        systemPromptAvailable: Boolean,
    ): NanoQuestionSuggestions? {
        fun request(input: String, outputTokens: Int = 1024) = generateTypedContentRequest(
            generateContentRequest(TextPart(if (systemPromptAvailable) input else "$prompt\n\n## Input\n$input")) {
                if (systemPromptAvailable) systemInstruction = SystemInstruction(prompt)
                temperature = 0.2f
                enableThinking = false
                maxOutputTokens = outputTokens
            }, NanoQuestionSuggestions::class,
        )
        val budget = NanoPromptBudget.fit(text, model.getTokenLimit(), preserveInput = true) {
            model.countTokens(request(it)).totalTokens
        }
        val candidate = model.generateContent(request(budget.text, budget.outputTokens)).candidates.firstOrNull()
        return candidate?.response.takeIf { candidate?.finishReason == TypedCandidate.TypedFinishReason.STOP }
    }

    private fun summarizeInput(
        input: StorySummaryInput,
        request: SummaryRequest,
    ): Flow<StorySummaryEvent> = channelFlow {
        val content = if (request.preserveInput) input.articleText.orEmpty().trim()
            else LocalSummaryPreparation.prepareManagedText(input.articleText.orEmpty())
        if (!request.preserveInput && !LocalSummaryPreparation.isLongEnough(content)) {
            send(StorySummaryEvent.Failure(LOCAL_SUMMARY_ARTICLE_TOO_SHORT))
            return@channelFlow
        }
        try {
            val selected = models.selectedModel
            val result = if (selected.id == LocalModelCatalog.MODEL_GEMINI_NANO) {
                send(StorySummaryEvent.DebugInfo("Gemini Nano · load —"))
                if (request.useGeminiNanoSummarizationLora) {
                    summarizeWithGeminiNanoLora(
                        content = content,
                        onProgress = { progress ->
                            if (request.streamResponses) {
                                trySend(StorySummaryEvent.Progress(progress))
                            }
                        },
                    ).let(::SummaryResult)
                } else {
                    summarizeWithGeminiNanoPrompt(
                        content = content,
                        preserveInput = request.preserveInput,
                        reserveDiscussionSpace = request.reserveDiscussionSpace,
                        systemPrompt = request.prompt
                            ?.takeIf(String::isNotBlank)
                            ?: LocalSummaryPreparation.SYSTEM_INSTRUCTION,
                        streamResponses = request.streamResponses,
                        thinkingRequested = request.enableThinking,
                        onProgress = { trySend(StorySummaryEvent.Progress(it)) },
                    )
                }
            } else {
                summarizeWithDownloadedModel(
                    content = content,
                    preserveInput = request.preserveInput,
                    systemPrompt = request.prompt
                        ?.takeIf(String::isNotBlank)
                        ?: LocalSummaryPreparation.SYSTEM_INSTRUCTION,
                    onProgress = { progress ->
                        if (request.streamResponses) {
                            trySend(StorySummaryEvent.Progress(progress))
                        }
                    },
                    onLoaded = { loadMillis ->
                        trySend(
                            StorySummaryEvent.DebugInfo(
                                SummaryFormatting.formatLoadInfo(selected.displayName, loadMillis),
                                modelLoadMillis = loadMillis,
                            ),
                        )
                    },
                ).let(::SummaryResult)
            }
            send(StorySummaryEvent.Success(result.text, truncated = result.truncated))
        } catch (error: CancellationException) {
            throw error
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
        preserveInput: Boolean,
        systemPrompt: String,
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
            systemPrompt,
            LocalModelInference.ProgressCallback(onProgress),
            LocalModelInference.LoadCallback(onLoaded),
            preserveInput = preserveInput,
        )
    }

    private suspend fun summarizeWithGeminiNanoLora(
        content: String,
        onProgress: (String) -> Unit,
    ): String =
        withContext(Dispatchers.IO) {
            val summarizer = Summarization.getClient(nanoSummarizerOptions(appContext))
            try {
                var featureStatus = summarizer.checkFeatureStatus().get()
                status.recordFeatureStatus(true, featureStatus)
                if (!status.isLocalFeatureUsable(featureStatus)) {
                    error(
                        "Gemini Nano is unavailable. Select another local model in AI " +
                            "summarization settings.",
                    )
                }
                if (featureStatus == FeatureStatus.DOWNLOADABLE || featureStatus == FeatureStatus.DOWNLOADING) {
                    awaitDownload(summarizer)
                    featureStatus = summarizer.checkFeatureStatus().get()
                    status.recordFeatureStatus(true, featureStatus)
                }
                check(featureStatus == FeatureStatus.AVAILABLE) { "Gemini Nano is not ready. Please try again." }
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

    private suspend fun summarizeWithGeminiNanoPrompt(
        content: String,
        preserveInput: Boolean,
        reserveDiscussionSpace: Boolean,
        systemPrompt: String,
        streamResponses: Boolean,
        thinkingRequested: Boolean,
        onProgress: (String) -> Unit,
    ): SummaryResult = withContext(Dispatchers.IO) {
        val generativeModel = Generation.getClient()
        try {
            preparePromptModel(generativeModel)
            val features = generativeModel.getAvailableFeatures()
            val nativeSystemPrompt = Feature.SYSTEM_PROMPT in features
            val thinkingAvailable = thinkingRequested && Feature.THINKING_MODE in features
            fun request(text: String, outputTokens: Int = 1024) = generateContentRequest(
                TextPart(if (nativeSystemPrompt) text else "$systemPrompt\n\n## Input\n$text"),
            ) {
                if (nativeSystemPrompt) systemInstruction = SystemInstruction(systemPrompt)
                temperature = 0.2f
                enableThinking = thinkingAvailable
                maxOutputTokens = outputTokens
            }
            val budget = NanoPromptBudget.fit(content, generativeModel.getTokenLimit(), preserveInput, reserveDiscussionSpace) {
                generativeModel.countTokens(request(it)).totalTokens
            }
            val promptRequest = request(budget.text, budget.outputTokens)
            var finishReason: Int? = null
            val text = if (streamResponses) {
                val summary = StringBuilder()
                generativeModel.generateContentStream(promptRequest).collect { response ->
                    response.candidates.firstOrNull()?.finishReason?.let { finishReason = it }
                    response.candidates.firstOrNull()?.text
                        ?.takeIf(String::isNotEmpty)
                        ?.let { chunk ->
                            summary.append(chunk)
                            onProgress(summary.toString())
                        }
                }
                summary.toString().trim().takeIf(String::isNotEmpty)
                    ?: error("Gemini Nano returned an empty summary")
            } else {
                val candidate = generativeModel.generateContent(promptRequest).candidates.firstOrNull()
                finishReason = candidate?.finishReason
                candidate
                    ?.text
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: error("Gemini Nano returned an empty summary")
            }
            nanoSummaryResult(text, finishReason)
        } finally {
            generativeModel.close()
        }
    }

    private suspend fun preparePromptModel(model: GenerativeModel) {
        var featureStatus = model.checkStatus()
        status.recordFeatureStatus(false, featureStatus)
        when (featureStatus) {
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                model.download().collect { download ->
                    if (download is DownloadStatus.DownloadFailed) throw download.e
                }
                featureStatus = model.checkStatus()
                status.recordFeatureStatus(false, featureStatus)
            }
            FeatureStatus.AVAILABLE, FeatureStatus.UNAVAILABLE -> Unit
            else -> error("Gemini Nano prompt feature returned status $featureStatus")
        }
        check(featureStatus == FeatureStatus.AVAILABLE) {
            "Gemini Nano's custom prompt feature is unavailable on this device. " +
                "Choose the 3-bullet summarizer in AI features settings."
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
