package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.settings.ReadingPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Main-thread adapter for native browser lifecycles. Navigation cancels stale script results. */
class NativeReaderModeSession(
    private val scope: CoroutineScope,
    private val evaluate: suspend (String) -> String?,
    private val script: suspend () -> String,
    private val onMessage: (String) -> Unit = {},
) {
    private val machine = ReaderModeStateMachine()
    private val mutableState = MutableStateFlow(machine.state)
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    private var ready = false
    private var eligible = false
    private var disposed = false
    private var theme: (suspend () -> ReaderModeTheme)? = null

    fun configure(reading: ReadingPreferences, theme: suspend () -> ReaderModeTheme) {
        this.theme = theme
        val restore = machine.state.enabled && (!reading.readerModeEnabled || !reading.integratedWebView)
        machine.configure(reading.readerModeEnabled, reading.integratedWebView, reading.readerModeDefault)
        publish()
        if (restore) run { apply(false, false) }
        else if (ready) {
            if (machine.state.enabled) run { apply(true, false) }
            else pageFinished()
        }
    }

    fun navigationStarted(url: String?) {
        generation++
        job?.cancel()
        ready = false
        eligible = WebContentPolicy.validatedHttpUrl(url) != null && WebContentPagePolicy.isReaderEligible(
            url, WebContentPlatformUrls("about:harmonic-pdf", "about:harmonic-error"),
        )
        if (eligible) machine.onEligiblePageLoadStarted() else machine.onIneligiblePageLoadStarted()
        publish()
    }

    fun pageFinished(url: String? = null) {
        if (disposed) return
        if (url != null) eligible = WebContentPolicy.validatedHttpUrl(url) != null &&
            WebContentPagePolicy.isReaderEligible(url, WebContentPlatformUrls("about:harmonic-pdf", "about:harmonic-error"))
        ready = true
        if (theme == null) return
        when (val decision = machine.onPageFinished(eligible)) {
            is ReaderModePageDecision.Apply -> run { apply(decision.enabled, decision.showFeedback) }
            ReaderModePageDecision.CheckAvailability -> run {
                checkAvailability()
                if (!machine.state.available) {
                    delay(WebContentTiming.READER_AVAILABILITY_RECHECK_DELAY_MILLIS)
                    checkAvailability()
                }
            }
            ReaderModePageDecision.None -> publish()
        }
    }

    fun toggle() {
        when (val decision = machine.toggle(eligible, ready, eligible)) {
            is ReaderModeToggleDecision.Apply -> run { apply(decision.enabled, true) }
            ReaderModeToggleDecision.Unavailable -> onMessage("Reader mode is unavailable for this page")
            ReaderModeToggleDecision.LoadThenEnable -> Unit
        }
        publish()
    }

    fun dispose() {
        disposed = true
        generation++
        job?.cancel()
    }

    private suspend fun checkAvailability() {
        val available = ReaderModeScriptProtocol.isAvailable(evaluateCurrent(ReaderModeScriptProtocol.availabilityCommand(script())))
        if (available) machine.confirmAvailable() else machine.setUnavailable()
        publish()
    }

    private suspend fun apply(enabled: Boolean, feedback: Boolean) {
        val currentTheme = theme?.invoke() ?: return
        val result = ReaderModeScriptProtocol.parseStatus(evaluateCurrent(
            ReaderModeScriptProtocol.applyCommand(script(), currentTheme, enabled),
        ))
        machine.applyResult(result)
        publish()
        if (feedback && result != ReaderModeScriptStatus.ENABLED && result != ReaderModeScriptStatus.DISABLED) {
            onMessage("Couldn't simplify this page")
        }
    }

    private suspend fun evaluateCurrent(source: String): String? {
        currentCoroutineContext().ensureActive()
        val expected = generation
        val result = withTimeoutOrNull(10_000) { evaluate(source) }
        currentCoroutineContext().ensureActive()
        if (disposed || generation != expected) throw CancellationException("Page changed")
        return result
    }

    private fun run(action: suspend () -> Unit) {
        if (disposed) return
        job?.cancel()
        val expectedGeneration = generation
        job = scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == expectedGeneration) {
                    machine.setUnavailable()
                    publish()
                }
            }
        }
    }

    private fun publish() { mutableState.value = machine.state }
}
