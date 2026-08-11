package com.simon.harmonichackernews.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
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
import com.simon.harmonichackernews.settings.TextPreferences
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.utils.SettingsUtils
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
    val previewImageMode = SettingsUtils.getPreferredStoryPreviewImageMode(context)
    val textSize = SettingsUtils.getPreferredStoryTextSize(context)
    val additionalFrontpages = AdditionalFrontpagePreferences.sanitize(
        prefs.getStringSet(SettingsUtils.PREF_ADDITIONAL_FRONTPAGES, emptySet()) ?: emptySet(),
    )
    val hotness = prefs.getString("pref_hotness", "-1") ?: "-1"
    val paletteTintConfigKey = SettingsUtils.getPreferredPaletteTintConfigKey(context)
    val previewModel = remember(context, paletteTintConfigKey) {
        val tintFallback = PreviewImageTintUtils.getTintBaseColor(context)
        SettingsStoryPreviewModel.copy(
            faviconTintArgb = PreviewImageTintUtils.calculateCardTint(
                tintFallback,
                AppCompatResources.getDrawable(context, R.drawable.quanta),
                paletteTintConfigKey,
            ),
            previewImageTintArgb = PreviewImageTintUtils.calculateCardTint(
                tintFallback,
                AppCompatResources.getDrawable(context, R.drawable.web_preview),
                paletteTintConfigKey,
            ),
            tintFallbackArgb = tintFallback,
        )
    }
    val compact = prefs.getBoolean("pref_compact_view", false)
    val showThumbnails = prefs.getBoolean("pref_thumbnails", true)
    val hideClicked = prefs.getBoolean(SettingsUtils.PREF_HIDE_CLICKED, false)
    val faviconProvider = SettingsUtils.getPreferredFaviconProvider(context)
    val state = StoriesSettingsUiState(
        previewModel = previewModel,
        previewImageMode = previewImageMode,
        previewOffValue = SettingsUtils.STORY_PREVIEW_IMAGE_OFF,
        previewSmallValue = SettingsUtils.STORY_PREVIEW_IMAGE_SMALL,
        previewLargeValue = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE,
        borderlessLargeImage = prefs.getBoolean(
            SettingsUtils.PREF_STORY_PREVIEW_IMAGE_BORDERLESS,
            false,
        ),
        compact = compact,
        showSummary = prefs.getBoolean(SettingsUtils.PREF_SHOW_STORY_SUMMARY, false),
        showThumbnails = showThumbnails,
        showPoints = prefs.getBoolean("pref_show_points", true),
        compactPoints = prefs.getBoolean(SettingsUtils.PREF_COMPACT_POINTS, false),
        includeTopLevelDomain = prefs.getBoolean(
            SettingsUtils.PREF_INCLUDE_TOP_LEVEL_DOMAIN,
            true,
        ),
        showComments = prefs.getBoolean("pref_show_comments_count", true),
        showIndex = prefs.getBoolean("pref_show_index", true),
        leftAlignComments = prefs.getBoolean("pref_left_align", false),
        tint = prefs.getBoolean(SettingsUtils.PREF_TINT_CARD_USING_PREVIEW, true),
        displayStyle = SettingsUtils.getPreferredStoryDisplayStyle(context),
        standardStyleValue = SettingsUtils.STORY_DISPLAY_STYLE_STANDARD,
        cardStyleValue = SettingsUtils.STORY_DISPLAY_STYLE_CARD,
        textSize = textSize,
        textSizeOffset = TextPreferences.storyTextSizeOffset(textSize),
        minTextSizeOffset = SettingsUtils.MIN_STORY_TEXT_SIZE_OFFSET,
        maxTextSizeOffset = SettingsUtils.MAX_STORY_TEXT_SIZE_OFFSET,
        hotnessEnabled = hotness != "-1",
        hotnessLabel = hotnessLabel(hotness),
        preferredFont = SettingsUtils.getPreferredFont(context),
        paletteTintConfigKey = paletteTintConfigKey,
        startingPage = prefs.getString("pref_default_story_type", "Top Stories") ?: "Top Stories",
        additionalFrontpagesSummary = AdditionalFrontpagePreferences.summary(additionalFrontpages),
        alwaysOpenComments = prefs.getBoolean("pref_always_open_comments", false),
        pagination = prefs.getBoolean("pref_pagination_mode", false),
        hideClicked = hideClicked,
        grayOutClicked = prefs.getBoolean(SettingsUtils.PREF_GRAY_OUT_CLICKED, true),
        faviconProvider = faviconProvider,
        faviconIcon = painterResource(SettingsUtils.getFaviconProviderIconResource(faviconProvider)),
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
            prefs.edit().putString(SettingsUtils.PREF_STORY_TEXT_SIZE, formatTextSize(size)).apply()
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
            onSelected = { prefs.edit().putString("pref_hotness", it).apply() },
        )
        StoriesSettingsDialog.StartingPage -> ChoiceDialog(
            title = "Starting page",
            options = StoryTypeAndroid.buildStartingPageLabels(resources, additionalFrontpages)
                .map { it.toString() }
                .map { it to it },
            selected = state.startingPage,
            onDismiss = { dialog = null },
            onSelected = {
                prefs.edit().putString("pref_default_story_type", it).apply()
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
                    SettingsUtils.PREF_ADDITIONAL_FRONTPAGES,
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
        StoriesBooleanSetting.BorderlessLargeImage -> SettingsUtils.PREF_STORY_PREVIEW_IMAGE_BORDERLESS
        StoriesBooleanSetting.Tint -> SettingsUtils.PREF_TINT_CARD_USING_PREVIEW
        StoriesBooleanSetting.Compact -> "pref_compact_view"
        StoriesBooleanSetting.ShowSummary -> SettingsUtils.PREF_SHOW_STORY_SUMMARY
        StoriesBooleanSetting.ShowThumbnails -> "pref_thumbnails"
        StoriesBooleanSetting.ShowPoints -> "pref_show_points"
        StoriesBooleanSetting.CompactPoints -> SettingsUtils.PREF_COMPACT_POINTS
        StoriesBooleanSetting.IncludeTopLevelDomain -> SettingsUtils.PREF_INCLUDE_TOP_LEVEL_DOMAIN
        StoriesBooleanSetting.ShowComments -> "pref_show_comments_count"
        StoriesBooleanSetting.ShowIndex -> "pref_show_index"
        StoriesBooleanSetting.LeftAlignComments -> "pref_left_align"
        StoriesBooleanSetting.AlwaysOpenComments -> "pref_always_open_comments"
        StoriesBooleanSetting.Pagination -> "pref_pagination_mode"
        StoriesBooleanSetting.HideClicked -> SettingsUtils.PREF_HIDE_CLICKED
        StoriesBooleanSetting.GrayOutClicked -> SettingsUtils.PREF_GRAY_OUT_CLICKED
    }

private val StoriesStringSetting.preferenceKey: String
    get() = when (this) {
        StoriesStringSetting.PreviewImageMode -> SettingsUtils.PREF_STORY_PREVIEW_IMAGE_MODE
        StoriesStringSetting.DisplayStyle -> SettingsUtils.PREF_STORY_DISPLAY_STYLE
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
