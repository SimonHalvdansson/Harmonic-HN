package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Dp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.ui.content.CommentItem
import com.simon.harmonichackernews.ui.content.CommentItemStyleContext
import com.simon.harmonichackernews.ui.content.toCommentItemStyle
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.CollectedReferenceLinks

@Composable
fun CommentsSearchDialog(
    searchTerm: String,
    visibleComments: List<PortableCommentItem>,
    settings: CommentDisplaySettings,
    storyAuthor: String?,
    accountUser: String?,
    maxDialogHeight: Dp,
    onSearchTermChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onCommentSelected: (PortableCommentItem) -> Unit,
    onOpenLink: (String) -> Unit,
    onLinkLongClick: (PortableCommentItem, String, String, Rect) -> Unit,
    onReferenceLongClick: (
        PortableCommentItem,
        CollectedReferenceLinks.ReferenceLink,
        Rect,
        GraphicsLayer?,
    ) -> Unit,
    foreground: (@Composable BoxScope.() -> Unit)? = null,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        modifier = Modifier
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = SearchDialogSizeDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
                alignment = Alignment.TopCenter,
            )
            .heightIn(max = maxDialogHeight),
        text = {
            CommentsSearchContent(
                searchTerm = searchTerm,
                onSearchTermChanged = onSearchTermChanged,
                visibleComments = visibleComments,
                settings = settings,
                storyAuthor = storyAuthor,
                accountUser = accountUser,
                onCommentSelected = onCommentSelected,
                onOpenLink = onOpenLink,
                onLinkLongClick = onLinkLongClick,
                onReferenceLongClick = onReferenceLongClick,
                requestFocus = true,
            )
        },
        edgeToEdgeContent = true,
        showButtons = false,
        foreground = foreground,
    )
}

@Composable
fun CommentsSearchContent(
    searchTerm: String,
    onSearchTermChanged: (String) -> Unit,
    visibleComments: List<PortableCommentItem>,
    settings: CommentDisplaySettings,
    storyAuthor: String?,
    accountUser: String?,
    onCommentSelected: (PortableCommentItem) -> Unit,
    onOpenLink: (String) -> Unit,
    onLinkLongClick: (PortableCommentItem, String, String, Rect) -> Unit,
    onReferenceLongClick: (
        PortableCommentItem,
        CollectedReferenceLinks.ReferenceLink,
        Rect,
        GraphicsLayer?,
    ) -> Unit,
    requestFocus: Boolean,
) {
    val itemStyle = remember(settings) {
        settings.toCommentItemStyle(CommentItemStyleContext.Search)
    }

    CommentSearchScreen(
        searchTerm = searchTerm,
        visibleComments = visibleComments,
        mutedColor = HarmonicTheme.colors.storyDisabled,
        fontFamily = ProductSansFontFamily,
        onSearchTermChanged = onSearchTermChanged,
        requestFocus = requestFocus,
    ) { comment ->
        CommentItem(
            comment = comment,
            style = itemStyle,
            storyAuthor = storyAuthor,
            accountUser = accountUser,
            userTag = null,
            hiddenReplyCount = 0,
            collapseParent = false,
            showTopLevelIndicator = false,
            flattenHierarchy = true,
            forceExpanded = true,
            searchTerm = searchTerm,
            animateSearchMatches = true,
            enableLongClick = false,
            onToggleExpanded = { _ -> onCommentSelected(comment) },
            onShowActions = {},
            onLinkLongClick = { url, title, bounds ->
                onLinkLongClick(comment, url, title, bounds)
            },
            onReferenceLongClick = { link, bounds, sourceContentLayer ->
                onReferenceLongClick(comment, link, bounds, sourceContentLayer)
            },
            onLinkClick = onOpenLink,
        )
    }
}

private const val SearchDialogSizeDurationMillis = 300
