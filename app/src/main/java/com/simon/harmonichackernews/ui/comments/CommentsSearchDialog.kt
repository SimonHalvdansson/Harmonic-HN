package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.utils.Utils

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
    val context = LocalContext.current
    SharedCommentsSearchDialog(
        searchTerm = searchTerm,
        visibleComments = visibleComments,
        settings = settings,
        storyAuthor = storyAuthor,
        accountUser = accountUser,
        maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f,
        onSearchTermChanged = onSearchTermChanged,
        onDismiss = onDismiss,
        onCommentSelected = onCommentSelected,
        onOpenLink = { url -> Utils.openLinkMaybeHN(context, url) },
    )
}
