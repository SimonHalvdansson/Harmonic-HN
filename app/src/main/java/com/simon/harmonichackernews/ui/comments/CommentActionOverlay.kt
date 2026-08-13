package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.utils.AndroidLinkNavigation

@Composable
internal fun CommentActionOverlay(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
) {
    val context = LocalContext.current
    val dependencies = LocalHarmonicUiDependencies.current
    SharedCommentActionOverlay(
        controller = controller,
        settings = settings,
        hasAccount = dependencies.platform.accounts.load() != null,
        bookmarksEnabled = dependencies.userSettings.general.bookmarksEnabled,
        textStyle = commentActionLegacyTextStyle,
        onOpenLink = { url -> AndroidLinkNavigation.openMaybeHackerNews(context, url) },
    )
}

private val commentActionLegacyTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)
