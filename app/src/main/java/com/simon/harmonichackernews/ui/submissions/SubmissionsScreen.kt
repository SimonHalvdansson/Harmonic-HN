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
import com.simon.harmonichackernews.ui.common.SharedHazeHost
import com.simon.harmonichackernews.ui.common.SharedTranslucentBackButton

/** Android host hook for the shared submissions route. */
@Composable
internal fun SubmissionsScreen(
    controller: SubmissionsComposeController,
    onBack: () -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    SharedHazeHost {
        Box(Modifier.fillMaxSize()) {
            SharedSubmissionsRoute(
                controller = controller,
                previewService = app.previewResources,
                tintStore = app.storyResourceTints,
                reserveBackButtonSpace = true,
                onOpenLink = { app.links.open(it) },
            )
            SharedTranslucentBackButton(
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
