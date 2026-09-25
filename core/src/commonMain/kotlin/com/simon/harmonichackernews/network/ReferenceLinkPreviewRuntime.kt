package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.platform.ConnectivityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReferenceLinkPreviewState(
    val url: String = "",
    val loading: Boolean = false,
    val showFallback: Boolean = false,
    val summary: LinkSummary? = null,
    val error: String? = null,
    val retrying: Boolean = false,
    val offline: Boolean = false,
    val generation: Long = 0,
)

/**
 * Owns cache lookup, network loading, retry and PDF/non-page fallback for comment reference links.
 * Compose hosts only render [state] and execute the resulting final-URL update.
 */
class ReferenceLinkPreviewRuntime(
    private val scope: CoroutineScope,
    private val previews: StoryPreviewRepository,
    private val connectivity: ConnectivityService,
) {
    private val mutableState = MutableStateFlow(ReferenceLinkPreviewState())
    private var loadJob: Job? = null
    val state: StateFlow<ReferenceLinkPreviewState> = mutableState.asStateFlow()

    fun load(
        url: String,
        fallbackTitle: String,
        resolvedTitle: String?,
        forceRefresh: Boolean = false,
        resolvedSummary: LinkSummary? = null,
    ) {
        if (url.isBlank()) return
        loadJob?.cancel()
        val generation = mutableState.value.generation + 1
        val known = resolvedSummary?.takeIf { !forceRefresh && StoryPreviewRepository.isValidSummary(url, it) }
        mutableState.value = ReferenceLinkPreviewState(
            url = known?.finalUrl?.takeIf(String::isNotBlank) ?: url,
            summary = known,
            loading = known == null,
            showFallback = !resolvedTitle.isNullOrBlank(),
            retrying = forceRefresh,
            generation = generation,
        )
        loadJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = previews.loadLinkSummary(url, fallbackTitle, forceRefresh, known)
                currentCoroutineContext().ensureActive()
                publish(
                    generation,
                    ReferenceLinkPreviewState(
                        url = result.finalUrl.takeIf(String::isNotBlank) ?: url,
                        summary = result,
                        generation = generation,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val message = error.message?.takeIf(String::isNotBlank)
                    ?: "The page could not be read"
                val contentFallback = message.trim().equals(PDF_CONTENT_TYPE_ERROR, true) ||
                    !resolvedTitle.isNullOrBlank()
                publish(
                    generation,
                    ReferenceLinkPreviewState(
                        url = url,
                        showFallback = true,
                        error = message.takeUnless { contentFallback },
                        offline = !connectivity.isOnline(),
                        generation = generation,
                    ),
                )
            }
        }
    }

    fun retry(url: String, fallbackTitle: String, resolvedTitle: String?): Boolean {
        if (!connectivity.isOnline() || mutableState.value.retrying) return false
        load(url, fallbackTitle, resolvedTitle, forceRefresh = true)
        return true
    }

    fun dispose() {
        loadJob?.cancel()
        loadJob = null
        mutableState.value = mutableState.value.copy(generation = mutableState.value.generation + 1)
    }

    private fun publish(generation: Long, value: ReferenceLinkPreviewState) {
        if (mutableState.value.generation == generation) mutableState.value = value
    }

    private companion object {
        const val PDF_CONTENT_TYPE_ERROR = "This link contains application/pdf, not a web page"
    }
}
