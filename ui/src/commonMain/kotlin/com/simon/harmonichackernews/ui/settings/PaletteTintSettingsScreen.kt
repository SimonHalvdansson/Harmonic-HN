package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemStyle
import com.simon.harmonichackernews.ui.content.StoryItemUiModel
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.rememberResourceTintPalette
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val PalettePreviewSamples = listOf(
    StoryItemUiModel(
        index = "1.", title = "How machines learn to see", summary = "",
        points = 28, domain = "mit.edu", domainWithoutTopLevel = "mit",
        age = "2h", commentCount = 42, previewImageFallback = Res.drawable.palette1,
    ),
    StoryItemUiModel(
        index = "2.", title = "How New York’s skyline was built", summary = "",
        points = 96, domain = "nyc.gov", domainWithoutTopLevel = "nyc",
        age = "4h", commentCount = 28, previewImageFallback = Res.drawable.palette2,
    ),
    StoryItemUiModel(
        index = "3.", title = "Mapping buildings with 3D scans", summary = "",
        points = 73, domain = "ieee.org", domainWithoutTopLevel = "ieee",
        age = "3h", commentCount = 16, previewImageFallback = Res.drawable.palette3,
    ),
    StoryItemUiModel(
        index = "4.", title = "Rendering impossible architecture", summary = "",
        points = 54, domain = "blender.org", domainWithoutTopLevel = "blender",
        age = "5h", commentCount = 37, previewImageFallback = Res.drawable.palette4,
    ),
    StoryItemUiModel(
        index = "5.", title = "Photographing a rocket launch at night", summary = "",
        points = 85, domain = "nasa.gov", domainWithoutTopLevel = "nasa",
        age = "1h", commentCount = 61, previewImageFallback = Res.drawable.palette5,
    ),
)

