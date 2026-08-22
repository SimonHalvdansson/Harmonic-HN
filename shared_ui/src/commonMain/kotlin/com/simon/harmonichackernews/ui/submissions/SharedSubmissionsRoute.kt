package com.simon.harmonichackernews.ui.submissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.data.StoryResourceTintStore
import com.simon.harmonichackernews.network.StoryPreviewResourceService
import com.simon.harmonichackernews.presentation.StoryListResourceRuntime
import com.simon.harmonichackernews.ui.content.rememberSubmissionStoryItemUiModel
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

/** Portable submissions Compose bridge; hosts provide only the final URL effect. */
@Composable
fun SharedSubmissionsRoute(
    controller: SubmissionsComposeController,
    previewService: StoryPreviewResourceService,
    tintStore: StoryResourceTintStore,
    includeStatusBarInset: Boolean = true,
    reserveBackButtonSpace: Boolean = false,
    pullToRefreshEnabled: Boolean = true,
    onOpenLink: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val previewResources = remember(scope, previewService, tintStore) {
        StoryListResourceRuntime(
            scope = scope,
            service = previewService,
            settings = controller.displaySettings,
            tintStore = tintStore,
        )
    }
    SideEffect { previewResources.updateSettings(controller.displaySettings) }
    val states by previewResources.statesFlow.collectAsState()
    DisposableEffect(previewResources) { onDispose(previewResources::dispose) }
    val tintBaseColor = HarmonicTheme.colors.storyCardBackground.toArgb()
    SharedSubmissionsScreen(
        controller = controller,
        previewResources = previewResources,
        includeStatusBarInset = includeStatusBarInset,
        reserveBackButtonSpace = reserveBackButtonSpace,
        pullToRefreshEnabled = pullToRefreshEnabled,
        storyItemModel = { story, settings ->
            rememberSubmissionStoryItemUiModel(
                story = story,
                settings = settings,
                previewState = states[story.id],
                previewResources = previewResources,
                tintBaseColor = tintBaseColor,
            )
        },
        onOpenLink = onOpenLink,
    )
}
