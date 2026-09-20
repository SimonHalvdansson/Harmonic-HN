package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_palette
import com.simon.harmonichackernews.resources.ic_refresh
import com.simon.harmonichackernews.resources.settings_section_glass
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.GlassParameter
import com.simon.harmonichackernews.settings.GlassPreferences
import com.simon.harmonichackernews.settings.GlassSurfaceProfile
import com.simon.harmonichackernews.settings.GlassSwitch
import com.simon.harmonichackernews.settings.SurfaceEffectMode
import com.simon.harmonichackernews.settings.SurfaceEffectPreferences
import com.simon.harmonichackernews.ui.common.HazeGlassAppearance
import com.simon.harmonichackernews.ui.common.HazeHost
import com.simon.harmonichackernews.ui.common.LocalHazePreferences
import com.simon.harmonichackernews.ui.common.currentSharedHazeState
import com.simon.harmonichackernews.ui.common.sharedHazeBackground
import com.simon.harmonichackernews.ui.common.sharedHazeDialogBackground
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

@Composable
fun GlassSettingsRoute(repository: AppSettingsRepository, onBack: () -> Unit) {
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    val preferences = SurfaceEffectPreferences(settings.appearance.surfaceEffectMode, settings.debug.glass)
    SettingsPage(
        title = stringResource(Res.string.settings_section_glass),
        showNavigation = true,
        onBack = onBack,
        contentVersion = preferences.hashCode(),
        pinnedContent = { SurfaceEffectPreview(preferences) },
    ) {
        if (preferences.mode == SurfaceEffectMode.Glass) {
            glassTuningSettings(
                preferences = preferences,
                onParameterChanged = repository::setGlassParameter,
                onSwitchChanged = repository::setGlassSwitch,
                onProfileChanged = repository::setGlassSurfaceProfile,
                onResetGlass = repository::resetGlassPreferences,
            )
        } else {
            item {
                Text(
                    "Select Glass in Appearance to adjust its settings.",
                    modifier = Modifier.padding(24.dp),
                    color = HarmonicTheme.colors.textPrimary,
                )
            }
        }
    }
}

private fun LazyListScope.glassTuningSettings(
    preferences: SurfaceEffectPreferences,
    onParameterChanged: (GlassParameter, Float) -> Unit,
    onSwitchChanged: (GlassSwitch, Boolean) -> Unit,
    onProfileChanged: (GlassSurfaceProfile) -> Unit,
    onResetGlass: () -> Unit,
) {
    val glass = preferences.glass
    item(key = "glass-material") {
        SettingsCategory("Glass · material") {
            SettingRow(
                title = "Reset glass settings",
                summary = "Restore the default glass appearance.",
                icon = Res.drawable.ic_refresh,
                onClick = onResetGlass,
            )
            SwitchSettingRow(
                title = "Keep surface tint",
                summary = "Use theme and image-derived colours.",
                icon = Res.drawable.ic_palette,
                checked = glass[GlassSwitch.TintEnabled],
                onCheckedChange = { onSwitchChanged(GlassSwitch.TintEnabled, it) },
            )
            GlassSliders(glass, listOf(
                GlassParameter.ButtonTint to "Floating button tint",
                GlassParameter.DialogTint to "Dialog tint",
                GlassParameter.SubtleTint to "Back button and story pill tint",
            ), enabled = glass[GlassSwitch.TintEnabled], onChanged = onParameterChanged)
            GlassSliders(glass, listOf(
                GlassParameter.BackgroundOpacity to "Backdrop base opacity",
                GlassParameter.MaterialOpacity to "Glass opacity",
            ), onChanged = onParameterChanged)
        }
    }
    item(key = "glass-optics") {
        SettingsCategory("Glass · optics") {
            SwitchSettingRow(
                title = "Adaptive optics",
                summary = "Automatically adjust blur and refraction to each surface. Turn off for manual control.",
                icon = Res.drawable.ic_palette,
                checked = glass[GlassSwitch.AdaptiveOptics],
                onCheckedChange = { onSwitchChanged(GlassSwitch.AdaptiveOptics, it) },
            )
            GlassSliders(glass, listOf(
                GlassParameter.BlurRadius to "Blur radius",
                GlassParameter.RefractionStrength to "Refraction strength",
                GlassParameter.RefractionHeight to "Refraction edge height",
                GlassParameter.RefractionDisplacement to "Refraction displacement",
                GlassParameter.Depth to "Depth",
                GlassParameter.RefractionFold to "Edge fold",
            ), enabled = !glass[GlassSwitch.AdaptiveOptics], onChanged = onParameterChanged)
            SegmentedSetting(
                title = "Surface profile",
                options = GlassSurfaceProfile.entries.map { it to it.name },
                selected = glass.surfaceProfile,
                onSelected = onProfileChanged,
            )
            GlassSliders(glass, listOf(GlassParameter.EdgeSoftness to "Edge softness"),
                onChanged = onParameterChanged)
        }
    }
    item(key = "glass-light") {
        SettingsCategory("Glass · lighting") {
            GlassSliders(glass, listOf(
                GlassParameter.SpecularIntensity to "Highlight intensity",
                GlassParameter.AmbientResponse to "Ambient light",
                GlassParameter.LightX to "Light position · horizontal",
                GlassParameter.LightY to "Light position · vertical",
                GlassParameter.SpecularExponent to "Highlight sharpness",
                GlassParameter.FresnelExponent to "Edge light falloff",
                GlassParameter.ContentNormalBlend to "Backdrop texture lighting",
            ), onChanged = onParameterChanged)
        }
    }
    item(key = "glass-colour") {
        SettingsCategory("Glass · colour") {
            GlassSliders(glass, listOf(
                GlassParameter.Contrast to "Contrast",
                GlassParameter.WhitePoint to "White point",
                GlassParameter.ChromaMultiplier to "Saturation",
            ), onChanged = onParameterChanged)
            SwitchSettingRow(
                title = "Colour dispersion",
                summary = "Split colours along the glass edges.",
                icon = Res.drawable.ic_palette,
                checked = glass[GlassSwitch.ChromaticAberration],
                onCheckedChange = { onSwitchChanged(GlassSwitch.ChromaticAberration, it) },
            )
            GlassSliders(glass, listOf(GlassParameter.ChromaticAberration to "Dispersion strength"),
                enabled = glass[GlassSwitch.ChromaticAberration], onChanged = onParameterChanged)
            SwitchSettingRow(
                title = "Full-spectrum dispersion",
                summary = "A more detailed, more expensive colour split.",
                icon = Res.drawable.ic_palette,
                checked = glass[GlassSwitch.FullChromaticAberration],
                enabled = glass[GlassSwitch.ChromaticAberration],
                onCheckedChange = { onSwitchChanged(GlassSwitch.FullChromaticAberration, it) },
            )
        }
    }
}

