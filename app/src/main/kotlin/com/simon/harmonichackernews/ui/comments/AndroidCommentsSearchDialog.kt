package com.simon.harmonichackernews.ui.comments

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import kotlinx.coroutines.CancellationException

@Composable
fun AndroidCommentsSearchDialog(
    controller: CommentsComposeController,
    searchTerm: String,
    visibleComments: List<PortableCommentItem>,
    settings: CommentDisplaySettings,
    storyAuthor: String?,
    accountUser: String?,
    onSearchTermChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onCommentSelected: (PortableCommentItem) -> Unit,
) {
    val links = LocalHarmonicUiDependencies.current.links
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxDialogHeight = with(LocalDensity.current) { windowHeight.toDp() * 0.9f }
    PredictiveBackHandler(enabled = controller.linkPreviewOverlay != null) { events ->
        var predictiveBackStarted = false
        try {
            events.collect { event ->
                if (predictiveBackStarted) {
                    controller.updateLinkPreviewPredictiveBack(
                        event.progress,
                        event.swipeEdge,
                        event.touchY,
                    )
                } else {
                    predictiveBackStarted = true
                    controller.startLinkPreviewPredictiveBack(
                        event.progress,
                        event.swipeEdge,
                        event.touchY,
                    )
                }
            }
            if (predictiveBackStarted) {
                controller.commitLinkPreviewPredictiveBack()
            } else {
                controller.requestDismissLinkPreview()
            }
        } catch (_: CancellationException) {
            if (predictiveBackStarted) controller.cancelLinkPreviewPredictiveBack()
        }
    }
    CommentsSearchDialog(
        preparing = controller.searchPreparing,
        searchTerm = searchTerm,
        visibleComments = visibleComments,
        settings = settings,
        storyAuthor = storyAuthor,
        accountUser = accountUser,
        maxDialogHeight = maxDialogHeight,
        onSearchTermChanged = onSearchTermChanged,
        onDismiss = onDismiss,
        onCommentSelected = onCommentSelected,
        onOpenLink = { url -> links.open(url) },
        onLinkLongClick = { comment, url, title, bounds ->
            controller.showReferencePreview(
                url = url,
                title = title,
                sourceBounds = bounds,
                sourceCommentId = comment.id,
            )
        },
        onReferenceLongClick = { comment, link, bounds, sourceContentLayer ->
            controller.showReferencePreview(
                link = link,
                sourceBounds = bounds,
                sourceCommentId = comment.id,
                sourceContentLayer = sourceContentLayer,
            )
        },
        foreground = { AndroidCommentLinkPreviewOverlay(controller) },
    )
}
