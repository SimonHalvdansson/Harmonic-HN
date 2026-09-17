package com.simon.harmonichackernews.presentation

enum class EmbeddedWebContentPage { CONTENT, ERROR, CACHED_CONTENT }

data class EmbeddedWebContentSessionState(
    val page: EmbeddedWebContentPage = EmbeddedWebContentPage.CONTENT,
    val lastRequestedUrl: String? = null,
    val lastFailedUrl: String? = null,
    val retryingFailedUrl: Boolean = false,
)

data class ReaderModeStateChange(
    val previous: ReaderModeState,
    val current: ReaderModeState,
) {
    val availabilityChanged: Boolean get() = previous.available != current.available
    val enabledChanged: Boolean get() = previous.enabled != current.enabled
}

data class EmbeddedWebLoadStart(
    val generation: Int,
    val readerMode: ReaderModeStateChange,
)

data class ReaderModeToggleResult(
    val decision: ReaderModeToggleDecision,
    val change: ReaderModeStateChange,
)

data class ReaderModePageResult(
    val decision: ReaderModePageDecision,
    val change: ReaderModeStateChange,
)

data class ReaderModeEvaluationResult(
    val change: ReaderModeStateChange,
    val message: String? = null,
    val delayedUnavailableMillis: Long? = null,
)

data class ReaderModeAvailabilityResult(
    val change: ReaderModeStateChange,
    val delayedUnavailableMillis: Long? = null,
    val scheduleRecheck: Boolean = false,
)

data class EmbeddedWebFailurePlan(
    val failedUrl: String?,
    val tryCachedArticle: Boolean,
    val errorPageFragment: String,
)

/**
 * Portable orchestration around an embedded browser. Native hosts render pages, load assets,
 * evaluate JavaScript and schedule delays; load ordering, reader-mode decisions, failure recovery,
 * retry ownership, and stale page-text request rejection live here.
 */
