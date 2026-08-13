package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryResourceTintStore
import com.simon.harmonichackernews.data.canonicalize
import com.simon.harmonichackernews.network.LinkSummary
import com.simon.harmonichackernews.network.StoryPreviewResourceRequest
import com.simon.harmonichackernews.network.StoryPreviewResourceRuntime
import com.simon.harmonichackernews.network.StoryPreviewResourceService
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.network.StoryResourceTintState
import com.simon.harmonichackernews.settings.StoryPreviewTintState
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import com.simon.harmonichackernews.settings.AiSummaryMode
import com.simon.harmonichackernews.settings.AiSummarySettingsRepository
import com.simon.harmonichackernews.settings.ContentFilters
import com.simon.harmonichackernews.settings.ReadingPreferences
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.summary.AiSummaryAvailabilityPolicy
import com.simon.harmonichackernews.summary.StorySummaryInput
import com.simon.harmonichackernews.summary.StorySummaryMode
import com.simon.harmonichackernews.summary.StorySummaryRuntime
import com.simon.harmonichackernews.summary.StorySummaryState
import com.simon.harmonichackernews.summary.StorySummaryStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed interface CommentsRuntimeEffect {
    data class Platform(val effect: CommentsPlatformEffect) : CommentsRuntimeEffect
    data class StateChanged(val refreshNavigation: Boolean = false) : CommentsRuntimeEffect
    data class ShowCommentActions(val comment: PortableCommentItem) : CommentsRuntimeEffect
    data class ThreadReady(
        val restoreScroll: Boolean,
        val headerChanged: Boolean,
    ) : CommentsRuntimeEffect
    data class Diagnostic(val message: String, val cause: Throwable? = null) : CommentsRuntimeEffect
    data class ActionFailed(val presentation: ActionFailurePresentation) : CommentsRuntimeEffect
}

data class CommentsScrollRestoration(
    val commentId: Int,
    val offset: Int,
)

/** Host facts required to build portable comment presentation settings. */
data class CommentsPresentationCapabilities(
    val showInvertAction: Boolean,
    val isTablet: Boolean,
)

data class CommentsSettingsState(
    val displaySettings: CommentDisplaySettings,
    val reading: ReadingPreferences,
    val integratedWebView: Boolean,
    val smoothScroll: Boolean,
    val transparentStatusBar: Boolean,
    val version: Long = 0L,
    val themeRefreshVersion: Long = 0L,
)

sealed interface CommentTargetResolution {
    data object None : CommentTargetResolution
    data class Found(val commentId: Int) : CommentTargetResolution
    data class NotFound(val commentId: Int) : CommentTargetResolution
}

