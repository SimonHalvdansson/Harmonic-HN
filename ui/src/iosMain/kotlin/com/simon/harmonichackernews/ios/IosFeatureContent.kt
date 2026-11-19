package com.simon.harmonichackernews.ios

import com.simon.harmonichackernews.ui.session.submissionsViewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
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
internal fun IosSubmissionsContent(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    request: MainSubmissionsRequest,
) {
    val viewModel = submissionsViewModel(scene, request)
    val store = viewModel.store
    val initialScrollRestoration = remember(viewModel) { viewModel.scrollRestoration() }
    val displaySettings = remember(store) {
        StoryDisplaySettings.from(app.userSettings.story).withShowIndex(false)
    }
    val railWidth = iosSideRailWidth()

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
                .background(MaterialTheme.colorScheme.pageBackground),
        ) {
            Box(
                Modifier.fillMaxSize().windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                ),
            ) {
                SubmissionsRoute(
                    userName = request.userName,
                    store = store,
                    displaySettings = displaySettings,
                    initialScrollRestoration = initialScrollRestoration,
                    previewService = app.previewResources,
                    tintStore = app.storyResourceTints,
                    includeStatusBarInset = true,
                    reserveBackButtonSpace = railWidth == 0.dp,
                    onOpenLink = { scene.links.open(it) },
                )
                IosStatusBarProtection(MaterialTheme.colorScheme.pageBackground)
                if (railWidth == 0.dp) {
                    TranslucentBackButton(
                        onClick = scene.navigation::closeSubmissions,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 4.dp)
                            .zIndex(101f),
                    )
                }
            }
            if (railWidth > 0.dp) {
                IosSideRailUpButton(
                    railWidth = railWidth,
                    onClick = scene.navigation::closeSubmissions,
                    modifier = Modifier.align(AbsoluteAlignment.TopRight),
                )
            }
        }
    }
}
