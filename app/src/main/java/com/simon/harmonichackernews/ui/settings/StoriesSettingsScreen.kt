package com.simon.harmonichackernews.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.StoryTypeAndroid
import com.simon.harmonichackernews.settings.AdditionalFrontpagePreferences
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.settings.AndroidSettingsResources
import com.simon.harmonichackernews.settings.DisplayStylePreferences
import com.simon.harmonichackernews.settings.StoryPreviewPreferences
import com.simon.harmonichackernews.settings.TextPreferences
import com.simon.harmonichackernews.settings.UserPreferenceKeys
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.widget.StoriesRemoteViewsFactory
import com.simon.harmonichackernews.widget.StoriesWidgetProvider
import java.util.Locale

@Composable
fun StoriesSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onRequestRestart: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var localRefresh by remember { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf<StoriesSettingsDialog?>(null) }
    val storySettings = AndroidUserSettings.get(context).story
    val previewImageMode = storySettings.previewImageMode
    val textSize = storySettings.storyTextSize
    val additionalFrontpages = storySettings.additionalFrontpages
    val hotness = storySettings.hotness.toString()
    val paletteTintConfigKey = storySettings.paletteTintConfigKey
    val previewModel = remember(context, paletteTintConfigKey) {
        val tintFallback = PreviewImageTintUtils.getTintBaseColor(context)
        SettingsStoryPreviewModel.copy(
            tintFallbackArgb = tintFallback,
        )
    }
    val compact = storySettings.compactView
    val showThumbnails = storySettings.thumbnails
    val hideClicked = storySettings.hideClicked
    val faviconProvider = storySettings.faviconProvider
    val state = StoriesSettingsUiState(
        previewModel = previewModel,
        previewImageMode = previewImageMode,
        previewOffValue = StoryPreviewPreferences.OFF,
        previewSmallValue = StoryPreviewPreferences.SMALL,
        previewLargeValue = StoryPreviewPreferences.LARGE,
        borderlessLargeImage = storySettings.borderlessLargePreviewImage,
        compact = compact,
        showSummary = storySettings.showSummary,
        showThumbnails = showThumbnails,
        showPoints = storySettings.showPoints,
        compactPoints = storySettings.compactPoints,
        includeTopLevelDomain = storySettings.includeTopLevelDomain,
        showComments = storySettings.showCommentsCount,
        showIndex = storySettings.showIndex,
        leftAlignComments = storySettings.leftAlign,
        tint = storySettings.tintCardUsingPreview,
        displayStyle = if (storySettings.cardStyle) {
            DisplayStylePreferences.CARD
        } else {
            DisplayStylePreferences.STANDARD
        },
        standardStyleValue = DisplayStylePreferences.STANDARD,
        cardStyleValue = DisplayStylePreferences.CARD,
        textSize = textSize,
        textSizeOffset = TextPreferences.storyTextSizeOffset(textSize),
        minTextSizeOffset = TextPreferences.MIN_TEXT_SIZE_OFFSET,
        maxTextSizeOffset = TextPreferences.MAX_TEXT_SIZE_OFFSET,
        hotnessEnabled = hotness != "-1",
        hotnessLabel = hotnessLabel(hotness),
        preferredFont = storySettings.font,
        paletteTintConfigKey = paletteTintConfigKey,
        startingPage = storySettings.preferredStoryType,
        additionalFrontpagesSummary = AdditionalFrontpagePreferences.summary(additionalFrontpages),
        alwaysOpenComments = storySettings.alwaysOpenComments,
        pagination = storySettings.pagination,
        hideClicked = hideClicked,
        grayOutClicked = storySettings.grayOutClicked,
        faviconProvider = faviconProvider,
        faviconIcon = painterResource(AndroidSettingsResources.faviconProviderIcon(faviconProvider)),
    )
    SharedStoriesSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onBooleanChanged = { setting, value ->
            prefs.edit().putBoolean(setting.preferenceKey, value).apply()
            if (setting == StoriesBooleanSetting.IncludeTopLevelDomain ||
                setting == StoriesBooleanSetting.ShowIndex
            ) {
                refreshStoryWidgets(context)
            }
            if (setting == StoriesBooleanSetting.HideClicked) onRequestRestart()
        },
        onStringChanged = { setting, value ->
            prefs.edit().putString(setting.preferenceKey, value).apply()
        },
        onTextSizeOffsetChanged = { offset ->
            val size = TextPreferences.storyTextSizeForOffset(offset)
            prefs.edit().putString(UserPreferenceKeys.STORY_TEXT_SIZE, formatTextSize(size)).apply()
        },
        onDialogRequested = { dialog = it },
        contentVersion = refresh + localRefresh,
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
            onSelected = { prefs.edit().putString(UserPreferenceKeys.HOTNESS, it).apply() },
        )
        StoriesSettingsDialog.StartingPage -> ChoiceDialog(
            title = "Starting page",
            options = StoryTypeAndroid.buildStartingPageLabels(resources, additionalFrontpages)
                .map { it.toString() }
                .map { it to it },
            selected = state.startingPage,
            onDismiss = { dialog = null },
            onSelected = {
                prefs.edit().putString(UserPreferenceKeys.DEFAULT_STORY_TYPE, it).apply()
                onRequestRestart()
            },
        )
        StoriesSettingsDialog.AdditionalFrontpages -> MultiChoiceDialog(
            title = "Additional frontpages",
            options = resources.getStringArray(R.array.additional_frontpage_options).toList(),
            selected = additionalFrontpages,
            onDismiss = { dialog = null },
            onSelectionChanged = {
                prefs.edit().putStringSet(
                    UserPreferenceKeys.ADDITIONAL_FRONTPAGES,
                    AdditionalFrontpagePreferences.sanitize(it),
                ).apply()
                onRequestRestart()
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

private val StoriesBooleanSetting.preferenceKey: String
    get() = when (this) {
        StoriesBooleanSetting.BorderlessLargeImage -> UserPreferenceKeys.STORY_PREVIEW_IMAGE_BORDERLESS
        StoriesBooleanSetting.Tint -> UserPreferenceKeys.TINT_CARD_USING_PREVIEW
        StoriesBooleanSetting.Compact -> UserPreferenceKeys.COMPACT_VIEW
        StoriesBooleanSetting.ShowSummary -> UserPreferenceKeys.SHOW_STORY_SUMMARY
        StoriesBooleanSetting.ShowThumbnails -> UserPreferenceKeys.THUMBNAILS
        StoriesBooleanSetting.ShowPoints -> UserPreferenceKeys.SHOW_POINTS
        StoriesBooleanSetting.CompactPoints -> UserPreferenceKeys.COMPACT_POINTS
        StoriesBooleanSetting.IncludeTopLevelDomain -> UserPreferenceKeys.INCLUDE_TOP_LEVEL_DOMAIN
        StoriesBooleanSetting.ShowComments -> UserPreferenceKeys.SHOW_COMMENTS_COUNT
        StoriesBooleanSetting.ShowIndex -> UserPreferenceKeys.SHOW_INDEX
        StoriesBooleanSetting.LeftAlignComments -> UserPreferenceKeys.LEFT_ALIGN
        StoriesBooleanSetting.AlwaysOpenComments -> UserPreferenceKeys.ALWAYS_OPEN_COMMENTS
        StoriesBooleanSetting.Pagination -> UserPreferenceKeys.PAGINATION_MODE
        StoriesBooleanSetting.HideClicked -> UserPreferenceKeys.HIDE_CLICKED
        StoriesBooleanSetting.GrayOutClicked -> UserPreferenceKeys.GRAY_OUT_CLICKED
    }

private val StoriesStringSetting.preferenceKey: String
    get() = when (this) {
        StoriesStringSetting.PreviewImageMode -> UserPreferenceKeys.STORY_PREVIEW_IMAGE_MODE
        StoriesStringSetting.DisplayStyle -> UserPreferenceKeys.STORY_DISPLAY_STYLE
    }

private fun formatTextSize(value: Float): String = if (value == value.toInt().toFloat()) {
    value.toInt().toString()
} else {
    String.format(Locale.US, "%.1f", value)
}

private fun hotnessLabel(value: String): String = when (value) {
    "100", "200", "300", "400" -> "Points + comments > $value"
    else -> "Never"
}

private fun refreshStoryWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, StoriesWidgetProvider::class.java))
    if (ids.isNotEmpty()) {
        StoriesRemoteViewsFactory.setSkipFetchAll(context, true)
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_stories_list)
    }
}
