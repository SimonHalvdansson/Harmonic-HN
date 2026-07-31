package com.simon.harmonichackernews.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.settings.FaviconProviderDialogFragment
import com.simon.harmonichackernews.settings.StoryContentPreviewPreference
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.widget.StoriesRemoteViewsFactory
import com.simon.harmonichackernews.widget.StoriesWidgetProvider
import java.util.HashSet
import java.util.Locale

private const val KeyCompactView = "pref_compact_view"
private const val KeyShowPoints = "pref_show_points"
private const val KeyShowComments = "pref_show_comments_count"
private const val KeyShowIndex = "pref_show_index"
private const val KeyThumbnails = "pref_thumbnails"

@Composable
fun StoriesSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onRequestRestart: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val resources = LocalResources.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var dialog by remember { mutableStateOf<String?>(null) }

    @Suppress("UNUSED_VARIABLE")
    val observedRefresh = refresh

    val previewImageMode = SettingsUtils.getPreferredStoryPreviewImageMode(context)
    val borderlessLarge = prefs.getBoolean(
        SettingsUtils.PREF_STORY_PREVIEW_IMAGE_BORDERLESS,
        false,
    )
    val compact = prefs.getBoolean(KeyCompactView, false)
    val showSummary = prefs.getBoolean(SettingsUtils.PREF_SHOW_STORY_SUMMARY, false)
    val showThumbnails = prefs.getBoolean(KeyThumbnails, true)
    val showPoints = prefs.getBoolean(KeyShowPoints, true)
    val compactPoints = prefs.getBoolean(SettingsUtils.PREF_COMPACT_POINTS, false)
    val includeTld = prefs.getBoolean(SettingsUtils.PREF_INCLUDE_TOP_LEVEL_DOMAIN, true)
    val showComments = prefs.getBoolean(KeyShowComments, true)
    val showIndex = prefs.getBoolean(KeyShowIndex, true)
    val leftAlign = prefs.getBoolean("pref_left_align", false)
    val tint = prefs.getBoolean(SettingsUtils.PREF_TINT_CARD_USING_PREVIEW, true)
    val displayStyle = SettingsUtils.getPreferredStoryDisplayStyle(context)
    val textSize = SettingsUtils.getPreferredStoryTextSize(context)
    val textSizeOffset = SettingsUtils.getStoryTextSizeOffset(textSize)
    val hideClicked = prefs.getBoolean(SettingsUtils.PREF_HIDE_CLICKED, false)
    val additionalFrontpages = SettingsUtils.sanitizeAdditionalFrontpages(
        prefs.getStringSet(SettingsUtils.PREF_ADDITIONAL_FRONTPAGES, emptySet())
            ?: emptySet(),
    )

    SettingsPage(
        title = "Stories",
        showNavigation = showNavigation,
        onBack = onBack,
        pinnedContent = {
            AndroidView(
                factory = { StoryContentPreviewPreference(it) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        item {
            SettingsCategory("Layout") {
                SegmentedSetting(
                    title = "Preview image",
                    options = listOf(
                        SettingsUtils.STORY_PREVIEW_IMAGE_OFF to "Off",
                        SettingsUtils.STORY_PREVIEW_IMAGE_SMALL to "Small",
                        SettingsUtils.STORY_PREVIEW_IMAGE_LARGE to "Large",
                    ),
                    selected = previewImageMode,
                    onSelected = {
                        prefs.edit()
                            .putString(SettingsUtils.PREF_STORY_PREVIEW_IMAGE_MODE, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Borderless large image",
                    icon = R.drawable.ic_fullscreen,
                    checked = borderlessLarge,
                    enabled = previewImageMode == SettingsUtils.STORY_PREVIEW_IMAGE_LARGE,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(
                                SettingsUtils.PREF_STORY_PREVIEW_IMAGE_BORDERLESS,
                                it,
                            )
                            .apply()
                    },
                )
                SettingsDivider()
                SliderSetting(
                    title = "Text size",
                    valueLabel = formatOffset(textSizeOffset),
                    value = textSizeOffset.toFloat(),
                    valueRange = SettingsUtils.MIN_STORY_TEXT_SIZE_OFFSET.toFloat()..
                        SettingsUtils.MAX_STORY_TEXT_SIZE_OFFSET.toFloat(),
                    steps = SettingsUtils.MAX_STORY_TEXT_SIZE_OFFSET -
                        SettingsUtils.MIN_STORY_TEXT_SIZE_OFFSET - 1,
                    onValueChange = { value ->
                        val size = SettingsUtils.getStoryTextSizeForOffset(value.toInt())
                        prefs.edit()
                            .putString(
                                SettingsUtils.PREF_STORY_TEXT_SIZE,
                                formatTextSize(size),
                            )
                            .apply()
                    },
                )
                SettingsDivider()
                SegmentedSetting(
                    title = "Display style",
                    options = listOf(
                        SettingsUtils.STORY_DISPLAY_STYLE_STANDARD to "Standard",
                        SettingsUtils.STORY_DISPLAY_STYLE_CARD to "Card",
                    ),
                    selected = displayStyle,
                    onSelected = {
                        prefs.edit()
                            .putString(SettingsUtils.PREF_STORY_DISPLAY_STYLE, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Tint",
                    summary = "Uses preview or favicon",
                    icon = R.drawable.ic_palette,
                    checked = tint,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_TINT_CARD_USING_PREVIEW, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Compact stories",
                    summary = "Hides points, domain and time",
                    icon = R.drawable.ic_view_agenda,
                    checked = compact,
                    onCheckedChange = {
                        prefs.edit().putBoolean(KeyCompactView, it).apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Show summary",
                    icon = R.drawable.ic_subject,
                    checked = showSummary,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_SHOW_STORY_SUMMARY, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Show story thumbnails",
                    icon = R.drawable.ic_public,
                    checked = showThumbnails,
                    enabled = !compact,
                    onCheckedChange = {
                        prefs.edit().putBoolean(KeyThumbnails, it).apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Show story points",
                    icon = R.drawable.ic_thumbs_up_down,
                    checked = showPoints,
                    enabled = !compact,
                    onCheckedChange = {
                        prefs.edit().putBoolean(KeyShowPoints, it).apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Compact points",
                    icon = R.drawable.ic_thumb_up,
                    checked = compactPoints,
                    enabled = !compact && showPoints,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_COMPACT_POINTS, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Include top level domain",
                    icon = R.drawable.ic_public,
                    checked = includeTld,
                    enabled = !compact,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_INCLUDE_TOP_LEVEL_DOMAIN, it)
                            .apply()
                        refreshStoryWidgets(context)
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Show comment count",
                    icon = R.drawable.ic_comment,
                    checked = showComments,
                    enabled = !compact,
                    onCheckedChange = {
                        prefs.edit().putBoolean(KeyShowComments, it).apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Show story indices",
                    icon = R.drawable.ic_format_list_numbered,
                    checked = showIndex,
                    onCheckedChange = {
                        prefs.edit().putBoolean(KeyShowIndex, it).apply()
                        refreshStoryWidgets(context)
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Left align comments button",
                    icon = R.drawable.ic_pan_tool,
                    checked = leftAlign,
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_left_align", it).apply()
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Highlight hot stories",
                    summary = hotnessLabel(prefs.getString("pref_hotness", "-1") ?: "-1"),
                    icon = R.drawable.ic_whatshot,
                    onClick = { dialog = "hotness" },
                )
            }
        }

        item {
            SettingsCategory("Behavior") {
                SettingRow(
                    title = "Starting page",
                    summary = prefs.getString("pref_default_story_type", "Top Stories"),
                    icon = R.drawable.ic_bookmark,
                    onClick = { dialog = "starting_page" },
                )
                SettingsDivider()
                SettingRow(
                    title = "Additional frontpages",
                    summary = SettingsUtils.summarizeAdditionalFrontpages(
                        additionalFrontpages,
                    ),
                    icon = R.drawable.ic_library_books,
                    onClick = { dialog = "frontpages" },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Always open comments",
                    summary = "Clicking a story takes you directly to the comments view",
                    icon = R.drawable.ic_keyboard_double_arrow_right,
                    checked = prefs.getBoolean("pref_always_open_comments", false),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_always_open_comments", it).apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Use pagination",
                    summary = "Load 30 stories at a time",
                    icon = R.drawable.ic_swipe_vertical,
                    checked = prefs.getBoolean("pref_pagination_mode", false),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_pagination_mode", it).apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Hide clicked posts",
                    icon = R.drawable.ic_visibility_off,
                    checked = hideClicked,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_HIDE_CLICKED, it)
                            .apply()
                        onRequestRestart()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Gray out clicked posts",
                    icon = R.drawable.ic_visibility,
                    checked = prefs.getBoolean(SettingsUtils.PREF_GRAY_OUT_CLICKED, true),
                    enabled = !hideClicked,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_GRAY_OUT_CLICKED, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Favicon provider",
                    summary = SettingsUtils.getPreferredFaviconProvider(context),
                    icon = SettingsUtils.getFaviconProviderIconResource(
                        SettingsUtils.getPreferredFaviconProvider(context),
                    ),
                    enabled = !compact && showThumbnails,
                    onClick = {
                        activity?.supportFragmentManager?.let(
                            FaviconProviderDialogFragment::show,
                        )
                    },
                )
            }
        }
    }

    when (dialog) {
        "hotness" -> SingleChoiceDialog(
            title = "Highlight hot stories",
            options = listOf(
                "-1" to "Never",
                "100" to "Points + comments > 100",
                "200" to "Points + comments > 200",
                "300" to "Points + comments > 300",
                "400" to "Points + comments > 400",
            ),
            selected = prefs.getString("pref_hotness", "-1") ?: "-1",
            onDismiss = { dialog = null },
            onSelected = {
                prefs.edit().putString("pref_hotness", it).apply()
                dialog = null
            },
        )

        "starting_page" -> {
            val options = StoryType.buildStartingPageLabels(
                resources,
                additionalFrontpages,
            ).map { it.toString() }
            SingleChoiceDialog(
                title = "Starting page",
                options = options.map { it to it },
                selected = prefs.getString("pref_default_story_type", "Top Stories")
                    ?: "Top Stories",
                onDismiss = { dialog = null },
                onSelected = {
                    prefs.edit().putString("pref_default_story_type", it).apply()
                    onRequestRestart()
                    dialog = null
                },
            )
        }

        "frontpages" -> MultiChoiceDialog(
            title = "Additional frontpages",
            options = resources
                .getStringArray(R.array.additional_frontpage_options)
                .toList(),
            selected = additionalFrontpages,
            onDismiss = { dialog = null },
            onSelectionChanged = {
                prefs.edit()
                    .putStringSet(
                        SettingsUtils.PREF_ADDITIONAL_FRONTPAGES,
                        HashSet(SettingsUtils.sanitizeAdditionalFrontpages(it)),
                    )
                    .apply()
                onRequestRestart()
                dialog = null
            },
        )

    }
}

@Composable
private fun StorySettingsPreview(
    previewImageMode: String,
    borderlessLarge: Boolean,
    compact: Boolean,
    showSummary: Boolean,
    showThumbnails: Boolean,
    showPoints: Boolean,
    compactPoints: Boolean,
    includeTld: Boolean,
    showComments: Boolean,
    showIndex: Boolean,
    leftAlign: Boolean,
    tint: Boolean,
    displayStyle: String,
    textSize: Float,
) {
    val colors = HarmonicTheme.colors
    val shapeRadius by animateDpAsState(
        if (displayStyle == SettingsUtils.STORY_DISPLAY_STYLE_CARD) 20.dp else 10.dp,
        label = "story preview corners",
    )
    val shape = RoundedCornerShape(shapeRadius)
    val background by animateColorAsState(
        targetValue = if (tint) {
            colors.secondaryContainer.copy(alpha = 0.42f)
        } else {
            colors.surfaceContainerHigh
        },
        label = "story preview tint",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp)
            .clip(shape)
            .background(background)
            .then(
                if (displayStyle == SettingsUtils.STORY_DISPLAY_STYLE_CARD) {
                    Modifier.border(1.dp, colors.outlineVariant, shape)
                } else {
                    Modifier
                },
            )
            .animateContentSize(),
    ) {
        AnimatedVisibility(
            visible = previewImageMode == SettingsUtils.STORY_PREVIEW_IMAGE_LARGE &&
                showThumbnails &&
                borderlessLarge,
            enter = fadeIn(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Image(
                painter = painterResource(R.drawable.web_preview),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp),
                contentScale = ContentScale.Crop,
            )
        }

        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIndex) {
                Text(
                    text = "3.",
                    modifier = Modifier.padding(end = 8.dp),
                    color = colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 15.sp,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Algorithm breaks speed limit for solving linear equations",
                    color = colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = textSize.sp,
                    lineHeight = (textSize + 4).sp,
                )
                AnimatedVisibility(
                    visible = showSummary,
                    enter = fadeIn(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Text(
                        text = "A faster approach improves a classic linear algebra problem.",
                        modifier = Modifier.padding(top = 5.dp),
                        color = colors.storyDisabled,
                        fontFamily = ProductSansFontFamily,
                        fontSize = (textSize - 3).coerceAtLeast(11f).sp,
                    )
                }
                AnimatedVisibility(
                    visible = !compact,
                    enter = fadeIn(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Text(
                        text = buildString {
                            if (showPoints) {
                                append(if (compactPoints) "53 ▲" else "53 points")
                                append(" • ")
                            }
                            append(if (includeTld) "science.org" else "science")
                            append(" • 2h")
                        },
                        modifier = Modifier.padding(top = 6.dp),
                        color = colors.storyDisabled,
                        fontFamily = ProductSansFontFamily,
                        fontSize = SettingsUtils.getStoryMetaTextSize(textSize).sp,
                    )
                }
            }

            AnimatedVisibility(
                visible = showThumbnails &&
                    previewImageMode == SettingsUtils.STORY_PREVIEW_IMAGE_SMALL,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Image(
                    painter = painterResource(R.drawable.web_preview),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(width = 86.dp, height = 60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            AnimatedVisibility(
                visible = showComments,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = if (leftAlign) 4.dp else 12.dp)
                        .width(34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_comment),
                        contentDescription = null,
                        tint = colors.drawable,
                    )
                    Text(
                        text = "18",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = previewImageMode == SettingsUtils.STORY_PREVIEW_IMAGE_LARGE &&
                showThumbnails &&
                !borderlessLarge,
            enter = fadeIn(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Image(
                painter = painterResource(R.drawable.web_preview),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun formatOffset(offset: Int): String = if (offset >= 0) "+$offset" else "$offset"

private fun formatTextSize(value: Float): String =
    if (value == value.toInt().toFloat()) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }

private fun hotnessLabel(value: String): String = when (value) {
    "100", "200", "300", "400" -> "Points + comments > $value"
    else -> "Never"
}

private fun refreshStoryWidgets(context: android.content.Context) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(
        ComponentName(context, StoriesWidgetProvider::class.java),
    )
    if (ids.isNotEmpty()) {
        StoriesRemoteViewsFactory.setSkipFetchAll(context, true)
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_stories_list)
    }
}
