package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.data.StoryResourceTintStore
import com.simon.harmonichackernews.presentation.CommentPreloadCandidate
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.content.storyRowModel

/** Portable stories Compose bridge; host inputs are limited to form-factor resource flags. */
@Composable
fun StoriesRoute(
    controller: StoriesScreenController,
    mainListState: LazyListState = rememberLazyListState(),
    showTapToUpdateButton: Boolean = true,
    tintStore: StoryResourceTintStore,
    filterColors: HarmonicFilterButtonColors,
    pullToRefreshEnabled: Boolean = true,
    showRefreshMenuItem: Boolean = false,
    onVisibleStoriesChanged: ((List<CommentPreloadCandidate>) -> Unit)? = null,
) {
    val dependencies = LocalHarmonicUiDependencies.current
    val menuHintPending by dependencies.launchState.storiesMenuHintPending.collectAsStateWithLifecycle()
    val navigation by dependencies.navigation.state.collectAsStateWithLifecycle()
    val preloadEnabled = if (onVisibleStoriesChanged != null) {
        val settings = dependencies.settings
        val initialPreloadEnabled = remember(settings) { settings.snapshot().comments.preloadCommentsFromStories }
        remember(settings) {
            settings.updates.map { it.comments.preloadCommentsFromStories }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialPreloadEnabled).value
    } else false
    val tintBaseColor = HarmonicTheme.targetColorScheme.cardBackground.toArgb()
    StoriesScreen(
        categorySettings = dependencies.articleCategorySettings,
        categoryRuntimeService = dependencies.articleCategories,
        userTags = dependencies.userTags,
        controller = controller,
        mainListState = mainListState,
        showTapToUpdateButton = showTapToUpdateButton,
        storyItemModelCacheKey = tintBaseColor,
        storyItemModel = { story, position, settings, previewResource, nowMillis ->
            storyRowModel(
                story,
                position,
                settings,
                previewResource,
                tintBaseColor,
                tintStore,
                nowMillis,
            )
        },
        filterColors = filterColors,
        pullToRefreshEnabled = pullToRefreshEnabled,
        showRefreshMenuItem = showRefreshMenuItem,
        onVisibleStoriesChanged = onVisibleStoriesChanged.takeIf { preloadEnabled },
        showWelcomeHint = menuHintPending && navigation.currentDestination == MainDestination.STORIES &&
            !controller.searching && !controller.headerMenuVisible,
        onDismissWelcomeHint = dependencies.launchState::dismissStoriesMenuHint,
    )
}
