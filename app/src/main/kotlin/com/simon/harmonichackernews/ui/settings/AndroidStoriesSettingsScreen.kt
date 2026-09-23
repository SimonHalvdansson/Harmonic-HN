package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.theme.previewTintBaseColor
import com.simon.harmonichackernews.widget.refreshStoryWidgets

@Composable
fun AndroidStoriesSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onManageFrontpages: () -> Unit,
) {
    val context = LocalContext.current
    val repository = LocalHarmonicUiDependencies.current.settings
    val story = repository.snapshot().story
    val previewModel = remember(context, story.paletteTintConfigKey) {
        SettingsStoryPreviewModel.copy(
            tintFallbackArgb = previewTintBaseColor(context),
        )
    }
    StoriesSettingsRoute(
        repository = repository,
        previewModel = previewModel,
        faviconIcon = faviconProviderPainter(story.faviconProvider),
        showNavigation = showNavigation,
        onBack = onBack,
        onManageFrontpages = onManageFrontpages,
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
