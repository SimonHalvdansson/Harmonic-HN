package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.resources.*

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.ui.content.CommentItem
import com.simon.harmonichackernews.ui.content.CommentItemStyle
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils

@Composable
fun CommentsSearchDialog(
    searchTerm: String,
    visibleComments: List<Comment>,
    settings: CommentDisplaySettings,
    storyAuthor: String?,
    accountUser: String?,
    onSearchTermChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onCommentSelected: (Comment) -> Unit,
) {
    val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        modifier = Modifier.heightIn(max = maxDialogHeight),
        text = {
            CommentsSearchContent(
                searchTerm = searchTerm,
                onSearchTermChanged = onSearchTermChanged,
                visibleComments = visibleComments,
                settings = settings,
                storyAuthor = storyAuthor,
                accountUser = accountUser,
                onCommentSelected = onCommentSelected,
                requestFocus = true,
            )
        },
        edgeToEdgeContent = true,
        showButtons = false,
    )
}

@Composable
private fun CommentsSearchContent(
    searchTerm: String,
    onSearchTermChanged: (String) -> Unit,
    visibleComments: List<Comment>,
    settings: CommentDisplaySettings,
    storyAuthor: String?,
    accountUser: String?,
    onCommentSelected: (Comment) -> Unit,
    requestFocus: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val itemStyle = remember(settings) {
        CommentItemStyle(
            cardStyle = settings.cardStyle,
            showCardBorder = settings.cardBorder,
            textSize = settings.preferredTextSize,
            collectLinks = false,
            emphasizeMeta = settings.highlightCommentMeta,
            depthIndicatorMode = CommentDepthIndicatorUtils.MODE_NONE,
            showDivider = false,
            preferredFont = settings.font,
            animateChanges = false,
        )
    }

    SharedCommentSearchScreen(
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
            onToggleExpanded = { onCommentSelected(comment) },
            onShowActions = { onCommentSelected(comment) },
            onLinkLongClick = { _, _, _ -> },
            onReferenceLongClick = { _, _ -> },
            onLinkClick = { url -> com.simon.harmonichackernews.utils.Utils.openLinkMaybeHN(context, url) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CommentsSearchContentPreview() {
    val comment = remember {
        Comment().apply {
            id = 1
            by = "pg"
            text = "Compose makes the state transition easier to follow."
            expanded = true
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    HarmonicTheme {
        CommentsSearchContent(
            searchTerm = "state",
            onSearchTermChanged = {},
            visibleComments = listOf(comment),
            settings = CommentDisplaySettings.from(
                AndroidUserSettings(context).comments,
                false,
                false,
                false,
                false,
            ),
            storyAuthor = "dang",
            accountUser = null,
            onCommentSelected = {},
            requestFocus = false,
        )
    }
}
