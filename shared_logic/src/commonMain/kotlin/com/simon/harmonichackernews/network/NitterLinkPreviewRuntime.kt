package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.NitterInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

interface WebPageExtractor<out T> {
    val currentUrl: String?
    suspend fun extract(): T?
}

data class NitterLinkPreviewPreferences(
    val previewEnabled: Boolean,
    val redirectEnabled: Boolean,
)

enum class NitterLinkPreviewPhase {
    IDLE,
    WAITING_FOR_PAGE,
    READING,
    RETRY_WAIT,
    FINISHED,
    FAILED,
}

data class NitterLinkPreviewState(
    val phase: NitterLinkPreviewPhase = NitterLinkPreviewPhase.IDLE,
    val targetUrl: String? = null,
    val attempt: Int = 0,
    val preview: NitterInfo? = null,
    val failure: String? = null,
    val generation: Long = 0,
) {
    val loading: Boolean
        get() = phase == NitterLinkPreviewPhase.WAITING_FOR_PAGE ||
            phase == NitterLinkPreviewPhase.READING ||
            phase == NitterLinkPreviewPhase.RETRY_WAIT
}

/**
 * Portable redirect and page-extraction state machine. The platform owns the web page and
 * JavaScript implementation through [WebPageExtractor].
 */
class NitterLinkPreviewRuntime(
    private val scope: CoroutineScope,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val pageLoadTimeoutMillis: Long = DEFAULT_PAGE_LOAD_TIMEOUT_MILLIS,
    private val htmlReadTimeoutMillis: Long = DEFAULT_HTML_READ_TIMEOUT_MILLIS,
    private val retryDelaysMillis: List<Long> = DEFAULT_RETRY_DELAYS_MILLIS,
) {
    private val mutableState = MutableStateFlow(NitterLinkPreviewState())
    private var activeJob: Job? = null
    private var activeExtractor: WebPageExtractor<NitterInfo>? = null

    val state: StateFlow<NitterLinkPreviewState> = mutableState.asStateFlow()

    fun shouldInitializeWebPage(
        sourceUrl: String?,
        preferences: NitterLinkPreviewPreferences,
    ): Boolean = preferences.previewEnabled && NitterPreview.isConvertibleUrl(sourceUrl)

    fun prepareLoad(
        requestedUrl: String,
        preferences: NitterLinkPreviewPreferences,
        alreadyLoaded: Boolean,
        extractor: WebPageExtractor<NitterInfo>,
    ): String {
        val targetUrl = if (preferences.redirectEnabled &&
            NitterPreview.isConvertibleUrl(requestedUrl)
        ) {
            NitterPreview.convertUrl(requestedUrl)
        } else {
            requestedUrl
        }
        if (!alreadyLoaded && shouldExtract(targetUrl, preferences)) {
            beginRead(targetUrl, extractor, pageLoadTimeoutMillis)
        } else {
            cancel()
        }
        return targetUrl
    }

    fun onPageFinished(
        loadedUrl: String?,
        preferences: NitterLinkPreviewPreferences,
        alreadyLoaded: Boolean,
        extractor: WebPageExtractor<NitterInfo>,
    ): Boolean {
        if (alreadyLoaded || !preferences.previewEnabled || !NitterPreview.isNitterUrl(loadedUrl)) {
            return false
        }
        beginRead(loadedUrl.orEmpty(), extractor, initialDelayMillis = 0)
        return true
    }

    fun offlineFallback() {
        if (mutableState.value.loading) cancel()
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        activeExtractor = null
        mutableState.value = mutableState.value.copy(
            phase = NitterLinkPreviewPhase.IDLE,
            targetUrl = null,
            attempt = 0,
            failure = null,
            generation = mutableState.value.generation + 1,
        )
    }

    fun dispose() = cancel()

    private fun shouldExtract(
        url: String?,
        preferences: NitterLinkPreviewPreferences,
    ): Boolean = preferences.previewEnabled && NitterPreview.isNitterUrl(url)

    private fun beginRead(
        targetUrl: String,
        extractor: WebPageExtractor<NitterInfo>,
        initialDelayMillis: Long,
    ) {
        activeJob?.cancel()
        val generation = mutableState.value.generation + 1
        activeExtractor = extractor
        mutableState.value = NitterLinkPreviewState(
            phase = NitterLinkPreviewPhase.WAITING_FOR_PAGE,
            targetUrl = targetUrl,
            generation = generation,
        )
        val job = scope.launch {
            try {
                if (initialDelayMillis > 0) delay(initialDelayMillis)
                readWithRetry(targetUrl, extractor, generation)
            } catch (error: CancellationException) {
                throw error
            }
        }
        activeJob = job
        job.invokeOnCompletion {
            if (activeJob === job) activeJob = null
        }
    }

    private suspend fun readWithRetry(
        targetUrl: String,
        extractor: WebPageExtractor<NitterInfo>,
        generation: Long,
    ) {
        val attemptCount = maxAttempts.coerceAtLeast(1)
        repeat(attemptCount) { attempt ->
            if (!isCurrent(targetUrl, extractor, generation)) return
            mutableState.value = mutableState.value.copy(
                phase = NitterLinkPreviewPhase.READING,
                attempt = attempt,
                failure = null,
            )
            val preview = if (isExtractorAtTarget(extractor, targetUrl)) {
                withTimeoutOrNull(htmlReadTimeoutMillis.coerceAtLeast(1)) {
                    try {
                        extractor.extract()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        null
                    }
                }
            } else {
                null
            }
            if (!isCurrent(targetUrl, extractor, generation)) return
            if (preview != null) {
                mutableState.value = mutableState.value.copy(
                    phase = NitterLinkPreviewPhase.FINISHED,
                    preview = preview,
                    failure = null,
                )
                activeExtractor = null
                return
            }
            if (attempt + 1 < attemptCount) {
                mutableState.value = mutableState.value.copy(
                    phase = NitterLinkPreviewPhase.RETRY_WAIT,
                    attempt = attempt + 1,
                )
                delay(retryDelay(attempt))
            }
        }
        if (isCurrent(targetUrl, extractor, generation)) {
            mutableState.value = mutableState.value.copy(
                phase = NitterLinkPreviewPhase.FAILED,
                failure = "Preview extraction failed",
            )
            activeExtractor = null
        }
    }

    private fun isCurrent(
        targetUrl: String,
        extractor: WebPageExtractor<NitterInfo>,
        generation: Long,
    ): Boolean = mutableState.value.generation == generation &&
        mutableState.value.preview == null &&
        activeExtractor === extractor &&
        NitterPreview.isNitterUrl(targetUrl)

    private fun isExtractorAtTarget(
        extractor: WebPageExtractor<NitterInfo>,
        targetUrl: String,
    ): Boolean = NitterPreview.isNitterUrl(extractor.currentUrl) &&
        NitterPreview.isSamePage(extractor.currentUrl, targetUrl)

    private fun retryDelay(attempt: Int): Long = retryDelaysMillis
        .getOrNull(attempt)
        ?: retryDelaysMillis.lastOrNull()
        ?: 0

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 4
        const val DEFAULT_PAGE_LOAD_TIMEOUT_MILLIS = 6_000L
        const val DEFAULT_HTML_READ_TIMEOUT_MILLIS = 2_500L
        val DEFAULT_RETRY_DELAYS_MILLIS = listOf(500L, 1_500L, 3_000L)
    }
}
