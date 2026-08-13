package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
fun CommentsSearchDialog(
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
        onOpenLink = { url -> links.open(url) },
    )
}