/** Lifecycle-independent comments workflow used by every platform shell. */
class CommentsFeatureRuntime(
    private val scope: CoroutineScope,
    val sessionState: CommentsSessionState,
    val presenter: CommentsPresenter,
    private val archiveUrlResolver: ArchiveUrlResolver? = null,
    private val userSettings: UserSettings? = null,
    private val loadContentFilters: () -> ContentFilters = { ContentFilters() },
    private val accounts: ObservableHackerNewsAccountRepository? = null,
    private val summarySettings: AiSummarySettingsRepository? = null,
    private val localSummaryAvailable: () -> Boolean = { false },
    private val summaryRuntime: StorySummaryRuntime? = null,
    private val hydrateCachedStory: (Story) -> Boolean = { false },
    private val loadCachedThread: (Int) -> String? = { null },
    private val storeCachedThread: (Int, String) -> Unit = { _, _ -> },
    private val publishStoryUpdate: (Story) -> Unit = {},
    previewResourceService: StoryPreviewResourceService? = null,
    private val storyResourceTints: StoryResourceTintStore = StoryResourceTintStore.None,
    private val nowMillis: () -> Long,
) {
    private val mutableEffects = MutableSharedFlow<CommentsRuntimeEffect>(extraBufferCapacity = 32)
    val effects: SharedFlow<CommentsRuntimeEffect> = mutableEffects.asSharedFlow()

    val thread: CommentThreadStore get() = presenter.thread
    val comments: MutableList<Comment> get() = thread.displayedComments
    val allComments: MutableList<Comment> get() = thread.allComments
    val story: Story? get() = sessionState.story
    val state: CommentsPresenterState get() = presenter.state.value
    val savedItems: SavedItemStateReader get() = presenter.savedItemState
    private val mutableSettingsState = MutableStateFlow<CommentsSettingsState?>(null)
    val settingsState: StateFlow<CommentsSettingsState?> = mutableSettingsState.asStateFlow()
    private val previewResourceRuntime = previewResourceService?.let {
        StoryPreviewResourceRuntime(scope, it)
    }

    val headerPreviewResource: StoryPreviewResourceState?
        get() = story?.id?.let { previewResourceRuntime?.stateFor(it) }

    private var useAlgolia = true
    private var filteredUsers: Set<String> = emptySet()
    private var collapseTopLevel = false
    private var hasAccount = false
    private var presentationCapabilities: CommentsPresentationCapabilities? = null

    val accountUser: String?
        get() = accounts?.accountState?.value?.username

    val summaryLoading: Boolean
        get() = summaryRuntime?.state?.value?.status is StorySummaryStatus.Running

    init {
        scope.launch {
            presenter.effects.collect(::applyPresenterEffect)
        }
        userSettings?.let { settings ->
            scope.launch { settings.changes.collect { reconcileSettings() } }
        }
        accounts?.let { repository ->
            scope.launch { repository.accountState.drop(1).collect { reconcileSettings() } }
        }
        summarySettings?.let { repository ->
            scope.launch { repository.updates.drop(1).collect { reconcileSettings() } }
        }
        summaryRuntime?.let { runtime ->
            scope.launch { runtime.state.collect(::applySummaryState) }
        }
        previewResourceRuntime?.let { runtime ->
            scope.launch { runtime.states.drop(1).collect { changed() } }
        }
    }

    fun initialize(
        initialStory: Story,
        showWebsite: Boolean,
        scrollToCommentId: Int,
        sorting: String,
        restoring: Boolean,
        restoredSorting: String? = null,
    ) {
        if (!restoring) {
            sessionState.story = initialStory
            sessionState.showWebsite = showWebsite
            sessionState.scrollToCommentId = scrollToCommentId
            presenter.dispatch(
                CommentsAction.ResetThread(
                    initialStory,
                    Comment(),
                    restoredSorting?.takeIf(String::isNotBlank) ?: sorting,
                ),
            )
        }
        sessionState.initialized = true
        storyNeedingCachedSummary()?.let(hydrateCachedStory)
        reconcileSettings()
    }

    fun initializeFromSettings(
        initialStory: Story,
        showWebsite: Boolean,
        scrollToCommentId: Int,
        restoring: Boolean,
        restoredSorting: String? = null,
    ) = initialize(
        initialStory = initialStory,
        showWebsite = showWebsite,
        scrollToCommentId = scrollToCommentId,
        sorting = userSettings?.comments?.sorting ?: "Default",
        restoring = restoring,
        restoredSorting = restoredSorting,
    )

    fun configure(
        hasAccount: Boolean,
        useAlgolia: Boolean,
        filteredUsers: Set<String>,
        collapseTopLevel: Boolean,
    ) {
        this.hasAccount = hasAccount
        this.useAlgolia = useAlgolia
        this.filteredUsers = filteredUsers
        this.collapseTopLevel = collapseTopLevel
    }

    fun configure(
        settings: UserSettings,
        filters: ContentFilters,
        hasAccount: Boolean,
    ) = configure(
        hasAccount = hasAccount,
        useAlgolia = settings.reading.useAlgoliaApi,
        filteredUsers = filters.users,
        collapseTopLevel = settings.comments.collapseTopLevel,
    )

    fun updatePresentationCapabilities(capabilities: CommentsPresentationCapabilities) {
        if (presentationCapabilities == capabilities) return
        presentationCapabilities = capabilities
        reconcileSettings()
    }

    /** Reconciles settings, account state, filters, summary availability and shared UI settings. */
    fun reconcileSettings() {
        val settings = userSettings ?: return
        val capabilities = presentationCapabilities ?: return
        configure(
            settings = settings,
            filters = loadContentFilters(),
            hasAccount = accountUser != null,
        )
        val aiSnapshot = summarySettings?.snapshot()
        val canProvideSummary = aiSnapshot?.let { snapshot ->
            AiSummaryAvailabilityPolicy.canProvideSummary(
                explicitlyEnabled = snapshot.explicitlyEnabled,
                mode = snapshot.mode.storedValue,
                localAvailable = localSummaryAvailable(),
                cloudApiKeyAvailable = snapshot.apiKey.isNotBlank(),
            )
        } ?: false
        val previous = mutableSettingsState.value
        val display = CommentDisplaySettings.from(
            preferences = settings.comments,
            showInvert = capabilities.showInvertAction,
            isTablet = capabilities.isTablet,
            hasAccountDetails = hasAccount,
            canProvideSummary = story?.isLink == true && canProvideSummary,
        )
        val reading = settings.reading
        val next = CommentsSettingsState(
            displaySettings = display,
            reading = reading,
            integratedWebView = shouldUseIntegratedWebView(reading.integratedWebView),
            smoothScroll = settings.comments.smoothScroll,
            transparentStatusBar = settings.general.transparentStatusBar,
            version = (previous?.version ?: -1L) + 1L,
            themeRefreshVersion = (previous?.themeRefreshVersion ?: 0L) +
                if (previous != null && previous.displaySettings.theme != display.theme) 1L else 0L,
        )
        if (previous?.displaySettings == display && previous.reading == reading &&
            previous.integratedWebView == next.integratedWebView &&
            previous.smoothScroll == next.smoothScroll &&
            previous.transparentStatusBar == next.transparentStatusBar
        ) {
            requestHeaderPreview()
            return
        }
        mutableSettingsState.value = next
        requestHeaderPreview()
        changed(refreshNavigation = true)
    }

    fun requestHeaderPreview() {
        val currentStory = story ?: return
        val settings = settingsState.value?.displaySettings ?: return
        if (!settings.showHeaderPreviewImage || !currentStory.isLink ||
            currentStory.url.isNullOrBlank()
        ) return
        previewResourceRuntime?.request(
            StoryPreviewResourceRequest(
                storyId = currentStory.id,
                pageUrl = currentStory.url.orEmpty(),
                loadImage = true,
                loadSummary = false,
                knownImageUrl = currentStory.previewImageUrl,
                imageUrlAlreadyResolved = currentStory.previewImageUrlLoaded,
                knownSummary = currentStory.linkSummaryDescription
                    ?.takeIf { currentStory.linkSummaryLoaded }
                    ?.let {
                        LinkSummary(
                            description = it,
                            imageUrl = currentStory.previewImageUrl.orEmpty(),
                        )
                    },
            ),
        )
    }

    fun completeHeaderPreviewImageLoad(imageUrl: String, success: Boolean) {
        val currentStory = story ?: return
        previewResourceRuntime?.completeImageLoad(
            currentStory.id,
            currentStory.url.orEmpty(),
            imageUrl,
            success,
        )
    }

    fun recordHeaderPreviewTint(
        sourceUrl: String,
        baseColorArgb: Int,
        paletteConfigKey: String,
        tintColorArgb: Int,
    ): Int? {
        val currentStory = story ?: return null
        val candidate = StoryResourceTintState(
            sourceUrl = sourceUrl,
            baseColorArgb = baseColorArgb,
            paletteConfigKey = StoryPreviewTintState.storedMode(paletteConfigKey),
            tintColorArgb = tintColorArgb,
        )
        if (previewResourceRuntime?.recordTint(
                currentStory.id,
                currentStory.url.orEmpty(),
                StoryResourceTintKind.PREVIEW_IMAGE,
                candidate,
            ) != true
        ) return null
        val tint = storyResourceTints.canonicalize(
            currentStory.id,
            StoryResourceTintKind.PREVIEW_IMAGE,
            candidate,
        )
        if (tint != candidate) {
            previewResourceRuntime.recordTint(
                currentStory.id,
                currentStory.url.orEmpty(),
                StoryResourceTintKind.PREVIEW_IMAGE,
                tint,
            )
        }
        StoryPreviewTintState.applyPreview(
            currentStory,
            sourceUrl,
            baseColorArgb,
            paletteConfigKey,
            tint.tintColorArgb,
        )
        return tint.tintColorArgb
    }

    fun resume() {
        reconcileSettings()
        userSettings?.let { evaluateUpdate(it.story.alwaysShowTapToRefresh) }
    }

    fun load(
        cachedResponse: String?,
        restoreScrollFromCache: Boolean = false,
        refreshing: Boolean = false,
    ) {
        val story = story ?: return
        presenter.dispatch(CommentsAction.SetRefreshing(refreshing))
        presenter.dispatch(CommentsAction.BeginThreadLoad(nowMillis()))
        presenter.dispatch(
            CommentsAction.LoadThread(
                story = story,
                useAlgolia = useAlgolia,
                filteredUsers = filteredUsers,
                sorting = sorting,
                collapseTopLevel = collapseTopLevel,
                previousResponse = cachedResponse,
                restoreScrollFromCache = restoreScrollFromCache,
            ),
        )
        presenter.dispatch(CommentsAction.LoadPollOptions(story))
        changed()
    }

    fun loadInitial(restoreScrollFromCache: Boolean) {
        val storyId = story?.id ?: return
        load(loadCachedThread(storyId), restoreScrollFromCache)
        platform(CommentsPlatformEffect.ReloadLinkPreviews)
    }

    fun retry(cachedResponse: String? = null) = load(cachedResponse, refreshing = true)

    fun reloadPollOptions() {
        story?.let { presenter.dispatch(CommentsAction.LoadPollOptions(it)) }
    }

    fun shouldUseIntegratedWebView(preferred: Boolean): Boolean =
        preferred && story?.isLink == true

    fun storyNeedingCachedSummary(): Story? = story?.takeIf { !it.loaded && it.id > 0 }

    fun canSwitchStoryView(storyId: Int): Boolean =
        story?.id == storyId && settingsState.value?.integratedWebView == true

    fun comment(commentId: Int): PortableCommentItem? =
        thread.state.value.allComments.firstOrNull { it.id == commentId }

    fun evaluateUpdate(alwaysShow: Boolean) {
        presenter.dispatch(
            CommentsAction.EvaluateUpdateAvailability(
                nowMillis = nowMillis(),
                alwaysShow = alwaysShow,
                storyTimeEpochSeconds = story?.time ?: 0,
            ),
        )
        changed()
    }

    fun toggleExpanded(commentId: Int) {
        presenter.dispatch(CommentsAction.ToggleExpanded(commentId))
        changed(refreshNavigation = true)
    }

    fun expandParents(commentId: Int) {
        presenter.dispatch(CommentsAction.ExpandParents(commentId))
        changed(refreshNavigation = true)
    }

    private fun restoreCollapsedComments(ids: Set<Int>) {
        presenter.dispatch(CommentsAction.RestoreCollapsedComments(ids))
        changed(refreshNavigation = true)
    }

    /** Restores portable thread state and returns only the visual scroll command for the host. */
    fun restoreScrollProgress(): CommentsScrollRestoration? {
        val progress = sessionState.scrollProgress
        if (!progress.initialized || progress.storyId != story?.id) return null
        restoreCollapsedComments(progress.collapsedIDs.toSet())
        return CommentsScrollRestoration(progress.topCommentId, progress.topCommentOffset)
    }

    /** Consumes the route's comment target and expands its parents before the host scrolls. */
    fun consumeCommentTarget(): CommentTargetResolution {
        val commentId = sessionState.scrollToCommentId
        if (commentId <= 0) return CommentTargetResolution.None
        sessionState.scrollToCommentId = -1
        val comment = thread.findComment(commentId)
            ?: return CommentTargetResolution.NotFound(commentId)
        expandParents(comment.id)
        return CommentTargetResolution.Found(commentId)
    }

    fun setSearchQuery(query: String) {
        presenter.dispatch(CommentsAction.SetSearchQuery(query))
        changed()
    }

    fun recordScrollPosition(commentId: Int, offset: Int) {
        val progress = sessionState.scrollProgress
        progress.initialized = true
        progress.storyId = story?.id ?: 0
        progress.topCommentId = commentId
        progress.topCommentOffset = -offset
    }

    fun captureCollapsedComments() {
        val progress = sessionState.scrollProgress
        progress.initialized = true
        progress.storyId = story?.id ?: 0
        progress.collapsedIDs.clear()
        comments.asSequence()
            .filter { !it.expanded }
            .mapTo(progress.collapsedIDs) { it.id }
    }

    fun setSorting(sorting: String) {
        presenter.dispatch(CommentsAction.SetSorting(sorting))
        changed(refreshNavigation = true)
    }

    fun requestCommentActions(comment: PortableCommentItem) =
        presenter.dispatch(CommentsAction.RequestCommentActions(comment))

    fun votePollOption(optionId: Int) =
        presenter.dispatch(CommentsAction.VotePollOption(optionId))

    fun openHeaderLink() {
        story?.takeIf { it.isLink }?.url?.takeIf(String::isNotBlank)?.let { url ->
            platform(CommentsPlatformEffect.OpenExternalLink(url))
        }
    }

    fun header(action: CommentsHeaderAction) {
        val story = story ?: return
        if (action == CommentsHeaderAction.REFRESH) {
            retry()
            platform(CommentsPlatformEffect.ReloadLinkPreviews)
            return
        }
        execute(CommentsUiOrchestrator.header(action, CommentsHeaderContext(story, hasAccount)))
    }

    /** Validates the portable summary policy before asking the host to extract article text. */
    fun requestSummary() {
        val currentStory = story ?: return
        val snapshot = summarySettings?.snapshot() ?: return
        if (currentStory.url.isNullOrBlank()) return
        if (!AiSummaryAvailabilityPolicy.isEnabled(
                explicitlyEnabled = snapshot.explicitlyEnabled,
                localAvailable = localSummaryAvailable(),
                cloudApiKeyAvailable = snapshot.apiKey.isNotBlank(),
            )
        ) return
        platform(CommentsPlatformEffect.Summarize)
    }

    fun startSummary(articleText: String?) {
        val currentStory = story ?: return
        val mode = when (summarySettings?.snapshot()?.mode ?: return) {
            AiSummaryMode.LOCAL -> StorySummaryMode.LOCAL
            AiSummaryMode.CLOUD -> StorySummaryMode.CLOUD
        }
        summaryRuntime?.start(
            mode = mode,
            input = StorySummaryInput(
                articleUrl = currentStory.url.orEmpty(),
                articleText = articleText,
            ),
            currentText = currentStory.summary,
        )
    }

    fun share(action: CommentsShareAction) {
        story?.let { platform(CommentsUiOrchestrator.share(action, it)) }
    }

    fun more(action: CommentsMoreAction) {
        val story = story ?: return
        if (action == CommentsMoreAction.REFRESH) {
            retry()
            platform(CommentsPlatformEffect.ReloadLinkPreviews)
            return
        }
        CommentsUiOrchestrator.archiveProvider(action)?.let {
            resolveArchive(it)
            return
        }
        execute(CommentsUiOrchestrator.more(action, story, thread.state.value.commentsByOp))
    }

    fun sheet(action: CommentsSheetAction) = platform(CommentsUiOrchestrator.sheet(action))

    fun commentAction(
        action: CommentMenuAction,
        comment: PortableCommentItem,
        voteLoading: Boolean,
        downvoted: Boolean,
    ) {
        execute(
            CommentsUiOrchestrator.comment(
                action = action,
                context = CommentActionContext(
                    comment = comment.comment,
                    storyTitle = story?.title,
                    hasAccount = hasAccount,
                    voteLoading = voteLoading,
                    upvoted = savedItems.isUpvoted(comment.id, true),
                    downvoted = downvoted,
                    nowMillis = nowMillis(),
                ),
            ),
        )
    }

    fun dispose() {
        presenter.dispatch(CommentsAction.CancelThreadLoad)
        presenter.dispatch(CommentsAction.CancelPollOptionsLoad)
        presenter.dispatch(CommentsAction.CancelPollVote)
        previewResourceRuntime?.dispose()
        summaryRuntime?.dispose()
    }

    private fun execute(decision: FeatureDecision<CommentsAction, CommentsPlatformEffect>) {
        decision.actions.forEach(presenter::dispatch)
        if (decision.refreshState || decision.refreshNavigation) {
            changed(decision.refreshNavigation)
        }
        decision.effects.forEach { effect ->
            if (effect == CommentsPlatformEffect.Summarize) requestSummary() else platform(effect)
        }
    }

    private fun platform(effect: CommentsPlatformEffect) {
        mutableEffects.tryEmit(CommentsRuntimeEffect.Platform(effect))
    }

    private fun resolveArchive(provider: ArchiveProvider) {
        val resolver = archiveUrlResolver
        val articleUrl = story?.url.orEmpty()
        if (resolver == null) {
            platform(CommentsPlatformEffect.ShowMessage("Archive lookup is unavailable"))
            return
        }
        if (articleUrl.isBlank()) return
        if (provider == ArchiveProvider.ORG) {
            platform(CommentsPlatformEffect.ShowMessage("Contacting archive.org API..."))
        }
        scope.launch {
            try {
                platform(CommentsPlatformEffect.OpenExternalLink(resolver.resolve(provider, articleUrl)))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                platform(
                    CommentsPlatformEffect.ShowMessage(
                        "Error: ${error.message ?: "Couldn't open archive"}",
                    ),
                )
            }
        }
    }

    private fun changed(refreshNavigation: Boolean = false) {
        mutableEffects.tryEmit(CommentsRuntimeEffect.StateChanged(refreshNavigation))
    }

    private fun applyPresenterEffect(effect: CommentsEffect) {
        when (effect) {
            is CommentsEffect.ShowCommentActions ->
                mutableEffects.tryEmit(CommentsRuntimeEffect.ShowCommentActions(effect.comment))
            is CommentsEffect.ThreadApplied -> {
                if (!presenter.isCurrentThreadLoad(effect.requestId, effect.storyId) ||
                    story?.id != effect.storyId
                ) return
                if (effect.usedOfficialFallback) {
                    platform(CommentsPlatformEffect.ShowMessage("Algolia API failed, using official HN API"))
                }
                effect.responseToCache?.let { response ->
                    storeCachedThread(effect.storyId, response)
                }
                if (effect.broadcastStoryUpdate) {
                    story?.let(publishStoryUpdate)
                }
                if (effect.contentApplied) {
                    reconcileSettings()
                    platform(CommentsPlatformEffect.ReloadLinkPreviews)
                    reloadPollOptions()
                    changed(refreshNavigation = true)
                    mutableEffects.tryEmit(
                        CommentsRuntimeEffect.ThreadReady(
                            restoreScroll = effect.restoreScroll,
                            headerChanged = effect.headerChanged,
                        ),
                    )
                }
            }
            is CommentsEffect.ThreadFailed -> {
                mutableEffects.tryEmit(
                    CommentsRuntimeEffect.Diagnostic(
                        message = "${effect.result.source} comments load failed for " +
                            "storyId=${effect.storyId}, noInternet=${effect.result.noInternet}",
                        cause = effect.result.cause,
                    ),
                )
                changed()
            }
            is CommentsEffect.PollOptionsChanged -> {
                effect.failedOptionId?.let { failedId ->
                    mutableEffects.tryEmit(
                        CommentsRuntimeEffect.Diagnostic("Poll option request failed for id=$failedId"),
                    )
                }
                if (story?.id == effect.storyId) changed()
            }
            is CommentsEffect.PollOptionsLookupFailed -> {
                if (story?.id == effect.storyId) {
                    mutableEffects.tryEmit(
                        CommentsRuntimeEffect.Diagnostic(
                            "Poll lookup failed for id=${effect.storyId}",
                            effect.cause,
                        ),
                    )
                }
            }
            is CommentsEffect.PollVoteStarted -> changed()
            is CommentsEffect.PollVoteCompleted -> {
                changed()
                when (val outcome = effect.outcome) {
                    PollVoteOutcome.Success ->
                        platform(CommentsPlatformEffect.ShowMessage("Poll vote successful"))
                    is PollVoteOutcome.Failure -> mutableEffects.tryEmit(
                        CommentsRuntimeEffect.ActionFailed(
                            ActionFailurePresentation(
                                result = outcome.result,
                                message = "Vote unsuccessful, see dialog for response",
                                showDetails = true,
                                requestLoginIfMissing = true,
                            ),
                        ),
                    )
                }
            }
            is CommentsEffect.SavedItemActionStarted -> changed()
            is CommentsEffect.SavedItemActionCompleted -> {
                changed()
                (effect.outcome as? SavedItemActionOutcome.Failure)?.let { failure ->
                    when (effect.request) {
                        is CommentsSavedItemRequest.CommentFavorite,
                        is CommentsSavedItemRequest.StoryFavorite,
                        -> mutableEffects.tryEmit(
                            CommentsRuntimeEffect.ActionFailed(
                                ActionFailurePresentation(
                                    result = failure.result,
                                    message = if (!failure.action.previousPresent) {
                                        "Couldn't add favorite"
                                    } else {
                                        "Couldn't update favorite"
                                    },
                                    showDetails = failure.action.previousPresent,
                                    requestLoginIfMissing = true,
                                ),
                            ),
                        )
                        is CommentsSavedItemRequest.CommentVote,
                        is CommentsSavedItemRequest.StoryVote,
                        -> mutableEffects.tryEmit(
                            CommentsRuntimeEffect.ActionFailed(
                                ActionFailurePresentation(
                                    result = failure.result,
                                    message = "Vote unsuccessful, see dialog for response",
                                    showDetails = true,
                                    requestLoginIfMissing = true,
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun applySummaryState(state: StorySummaryState) {
        if (state.generation == 0L) return
        val currentStory = story ?: return
        currentStory.summary = state.text
        when (state.status) {
            StorySummaryStatus.Idle,
            StorySummaryStatus.Running,
            -> Unit
            StorySummaryStatus.Success -> currentStory.summaryGeneratedSuccessfully = true
            is StorySummaryStatus.Failure -> currentStory.summaryGeneratedSuccessfully = false
        }
        changed()
    }

    private val sorting: String
        get() = thread.state.value.sorting.orEmpty()
}
