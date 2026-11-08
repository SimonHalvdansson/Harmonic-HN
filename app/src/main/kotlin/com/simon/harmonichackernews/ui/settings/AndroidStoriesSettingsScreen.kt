package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.widget.refreshStoryWidgets

@Composable
fun AndroidStoriesSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onSpacingRequested: () -> Unit,
    onManageFrontpages: () -> Unit,
    onPaletteTintRequested: () -> Unit,
    onArticleCategoriesRequested: () -> Unit,
) {
    val context = LocalContext.current
    val repository = LocalHarmonicUiDependencies.current.settings
    val story = repository.snapshot().story
    StoriesSettingsRoute(
        repository = repository,
        categorySettings = LocalHarmonicUiDependencies.current.articleCategorySettings,
        previewModel = SettingsStoryPreviewModel,
        faviconIcon = faviconProviderPainter(story.faviconProvider),
        showNavigation = showNavigation,
        onBack = onBack,
        onSpacingRequested = onSpacingRequested,
        onManageFrontpages = onManageFrontpages,
        onPaletteTintRequested = onPaletteTintRequested,
        onArticleCategoriesRequested = onArticleCategoriesRequested,
        categoryAvailability = storyCategoryAvailability(),
        onPlatformEffect = { effect ->
            when (effect) {
                SettingsPlatformEffect.RefreshStoryWidgets -> refreshStoryWidgets(context)
                SettingsPlatformEffect.ThemeChanged -> Unit
            }
        },
        faviconDialog = { selected, _, onSelected, dismiss ->
            FaviconProviderRoute(
                selected = selected,
                onProviderSelected = onSelected,
                onDismiss = dismiss,
            )
        },
    )
}
