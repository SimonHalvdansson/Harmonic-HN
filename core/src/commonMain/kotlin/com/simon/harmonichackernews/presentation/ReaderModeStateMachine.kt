package com.simon.harmonichackernews.presentation

enum class ReaderModeScriptStatus { ENABLED, DISABLED, NO_ARTICLE, UNAVAILABLE, FAILED }

data class ReaderModeState(
    val featureEnabled: Boolean = true,
    val integrated: Boolean = true,
    val defaultEnabled: Boolean = false,
    val available: Boolean = false,
    val enabled: Boolean = false,
    val pending: Boolean = false,
    val disabledForCurrentPage: Boolean = false,
)

sealed interface ReaderModeToggleDecision {
    data object Unavailable : ReaderModeToggleDecision
    data object LoadThenEnable : ReaderModeToggleDecision
    data class Apply(val enabled: Boolean) : ReaderModeToggleDecision
}

sealed interface ReaderModePageDecision {
    data object None : ReaderModePageDecision
    data object CheckAvailability : ReaderModePageDecision
    data class Apply(val enabled: Boolean, val showFeedback: Boolean) : ReaderModePageDecision
}

/**
 * Platform-neutral reader-mode policy. The embedded session supplies clocks and owns delayed
 * transition decisions; hosts only schedule the returned delays and evaluate native JavaScript.
 */
class ReaderModeStateMachine {
    var state: ReaderModeState = ReaderModeState()
        private set

    fun configure(featureEnabled: Boolean, integrated: Boolean, defaultEnabled: Boolean) {
        state = state.copy(
            featureEnabled = featureEnabled,
            integrated = integrated,
            defaultEnabled = featureEnabled && defaultEnabled,
            available = state.available && featureEnabled && integrated,
            enabled = state.enabled && featureEnabled && integrated,
        )
    }

    fun setIntegrated(integrated: Boolean) {
        state = state.copy(
            integrated = integrated,
            available = state.available && integrated,
            enabled = state.enabled && integrated,
            pending = state.pending && integrated,
        )
    }

    fun onEligiblePageLoadStarted() {
        state = state.copy(
            // Availability is re-evaluated after the page has loaded. Do not carry the
            // previous page's reader button through the first frames of a new load.
            available = false,
            enabled = false,
            disabledForCurrentPage = false,
        )
    }

    fun onIneligiblePageLoadStarted() {
        state = state.copy(
            available = false,
            enabled = false,
            pending = false,
            disabledForCurrentPage = false,
        )
    }

    fun toggle(pageEligible: Boolean, pageReady: Boolean, storyUrlAvailable: Boolean): ReaderModeToggleDecision {
        if (!state.featureEnabled || !state.integrated || !pageEligible) {
            return ReaderModeToggleDecision.Unavailable
        }
        val enable = !state.enabled
        state = state.copy(disabledForCurrentPage = !enable)
        if (!pageReady) {
            if (!storyUrlAvailable) return ReaderModeToggleDecision.Unavailable
            state = state.copy(pending = true)
            return ReaderModeToggleDecision.LoadThenEnable
        }
        return ReaderModeToggleDecision.Apply(enable)
    }

    fun disable(): ReaderModeToggleDecision.Apply? {
        if (!state.enabled) return null
        state = state.copy(disabledForCurrentPage = true)
        return ReaderModeToggleDecision.Apply(false)
    }

    fun onPageFinished(pageEligible: Boolean): ReaderModePageDecision {
        if (!pageEligible || !state.featureEnabled || !state.integrated) {
            onIneligiblePageLoadStarted()
            return ReaderModePageDecision.None
        }
        return when {
            state.pending -> {
                state = state.copy(pending = false)
                ReaderModePageDecision.Apply(enabled = true, showFeedback = true)
            }
            state.defaultEnabled && !state.disabledForCurrentPage && !state.enabled ->
                ReaderModePageDecision.Apply(enabled = true, showFeedback = false)
            else -> ReaderModePageDecision.CheckAvailability
        }
    }

    fun confirmAvailable() {
        state = state.copy(available = state.featureEnabled && state.integrated)
    }

    fun setUnavailable() {
        state = state.copy(available = false)
    }

    fun setEnabled(enabled: Boolean) {
        state = state.copy(enabled = enabled && state.featureEnabled && state.integrated)
    }

    fun applyResult(status: ReaderModeScriptStatus) {
        state = when (status) {
            ReaderModeScriptStatus.ENABLED -> state.copy(available = true, enabled = true)
            ReaderModeScriptStatus.DISABLED -> state.copy(available = true, enabled = false)
            ReaderModeScriptStatus.NO_ARTICLE,
            ReaderModeScriptStatus.UNAVAILABLE -> state.copy(available = false, enabled = false)
            ReaderModeScriptStatus.FAILED -> state
        }
    }
}
