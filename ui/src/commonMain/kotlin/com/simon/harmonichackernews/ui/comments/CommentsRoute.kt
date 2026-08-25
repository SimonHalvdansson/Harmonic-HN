package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

/**
 * Shared comments feature route. Platform hosts only add interop modifiers and the small pieces
 * that still require native formatting, PDF handling or WebView integration.
 */
@Composable
fun CommentsRoute(
    controller: CommentsComposeController,
    listModifier: Modifier = Modifier,
    reserveUpButtonInset: Boolean = false,
    pullToRefreshEnabled: Boolean = true,
    headerContent: @Composable (CommentDisplaySettings) -> Unit,
    searchDialog: @Composable (CommentDisplaySettings) -> Unit,
    actionOverlay: @Composable (CommentDisplaySettings) -> Unit,
) {
    val dependencies = LocalHarmonicUiDependencies.current
    val settings = controller.displaySettings
    val commentPreferences = dependencies.userSettings.comments
    val userTags = remember(controller.contentVersion, dependencies.userTags) {
        dependencies.userTags.tags()
    }
    val openLink: (String) -> Unit = remember(dependencies.links) {
        { url: String -> dependencies.links.open(url).let { } }
    }
    CommentsScreen(
        controller = controller,
        listModifier = listModifier,
        reserveUpButtonInset = reserveUpButtonInset,
        pullToRefreshEnabled = pullToRefreshEnabled,
        animateComments = commentPreferences.animateChanges,
        showScrollbar = commentPreferences.showScrollbar,
        smoothScroll = commentPreferences.smoothScroll,
        userTags = userTags,
        // Keep the callback stable so CommentBodyText can retain its parsed AnnotatedString when
        // loading, voting, sheet progress, or another screen-level state causes recomposition.
        onOpenLink = openLink,
        headerContent = { settings?.let { headerContent(it) } },
        searchDialog = { settings?.let { searchDialog(it) } },
        actionOverlay = { settings?.let { actionOverlay(it) } },
    )
}
