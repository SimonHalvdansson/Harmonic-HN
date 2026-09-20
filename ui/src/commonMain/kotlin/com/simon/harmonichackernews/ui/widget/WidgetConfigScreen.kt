package com.simon.harmonichackernews.ui.widget

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.settings.SurfaceEffectMode
import com.simon.harmonichackernews.settings.SurfaceEffectPreferences
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.stories.StoryTypeDropdownMenu
import com.simon.harmonichackernews.ui.settings.*
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.common.HazeGlassAppearance
import com.simon.harmonichackernews.ui.common.LocalHazeGlassEnabled
import com.simon.harmonichackernews.ui.common.LocalHazePreferences
import com.simon.harmonichackernews.ui.common.sharedHazeBackground
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun WidgetConfigScreen(
    initialConfiguration: WidgetConfiguration,
    frontpages: List<StoryType>,
    onConfirm: (WidgetConfiguration) -> Unit,
    onBack: () -> Unit,
    paletteTintConfigKey: String = PaletteTintPreferences.DEFAULT,
    headlineFontFamily: FontFamily? = null,
    headlineFontLabel: String = "Device headline",
) {
    val feeds = frontpages.ifEmpty { listOf(StoryType.TOP_STORIES) }
    var feed by rememberSaveable { mutableStateOf(initialConfiguration.storyType.takeIf { it in feeds } ?: feeds.first()) }
    var count by rememberSaveable { mutableIntStateOf(initialConfiguration.visibleStoryCount) }
    var image by rememberSaveable { mutableStateOf(initialConfiguration.previewImageMode) }
    var style by rememberSaveable { mutableStateOf(initialConfiguration.displayStyle) }
    var tint by rememberSaveable { mutableStateOf(initialConfiguration.tint) }
    var useHeadlineFont by rememberSaveable { mutableStateOf(initialConfiguration.useHeadlineFont) }
    var choosingFeed by rememberSaveable { mutableStateOf(false) }
    val configuration = WidgetConfiguration(feed, feed.label, count, image, style, tint, useHeadlineFont)
    val fontFamily = if (useHeadlineFont) headlineFontFamily ?: FontFamily.SansSerif else FontFamily.SansSerif
    val hazeState = rememberHazeState()
    val buttonShape = RoundedCornerShape(16.dp)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pinPreview = maxHeight >= 560.dp
        val preview: @Composable () -> Unit = {
            // The launcher cannot render Haze. Keep all sample-widget content under the same
            // restriction, independently of the screen's glass-styled confirmation button.
            CompositionLocalProvider(LocalHazeGlassEnabled provides false,
                LocalHazePreferences provides SurfaceEffectPreferences(mode = SurfaceEffectMode.Solid)) {
                WidgetConfigurationPreview(configuration, paletteTintConfigKey, fontFamily)
            }
        }
        SettingsPage(
            modifier = Modifier.sharedHazeSource(hazeState),
            title = stringResource(Res.string.widget_config_title),
            showNavigation = true,
            onBack = onBack,
            extraBottomPadding = 88.dp,
            pinnedContent = preview.takeIf { pinPreview },
            headerContent = preview.takeUnless { pinPreview },
        ) {
            item {
                SettingsCategory("Content") {
                    Box {
                        SettingRow(
                            title = "Frontpage", summary = feed.label,
                            icon = Res.drawable.ic_library_books,
                            onClick = { choosingFeed = true },
                        )
                        StoryTypeDropdownMenu(
                            expanded = choosingFeed, onDismiss = { choosingFeed = false },
                            types = feeds, selectedType = feed,
                            onSelected = { feed = it; choosingFeed = false },
                        )
                    }
                    SettingsDivider()
                    SliderSetting(
                        title = stringResource(Res.string.widget_config_story_count_label),
                        valueLabel = count.toString(), value = count.toFloat(),
                        valueRange = WidgetConfiguration.MIN_STORY_COUNT.toFloat()..WidgetConfiguration.MAX_STORY_COUNT.toFloat(),
                        steps = WidgetConfiguration.MAX_STORY_COUNT - WidgetConfiguration.MIN_STORY_COUNT - 1,
                        onValueChange = { count = it.roundToInt() },
                    )
                }
            }
            item {
                SettingsCategory("Appearance") {
                    if (headlineFontFamily != null) {
                        SegmentedSetting(
                            title = "Font",
                            options = listOf(false to "Default", true to headlineFontLabel),
                            optionWeights = if (headlineFontLabel.length > 18) mapOf(false to 1f, true to 2f) else emptyMap(),
                            selected = useHeadlineFont, onSelected = { useHeadlineFont = it },
                        )
                        SettingsDivider()
                    }
                    SegmentedSetting(
                        title = "Preview image",
                        options = listOf(StoryPreviewMode.OFF to "Off", StoryPreviewMode.SMALL to "Small", StoryPreviewMode.MEDIUM to "Medium"),
                        selected = image, onSelected = { image = it },
                    )
                    SettingsDivider()
                    SegmentedSetting(
                        title = "Display style",
                        options = listOf(DisplayStyle.FLAT to "Flat", DisplayStyle.STANDARD to "Filled", DisplayStyle.RAISED to "Raised", DisplayStyle.OUTLINED to "Outlined"),
                        selected = style,
                        onSelected = { style = it; if (it == DisplayStyle.FLAT) tint = false },
                    )
                    SettingsDivider()
                    SwitchSettingRow(
                        title = "Tint", summary = "Uses preview or favicon",
                        icon = Res.drawable.ic_palette, checked = tint,
                        onCheckedChange = {
                            tint = it
                            if (it && style == DisplayStyle.FLAT) style = DisplayStyle.STANDARD
                        },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onConfirm(configuration) },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)
                .shadow(if (LocalHazeGlassEnabled.current) 2.dp else 6.dp, buttonShape, clip = false)
                .sharedHazeBackground(
                    hazeState = hazeState,
                    surfaceColor = HarmonicTheme.colors.overlayButton.copy(alpha = 0.8f),
                    shape = buttonShape,
                    glassAppearance = HazeGlassAppearance.FloatingButton,
                ),
            shape = buttonShape,
            containerColor = Color.Transparent,
            contentColor = HarmonicTheme.colors.overlayButtonContent,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        ) {
            Icon(painterResource(Res.drawable.ic_check), null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.widget_config_confirm))
        }
    }
}

