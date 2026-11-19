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
    onClickedPostsRequested: () -> Unit,
    onArticleCategoriesRequested: () -> Unit,
) {
    val context = LocalContext.current
    val repository = LocalHarmonicUiDependencies.current.settings
    StoriesSettingsRoute(
        repository = repository,
        categorySettings = LocalHarmonicUiDependencies.current.articleCategorySettings,
        previewModel = SettingsStoryPreviewModel,
        showNavigation = showNavigation,
        onBack = onBack,
        onSpacingRequested = onSpacingRequested,
        onManageFrontpages = onManageFrontpages,
        onPaletteTintRequested = onPaletteTintRequested,
        onClickedPostsRequested = onClickedPostsRequested,
        onArticleCategoriesRequested = onArticleCategoriesRequested,
        categoryAvailability = storyCategoryAvailability(),
        onPlatformEffect = { effect ->
            when (effect) {
                SettingsPlatformEffect.RefreshStoryWidgets -> refreshStoryWidgets(context)
                SettingsPlatformEffect.ThemeChanged -> Unit
            }
        },
    )
}
