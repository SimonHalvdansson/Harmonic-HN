package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.network.CloudSummaryConfig
import com.simon.harmonichackernews.network.CloudSummaryEvent
import com.simon.harmonichackernews.network.SummaryUseCase
import com.simon.harmonichackernews.platform.LocalSummaryEngine
import com.simon.harmonichackernews.platform.SummaryRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch

enum class StorySummaryMode {
    CLOUD,
    LOCAL,
}

const val LOCAL_SUMMARY_ARTICLE_TOO_SHORT = "Article is too short for local summarization"
const val SUMMARY_ARTICLE_HTTP_UNAUTHORIZED =
    "Extraction failed: Article returned HTTP 401"
const val GEMINI_NANO_POLICY_BLOCKED_MESSAGE = "Blocked by Gemini Nano policy check."

data class StorySummaryInput(
    val articleUrl: String,
    val articleText: String? = null,
) {
    val hasArticleText: Boolean
        get() = !articleText.isNullOrBlank()
}

sealed interface StorySummaryEvent {
    data class DebugInfo(val value: String) : StorySummaryEvent
    data class Progress(val text: String) : StorySummaryEvent
    data class Success(val text: String) : StorySummaryEvent
    data class Failure(val message: String) : StorySummaryEvent
}

fun interface StorySummaryBackend {
    fun summarize(input: StorySummaryInput): Flow<StorySummaryEvent>
}

/** Adapts the platform inference capability to the shared streaming summary runtime. */
class PlatformLocalStorySummaryBackend(
    private val engine: LocalSummaryEngine,
    private val behavior: () -> LocalSummaryBehavior = { LocalSummaryBehavior() },
) : StorySummaryBackend {
    override fun summarize(input: StorySummaryInput): Flow<StorySummaryEvent> = flow {
        val current = behavior()
        engine.summarizeEvents(
            SummaryRequest(
                text = input.articleText.orEmpty(),
                prompt = current.systemPrompt,
                streamResponses = current.streamResponses,
                useGeminiNanoSummarizationLora = current.useGeminiNanoSummarizationLora,
            ),
        ).collect { event ->
            if (current.streamResponses || event !is StorySummaryEvent.Progress) emit(event)
        }
    }
}

data class LocalSummaryBehavior(
    val systemPrompt: String = LocalSummaryPreparation.SYSTEM_INSTRUCTION,
    val streamResponses: Boolean = true,
    val useGeminiNanoSummarizationLora: Boolean = true,
)

class UnavailableStorySummaryBackend(
    private val reason: String,
) : StorySummaryBackend {
    override fun summarize(input: StorySummaryInput): Flow<StorySummaryEvent> = flow {
        emit(StorySummaryEvent.Failure(reason))
    }
}

data class LocalSummaryAvailability(
    val available: Boolean,
    val downloadableFallbackRequired: Boolean,
    val statusMessage: String? = null,
    val baseModelName: String? = null,
)

/** Adds shared article-text extraction to a platform backend that only performs local inference. */
class ExtractingStorySummaryBackend(
    private val useCase: SummaryUseCase,
    private val textBackend: StorySummaryBackend,
) : StorySummaryBackend {
    override fun summarize(input: StorySummaryInput): Flow<StorySummaryEvent> = flow {
        if (input.hasArticleText) {
            emitAll(textBackend.summarize(input))
            return@flow
        }
        val articleText = try {
            useCase.extractArticleText(input.articleUrl)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            emit(
                StorySummaryEvent.Failure(
                    "Extraction failed: " +
                        (error.message?.takeIf(String::isNotBlank) ?: "Unknown error"),
                ),
            )
            return@flow
        }
        emitAll(
            textBackend.summarize(
                StorySummaryInput(articleUrl = input.articleUrl, articleText = articleText),
            ),
        )
    }
}

sealed interface StorySummaryStatus {
    data object Idle : StorySummaryStatus
    data object Running : StorySummaryStatus
    data object Success : StorySummaryStatus
    data class Failure(val message: String) : StorySummaryStatus
}

