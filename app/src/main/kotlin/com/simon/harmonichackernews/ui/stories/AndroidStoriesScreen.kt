@file:OptIn(

    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.simon.harmonichackernews.ui.stories



import com.simon.harmonichackernews.settings.DisplayStyle

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemStyle
import com.simon.harmonichackernews.ui.common.rememberAndroidHarmonicFilterColors
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.settings.TextPreferences
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot

/**
 * Compose presentation bridge for the stories screen. The coordinator remains the data/network
 * controller during this migration; adapter notifications are converted to immutable snapshots.
 */
@Composable
internal fun AndroidStoriesScreen(
    controller: StoriesComposeController,
    mainListState: LazyListState,
    onVisibleStoriesChanged: (List<StoryListItemSnapshot>) -> Unit,
) {
    val tintStore = LocalHarmonicUiDependencies.current.storyResourceTints
    StoriesRoute(
        controller = controller,
        mainListState = mainListState,
        showTapToUpdateButton = false,
        tintStore = tintStore,
        filterColors = rememberAndroidHarmonicFilterColors(),
        onVisibleStoriesChanged = onVisibleStoriesChanged,
    )
}

@Preview(name = "Phone", device = Devices.PIXEL_7, showBackground = true)
@Preview(name = "Fold inner", widthDp = 673, heightDp = 841, showBackground = true)
@Preview(name = "Tablet pane", widthDp = 600, heightDp = 960, showBackground = true)
@Composable
private fun StoryItemFormFactorPreview() {
    HarmonicTheme {
        StoryItem(
            model = SettingsStoryPreviewModel,
            style = StoryItemStyle(
                previewImageMode = StoryPreviewMode.MEDIUM,
                borderlessLargeImage = false,
                compact = false,
                showPreviewText = true,
                showFavicon = true,
                showPoints = true,
                compactPoints = false,
                includeTopLevelDomain = true,
                showCommentCount = true,
                showIndex = true,
                commentsOnLeft = false,
                tintCard = true,
                displayStyle = DisplayStyle.STANDARD,
                useHotnessIcon = false,
                preferredFont = "googlesansflexrounded",
                textSize = TextPreferences.DEFAULT_STORY_TEXT_SIZE,
            ),
        )
    }
}
