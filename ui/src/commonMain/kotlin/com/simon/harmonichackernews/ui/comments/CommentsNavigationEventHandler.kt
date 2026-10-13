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
    public override fun onBackCancelled() {
        when (target()) {
            CommentsBackTarget.LINK_PREVIEW ->
                controller()?.cancelLinkPreviewPredictiveBack()
            CommentsBackTarget.COMMENT_ACTION ->
                controller()?.cancelCommentActionPredictiveBack()
            CommentsBackTarget.CLOSE_WEBSITE -> onWebsiteGestureFinished()
            else -> Unit
        }
    }

    public override fun onBackProgressed(event: NavigationEvent) {
        when (target()) {
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
            else -> Unit
        }
    }

    public override fun onBackStarted(event: NavigationEvent) {
        when (target()) {
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
            else -> Unit
        }
    }

    public override fun onBackCompleted() {
        when (target()) {
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
            CommentsBackTarget.CLOSE_WEBSITE -> {
                controller()?.requestExpandSheet()
                onWebsiteGestureFinished()
            }
            CommentsBackTarget.WEB_HISTORY ->
                onWebHistoryBack()
            CommentsBackTarget.NONE -> onCloseStory()
        }
    }
}
