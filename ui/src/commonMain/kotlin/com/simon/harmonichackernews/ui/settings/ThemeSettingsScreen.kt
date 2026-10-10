package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.AppearancePreferences
import com.simon.harmonichackernews.settings.ColorSchemeSelection
import com.simon.harmonichackernews.settings.ColorSchemeStyle
import com.simon.harmonichackernews.settings.ColorSchemePreferences
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.comments.CommentNavigationButtons
import com.simon.harmonichackernews.ui.common.HazeHost
import com.simon.harmonichackernews.ui.common.currentSharedHazeState
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.content.CommentRowStyle
import com.simon.harmonichackernews.ui.content.SettingsCommentPreviewModel
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryRow
import com.simon.harmonichackernews.ui.content.StoryRowStyle
import com.simon.harmonichackernews.ui.content.rememberPainterPaletteTint
import com.simon.harmonichackernews.ui.theme.*
import org.jetbrains.compose.resources.painterResource

data class ThemeSettingsUiState(
    val followSystem: Boolean,
    val manualDark: Boolean,
    val schemes: ColorSchemeSelection,
    val activeDark: Boolean,
    val specialNighttime: Boolean,
    val nighttimeRangeLabel: String,
    val dynamicColorAvailable: Boolean,
)

enum class ThemeSettingsDialog { NighttimeRange }

fun themeSettingsSummary(appearance: AppearancePreferences): String {
    val scheme = ColorSchemeCatalog.label(appearance.colorSchemes.light)
    val mode = if (appearance.followSystem) "System" else if (appearance.manualDark) "Dark" else "Light"
    return "$mode · $scheme"
}

