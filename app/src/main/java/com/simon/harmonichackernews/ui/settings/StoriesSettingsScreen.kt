package com.simon.harmonichackernews.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.StoryTypeAndroid
import com.simon.harmonichackernews.settings.AndroidSettingsResources
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.widget.StoriesRemoteViewsFactory
import com.simon.harmonichackernews.widget.StoriesWidgetProvider

@Composable
fun StoriesSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onRequestRestart: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var localRefresh by remember { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf<StoriesSettingsDialog?>(null) }
    val app = LocalHarmonicUiDependencies.current
    val repository = app.settings
    val presenter = remember(app) { StoriesSettingsPresenter(repository) }
    val appSettings by repository.updates.collectAsState(initial = repository.snapshot())
    val storySettings = appSettings.story
    val additionalFrontpages = storySettings.additionalFrontpages
    val hotness = storySettings.hotness.toString()
    val paletteTintConfigKey = storySettings.paletteTintConfigKey
    val previewModel = remember(context, paletteTintConfigKey) {
        val tintFallback = PreviewImageTintUtils.getTintBaseColor(context)
        SettingsStoryPreviewModel.copy(
            tintFallbackArgb = tintFallback,
        )
    }
    val faviconProvider = storySettings.faviconProvider
    val state = presenter.state(
        settings = appSettings,
        previewModel = previewModel,
        faviconIcon = painterResource(AndroidSettingsResources.faviconProviderIcon(faviconProvider)),
    )
    SharedStoriesSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onBooleanChanged = { setting, value ->
            presenter.setBoolean(setting, value).forEach { effect ->
                when (effect) {
                    SettingsPlatformEffect.RefreshStoryWidgets -> refreshStoryWidgets(context)
                    SettingsPlatformEffect.RequestRestart -> onRequestRestart()
                    SettingsPlatformEffect.ThemeChanged -> Unit
                }
            }
        },
        onStringChanged = presenter::setString,
        onTextSizeOffsetChanged = presenter::setTextSizeOffset,
        onDialogRequested = { dialog = it },
        contentVersion = appSettings.hashCode() + localRefresh,
    )

    when (dialog) {
        StoriesSettingsDialog.Hotness -> ChoiceDialog(
            title = "Highlight hot stories",
            options = listOf(
                "-1" to "Never",
                "100" to "Points + comments > 100",
                "200" to "Points + comments > 200",
                "300" to "Points + comments > 300",
                "400" to "Points + comments > 400",
            ),
            selected = hotness,
            onDismiss = { dialog = null },
            onSelected = presenter::setHotness,
        )
        StoriesSettingsDialog.StartingPage -> ChoiceDialog(
            title = "Starting page",
            options = StoryTypeAndroid.buildStartingPageLabels(resources, additionalFrontpages)
                .map { it.toString() }
                .map { it to it },
            selected = state.startingPage,
            onDismiss = { dialog = null },
            onSelected = {
                presenter.setStartingPage(it).forEach { effect ->
                    if (effect == SettingsPlatformEffect.RequestRestart) onRequestRestart()
                }
            },
        )
        StoriesSettingsDialog.AdditionalFrontpages -> MultiChoiceDialog(
            title = "Additional frontpages",
            options = resources.getStringArray(R.array.additional_frontpage_options).toList(),
            selected = additionalFrontpages,
            onDismiss = { dialog = null },
            onSelectionChanged = {
                presenter.setAdditionalFrontpages(it).forEach { effect ->
                    if (effect == SettingsPlatformEffect.RequestRestart) onRequestRestart()
                }
                dialog = null
            },
        )
        StoriesSettingsDialog.FaviconProvider -> FaviconProviderDialog(
            onProviderSelected = { localRefresh++ },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private fun refreshStoryWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, StoriesWidgetProvider::class.java))
    if (ids.isNotEmpty()) {
        StoriesRemoteViewsFactory.setSkipFetchAll(context, true)
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_stories_list)
    }
}
