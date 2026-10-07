package com.simon.harmonichackernews.ui.stories

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.data.StoryResourceTintStore
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
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
    onVisibleStoriesChanged: (List<StoryListItemSnapshot>) -> Unit = {},
) {
    val tintBaseColor = MaterialTheme.colorScheme.cardBackground.toArgb()
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
