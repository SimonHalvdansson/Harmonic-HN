package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.HarmonicDimens
import com.simon.harmonichackernews.resources.ic_check
import com.simon.harmonichackernews.resources.ic_dark_mode
import com.simon.harmonichackernews.resources.ic_invert_colors
import com.simon.harmonichackernews.resources.ic_nights_stay
import com.simon.harmonichackernews.resources.ic_palette
import com.simon.harmonichackernews.resources.ic_schedule
import com.simon.harmonichackernews.resources.ic_style
import com.simon.harmonichackernews.settings.AppearancePreferences
import com.simon.harmonichackernews.settings.ThemePreferences
import com.simon.harmonichackernews.ui.theme.GoogleSansFlexRoundedFontFamily
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.ui.theme.ThemeAccentCatalog
import org.jetbrains.compose.resources.painterResource

data class ThemeSettingsUiState(
    val followSystem: Boolean,
    val manualDark: Boolean,
    val lightTheme: String,
    val darkTheme: String,
    val accentPreset: String,
    val specialNighttime: Boolean,
    val nighttimeRangeLabel: String,
    val nighttimeTheme: String,
    val activeTheme: String,
    val materialYouAvailable: Boolean,
)

enum class ThemeSettingsDialog { LightTheme, DarkTheme, NighttimeRange, NighttimeTheme }

private const val ThemeSelectionAnimationDurationMillis = 180

data class ThemePairPreset(
    val label: String,
    val description: String,
    val lightTheme: String,
    val darkTheme: String,
    val materialYou: Boolean = false,
)

val ThemePairPresets = listOf(
    ThemePairPreset(
        label = "Material You",
        description = "Dynamic colors",
        lightTheme = "material_light",
        darkTheme = "material_dark",
        materialYou = true,
    ),
    ThemePairPreset(
        label = "Material",
        description = "Harmonic violet",
        lightTheme = ThemePreferences.MATERIAL_FIXED_LIGHT,
        darkTheme = ThemePreferences.MATERIAL_FIXED_DARK,
    ),
    ThemePairPreset(
        label = "Classic",
        description = "Warm and calm",
        lightTheme = "light",
        darkTheme = "dark",
    ),
    ThemePairPreset(
        label = "Pure",
        description = "White and OLED black",
        lightTheme = "white",
        darkTheme = "amoled",
    ),
    ThemePairPreset(
        label = "Hacker",
        description = "HN by day, terminal by night",
        lightTheme = "hacker_news",
        darkTheme = "hacker",
    ),
)

fun themeSettingsSummary(
    appearance: AppearancePreferences,
    materialYouAvailable: Boolean,
): String {
    val lightLabel = harmonicThemeLabel(
        appearance.lightTheme,
        ThemePreferences.DEFAULT_LIGHT,
        materialYouAvailable,
    )
    val darkLabel = harmonicThemeLabel(
        appearance.darkTheme,
        ThemePreferences.DEFAULT_DARK,
        materialYouAvailable,
    )
    val pair = ThemePairPresets.firstOrNull {
        (materialYouAvailable || !it.materialYou) &&
            it.lightTheme == appearance.lightTheme && it.darkTheme == appearance.darkTheme
    }
    return when {
        appearance.followSystem && pair != null -> "Follows system · ${pair.label}"
        appearance.followSystem -> "Follows system · $lightLabel + $darkLabel"
        appearance.manualDark -> "Dark · $darkLabel"
        else -> "Light · $lightLabel"
    }
}

