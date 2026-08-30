package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.app.CommentsFeatureHost
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.app.createCommentsStore
import com.simon.harmonichackernews.navigation.MainStoryRequest
import com.simon.harmonichackernews.navigation.toStory
import com.simon.harmonichackernews.presentation.CommentTargetResolution
import com.simon.harmonichackernews.presentation.CommentsPlatformEffect
import com.simon.harmonichackernews.presentation.CommentsPresentationCapabilities
import com.simon.harmonichackernews.presentation.CommentsRuntimeEffect
import com.simon.harmonichackernews.presentation.CommentsStore
import kotlinx.coroutines.CoroutineScope

/** Shared store/controller assembly used by non-Android Compose hosts. */
class CommentsFeatureBinding private constructor(
    val store: CommentsStore,
    val controller: CommentsComposeController,
    private var restoringStoredProgress: Boolean,
) {
    private var beforeWebsiteCollapse: () -> Unit = {}

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
        onPlatformEffect: (CommentsPlatformEffect) -> Unit,
    ) {
        when (effect) {
            is CommentsRuntimeEffect.Platform -> onPlatformEffect(effect.effect)
            is CommentsRuntimeEffect.ShowCommentActions -> controller.showCommentActions(effect.comment)
            is CommentsRuntimeEffect.ThreadReady -> {
                if (effect.restoreScroll && restoringStoredProgress) {
                    store.restoreScrollProgress()?.let { restoration ->
                        controller.scrollToComment(
                            restoration.commentId,
                            restoration.offset,
                            false,
                        )
                    }
                }
                restoringStoredProgress = false
                when (val target = store.consumeCommentTarget()) {
                    is CommentTargetResolution.Found ->
                        controller.scrollToComment(target.commentId, 0, false)
                    is CommentTargetResolution.NotFound ->
                        scene.userMessages.show("Comment not found")
                    CommentTargetResolution.None -> Unit
                }
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
            CommentsRuntimeEffect.RequestSummaryPageTextRetry -> Unit
        }
    }

    fun close() {
        store.captureCollapsedComments()
        store.close()
        beforeWebsiteCollapse = {}
    }

    companion object {
        fun create(
            app: HarmonicAppComposition,
            scene: HarmonicSceneComposition,
            request: MainStoryRequest,
            scope: CoroutineScope,
        ): CommentsFeatureBinding {
            val sessionState = scene.sessions.commentsStateFor(request.serial, request.storyId)
            val restoring = sessionState.initialized
            val store = app.createCommentsStore(
                CommentsFeatureHost(
                    scope = scope,
                    sessionState = sessionState,
                    platform = app.commentsPlatformDependencies(),
                    userSettings = app.userSettings,
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
                override fun isRestoringScroll(): Boolean = binding.restoringStoredProgress
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
                override fun onSheetSettled(expanded: Boolean) = Unit
                override fun onHeaderColorChanged(color: Int) = Unit
                override fun onHeaderCoverageChanged(coverage: Float) = Unit
            }
            val initialState = checkNotNull(store.state.value.story)
            controller = CommentsComposeController.create(
                shouldSmoothScroll = { store.state.value.settings?.smoothScroll ?: true },
                story = initialState,
                initialThreadCached = store.state.value.initialThreadCached,
                showWebsite = request.destination.showWebsite,
                accountUser = store.state.value.accountUser,
                savedItemState = store.savedItemState,
                listener = CommentsFeatureListener(store, callbacks),
            )
            return CommentsFeatureBinding(store, controller, restoring).also { binding = it }
        }
    }
}
