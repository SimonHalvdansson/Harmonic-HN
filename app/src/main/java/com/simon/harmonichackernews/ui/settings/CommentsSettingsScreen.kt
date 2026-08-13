package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
fun CommentsSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    SharedCommentsSettingsRoute(
        repository = LocalHarmonicUiDependencies.current.settings,
        showNavigation = showNavigation,
        onBack = onBack,
        threadDepthDialog = { _, dismiss -> ThreadDepthIndicatorsDialog(dismiss) },
    )
}
