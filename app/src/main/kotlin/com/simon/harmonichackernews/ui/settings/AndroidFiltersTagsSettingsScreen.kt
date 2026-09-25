package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
fun AndroidFiltersTagsSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onManageFrontpages: () -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    FiltersTagsSettingsRoute(
        settings = app.settings,
        filters = app.contentFilters,
        userTags = app.userTags,
        showNavigation = showNavigation,
        onBack = onBack,
        onManageFrontpages = onManageFrontpages,
        onProfileAccepted = { app.scene.userProfiles.open(it) },
        profileDialog = { userName, dismiss, onTagChanged ->
            AndroidUserSettingsDialog(
                userName = userName,
                onDismiss = dismiss,
                onTagChanged = onTagChanged,
            )
        },
    )
}
