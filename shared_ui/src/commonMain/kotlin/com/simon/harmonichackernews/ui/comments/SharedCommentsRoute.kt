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
fun SharedCommentsRoute(
    controller: CommentsComposeController,
    listModifier: Modifier = Modifier,
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
    SharedCommentsScreen(
        controller = controller,
        listModifier = listModifier,
        animateComments = commentPreferences.animateChanges,
        showScrollbar = commentPreferences.showScrollbar,
        smoothScroll = commentPreferences.smoothScroll,
        userTags = userTags,
        onOpenLink = { url -> dependencies.links.open(url) },
        headerContent = { settings?.let { headerContent(it) } },
        searchDialog = { settings?.let { searchDialog(it) } },
        actionOverlay = { settings?.let { actionOverlay(it) } },
    )
}
