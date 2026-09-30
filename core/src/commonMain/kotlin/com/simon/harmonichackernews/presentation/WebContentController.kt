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
    private var readerInstallation: ReaderInstallation? = null
    private var readerRequest = 0L
    private var fontTokenSerial = 0L
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
        val request = ++readerRequest
        val generation = loadState.generation
        val existing = readerInstallation?.takeIf {
            it.generation == generation && it.script == script && it.fontFaceCss == theme.fontFaceCss
        }
        val installation = existing ?: ReaderInstallation(
            generation, script, theme.fontFaceCss, "font-${++fontTokenSerial}",
        )
        fun evaluate(install: Boolean) {
            if (install) readerInstallation = null
            val command = if (install) {
                ReaderModeScriptProtocol.applyCommand(script, theme, enabled, installation.fontToken)
            } else {
                ReaderModeScriptProtocol.updateCommand(theme, enabled, installation.fontToken)
            }
            driver.evaluateJavaScript(command) { result ->
                val current = request == readerRequest && generation == loadState.generation
                if (!install && current && ReaderModeScriptProtocol.needsInstallation(result)) {
                    // Covers same-URL reloads and document replacements even if a native host
                    // hasn't delivered its navigation callback yet. Retry only this latest action.
                    evaluate(install = true)
                } else {
                    val status = ReaderModeScriptProtocol.parseStatus(result)
                    if (current && status != ReaderModeScriptStatus.FAILED) {
                        readerInstallation = installation
                    }
                    onResult(status)
                }
            }
        }
        evaluate(install = existing == null)
    }

    fun evaluateReaderModeAvailability(script: String, onResult: (Boolean) -> Unit) {
        driver.evaluateJavaScript(ReaderModeScriptProtocol.availabilityCommand(script)) {
            val available = ReaderModeScriptProtocol.isAvailable(it)
            onResult(available)
        }
    }

    private data class ReaderInstallation(
        val generation: Int,
        val script: String,
        val fontFaceCss: String,
        val fontToken: String,
    )
}
