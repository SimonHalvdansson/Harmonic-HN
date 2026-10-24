package com.simon.harmonichackernews.ui.comments

import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import com.simon.harmonichackernews.presentation.CommentsBackTarget

/** Shared overlay/website Back precedence, with native browser operations supplied by the host. */
class CommentsNavigationEventHandler(
    private val target: () -> CommentsBackTarget,
    private val controller: () -> CommentsScreenController?,
    private val onWebsiteGestureProgress: (Float, Boolean) -> Unit,
    private val onWebsiteGestureFinished: () -> Unit,
    private val onHideCustomView: () -> Unit,
    private val onDisableReaderMode: () -> Unit,
    private val onWebHistoryBack: () -> Unit,
    private val onCloseStory: () -> Unit,
) : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, isBackEnabled = true) {
    private var gestureTarget: CommentsBackTarget? = null

    public override fun onBackCancelled() {
        when (gestureTarget ?: target()) {
            CommentsBackTarget.LINK_PREVIEW ->
                controller()?.cancelLinkPreviewPredictiveBack()
            CommentsBackTarget.COMMENT_ACTION ->
                controller()?.cancelCommentActionPredictiveBack()
            CommentsBackTarget.CLOSE_WEBSITE -> onWebsiteGestureFinished()
            CommentsBackTarget.RESTORE_TWO_PANE -> controller()?.browserPaneState?.restoreBackActive = false
            else -> Unit
        }
        gestureTarget = null
    }

    public override fun onBackProgressed(event: NavigationEvent) {
        when (gestureTarget ?: target()) {
            CommentsBackTarget.LINK_PREVIEW -> controller()?.updateLinkPreviewPredictiveBack(
                event.progress,
                event.swipeEdge,
                event.touchY
            )
            CommentsBackTarget.COMMENT_ACTION -> controller()?.updateCommentActionPredictiveBack(
                event.progress,
                event.swipeEdge,
                event.touchY
            )
            CommentsBackTarget.CLOSE_WEBSITE ->
                onWebsiteGestureProgress(event.progress, false)
            CommentsBackTarget.RESTORE_TWO_PANE -> previewPaneRestore(event.progress)
            else -> Unit
        }
    }

    public override fun onBackStarted(event: NavigationEvent) {
        val startedTarget = target()
        gestureTarget = startedTarget
        when (startedTarget) {
            CommentsBackTarget.LINK_PREVIEW -> controller()?.startLinkPreviewPredictiveBack(
                event.progress,
                event.swipeEdge,
                event.touchY
            )
            CommentsBackTarget.COMMENT_ACTION -> controller()?.updateCommentActionPredictiveBack(
                event.progress,
                event.swipeEdge,
                event.touchY
            )
            CommentsBackTarget.CLOSE_WEBSITE ->
                onWebsiteGestureProgress(event.progress, true)
            CommentsBackTarget.RESTORE_TWO_PANE -> previewPaneRestore(event.progress)
            else -> Unit
        }
    }

    public override fun onBackCompleted() {
        val completedTarget = gestureTarget ?: target()
        gestureTarget = null
        when (completedTarget) {
            CommentsBackTarget.LINK_PREVIEW -> {
                if (controller()?.isLinkPreviewPredictiveBackActive() == true) {
                    controller()?.commitLinkPreviewPredictiveBack()
                } else {
                    controller()?.requestDismissLinkPreview()
                }
            }
            CommentsBackTarget.COMMENT_ACTION -> {
                if (controller()?.isCommentActionPredictiveBackActive() == true) {
                    controller()?.commitCommentActionPredictiveBack()
                } else {
                    controller()?.requestDismissCommentActions()
                }
            }
            CommentsBackTarget.CUSTOM_WEB_CONTENT ->
                onHideCustomView()
            CommentsBackTarget.READER_MODE -> onDisableReaderMode()
            CommentsBackTarget.RESTORE_TWO_PANE -> controller()?.browserPaneState?.restoreFromBack?.invoke()
            CommentsBackTarget.CLOSE_WEBSITE -> {
                controller()?.requestExpandSheetFromBack()
                onWebsiteGestureFinished()
            }
            CommentsBackTarget.WEB_HISTORY ->
                onWebHistoryBack()
            CommentsBackTarget.NONE -> onCloseStory()
        }
    }

    private fun previewPaneRestore(progress: Float) {
        controller()?.browserPaneState?.let {
            it.restoreBackProgress = progress.coerceIn(0f, 1f)
            it.restoreBackActive = true
        }
    }
}
