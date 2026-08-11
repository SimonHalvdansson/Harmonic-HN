package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils

@Composable
internal fun CommentActionOverlay(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
) {
    val context = LocalContext.current
    SharedCommentActionOverlay(
        controller = controller,
        settings = settings,
        hasAccount = AccountUtils.hasAccountDetails(context),
        bookmarksEnabled = SettingsUtils.shouldUseBookmarks(context),
        textStyle = commentActionLegacyTextStyle,
        onOpenLink = { url -> Utils.openLinkMaybeHN(context, url) },
    )
}

private val commentActionLegacyTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)