@Composable
fun ThemeSettingsScreen(
    state: ThemeSettingsUiState,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onFollowSystemChanged: (Boolean) -> Unit,
    onManualDarkChanged: (Boolean) -> Unit,
    onPairSelected: (ThemePairPreset) -> Unit,
    onAccentSelected: (String) -> Unit,
    onSpecialNighttimeChanged: (Boolean) -> Unit,
    onDialogRequested: (ThemeSettingsDialog) -> Unit,
    previewPalette: (theme: String, dark: Boolean, accentPreset: String) -> ThemePreviewPalette,
    contentVersion: Int = 0,
) {
    val lightPreview = previewPalette(state.lightTheme, false, state.accentPreset)
    val darkPreview = previewPalette(state.darkTheme, true, state.accentPreset)
    val themeDefaultAccent = previewPalette(
        state.lightTheme,
        false,
        ThemePreferences.ACCENT_DEFAULT,
    ).accent

    SettingsPage(
        title = "Theme",
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = contentVersion,
    ) {
        item {
            ThemeLivePreview(
                light = lightPreview,
                dark = darkPreview,
                activeTheme = state.activeTheme,
                lightTheme = state.lightTheme,
                darkTheme = state.darkTheme,
                modifier = Modifier.padding(
                    start = HarmonicDimens.settings_list_segment_horizontal_margin,
                    top = 16.dp,
                    end = HarmonicDimens.settings_list_segment_horizontal_margin,
                ),
            )
        }
        item {
            SettingsCategory("Appearance mode") {
                SwitchSettingRow(
                    title = "Use system light dark",
                    icon = Res.drawable.ic_invert_colors,
                    checked = state.followSystem,
                    onCheckedChange = onFollowSystemChanged,
                )
                AnimatedVisibility(
                    visible = !state.followSystem,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    Column {
                        SettingsDivider()
                        SegmentedSetting(
                            options = listOf(false to "Light", true to "Dark"),
                            selected = state.manualDark,
                            onSelected = onManualDarkChanged,
                        )
                    }
                }
            }
        }
        item {
            SettingsCategory("Quick pairs") {
                ThemePairPicker(
                    state = state,
                    onPairSelected = onPairSelected,
                    previewPalette = previewPalette,
                )
            }
        }
        item {
            SettingsCategory("Choose palettes") {
                SettingRow(
                    title = "Light theme",
                    summary = harmonicThemeLabel(
                        state.lightTheme,
                        ThemePreferences.DEFAULT_LIGHT,
                        state.materialYouAvailable,
                    ),
                    icon = Res.drawable.ic_palette,
                    onClick = { onDialogRequested(ThemeSettingsDialog.LightTheme) },
                    trailing = { ThemeSwatch(lightPreview) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Dark theme",
                    summary = harmonicThemeLabel(
                        state.darkTheme,
                        ThemePreferences.DEFAULT_DARK,
                        state.materialYouAvailable,
                    ),
                    icon = Res.drawable.ic_dark_mode,
                    onClick = { onDialogRequested(ThemeSettingsDialog.DarkTheme) },
                    trailing = { ThemeSwatch(darkPreview) },
                )
            }
        }
        item {
            SettingsCategory("Accent color") {
                AccentPresetPicker(
                    selected = state.accentPreset,
                    themeDefaultAccent = themeDefaultAccent,
                    onSelected = onAccentSelected,
                )
            }
        }
        item {
            SettingsCategory("Night schedule") {
                SwitchSettingRow(
                    title = "Special nighttime theme",
                    summary = "Temporarily override the active theme on a schedule",
                    icon = Res.drawable.ic_nights_stay,
                    checked = state.specialNighttime,
                    onCheckedChange = onSpecialNighttimeChanged,
                )
                SettingsDivider()
                SettingRow(
                    title = "Timed range",
                    summary = state.nighttimeRangeLabel,
                    icon = Res.drawable.ic_schedule,
                    enabled = state.specialNighttime,
                    onClick = { onDialogRequested(ThemeSettingsDialog.NighttimeRange) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Nighttime theme",
                    summary = harmonicThemeLabel(
                        state.nighttimeTheme,
                        ThemePreferences.DEFAULT_NIGHTTIME,
                        state.materialYouAvailable,
                    ),
                    icon = Res.drawable.ic_dark_mode,
                    enabled = state.specialNighttime,
                    onClick = { onDialogRequested(ThemeSettingsDialog.NighttimeTheme) },
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ThemeLivePreview(
    light: ThemePreviewPalette,
    dark: ThemePreviewPalette,
    activeTheme: String,
    lightTheme: String,
    darkTheme: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = settingsItemBackgroundColor(),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your theme pair",
                        color = HarmonicTheme.colors.textPrimary,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "Stories update as you experiment",
                        color = HarmonicTheme.colors.storyDisabled,
                        fontFamily = ProductSansFontFamily,
                        fontSize = 13.sp,
                    )
                }
                Icon(
                    painter = painterResource(Res.drawable.ic_style),
                    contentDescription = null,
                    tint = HarmonicTheme.colors.drawable,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StoryThemePreview(
                    label = "Light",
                    palette = light,
                    current = activeTheme == lightTheme,
                    modifier = Modifier.weight(1f),
                )
                StoryThemePreview(
                    label = "Dark",
                    palette = dark,
                    current = activeTheme == darkTheme,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StoryThemePreview(
    label: String,
    palette: ThemePreviewPalette,
    current: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderWidth by animateDpAsState(
        targetValue = if (current) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = ThemeSelectionAnimationDurationMillis),
        label = "theme preview border width",
    )
    val borderColor by animateColorAsState(
        targetValue = if (current) palette.accent else palette.text.copy(alpha = 0.16f),
        animationSpec = tween(durationMillis = ThemeSelectionAnimationDurationMillis),
        label = "theme preview border color",
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.background)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = palette.text,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            AnimatedVisibility(
                visible = current,
                enter = fadeIn(tween(ThemeSelectionAnimationDurationMillis)) +
                    scaleIn(tween(ThemeSelectionAnimationDurationMillis), initialScale = 0.75f),
                exit = fadeOut(tween(ThemeSelectionAnimationDurationMillis)) +
                    scaleOut(tween(ThemeSelectionAnimationDurationMillis), targetScale = 0.75f),
            ) {
                Text(
                    text = "NOW",
                    color = palette.accent,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surface)
                .padding(9.dp),
        ) {
            Text(
                text = "A better way to read the web",
                color = palette.text,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "128 points · 42 comments",
                modifier = Modifier.padding(top = 5.dp),
                color = palette.accent,
                fontFamily = ProductSansFontFamily,
                fontSize = 9.sp,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.72f)
                    .height(3.dp)
                    .background(palette.secondaryText.copy(alpha = 0.42f), CircleShape),
            )
        }
    }
}

@Composable
private fun ThemePairPicker(
    state: ThemeSettingsUiState,
    onPairSelected: (ThemePairPreset) -> Unit,
    previewPalette: (theme: String, dark: Boolean, accentPreset: String) -> ThemePreviewPalette,
) {
    val pairs = ThemePairPresets.filter { state.materialYouAvailable || !it.materialYou }
    Column(
        modifier = Modifier.fillMaxWidth().background(settingsItemBackgroundColor()),
    ) {
        Text(
            text = "Apply both palettes and follow the system",
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp),
            color = HarmonicTheme.colors.storyDisabled,
            fontFamily = ProductSansFontFamily,
            fontSize = 13.sp,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).selectableGroup(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(pairs, key = { it.label }) { pair ->
                val selected = state.followSystem &&
                    state.lightTheme == pair.lightTheme && state.darkTheme == pair.darkTheme
                PairPresetCard(
                    pair = pair,
                    selected = selected,
                    light = previewPalette(pair.lightTheme, false, state.accentPreset),
                    dark = previewPalette(pair.darkTheme, true, state.accentPreset),
                    onClick = { onPairSelected(pair) },
                )
            }
        }
    }
}

@Composable
private fun PairPresetCard(
    pair: ThemePairPreset,
    selected: Boolean,
    light: ThemePreviewPalette,
    dark: ThemePreviewPalette,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = ThemeSelectionAnimationDurationMillis),
        label = "quick pair border width",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(durationMillis = ThemeSelectionAnimationDurationMillis),
        label = "quick pair border color",
    )
    Column(
        modifier = Modifier
            .width(150.dp)
            .defaultMinSize(minHeight = 108.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)),
        ) {
            PairPreviewHalf(light, Modifier.weight(1f))
            PairPreviewHalf(dark, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pair.label,
                    color = HarmonicTheme.colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                )
                Text(
                    text = pair.description,
                    color = HarmonicTheme.colors.storyDisabled,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(tween(ThemeSelectionAnimationDurationMillis)) +
                    scaleIn(tween(ThemeSelectionAnimationDurationMillis), initialScale = 0.75f),
                exit = fadeOut(tween(ThemeSelectionAnimationDurationMillis)) +
                    scaleOut(tween(ThemeSelectionAnimationDurationMillis), targetScale = 0.75f),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = "Selected",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PairPreviewHalf(palette: ThemePreviewPalette, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(palette.background).padding(8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface, RoundedCornerShape(6.dp))
                .padding(5.dp),
        ) {
            Box(Modifier.width(24.dp).height(4.dp).background(palette.accent, CircleShape))
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier.fillMaxWidth().height(3.dp)
                    .background(palette.text.copy(alpha = 0.72f), CircleShape),
            )
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier.fillMaxWidth(0.62f).height(3.dp)
                    .background(palette.secondaryText.copy(alpha = 0.54f), CircleShape),
            )
        }
    }
}

