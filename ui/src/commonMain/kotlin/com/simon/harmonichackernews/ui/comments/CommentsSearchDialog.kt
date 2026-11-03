package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.common.rememberTimeOnResume
import com.simon.harmonichackernews.data.ItemTimeFormatter
import com.simon.harmonichackernews.ui.content.CommentRowStyleContext
import com.simon.harmonichackernews.ui.content.toCommentRowStyle
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.SettingsDialogDismissState
import com.simon.harmonichackernews.ui.settings.CompleteSettingsDialogDismissal
import com.simon.harmonichackernews.ui.common.PlatformDisableDialogWindowAnimations
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
    preparing: Boolean = false,
) {
    val dismissal = remember { SettingsDialogDismissState() }
    // The native dialog has its own saveable registry. Keep its draft and scroll in the host's
    // registry so rebuilding the window on rotation does not discard an active search.
    // Retain TextFieldValue rather than round-tripping an asynchronously published String:
    // that would reset the desktop cursor between closely spaced key events.
    var fieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(searchTerm, TextRange(searchTerm.length)))
    }
    val listState = rememberLazyListState()
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        // A recreated result store starts empty; restore its query from the saved field draft.
        if (fieldValue.text != searchTerm) onSearchTermChanged(fieldValue.text)
    }
    SettingsAlertDialog(
        onDismissRequest = { dismissal.request(onDismiss) },
        confirmButton = {},
        modifier = Modifier.heightIn(max = maxDialogHeight),
        // Filled cards share surfaceContainerHigh in dark themes.
        containerColor = if (HarmonicTheme.isDark) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        text = {
            PlatformDisableDialogWindowAnimations()
            CompleteSettingsDialogDismissal(dismissal)
            // animateContentSize clips its bounds; keep it inside the elevated surface so
            // the dialog's shadow can draw outside those bounds throughout resizing.
            Box(
                Modifier.animateContentSize(
                    animationSpec = tween(
                        durationMillis = SearchDialogSizeDurationMillis,
                        easing = FastOutSlowInEasing,
                    ),
                    alignment = Alignment.TopCenter,
                ),
            ) {
                CommentsSearchContent(
                    searchTerm = searchTerm,
                    fieldValue = fieldValue,
                    onFieldValueChanged = {
                        if (!dismissal.isClosing) {
                            fieldValue = it
                            onSearchTermChanged(it.text)
                        }
                    },
                    listState = listState,
                    scrollState = scrollState,
                    visibleComments = visibleComments,
                    settings = settings,
                    storyAuthor = storyAuthor,
                    accountUser = accountUser,
                    onCommentSelected = { comment -> dismissal.request { onCommentSelected(comment) } },
                    onOpenLink = onOpenLink,
                    onLinkLongClick = onLinkLongClick,
                    onReferenceLongClick = onReferenceLongClick,
                    requestFocus = !dismissal.isClosing,
                    preparing = preparing,
                )
            }
        },
        edgeToEdgeContent = true,
        scrollableContent = true,
        showButtons = false,
        foreground = foreground,
    )
}

@Composable
fun CommentsSearchContent(
    searchTerm: String,
    fieldValue: TextFieldValue,
    onFieldValueChanged: (TextFieldValue) -> Unit,
    listState: LazyListState,
    scrollState: ScrollState,
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
    preparing: Boolean = false,
) {
    val itemStyle = remember(settings) {
        settings.toCommentRowStyle(CommentRowStyleContext.Search)
    }
    val rowTypography = rememberContentTypography(
        preferredFont = itemStyle.preferredFont,
        commentTextSize = itemStyle.textSize,
    )
    val resumedAt = rememberTimeOnResume()

    CommentSearchScreen(
        fieldValue = fieldValue,
        onFieldValueChanged = onFieldValueChanged,
        listState = listState,
        scrollState = scrollState,
        visibleComments = visibleComments,
        mutedColor = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = ProductSansFontFamily,
        requestFocus = requestFocus,
        preparing = preparing,
        collectLinks = settings.collectReferenceLinks,
    ) { comment ->
        CommentRow(
            comment = comment,
            style = itemStyle,
            typography = rowTypography,
            age = remember(comment.time, resumedAt) { ItemTimeFormatter.formatNow(comment.time) },
            storyAuthor = storyAuthor,
            accountUser = accountUser,
            userTag = null,
            subtreeReplyCount = 0,
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
