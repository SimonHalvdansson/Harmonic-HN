package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
fun WebLinksSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    SharedWebLinksSettingsRoute(
        repository = LocalHarmonicUiDependencies.current.settings,
        showNavigation = showNavigation,
        onBack = onBack,
    )
}
