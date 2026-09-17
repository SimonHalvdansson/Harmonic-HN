package com.simon.harmonichackernews.ui.submissions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.HazeHost
import com.simon.harmonichackernews.ui.common.TranslucentBackButton
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.SubmissionsFeatureStore
import com.simon.harmonichackernews.presentation.SubmissionsScrollRestoration

/** Android host hook for the shared submissions route. */
@Composable
internal fun AndroidSubmissionsScreen(
    userName: String,
    store: SubmissionsFeatureStore,
    displaySettings: StoryDisplaySettings,
    initialScrollRestoration: SubmissionsScrollRestoration?,
    onBack: () -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    HazeHost {
        Box(Modifier.fillMaxSize()) {
            SubmissionsRoute(
                userName = userName,
                store = store,
                displaySettings = displaySettings,
                initialScrollRestoration = initialScrollRestoration,
                previewService = app.previewResources,
                tintStore = app.storyResourceTints,
                reserveBackButtonSpace = true,
                onOpenLink = { app.links.open(it) },
            )
            TranslucentBackButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 4.dp)
                    .zIndex(101f),
            )
        }
    }
}
