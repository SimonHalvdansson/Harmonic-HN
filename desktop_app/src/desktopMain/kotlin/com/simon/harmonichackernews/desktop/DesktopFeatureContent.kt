package com.simon.harmonichackernews.desktop

import com.simon.harmonichackernews.ui.session.submissionsViewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.navigation.MainSubmissionsRequest
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.SubmissionsFeatureEffect
import com.simon.harmonichackernews.ui.common.HazeHost
import com.simon.harmonichackernews.ui.common.TranslucentBackButton
import com.simon.harmonichackernews.ui.submissions.SubmissionsRoute

@Composable
internal fun DesktopSubmissionsContent(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    request: MainSubmissionsRequest,
    isSplitLayout: Boolean = false,
) {
    val initialFocus = remember(request.serial) { FocusRequester() }
    LaunchedEffect(initialFocus) { initialFocus.requestFocus() }
    val viewModel = submissionsViewModel(scene, request)
    val store = viewModel.store
    val initialScrollRestoration = remember(viewModel) { viewModel.scrollRestoration() }
    val displaySettings = remember(store) {
        StoryDisplaySettings.from(app.userSettings.story).withShowIndex(false)
    }

    LaunchedEffect(store, scene) {
        store.effects.collect { effect ->
            when (effect) {
                is SubmissionsFeatureEffect.OpenStory -> {
                    scene.navigation.openStory(effect.destination)
                }
                is SubmissionsFeatureEffect.OpenExternalLink ->
                    scene.links.openExternal(ExternalLinkRequest(effect.url))
            }
        }
    }

    HazeHost {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.pageBackground)
                .padding(start = if (isSplitLayout) DesktopWidePaneHorizontalPadding else 0.dp)
                // Opening a destination should not keyboard-focus Back and show its tooltip.
                // The screen receives initial focus; Tab still reaches its controls normally.
                .focusRequester(initialFocus)
                .focusable(),
        ) {
            SubmissionsRoute(
                userName = request.userName,
                store = store,
                displaySettings = displaySettings,
                initialScrollRestoration = initialScrollRestoration,
                previewService = app.previewResources,
                tintStore = app.storyResourceTints,
                pullToRefreshEnabled = false,
                reserveBackButtonSpace = true,
                onOpenLink = { scene.links.open(it) },
            )
            TranslucentBackButton(
                onClick = scene.navigation::closeSubmissions,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 8.dp)
                    .zIndex(101f),
            )
        }
    }
}
