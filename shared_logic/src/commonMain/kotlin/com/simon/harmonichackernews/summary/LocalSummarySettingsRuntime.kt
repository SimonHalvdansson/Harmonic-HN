package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.platform.LocalSummaryEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocalSummarySettingsState(
    val supported: Boolean = false,
    val availabilityResolved: Boolean = false,
    val available: Boolean = false,
    val nanoAvailable: Boolean = false,
    val configurationReady: Boolean = false,
    val failure: String? = null,
    val revision: Int = 0,
)

/** Availability/fallback selection shared by all AI settings hosts. */
class LocalSummarySettingsRuntime(
    private val scope: CoroutineScope,
    private val summary: LocalSummaryEngine?,
    private val models: LocalModelService?,
) {
    private val mutableState = MutableStateFlow(LocalSummarySettingsState())
    private var job: Job? = null
    val state: StateFlow<LocalSummarySettingsState> = mutableState.asStateFlow()

    fun resolve() {
        job?.cancel()
        val supported = summary?.canAttempt() == true
        if (!supported) {
            mutableState.value = LocalSummarySettingsState(availabilityResolved = true)
            return
        }
        job = scope.launch {
            try {
                val availability = checkNotNull(summary).availability()
                val nanoAvailable = availability.available && !availability.downloadableFallbackRequired
                if (!nanoAvailable && models?.selectedModel?.id == LocalModelCatalog.MODEL_GEMINI_NANO) {
                    models.selectFirstReadyOrClear()
                }
                mutableState.value = LocalSummarySettingsState(
                    supported = true,
                    availabilityResolved = true,
                    available = availability.available,
                    nanoAvailable = nanoAvailable,
                    configurationReady = summary.isReady(),
                    revision = mutableState.value.revision + 1,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.value = LocalSummarySettingsState(
                    supported = true,
                    availabilityResolved = true,
                    failure = error.message,
                    revision = mutableState.value.revision + 1,
                )
            }
        }
    }

    fun dispose() = job?.cancel()
}