data class StorySummaryState(
    val text: String? = null,
    val debugInfo: String? = null,
    val status: StorySummaryStatus = StorySummaryStatus.Idle,
    val generation: Long = 0,
) {
    val complete: Boolean
        get() = status is StorySummaryStatus.Success || status is StorySummaryStatus.Failure
}

/**
 * Platform-neutral state machine for story summarization.
 *
 * Platform code supplies the local backend, while the cloud backend is backed directly by the
 * shared Ktor use case. The runtime owns cancellation, progress, failure normalization and the
 * terminal state observed by every UI shell.
 */
class StorySummaryRuntime(
    private val scope: CoroutineScope,
    private val cloudBackend: StorySummaryBackend,
    private val localBackend: StorySummaryBackend,
) {
    private val mutableState = MutableStateFlow(StorySummaryState())
    private var activeJob: Job? = null

    val state: StateFlow<StorySummaryState> = mutableState.asStateFlow()

    fun start(
        mode: StorySummaryMode,
        input: StorySummaryInput,
        currentText: String? = state.value.text,
    ) {
        activeJob?.cancel()
        val generation = state.value.generation + 1
        mutableState.value = StorySummaryState(
            text = currentText,
            status = StorySummaryStatus.Running,
            generation = generation,
        )
        activeJob = scope.launch {
            var reachedTerminalState = false
            try {
                backend(mode).summarize(input).collect { event ->
                    if (generation != mutableState.value.generation) return@collect
                    when (event) {
                        is StorySummaryEvent.DebugInfo -> mutableState.value =
                            mutableState.value.copy(debugInfo = event.value)
                        is StorySummaryEvent.Progress -> mutableState.value =
                            mutableState.value.copy(text = event.text)
                        is StorySummaryEvent.Success -> {
                            reachedTerminalState = true
                            mutableState.value = mutableState.value.copy(
                                text = event.text,
                                status = StorySummaryStatus.Success,
                            )
                        }
                        is StorySummaryEvent.Failure -> {
                            reachedTerminalState = true
                            fail(mode, event.message)
                        }
                    }
                }
                if (!reachedTerminalState && generation == mutableState.value.generation) {
                    fail(mode, "Summary provider completed without a result")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == mutableState.value.generation) {
                    fail(mode, error.message?.takeIf(String::isNotBlank) ?: "Unknown error")
                }
            }
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        mutableState.value = mutableState.value.copy(status = StorySummaryStatus.Idle)
    }

    fun dispose() = cancel()

    private fun backend(mode: StorySummaryMode): StorySummaryBackend = when (mode) {
        StorySummaryMode.CLOUD -> cloudBackend
        StorySummaryMode.LOCAL -> localBackend
    }

    private fun fail(mode: StorySummaryMode, detail: String) {
        val policyBlocked = mode == StorySummaryMode.LOCAL &&
            detail.contains("ErrorCode 11", ignoreCase = true) &&
            detail.contains("policy check", ignoreCase = true)
        val prefix = when (mode) {
            StorySummaryMode.CLOUD -> "Failed to generate summary"
            StorySummaryMode.LOCAL -> "Failed to generate local summary"
        }
        val message = if (policyBlocked) {
            GEMINI_NANO_POLICY_BLOCKED_MESSAGE
        } else {
            "$prefix: $detail"
        }
        mutableState.value = mutableState.value.copy(
            text = message,
            status = StorySummaryStatus.Failure(message),
        )
    }
}

class CloudStorySummaryBackend(
    private val useCase: SummaryUseCase,
    private val config: suspend () -> CloudSummaryConfig,
) : StorySummaryBackend {
    override fun summarize(input: StorySummaryInput): Flow<StorySummaryEvent> = flow {
        val upstream = if (input.hasArticleText) {
            useCase.summarizeText(config(), input.articleText)
        } else {
            useCase.summarizeArticle(config(), input.articleUrl)
        }
        upstream.collect { event ->
            emit(
                when (event) {
                    is CloudSummaryEvent.DebugInfo -> StorySummaryEvent.DebugInfo(event.value)
                    is CloudSummaryEvent.Progress -> StorySummaryEvent.Progress(event.summary)
                    is CloudSummaryEvent.Success -> StorySummaryEvent.Success(event.summary)
                },
            )
        }
    }
}
