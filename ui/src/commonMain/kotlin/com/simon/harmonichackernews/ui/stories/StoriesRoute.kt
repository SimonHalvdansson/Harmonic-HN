package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.data.StoryResourceTintStore
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.content.storyRowModel
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

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
    onVisibleStoriesChanged: (List<com.simon.harmonichackernews.presentation.StoryListItemSnapshot>) -> Unit = {},
) {
    val tintBaseColor = HarmonicTheme.colors.contentCardBackground.toArgb()
    StoriesScreen(
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
        onVisibleStoriesChanged = onVisibleStoriesChanged,
    )
}
