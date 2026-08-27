package com.simon.harmonichackernews.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LinkPreviewRuntimeState(
    val loading: Boolean = false,
    val preview: LinkPreviewData? = null,
    val failure: String? = null,
    val generation: Long = 0,
)

/** Owns provider selection, request cancellation and immutable preview request state. */
class LinkPreviewRuntime(
    private val scope: CoroutineScope,
    private val useCase: LinkPreviewUseCase,
) {
    private val mutableState = MutableStateFlow(LinkPreviewRuntimeState())
    private var activeJob: Job? = null

    val state: StateFlow<LinkPreviewRuntimeState> = mutableState.asStateFlow()

    fun load(
        url: String?,
        preferences: LinkPreviewPreferences,
        alreadyLoaded: Boolean,
    ): Boolean {
        if (url.isNullOrBlank() || alreadyLoaded || mutableState.value.loading) return false
        val provider = useCase.selectProvider(url, preferences) ?: return false
        activeJob?.cancel()
        val generation = mutableState.value.generation + 1
        mutableState.value = LinkPreviewRuntimeState(loading = true, generation = generation)
        activeJob = scope.launch {
            try {
                val preview = useCase.load(provider, url)
                if (mutableState.value.generation == generation) {
                    mutableState.value = LinkPreviewRuntimeState(
                        preview = preview,
                        generation = generation,
                    )
                }
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException && mutableState.value.generation == generation) {
                    mutableState.value = LinkPreviewRuntimeState(
                        failure = "Preview timed out",
                        generation = generation,
                    )
                } else {
                    throw error
                }
            } catch (error: Throwable) {
                if (mutableState.value.generation == generation) {
                    mutableState.value = LinkPreviewRuntimeState(
                        failure = error.message?.takeIf(String::isNotBlank) ?: "Preview failed",
                        generation = generation,
                    )
                }
            } finally {
                if (mutableState.value.generation == generation && mutableState.value.loading) {
                    mutableState.value = mutableState.value.copy(loading = false)
                }
            }
        }
        return true
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        if (mutableState.value.loading) {
            mutableState.value = mutableState.value.copy(loading = false)
        }
    }

    fun dispose() = cancel()
}