class EmbeddedWebContentSession(
    private val runtime: WebContentRuntime,
    driver: WebContentDriver,
) {
    val controller = WebContentController(runtime, driver)

    val readerState: ReaderModeState get() = runtime.reader.state

    var state: EmbeddedWebContentSessionState = EmbeddedWebContentSessionState()
        private set

    private val pageTextRequests = KeyedRequestSession<Unit>()
    private var pageTextReadGeneration = -1
    private val readerAvailability = ReaderModeAvailabilityCadence()

    fun configureReader(
        featureEnabled: Boolean,
        integrated: Boolean,
        defaultEnabled: Boolean,
    ): ReaderModeStateChange = readerChange {
        runtime.reader.configure(featureEnabled, integrated, defaultEnabled)
    }

    fun setReaderIntegrated(integrated: Boolean): ReaderModeStateChange = readerChange {
        runtime.reader.setIntegrated(integrated)
    }

    fun onLoadStarted(pageEligible: Boolean, nowMillis: Long): EmbeddedWebLoadStart {
        val generation = runtime.load.begin()
        val readerEligible = readerState.featureEnabled && readerState.integrated && pageEligible
        readerAvailability.onLoadStarted(
            generation = generation,
            eligible = readerEligible,
            nowMillis = nowMillis,
        )
        val change = readerChange {
            if (readerEligible) {
                runtime.reader.onEligiblePageLoadStarted()
            } else {
                runtime.reader.onIneligiblePageLoadStarted()
            }
        }
        return EmbeddedWebLoadStart(generation, change)
    }

    fun onPageFinished(pageEligible: Boolean): ReaderModePageResult {
        var decision: ReaderModePageDecision = ReaderModePageDecision.None
        val change = readerChange { decision = runtime.reader.onPageFinished(pageEligible) }
        finishRetry()
        return ReaderModePageResult(decision, change)
    }

    fun toggleReaderMode(
        pageEligible: Boolean,
        pageReady: Boolean,
        storyUrlAvailable: Boolean,
    ): ReaderModeToggleResult {
        var decision: ReaderModeToggleDecision = ReaderModeToggleDecision.Unavailable
        val change = readerChange {
            decision = runtime.reader.toggle(pageEligible, pageReady, storyUrlAvailable)
        }
        return ReaderModeToggleResult(decision, change)
    }

    fun disableReaderMode(): ReaderModeToggleResult? {
        var decision: ReaderModeToggleDecision.Apply? = null
        val change = readerChange { decision = runtime.reader.disable() }
        return decision?.let { ReaderModeToggleResult(it, change) }
    }

    fun applyReaderModeEvaluation(
        status: ReaderModeScriptStatus,
        generation: Int,
        nowMillis: Long,
        showFeedback: Boolean,
    ): ReaderModeEvaluationResult {
        var delayedUnavailableMillis: Long? = null
        val change = when (status) {
            ReaderModeScriptStatus.ENABLED,
            ReaderModeScriptStatus.DISABLED -> {
                readerAvailability.onAvailable()
                readerChange { runtime.reader.applyResult(status) }
            }
            ReaderModeScriptStatus.NO_ARTICLE,
            ReaderModeScriptStatus.UNAVAILABLE -> readerChange {
                delayedUnavailableMillis = setUnavailableRespectingInitialGrace(
                    generation,
                    nowMillis,
                )
                runtime.reader.setEnabled(false)
            }
            ReaderModeScriptStatus.FAILED -> readerChange {
                delayedUnavailableMillis = setUnavailableRespectingInitialGrace(
                    generation,
                    nowMillis,
                )
            }
        }
        val message = if (!showFeedback) null else when (status) {
            ReaderModeScriptStatus.NO_ARTICLE -> WebContentCopy.READER_NO_ARTICLE
            ReaderModeScriptStatus.UNAVAILABLE -> WebContentCopy.READER_UNAVAILABLE_FOR_PAGE
            ReaderModeScriptStatus.FAILED -> WebContentCopy.READER_OPEN_FAILED
            ReaderModeScriptStatus.ENABLED,
            ReaderModeScriptStatus.DISABLED -> null
        }
        return ReaderModeEvaluationResult(change, message, delayedUnavailableMillis)
    }

    fun applyReaderAvailability(
        available: Boolean,
        generation: Int,
        nowMillis: Long,
    ): ReaderModeAvailabilityResult {
        var delayedUnavailableMillis: Long? = null
        val change = readerChange {
            if (available) {
                readerAvailability.onAvailable()
                runtime.reader.confirmAvailable()
            } else {
                delayedUnavailableMillis = setUnavailableRespectingInitialGrace(
                    generation,
                    nowMillis,
                )
            }
        }
        return ReaderModeAvailabilityResult(
            change = change,
            delayedUnavailableMillis = delayedUnavailableMillis,
            scheduleRecheck = !available && readerAvailability.scheduleRecheck(
                generation,
                controller.loadState.generation,
            ),
        )
    }

    fun setReaderUnavailableNow(): ReaderModeStateChange = readerChange {
        readerAvailability.onUnavailableNow()
        runtime.reader.setUnavailable()
    }

    fun applyDelayedReaderUnavailable(generation: Int): ReaderModeStateChange? {
        if (!readerAvailability.shouldApplyDelayedUnavailable(
                generation,
                controller.loadState.generation,
            )
        ) return null
        return setReaderUnavailableNow()
    }

    fun shouldRunReaderAvailabilityRecheck(generation: Int): Boolean =
        readerAvailability.shouldRunRecheck(generation, controller.loadState.generation)

    fun recordRequestedUrl(url: String?, preservePage: Boolean = false) {
        val requested = url?.takeIf(String::isNotBlank) ?: return
        state = state.copy(
            page = if (preservePage) state.page else EmbeddedWebContentPage.CONTENT,
            lastRequestedUrl = requested,
        )
    }

    fun recordFailure(failingUrl: String?, currentUrl: String?): String? {
        val failed = resolveFailedUrl(failingUrl, currentUrl)
        state = state.copy(
            lastFailedUrl = failed,
            retryingFailedUrl = false,
        )
        return failed
    }

    fun planFailure(
        failure: WebContentFailure,
        failingUrl: String?,
        currentUrl: String?,
    ): EmbeddedWebFailurePlan = EmbeddedWebFailurePlan(
        failedUrl = resolveFailedUrl(failingUrl, currentUrl),
        tryCachedArticle = WebContentPolicy.shouldTryCachedArticle(failure),
        errorPageFragment = WebContentPolicy.errorPageFragment(failure),
    )

    fun showError(plan: EmbeddedWebFailurePlan) {
        state = state.copy(
            page = EmbeddedWebContentPage.ERROR,
            lastFailedUrl = plan.failedUrl,
            retryingFailedUrl = false,
        )
    }

    fun showContent() {
        state = state.copy(page = EmbeddedWebContentPage.CONTENT)
    }

    fun showError() {
        state = state.copy(page = EmbeddedWebContentPage.ERROR)
    }

    fun showCachedContent(failedUrl: String?, currentUrl: String?) {
        recordFailure(failedUrl, currentUrl)
        state = state.copy(page = EmbeddedWebContentPage.CACHED_CONTENT)
    }

    fun beginRetry(): String? {
        val failed = state.lastFailedUrl?.takeIf(String::isNotBlank) ?: return null
        state = state.copy(retryingFailedUrl = true)
        return failed
    }

    fun finishRetry() {
        state = state.copy(retryingFailedUrl = false)
    }

    fun beginPageTextRequest(): Int {
        pageTextReadGeneration = -1
        return pageTextRequests.begin(Unit)
    }

    fun currentPageTextRequestGeneration(): Int = pageTextRequests.generation

    fun isCurrentPageTextRequest(generation: Int): Boolean =
        pageTextRequests.isCurrent(generation, Unit)

    /** Claims a page-text result once, so a timeout and native callback cannot both complete it. */
    fun claimPageTextRequest(generation: Int): Boolean {
        if (!isCurrentPageTextRequest(generation)) return false
        pageTextRequests.invalidate()
        pageTextReadGeneration = -1
        return true
    }

    fun readRequestedPageText(generation: Int, onResult: (String?) -> Unit): Boolean {
        if (!isCurrentPageTextRequest(generation) || pageTextReadGeneration == generation) {
            return false
        }
        pageTextReadGeneration = generation
        controller.readPageText(onResult)
        return true
    }

    fun reset() {
        controller.reset()
        pageTextRequests.invalidate()
        pageTextReadGeneration = -1
        state = EmbeddedWebContentSessionState()
    }

    private fun setUnavailableRespectingInitialGrace(
        generation: Int,
        nowMillis: Long,
    ): Long? {
        val delay = readerAvailability.unavailableDelayMillis(generation, nowMillis)
        if (delay == null) {
            readerAvailability.onUnavailableNow()
            runtime.reader.setUnavailable()
        }
        return delay
    }

    private fun resolveFailedUrl(failingUrl: String?, currentUrl: String?): String? =
        failingUrl?.takeIf(String::isNotBlank)
            ?: state.lastRequestedUrl
            ?: currentUrl?.takeIf(String::isNotBlank)

    private inline fun readerChange(change: () -> Unit): ReaderModeStateChange {
        val previous = runtime.reader.state
        change()
        val current = runtime.reader.state
        if (!previous.available && current.available) readerAvailability.onAvailable()
        return ReaderModeStateChange(previous, current)
    }
}
