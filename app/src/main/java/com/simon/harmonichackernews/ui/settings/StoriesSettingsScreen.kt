package com.simon.harmonichackernews.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.widget.StoriesWidgetProvider
import com.simon.harmonichackernews.widget.setSkipFetchForAllWidgets

@Composable
fun StoriesSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onRequestRestart: () -> Unit,
) {
    val context = LocalContext.current
    val repository = LocalHarmonicUiDependencies.current.settings
    val story = repository.snapshot().story
    val previewModel = remember(context, story.paletteTintConfigKey) {
        SettingsStoryPreviewModel.copy(
            tintFallbackArgb = PreviewImageTintUtils.getTintBaseColor(context),
        )
    }
    SharedStoriesSettingsRoute(
        repository = repository,
        previewModel = previewModel,
        faviconIcon = faviconProviderPainter(story.faviconProvider),
        showNavigation = showNavigation,
        onBack = onBack,
        onPlatformEffect = { effect ->
            when (effect) {
                SettingsPlatformEffect.RefreshStoryWidgets -> refreshStoryWidgets(context)
                SettingsPlatformEffect.RequestRestart -> onRequestRestart()
                SettingsPlatformEffect.ThemeChanged -> Unit
            }
        },
        faviconDialog = { selected, _, onSelected, dismiss ->
            SharedFaviconProviderRoute(
                selected = selected,
                onProviderSelected = onSelected,
                onDismiss = dismiss,
            )
        },
    )
}

private fun refreshStoryWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, StoriesWidgetProvider::class.java))
    if (ids.isNotEmpty()) {
        setSkipFetchForAllWidgets(context, true)
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_stories_list)
    }
}
