package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
fun FiltersTagsSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    val app = LocalHarmonicUiDependencies.current
    SharedFiltersTagsSettingsRoute(
        settings = app.settings,
        filters = app.contentFilters,
        userTags = app.userTags,
        showNavigation = showNavigation,
        onBack = onBack,
        profileDialog = { userName, dismiss, onTagChanged ->
            UserSettingsDialog(
                userName = userName,
                onDismiss = dismiss,
                onTagChanged = onTagChanged,
            )
        },
    )
}
