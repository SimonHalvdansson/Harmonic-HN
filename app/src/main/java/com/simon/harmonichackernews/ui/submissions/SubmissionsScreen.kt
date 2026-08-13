package com.simon.harmonichackernews.ui.submissions

import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

/** Android host hook for the shared submissions route. */
@Composable
internal fun SubmissionsScreen(controller: SubmissionsComposeController) {
    val app = LocalHarmonicUiDependencies.current
    SharedSubmissionsRoute(
        controller = controller,
        previewService = app.previewResources,
        tintStore = app.storyResourceTints,
        onOpenLink = { app.links.open(it) },
    )
}
