package com.simon.harmonichackernews.presentation

import kotlinx.coroutines.flow.StateFlow

/**
 * Portable command controller layered over a native [WebContentDriver]. URL decisions, reader
 * protocol and state-machine transitions therefore stay identical across hosts.
 */
class WebContentController(
    private val runtime: WebContentRuntime,
    private val driver: WebContentDriver,
) {
    val driverState: StateFlow<WebContentDriverState> get() = driver.state
    val loadState: WebContentLoadState get() = runtime.load.state
    val readerState: ReaderModeState get() = runtime.reader.state

    fun load(url: String?, archiveDomains: Collection<String>): WebContentUrlPlan? {
        val plan = WebContentPolicy.resolveUrl(url, archiveDomains) ?: return null
        driver.load(plan.loadUrl)
        return plan
    }

    fun reload() = driver.reload()

    fun goBack(): Boolean = driver.goBack()

    fun readPageText(onResult: (String?) -> Unit) = driver.readPageText(onResult)

    fun onPageCommitVisible(generation: Int = runtime.load.state.generation): Boolean =
        runtime.load.commitVisible(generation)
    fun onLoadFinished(generation: Int = runtime.load.state.generation): Boolean =
        runtime.load.finish(generation)
    fun reset() = runtime.load.reset()

    fun evaluateReaderMode(
        script: String,
        theme: ReaderModeTheme,
        enabled: Boolean,
        onResult: (ReaderModeScriptStatus) -> Unit,
    ) {
        driver.evaluateJavaScript(ReaderModeScriptProtocol.applyCommand(script, theme, enabled)) {
            val status = ReaderModeScriptProtocol.parseStatus(it)
            onResult(status)
        }
    }

    fun evaluateReaderModeAvailability(script: String, onResult: (Boolean) -> Unit) {
        driver.evaluateJavaScript(ReaderModeScriptProtocol.availabilityCommand(script)) {
            val available = ReaderModeScriptProtocol.isAvailable(it)
            onResult(available)
        }
    }
}