@Composable
private fun GlassSliders(
    glass: GlassPreferences,
    controls: List<Pair<GlassParameter, String>>,
    enabled: Boolean = true,
    onChanged: (GlassParameter, Float) -> Unit,
) {
    for ((parameter, title) in controls) {
        val value = glass[parameter]
        val label = when (parameter) {
            GlassParameter.BlurRadius, GlassParameter.RefractionDisplacement, GlassParameter.EdgeSoftness ->
                "${(value * 10).roundToInt() / 10f} dp"
            GlassParameter.SpecularExponent, GlassParameter.FresnelExponent ->
                "${(value * 10).roundToInt() / 10f}"
            GlassParameter.ChromaMultiplier -> "${(value * 100).roundToInt()}%"
            else -> "${(value * 100).roundToInt()}%"
        }
        SliderSetting(
            title = title,
            valueLabel = label,
            value = value,
            valueRange = parameter.range,
            steps = 0,
            enabled = enabled,
            onValueChange = { onChanged(parameter, it) },
        )
    }
}

/** Uses the same renderer as actual dialogs/FABs so every adjustment can be seen immediately. */
@Composable
private fun SurfaceEffectPreview(preferences: SurfaceEffectPreferences) {
    CompositionLocalProvider(LocalHazePreferences provides preferences) {
        HazeHost {
            val hazeState = currentSharedHazeState()
            Box(Modifier.fillMaxWidth().background(settingsPageBackgroundColor()).padding(16.dp)) {
                Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(20.dp))) {
                    Row(Modifier.fillMaxSize().sharedHazeSource(hazeState)) {
                        listOf(Color(0xFF78A6C8), Color(0xFFE7A493), Color(0xFF8BBAA5)).forEachIndexed { index, color ->
                            Box(Modifier.weight(1f).fillMaxSize().background(color), contentAlignment = Alignment.Center) {
                                Text(listOf("Aa", "123", "Aa")[index], color = Color.Black.copy(alpha = 0.55f),
                                    fontSize = 36.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(80.dp).sharedHazeDialogBackground(
                            HarmonicTheme.colors.storyCardBackground, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center) {
                            Text("Dialog preview", color = HarmonicTheme.colors.storyNormal, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.width(96.dp).height(48.dp).sharedHazeBackground(
                            hazeState, HarmonicTheme.colors.overlayButton.copy(alpha = 0.8f),
                            RoundedCornerShape(24.dp), glassAppearance = HazeGlassAppearance.FloatingButton),
                            contentAlignment = Alignment.Center) {
                            Text("Button", color = HarmonicTheme.colors.overlayButtonContent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