/** Shared with the Glance renderer, which can only use platform fonts. */
object WidgetTypography {
    const val TITLE_SIZE = 14f
    const val HEADER_SIZE = 16f
    const val METADATA_SIZE = 11f
    const val COMMENT_COUNT_SIZE = 12f
}

object WidgetDimensions {
    val mediumImageWidth = 120.dp
    val mediumImageHeight = 72.dp
    val mediumNoImageWidth = 64.dp
    val mediumNoImageHeight = 60.dp
    val headerTopPadding = 6.dp
    val headerBottomPadding = 2.dp
    val listBottomPadding = 6.dp
    val cardSpacing = 7.dp
    val cardHorizontalMargin = 12.dp
    const val metricBackgroundAlpha = 0.92f
    val metricStartPadding = 5.dp
    val pointsStartPadding = 2.dp
    val metricEndPadding = 7.dp
    val metricVerticalPadding = 4.dp
    val metricIconSize = 13.dp
}

// A gesture that starts in the sample widget belongs to it, including at either scroll limit.
private val PreviewScrollBoundary = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
}

private val WidgetPreviewStories = listOf(
    SettingsStoryPreviewModel.copy(index = "1."),
    SettingsStoryPreviewModel.copy(
        index = "2.", title = "The hidden gardens of New York City",
        domain = "nytimes.com", domainWithoutTopLevel = "nytimes", age = "4h", points = 128,
        commentCount = 42, previewImageFallback = Res.drawable.palette2,
        faviconFallback = Res.drawable.ic_public, tintFaviconFallback = true,
    ),
    SettingsStoryPreviewModel.copy(
        index = "3.", title = "Ask HN: What have you been building this weekend?",
        domain = "news.ycombinator.com", domainWithoutTopLevel = "news.ycombinator", age = "5h", points = 76, commentCount = 103,
        previewImageFallback = null, faviconFallback = Res.drawable.ic_public, tintFaviconFallback = true,
    ),
    SettingsStoryPreviewModel.copy(
        index = "4.", title = "A small database that fits in your pocket",
        domain = "sqlite.org", domainWithoutTopLevel = "sqlite", age = "6h", points = 214, commentCount = 57,
        previewImageFallback = null, faviconFallback = Res.drawable.ic_public, tintFaviconFallback = true,
    ),
    SettingsStoryPreviewModel.copy(
        index = "5.", title = "New patterns",
        domain = "science.org", domainWithoutTopLevel = "science", age = "7h", points = 91, commentCount = 24,
    ),
)

/** Fixed widget viewport with animated Glance-equivalent rows and an independent scroll boundary. */
@Composable
private fun WidgetConfigurationPreview(configuration: WidgetConfiguration, paletteTintConfigKey: String, fontFamily: FontFamily) {
    val colors = HarmonicTheme.colors
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth().height(240.dp).clip(RoundedCornerShape(24.dp)).background(colors.settingsPageBackground)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(24.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = WidgetDimensions.headerTopPadding, bottom = WidgetDimensions.headerBottomPadding), verticalAlignment = Alignment.CenterVertically) {
            Crossfade(configuration.storyType to fontFamily, modifier = Modifier.weight(1f), label = "Widget feed") { (feed, family) ->
                Text(feed.label, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontFamily = family, fontSize = WidgetTypography.HEADER_SIZE.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalWidgetTextStyle.current)
            }
            Crossfade(fontFamily, label = "Widget status font") { family ->
                Text("Updated just now", Modifier.padding(start = 8.dp), color = colors.textSecondary,
                    fontFamily = family, fontSize = WidgetTypography.METADATA_SIZE.sp, maxLines = 1, style = LocalWidgetTextStyle.current)
            }
            Icon(painterResource(Res.drawable.ic_refresh), null, Modifier.size(48.dp).padding(12.dp), tint = colors.textPrimary)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).nestedScroll(PreviewScrollBoundary)
            .semantics { contentDescription = "Widget preview" }, contentPadding = PaddingValues(bottom = WidgetDimensions.listBottomPadding)) {
            items(WidgetPreviewStories, key = { it.index }) { model ->
                Crossfade(fontFamily, label = "Widget story font") { family ->
                    WidgetPreviewStoryRow(model, configuration, family, paletteTintConfigKey)
                }
            }
        }
    }
}
