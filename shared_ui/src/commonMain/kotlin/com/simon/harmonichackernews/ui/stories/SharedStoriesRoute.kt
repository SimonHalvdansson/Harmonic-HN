package com.simon.harmonichackernews.ui.stories

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import com.simon.harmonichackernews.data.StoryResourceTintStore
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.content.storyItemUiModel
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

/** Portable stories Compose bridge; host inputs are limited to form-factor resource flags. */
@Composable
fun SharedStoriesRoute(
    controller: StoriesComposeController,
    tintStore: StoryResourceTintStore,
    commentText: (String) -> AnnotatedString,
    filterColors: HarmonicFilterButtonColors,
    extraCompactSelectedText: Boolean,
    compactSelectedText: Boolean,
    pullToRefreshEnabled: Boolean = true,
    showRefreshMenuItem: Boolean = false,
    onVisibleStoriesChanged: (List<com.simon.harmonichackernews.presentation.StoryListItemSnapshot>) -> Unit = {},
) {
    val tintBaseColor = HarmonicTheme.colors.storyCardBackground.toArgb()
    SharedStoriesScreen(
        controller = controller,
        storyItemModelCacheKey = tintBaseColor,
        storyItemModel = { story, position, settings, previewResource, nowMillis ->
            storyItemUiModel(
                story,
                position,
                settings,
                previewResource,
                tintBaseColor,
                tintStore,
                nowMillis,
            )
        },
        commentText = commentText,
        filterColors = filterColors,
        extraCompactSelectedText = extraCompactSelectedText,
        compactSelectedText = compactSelectedText,
        pullToRefreshEnabled = pullToRefreshEnabled,
        showRefreshMenuItem = showRefreshMenuItem,
        onVisibleStoriesChanged = onVisibleStoriesChanged,
    )
}
