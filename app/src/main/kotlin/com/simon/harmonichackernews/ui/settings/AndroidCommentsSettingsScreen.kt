package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
fun AndroidCommentsSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onThreadDepthRequested: () -> Unit,
    onUserAvatarsRequested: () -> Unit,
) {
    CommentsSettingsRoute(
        repository = LocalHarmonicUiDependencies.current.settings,
        showNavigation = showNavigation,
        onBack = onBack,
        onThreadDepthRequested = onThreadDepthRequested,
        onUserAvatarsRequested = onUserAvatarsRequested,
    )
}