@Composable
fun PaletteTintSettingsScreen(
    initialMode: String,
    initialStrength: Int,
    initialColorfulness: Int,
    initialTone: Int,
    previewStyle: StoryItemStyle,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onSettingsChanged: (mode: String, strength: Int, colorfulness: Int, tone: Int) -> Unit,
    onReset: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(PaletteTintPreferences.sanitizeMode(initialMode)) }
    var strength by rememberSaveable {
        mutableStateOf(PaletteTintPreferences.clampStrength(initialStrength))
    }
    var colorfulness by rememberSaveable {
        mutableStateOf(PaletteTintPreferences.clampColorfulness(initialColorfulness))
    }
    var tone by rememberSaveable { mutableStateOf(PaletteTintPreferences.clampTone(initialTone)) }
    val animationScope = rememberCoroutineScope()
    var resetAnimation by remember { mutableStateOf<Job?>(null) }
    val resetAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    fun persist(
        newMode: String = mode,
        newStrength: Int = strength,
        newColorfulness: Int = colorfulness,
        newTone: Int = tone,
    ) {
        resetAnimation?.cancel()
        resetAnimation = null
        mode = PaletteTintPreferences.sanitizeMode(newMode)
        strength = PaletteTintPreferences.clampStrength(newStrength)
        colorfulness = PaletteTintPreferences.clampColorfulness(newColorfulness)
        tone = PaletteTintPreferences.clampTone(newTone)
        onSettingsChanged(mode, strength, colorfulness, tone)
    }

    fun reset() {
        val startStrength = strength
        val startColorfulness = colorfulness
        val startTone = tone
        mode = PaletteTintPreferences.DEFAULT
        onReset()
        resetAnimation?.cancel()
        resetAnimation = animationScope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = resetAnimationSpec,
            ) { progress, _ ->
                val boundedProgress = progress.coerceIn(0f, 1f)
                strength = interpolatedPaletteValue(
                    startStrength,
                    PaletteTintPreferences.DEFAULT_STRENGTH,
                    boundedProgress,
                )
                colorfulness = interpolatedPaletteValue(
                    startColorfulness,
                    PaletteTintPreferences.DEFAULT_COLORFULNESS,
                    boundedProgress,
                )
                tone = (
                    startTone +
                        (PaletteTintPreferences.DEFAULT_TONE - startTone) * boundedProgress
                    ).roundToInt()
            }
            strength = PaletteTintPreferences.DEFAULT_STRENGTH
            colorfulness = PaletteTintPreferences.DEFAULT_COLORFULNESS
            tone = PaletteTintPreferences.DEFAULT_TONE
            resetAnimation = null
        }
    }

    val configKey = PaletteTintPreferences.configKey(mode, strength, colorfulness, tone)
    SettingsPage(
        title = stringResource(Res.string.settings_section_palette_tint),
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = configKey.hashCode(),
    ) {
        items(PalettePreviewSamples, key = { it.index }) { model ->
            PaletteStoryPreview(model, previewStyle.copy(paletteTintConfigKey = configKey))
        }
        item {
            SettingsCategory("Palette source") {
                SegmentedSetting(
                    options = listOf(
                        PaletteTintPreferences.MUTED to "Muted",
                        PaletteTintPreferences.DOMINANT to "Dominant",
                        PaletteTintPreferences.VIBRANT to "Vibrant",
                    ),
                    selected = mode,
                    onSelected = { persist(newMode = it) },
                )
            }
        }
        item {
            SettingsCategory("Adjust") {
                Column(
                    Modifier.background(settingsItemBackgroundColor()).padding(horizontal = 24.dp),
                ) {
                    PaletteAdjustment(
                        label = "Tint strength",
                        valueLabel = "$strength%",
                        value = strength.toFloat(),
                        valueRange = PaletteTintPreferences.MIN_STRENGTH.toFloat()..
                            PaletteTintPreferences.MAX_STRENGTH.toFloat(),
                        onValueChange = { persist(newStrength = it.roundToInt()) },
                    )
                    PaletteAdjustment(
                        label = "Colorfulness",
                        valueLabel = "$colorfulness%",
                        value = colorfulness.toFloat(),
                        valueRange = PaletteTintPreferences.MIN_COLORFULNESS.toFloat()..
                            PaletteTintPreferences.MAX_COLORFULNESS.toFloat(),
                        onValueChange = { persist(newColorfulness = it.roundToInt()) },
                    )
                    PaletteAdjustment(
                        label = "Brightness",
                        valueLabel = if (tone > 0) "+$tone" else tone.toString(),
                        value = tone.toFloat(),
                        valueRange = PaletteTintPreferences.MIN_TONE.toFloat()..
                            PaletteTintPreferences.MAX_TONE.toFloat(),
                        onValueChange = { persist(newTone = it.roundToInt()) },
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                SettingsDialogTextButton(onClick = ::reset) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Reset")
                }
            }
        }
    }
}

private fun interpolatedPaletteValue(start: Int, target: Int, progress: Float): Int =
    (start + (target - start) * progress).roundToInt()

@Composable
private fun PaletteStoryPreview(model: StoryItemUiModel, style: StoryItemStyle) {
    val palette = rememberResourceTintPalette(requireNotNull(model.previewImageFallback))
    val baseColor = HarmonicTheme.colors.storyCardBackground
    val tint = remember(palette, style.paletteTintConfigKey, baseColor) {
        PreviewTintPolicy.calculateCardTint(
            baseColor.toArgb(),
            palette,
            style.paletteTintConfigKey,
        )
    }
    StoryItem(
        model = model.copy(previewImageTintArgb = tint),
        // Keep the list compact and show the sample images even when feed previews are disabled.
        style = style.copy(previewImageMode = StoryPreviewMode.SMALL, showSummary = false),
        listItem = true,
        animateChanges = true,
        pageBackground = settingsPageBackgroundColor(),
    )
}

@Composable
private fun PaletteAdjustment(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Text(
                text = valueLabel,
                color = HarmonicTheme.colors.storyDisabled,
                fontFamily = ProductSansFontFamily,
                fontSize = 13.sp,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            valueRange = valueRange,
        )
    }
}