@Composable
private fun AccentPresetPicker(
    selected: String,
    themeDefaultAccent: Color,
    onSelected: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(settingsItemBackgroundColor())
            .padding(vertical = 16.dp).selectableGroup(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(ThemeAccentCatalog.options, key = { it.value }) { option ->
            val checked = ThemePreferences.sanitizeAccent(selected) == option.value
            val color = option.lightColor ?: themeDefaultAccent
            Column(
                modifier = Modifier.width(60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (checked) 3.dp else 1.dp,
                            color = if (checked) HarmonicTheme.colors.textPrimary else
                                HarmonicTheme.colors.outlineVariant,
                            shape = CircleShape,
                        )
                        .selectable(
                            selected = checked,
                            role = Role.RadioButton,
                            onClick = { onSelected(option.value) },
                        )
                        .semantics { contentDescription = "${option.label} accent" }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AccentCheckmark(visible = checked, diskColor = color)
                }
                Text(
                    text = option.label.substringBefore(' '),
                    modifier = Modifier.padding(top = 4.dp),
                    color = if (checked) MaterialTheme.colorScheme.primary else
                        HarmonicTheme.colors.storyDisabled,
                    fontFamily = GoogleSansFlexRoundedFontFamily,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AccentCheckmark(visible: Boolean, diskColor: Color) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (diskColor.luminance() > 0.42f) Color(0xFF171717) else Color.White,
        )
    }
}

@Composable
private fun ThemeSwatch(palette: ThemePreviewPalette) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.background)
            .border(1.dp, palette.text.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(16.dp).background(palette.accent, CircleShape)
                .border(1.dp, palette.text.copy(alpha = 0.18f), CircleShape),
        )
    }
}
