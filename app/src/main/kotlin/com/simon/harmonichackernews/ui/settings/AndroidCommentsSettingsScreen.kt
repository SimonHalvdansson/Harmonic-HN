package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
fun AndroidCommentsSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onSpacingRequested: () -> Unit,
    onThreadDepthRequested: () -> Unit,
    onQuoteHighlightingRequested: () -> Unit,
    onMonoBlocksRequested: () -> Unit,
    onUserAvatarsRequested: () -> Unit,
    onTranslationRequested: (() -> Unit)? = null,
    twoPane: Boolean = false,
) {
    CommentsSettingsRoute(
        repository = LocalHarmonicUiDependencies.current.settings,
        swipeBackSupported = true,
        onTranslationRequested = onTranslationRequested,
        twoPane = twoPane,
        showNavigation = showNavigation,
        onBack = onBack,
        onSpacingRequested = onSpacingRequested,
        onThreadDepthRequested = onThreadDepthRequested,
        onQuoteHighlightingRequested = onQuoteHighlightingRequested,
        onMonoBlocksRequested = onMonoBlocksRequested,
        onUserAvatarsRequested = onUserAvatarsRequested,
    )
}
