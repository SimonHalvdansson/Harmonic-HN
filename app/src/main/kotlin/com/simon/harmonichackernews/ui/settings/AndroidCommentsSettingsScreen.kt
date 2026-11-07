package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
fun AndroidCommentsSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onThreadDepthRequested: () -> Unit,
    onQuoteHighlightingRequested: () -> Unit,
    onUserAvatarsRequested: () -> Unit,
    twoPane: Boolean = false,
) {
    CommentsSettingsRoute(
        repository = LocalHarmonicUiDependencies.current.settings,
        swipeBackSupported = true,
        twoPane = twoPane,
        showNavigation = showNavigation,
        onBack = onBack,
        onThreadDepthRequested = onThreadDepthRequested,
        onQuoteHighlightingRequested = onQuoteHighlightingRequested,
        onUserAvatarsRequested = onUserAvatarsRequested,
    )
}
