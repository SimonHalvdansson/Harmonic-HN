package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.app.CommentsFeatureHost
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.app.createCommentsStore
import com.simon.harmonichackernews.navigation.MainStoryRequest
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.toStory
import com.simon.harmonichackernews.presentation.CommentTargetResolution
import com.simon.harmonichackernews.presentation.CommentsPlatformEffect
import com.simon.harmonichackernews.presentation.CommentsPresentationCapabilities
import com.simon.harmonichackernews.presentation.CommentsRuntimeEffect
import com.simon.harmonichackernews.presentation.CommentsStore
import com.simon.harmonichackernews.presentation.CommentsSessionState
import com.simon.harmonichackernews.presentation.CommentsPerformanceTrace
import com.simon.harmonichackernews.presentation.CommentsScrollRestoration
import kotlinx.coroutines.CoroutineScope

/** Shared store/controller assembly used by non-Android Compose hosts. */
class CommentsFeatureBinding private constructor(
    val store: CommentsStore,
    val controller: CommentsComposeController,
    private var restoringStoredProgress: Boolean,
    private val sessionState: CommentsSessionState,
) {
    val story get() = sessionState.story
    private var beforeWebsiteCollapse: () -> Unit = {}
    private var deferredReadingPosition: CommentsScrollRestoration? = null
    private var presentation = CommentsPlatformPresentation(
        adBlockActive = false,
        readerModeAvailable = false,
        readerModeEnabled = false,
        topInsetPx = 0,
        contentInsetLeftPx = 0,
        contentInsetRightPx = 0,
    )

    fun updateContent(platform: CommentsPlatformPresentation) {
        presentation = platform
        syncContent()
    }

    private fun syncContent() {
        CommentsScreenStateFactory.create(store.state.value, presentation)?.let(controller::updateContent)
    }

    private fun restoreDeferredReadingPosition() {
        val position = deferredReadingPosition ?: return
        deferredReadingPosition = null
        syncContent()
        controller.restoreReadingPosition(position.commentId, position.offset)
    }

    fun setBeforeWebsiteCollapse(callback: () -> Unit) {
        beforeWebsiteCollapse = callback
    }

    fun updatePresentationCapabilities(isTablet: Boolean) {
        store.updatePresentationCapabilities(
            CommentsPresentationCapabilities(showInvertAction = false, isTablet = isTablet),
        )
    }

    fun loadInitial() {
        store.loadInitial(restoreScrollFromCache = restoringStoredProgress)
    }

    fun handleEffect(
        effect: CommentsRuntimeEffect,
        scene: HarmonicSceneComposition,
        onSummaryPageTextRetry: () -> Unit = {},
        onPlatformEffect: (CommentsPlatformEffect) -> Unit,
    ) {
        when (effect) {
            is CommentsRuntimeEffect.Platform -> onPlatformEffect(effect.effect)
            is CommentsRuntimeEffect.ShowCommentActions -> controller.showCommentActions(effect.comment)
            is CommentsRuntimeEffect.ThreadReady -> {
                if (restoringStoredProgress) {
                    store.restoreScrollProgress()?.let { restoration ->
                        // Install the restored thread before issuing a visual scroll request.
                        // Otherwise Compose can consume that request against the previous empty list.
                        syncContent()
                        if (presentation.showSheetControls && controller.integratedWebView &&
                            controller.sheetSlideOffset < 0.99f
                        ) {
                            // Keep browser controls visible at the top of the collapsed sheet.
                            // Restore the comments position when the reader expands that sheet.
                            deferredReadingPosition = restoration
                            controller.completeInitialScrollRestoration()
                        } else {
                            controller.restoreReadingPosition(restoration.commentId, restoration.offset)
                        }
                    }
                }
                restoringStoredProgress = false
                when (val target = store.consumeCommentTarget()) {
                    is CommentTargetResolution.Found -> {
                        deferredReadingPosition = null
                        syncContent()
                        controller.requestExpandSheet()
                        controller.restoreReadingPosition(target.commentId, 0)
                    }
                    is CommentTargetResolution.NotFound ->
                        scene.userMessages.show("Comment not found")
                    CommentTargetResolution.None -> Unit
                }
                if (controller.scrollToCommentRequest == null) controller.completeInitialScrollRestoration()
            }
            is CommentsRuntimeEffect.ActionFailed -> {
                if (effect.presentation.requestLogin) scene.navigation.showLoginDialog()
                if (effect.presentation.showDetails) {
                    scene.navigation.showFailureDetailDialog(
                        effect.presentation.failureSummary,
                        effect.presentation.failureDetail,
                        null,
                    )
                } else {
                    scene.userMessages.show(effect.presentation.message)
                }
            }
            is CommentsRuntimeEffect.Diagnostic -> effect.cause?.printStackTrace()
            is CommentsRuntimeEffect.StateChanged -> Unit
            CommentsRuntimeEffect.RequestSummaryPageTextRetry -> onSummaryPageTextRetry()
        }
    }

    fun close() {
        // Leaving before restoration finishes must not overwrite the retained reading state.
        if (!restoringStoredProgress && !controller.initialScrollRestorationPending) store.captureCollapsedComments()
        store.close()
        beforeWebsiteCollapse = {}
    }

    companion object {
        fun create(
            app: HarmonicAppComposition,
            scene: HarmonicSceneComposition,
            request: MainStoryRequest,
            scope: CoroutineScope,
            canLoadArticleTextOnDemand: Boolean = false,
            performanceTrace: CommentsPerformanceTrace = CommentsPerformanceTrace(),
        ): CommentsFeatureBinding {
            val sessionState = scene.sessions.commentsStateFor(request.serial, request.storyId)
            val restoring = sessionState.initialized
            val restoreProgress = sessionState.scrollProgress.initialized &&
                (restoring || scene.navigation.state.value.storyStackParentDestination !=
                    MainDestination.SUBMISSIONS)
            val store = app.createCommentsStore(
                CommentsFeatureHost(
                    scope = scope,
                    sessionState = sessionState,
                    platform = app.commentsPlatformDependencies(),
                    userSettings = app.userSettings,
                    canLoadArticleTextOnDemand = canLoadArticleTextOnDemand,
                    performanceTrace = performanceTrace,
                ),
            )
            store.start(
                initialStory = request.destination.toStory(),
                showWebsite = request.destination.showWebsite,
                scrollToCommentId = request.route.scrollToCommentId,
                restoring = restoring,
                restoredSorting = null,
            )
            lateinit var binding: CommentsFeatureBinding
            lateinit var controller: CommentsComposeController
            val callbacks = object : CommentsFeatureListener.PlatformCallbacks {
                override fun isRestoringScroll(): Boolean = binding.restoringStoredProgress ||
                    controller.initialScrollRestorationPending || binding.deferredReadingPosition != null
                override fun canHandleCommentAction(): Boolean = true
                override fun onCommentActionOverlayVisibilityChanged() = Unit
                override fun onLinkPreviewOverlayVisibilityChanged() = Unit
                override fun scrollToSearchResult(commentId: Int) {
                    controller.scrollToSearchResult(commentId)
                }

                override fun collapseSheetForWebsite() {
                    binding.beforeWebsiteCollapse()
                    controller.requestCollapseSheet()
                }

                override fun onSheetProgressChanged(expandedFraction: Float) = Unit
                override fun onSheetSettled(expanded: Boolean) {
                    if (expanded) binding.restoreDeferredReadingPosition()
                }
                override fun onHeaderColorChanged(color: Int) = Unit
                override fun onHeaderCoverageChanged(coverage: Float) = Unit
            }
            val initialState = checkNotNull(store.state.value.story)
            controller = CommentsComposeController.create(
                shouldSmoothScroll = { store.state.value.settings?.smoothScroll ?: true },
                story = initialState,
                initialThreadCached = store.state.value.initialThreadCached,
                initialScrollRestorationPending = restoreProgress && !request.destination.showWebsite,
                showWebsite = request.destination.showWebsite,
                accountUser = store.state.value.accountUser,
                savedItemState = store.savedItemState,
                listener = CommentsFeatureListener(store, callbacks),
            )
            return CommentsFeatureBinding(store, controller, restoreProgress, sessionState)
                .also { binding = it }
        }
    }
}
