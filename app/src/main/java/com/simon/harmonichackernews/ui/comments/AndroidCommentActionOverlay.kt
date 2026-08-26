package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.platform.accountOrNull
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
internal fun AndroidCommentActionOverlay(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
) {
    val dependencies = LocalHarmonicUiDependencies.current
    val accountState by dependencies.platform.accounts.accountState.collectAsState()
    CommentActionOverlay(
        controller = controller,
        settings = settings,
        hasAccount = accountState.accountOrNull != null,
        bookmarksEnabled = dependencies.userSettings.general.bookmarksEnabled,
        textStyle = commentActionLegacyTextStyle,
        onOpenLink = { url -> dependencies.links.open(url) },
    )
}

private val commentActionLegacyTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)