@Composable
fun ThemeSettingsScreen(
    state: ThemeSettingsUiState,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onFollowSystemChanged: (Boolean) -> Unit,
    onManualDarkChanged: (Boolean) -> Unit,
    onColorSchemeSelected: (String) -> Unit,
    onColorStyleSelected: (ColorSchemeStyle) -> Unit,
    onNighttimeColorSchemeSelected: (String) -> Unit,
    onNighttimeColorStyleSelected: (ColorSchemeStyle) -> Unit,
    onSpecialNighttimeChanged: (Boolean) -> Unit,
    onDialogRequested: (ThemeSettingsDialog) -> Unit,
    resolvePreviewScheme: (String, Boolean, ColorSchemeStyle) -> HarmonicThemePalette,
    previewStyle: StoryRowStyle,
    commentPreviewStyle: CommentRowStyle,
    contentVersion: Int = 0,
) {
    SettingsPage(
        title = "Color scheme",
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = contentVersion,
        pinnedContent = { ThemeLivePreview(state, previewStyle, commentPreviewStyle, resolvePreviewScheme) },
    ) {
        item {
            SettingsCategory("Appearance") {
                SegmentedSetting(
                    options = listOf("light" to "Light", "system" to "System", "dark" to "Dark"),
                    optionIcons = mapOf("light" to Res.drawable.ic_light_mode,
                        "system" to Res.drawable.ic_routine, "dark" to Res.drawable.ic_dark_mode),
                    buttonHeight = 48.dp,
                    optionWeights = mapOf("light" to 3f, "system" to 4f, "dark" to 3f),
                    selected = if (state.followSystem) "system" else if (state.manualDark) "dark" else "light",
                    onSelected = { mode ->
                        if (mode == "system") onFollowSystemChanged(true) else {
                            onManualDarkChanged(mode == "dark")
                            onFollowSystemChanged(false)
                        }
                    },
                )
            }
        }
        item(key = "color-scheme-picker") {
            SettingsCategory("Color scheme") {
                ColorSchemePicker(
                    selected = state.schemes.light,
                    dark = state.activeDark,
                    style = state.schemes.lightStyle,
                    tag = "Color scheme",
                    resolveScheme = resolvePreviewScheme,
                    onSelected = onColorSchemeSelected,
                    onStyleSelected = onColorStyleSelected,
                )
            }
        }
        if (!state.dynamicColorAvailable) {
            item {
                Text("Dynamic uses Violet on this device.",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SettingsCategory("Night schedule") {
                SwitchSettingRow(
                    title = "Use a nighttime color scheme",
                    summary = "Temporarily use a dark color scheme on a schedule",
                    icon = Res.drawable.ic_nights_stay,
                    checked = state.specialNighttime,
                    onCheckedChange = onSpecialNighttimeChanged,
                )
                ThemeSettingsVisibility(visible = state.specialNighttime) {
                    SettingsDivider()
                    SettingRow(title = "Timed range", summary = state.nighttimeRangeLabel,
                        icon = Res.drawable.ic_schedule,
                        onClick = { onDialogRequested(ThemeSettingsDialog.NighttimeRange) })
                }
            }
        }
        item(key = "nighttime-color-scheme") {
            ThemeSettingsVisibility(visible = state.specialNighttime) {
                SettingsCategory("Nighttime color scheme") {
                    ColorSchemePicker(state.schemes.nighttime, dark = true, style = state.schemes.nighttimeStyle,
                        tag = "Nighttime color scheme", resolveScheme = resolvePreviewScheme,
                        onSelected = onNighttimeColorSchemeSelected, onStyleSelected = onNighttimeColorStyleSelected)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ThemeSettingsVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(300), expandFrom = Alignment.Top) + fadeIn(tween(180, delayMillis = 60)),
        exit = shrinkVertically(tween(300), shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
    ) {
        Column { content() }
    }
}

@Composable
private fun ColorSchemePicker(
    selected: String,
    dark: Boolean,
    style: ColorSchemeStyle,
    tag: String,
    resolveScheme: (String, Boolean, ColorSchemeStyle) -> HarmonicThemePalette,
    onSelected: (String) -> Unit,
    onStyleSelected: (ColorSchemeStyle) -> Unit,
) {
    val options = ColorSchemeCatalog.options
    FlowRow(
        modifier = Modifier.fillMaxWidth().background(itemBackgroundColor())
            .testTag(tag).selectableGroup()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val checked = selected == option.value
            val targetScheme = resolveScheme(option.value, dark, style).colorScheme
            val scheme = targetScheme.copy(
                primary = animateThemeColor(targetScheme.primary),
                secondary = animateThemeColor(targetScheme.secondary),
                tertiary = animateThemeColor(targetScheme.tertiary),
                onPrimary = animateThemeColor(targetScheme.onPrimary),
            )
            Column(
                modifier = Modifier.width(80.dp).clip(RoundedCornerShape(16.dp))
                    .selectable(selected = checked, role = Role.RadioButton, onClick = { onSelected(option.value) })
                    .semantics { contentDescription = "${option.label} color scheme" }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(60.dp)
                    .border(2.dp, if (checked) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                    .padding(5.dp)) {
                    // Primary occupies the top half; secondary and tertiary share the bottom.
                    Canvas(Modifier.fillMaxSize().testTag("Swatch-$tag-${option.value}")) {
                        drawArc(scheme.primary, 180f, 180f, useCenter = true)
                        drawArc(scheme.secondary, 0f, 90f, useCenter = true)
                        drawArc(scheme.tertiary, 90f, 90f, useCenter = true)
                    }
                    if (checked) Icon(painterResource(Res.drawable.ic_check), null,
                        Modifier.align(Alignment.TopCenter).padding(top = 5.dp).size(20.dp),
                        tint = scheme.onPrimary)
                }
                Text(option.label, Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = ProductSansFontFamily, fontSize = 14.sp, lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
    ThemeSettingsVisibility(visible = ColorSchemePreferences.supportsStyle(selected)) {
        Box(Modifier.testTag("$tag style")) {
            SegmentedSetting(
                title = "Style",
                options = listOf(ColorSchemeStyle.NeutralSurfaces to "Neutral",
                    ColorSchemeStyle.Balanced to "Balanced", ColorSchemeStyle.Vibrant to "Vibrant"),
                selected = style,
                optionContent = { value, checked ->
                    Text(
                        when (value) {
                            ColorSchemeStyle.NeutralSurfaces -> "Neutral"
                            ColorSchemeStyle.Balanced -> "Balanced"
                            ColorSchemeStyle.Vibrant -> "Vibrant"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        fontFamily = ProductSansFontFamily, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
                    )
                },
                onSelected = onStyleSelected,
            )
        }
    }
}

@Composable
private fun ThemeLivePreview(
    state: ThemeSettingsUiState,
    style: StoryRowStyle,
    commentStyle: CommentRowStyle,
    resolveScheme: (String, Boolean, ColorSchemeStyle) -> HarmonicThemePalette,
) {
    val lightFraction by animateFloatAsState(
        targetValue = if (state.followSystem) 0.5f else if (state.manualDark) 0f else 1f,
        animationSpec = tween(250), label = "scheme preview split",
    )
    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
        StoryThemePreview(resolveScheme(state.schemes.light, false, state.schemes.lightStyle), style, commentStyle,
            Modifier.clip(GenericShape { size, _ ->
                lineTo(size.width * lightFraction, 0f); lineTo(size.width * lightFraction, size.height)
                lineTo(0f, size.height); close()
            }).then(if (state.manualDark && !state.followSystem) Modifier.clearAndSetSemantics {} else Modifier))
        StoryThemePreview(resolveScheme(state.schemes.dark, true, state.schemes.darkStyle), style, commentStyle,
            Modifier.clip(GenericShape { size, _ ->
                moveTo(size.width * lightFraction, 0f); lineTo(size.width, 0f)
                lineTo(size.width, size.height); lineTo(size.width * lightFraction, size.height); close()
            }).then(if (state.followSystem || !state.manualDark) Modifier.clearAndSetSemantics {} else Modifier))
    }
}

@Composable
private fun StoryThemePreview(
    palette: HarmonicThemePalette,
    style: StoryRowStyle,
    commentStyle: CommentRowStyle,
    modifier: Modifier = Modifier,
) {
    // Extract against the destination palette, not every intermediate animation color. Keep the
    // existing sample tint visible while extraction runs; unchanged tints need no transition.
    val tintBase = palette.colorScheme.cardBackground.toArgb()
    val faviconTint = rememberPainterPaletteTint(
        painter = painterResource(SettingsStoryPreviewModel.faviconFallback),
        baseColorArgb = tintBase,
        paletteTintConfigKey = style.paletteTintConfigKey,
        enabled = style.tintCard,
    )
    var retainedFaviconTint by remember(style.paletteTintConfigKey) { mutableStateOf<Int?>(null) }
    LaunchedEffect(faviconTint) {
        faviconTint?.let { retainedFaviconTint = it }
    }
    HarmonicTheme(palette.colorScheme, palette.dark) {
        val colors = MaterialTheme.colorScheme
        val pageBackground = colors.pageBackground
        HazeHost {
            val hazeState = currentSharedHazeState()
            Box(
                modifier = modifier.fillMaxWidth().fillMaxHeight().background(pageBackground),
            ) {
                Column(
                    Modifier.fillMaxWidth().sharedHazeSource(hazeState)
                        .padding(top = 6.dp, bottom = 32.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ThemePreviewPalettePills()
                        ThemePreviewPalettePills()
                    }
                    StoryRow(
                        model = SettingsStoryPreviewModel.copy(
                            title = "A faster algorithm",
                            faviconTintArgb = faviconTint ?: retainedFaviconTint ?: tintBase,
                        ),
                        // Keep the preview compact even with large image settings.
                        style = style.copy(previewImageMode = StoryPreviewMode.OFF, showPreviewText = false),
                        pageBackground = pageBackground,
                        animateChanges = false,
                        cardPadding = PaddingValues(
                            start = if (style.hasBackground || style.tintCard) 12.dp else 8.dp,
                            top = 4.dp,
                            end = if (style.hasBackground || style.tintCard) 12.dp else 8.dp,
                            bottom = 0.dp,
                        ),
                    )
                    CommentRow(
                        model = SettingsCommentPreviewModel.copy(
                            body = "A neat result.", referenceMarker = "", referenceUrl = "",
                        ),
                        style = commentStyle,
                        verticalPadding = 2.dp,
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                        .clearAndSetSemantics { contentDescription = "Comment navigation preview" },
                ) {
                    CommentNavigationButtons(onPrevious = {}, onNext = {}, onFirst = {}, onLast = {})
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewPalettePills() {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "Primary, secondary, and tertiary colors"
        },
    ) {
        listOf(colors.primary, colors.secondary, colors.tertiary).forEach { color ->
            Box(Modifier.size(width = 24.dp, height = 12.dp).background(color, CircleShape))
        }
    }
}
