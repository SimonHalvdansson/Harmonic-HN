package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
fun AndroidCommentsSearchDialog(
    controller: CommentsScreenController,
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
    CommentsSearchDialog(
        preparing = controller.searchPreparing,
        searchTerm = searchTerm,
        visibleComments = visibleComments,
        settings = settings,
        storyAuthor = storyAuthor,
        accountUser = accountUser,
        maxDialogHeight = maxDialogHeight,
        onSearchTermChanged = onSearchTermChanged,
        onDismiss = {
            if (controller.linkPreviewOverlay != null) {
                controller.requestDismissLinkPreview()
            } else {
                onDismiss()
            }
        },
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
        foreground = {
            // Register with the search dialog's dispatcher, above its own Back handler.
            SearchLinkPreviewBackHandler(controller)
            AndroidCommentLinkPreviewOverlay(controller)
        },
    )
}
