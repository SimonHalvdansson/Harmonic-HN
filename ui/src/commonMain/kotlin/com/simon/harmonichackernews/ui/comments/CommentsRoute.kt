package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

internal val LocalCommentStoryCategories = staticCompositionLocalOf<List<String>> { emptyList() }

/**
 * Shared comments feature route. Platform hosts only add interop modifiers and the small pieces
 * that still require native formatting, PDF handling or WebView integration.
 */
@Composable
fun CommentsRoute(
    controller: CommentsScreenController,
    listModifier: Modifier = Modifier,
    reserveUpButtonInset: Boolean = false,
    pullToRefreshEnabled: Boolean = true,
    showNavigationControls: Boolean = true,
    headerContent: @Composable (CommentDisplaySettings) -> Unit,
    searchDialog: @Composable (CommentDisplaySettings) -> Unit,
    actionOverlay: @Composable (CommentDisplaySettings) -> Unit,
    listState: LazyListState = rememberCommentsListState(),
) {
    val dependencies = LocalHarmonicUiDependencies.current
    val settings = controller.displaySettings
    val categoryPreferences by dependencies.articleCategorySettings.updates.collectAsStateWithLifecycle(
        initialValue = dependencies.articleCategorySettings.snapshot())
    val categoryRuntime by dependencies.articleCategories.state.collectAsStateWithLifecycle()
    val story = controller.story
    val categoryDescription = story.presentation.linkSummaryDescription
    val categoryLabels = com.simon.harmonichackernews.ui.content.rememberArticleCategoryLabels(
        storyId = story.id, title = story.title, url = story.url, description = categoryDescription,
        preferences = categoryPreferences, runtime = dependencies.articleCategories, runtimeState = categoryRuntime,
        eligible = !story.isFrontpageLink, requestDelayMillis = 0,
        cachedDescription = { story.url?.let { dependencies.previewResources.cachedLinkSummary(it)?.description } },
    )
    val commentPreferences = dependencies.userSettings.comments
    val userTags = remember(controller.contentVersion, dependencies.userTags) {
        dependencies.userTags.tags()
    }
    val openLink: (String) -> Unit = remember(dependencies.links) {
        { url: String -> dependencies.links.open(url).let { } }
    }
    CommentsScreen(
        controller = controller,
        listState = listState,
        listModifier = listModifier,
        reserveUpButtonInset = reserveUpButtonInset,
        pullToRefreshEnabled = pullToRefreshEnabled,
        showNavigationControls = showNavigationControls,
        animateComments = commentPreferences.animateChanges,
        showScrollbar = commentPreferences.showScrollbar,
        smoothScroll = commentPreferences.smoothScroll,
        userTags = userTags,
        // Keep the callback stable so CommentBodyText can retain its parsed AnnotatedString when
        // loading, voting, sheet progress, or another screen-level state causes recomposition.
        onOpenLink = openLink,
        headerContent = { CompositionLocalProvider(LocalCommentStoryCategories provides categoryLabels) {
            settings?.let { headerContent(it) }
        } },
        searchDialog = { settings?.let { searchDialog(it) } },
        actionOverlay = { settings?.let { actionOverlay(it) } },
    )
}
