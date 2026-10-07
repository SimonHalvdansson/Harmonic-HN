package com.simon.harmonichackernews.ui.submissions

import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.data.StoryResourceTintStore
import com.simon.harmonichackernews.network.StoryPreviewResourceService
import com.simon.harmonichackernews.presentation.StoryListResourceRuntime
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.SubmissionsFeatureStore
import com.simon.harmonichackernews.presentation.SubmissionsIntent
import com.simon.harmonichackernews.presentation.SubmissionsScrollRestoration
import com.simon.harmonichackernews.ui.content.rememberSubmissionStoryRowModel

/** Portable submissions Compose bridge; hosts provide only the final URL effect. */
@Composable
fun SubmissionsRoute(
    userName: String,
    store: SubmissionsFeatureStore,
    displaySettings: StoryDisplaySettings,
    initialScrollRestoration: SubmissionsScrollRestoration?,
    previewService: StoryPreviewResourceService,
    tintStore: StoryResourceTintStore,
    includeStatusBarInset: Boolean = true,
    reserveBackButtonSpace: Boolean = false,
    pullToRefreshEnabled: Boolean = true,
    onOpenLink: (String) -> Unit,
) {
    val state by store.state.collectAsStateWithLifecycle()
    val onIntent = remember(store) { { intent: SubmissionsIntent -> store.accept(intent) } }
    val scope = rememberCoroutineScope()
    val previewResources = remember(scope, previewService, tintStore) {
        StoryListResourceRuntime(
            scope = scope,
            service = previewService,
            settings = displaySettings,
            tintStore = tintStore,
        )
    }
    SideEffect { previewResources.updateSettings(displaySettings) }
    val states by previewResources.statesFlow.collectAsStateWithLifecycle()
    DisposableEffect(previewResources) { onDispose(previewResources::dispose) }
    val tintBaseColor = HarmonicTheme.targetColorScheme.cardBackground.toArgb()
    key(store) {
        SubmissionsScreen(
            userName = userName,
            state = state,
            displaySettings = displaySettings,
            initialScrollRestoration = initialScrollRestoration,
            onIntent = onIntent,
            previewResources = previewResources,
            includeStatusBarInset = includeStatusBarInset,
            reserveBackButtonSpace = reserveBackButtonSpace,
            pullToRefreshEnabled = pullToRefreshEnabled,
            storyItemModel = { story, settings ->
                rememberSubmissionStoryRowModel(
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
}
