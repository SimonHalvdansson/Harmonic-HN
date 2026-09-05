package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.ui.content.rememberResourceTintPalette
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

private data class PalettePreviewSample(
    val drawable: DrawableResource,
    val title: String,
    val source: String,
)

private val PalettePreviewSamples = listOf(
    PalettePreviewSample(
        Res.drawable.palette1, "How machines learn to see", "technologyreview.com · 2h",
    ),
    PalettePreviewSample(
        Res.drawable.palette2, "How New York’s skyline was built", "smithsonianmag.com · 4h",
    ),
    PalettePreviewSample(
        Res.drawable.palette3, "Mapping buildings with 3D scans", "spectrum.ieee.org · 3h",
    ),
    PalettePreviewSample(
        Res.drawable.palette4, "Rendering impossible architecture", "blender.org · 5h",
    ),
    PalettePreviewSample(
        Res.drawable.palette5, "Photographing a rocket launch at night", "nasa.gov · 1h",
    ),
)

@Composable
fun PaletteTintDialog(
    initialMode: String,
    initialStrength: Int,
    initialColorfulness: Int,
    initialTone: Int,
    onSettingsChanged: (mode: String, strength: Int, colorfulness: Int, tone: Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(PaletteTintPreferences.sanitizeMode(initialMode)) }
    var strength by remember {
        mutableStateOf(PaletteTintPreferences.clampStrength(initialStrength))
    }
    var colorfulness by remember {
        mutableStateOf(PaletteTintPreferences.clampColorfulness(initialColorfulness))
    }
    var tone by remember { mutableStateOf(PaletteTintPreferences.clampTone(initialTone)) }
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

    val configKey = PaletteTintPreferences.configKey(mode, strength, colorfulness, tone)

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Configure palette tint") },
        edgeToEdgeContent = true,
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 672.dp)) {
                item {
                    PaletteSectionLabel(
                        text = "Preview",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                    ) {
                        items(PalettePreviewSamples, key = { it.title }) { sample ->
                            PalettePreviewCard(
                                sample = sample,
                                configKey = configKey,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                        }
                    }
                }
                item {
                    PaletteSectionLabel(
                        text = "Palette source",
                        modifier = Modifier.padding(start = 24.dp, top = 18.dp, end = 24.dp),
                    )
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        SegmentedSetting(
                            options = listOf(
                                PaletteTintPreferences.DEFAULT to "Muted",
                                PaletteTintPreferences.VIBRANT to "Vibrant",
                                PaletteTintPreferences.DOMINANT to "Dominant",
                            ),
                            selected = mode,
                            containerColor = Color.Transparent,
                            onSelected = { persist(newMode = it) },
                        )
                    }
                }
                item {
                    PaletteSectionLabel(
                        text = "Adjust",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp),
                    )
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
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
                            onValueChange = {
                                persist(newColorfulness = it.roundToInt())
                            },
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
                item { Spacer(Modifier.height(8.dp)) }
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            SettingsDialogTextButton(
                onClick = {
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
                },
            ) {
                Text("Reset")
            }
        },
        separateDismissButton = true,
    )
}

private fun interpolatedPaletteValue(
    start: Int,
    target: Int,
    progress: Float,
): Int = (start + (target - start) * progress).roundToInt()

@Composable
private fun PaletteSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = HarmonicTheme.colors.storyDisabled,
        fontFamily = ProductSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
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

@Composable
private fun PalettePreviewCard(
    sample: PalettePreviewSample,
    configKey: String,
    modifier: Modifier = Modifier,
) {
    val baseColor = HarmonicTheme.colors.storyCardBackground
    val palette = rememberResourceTintPalette(sample.drawable)
    val targetColor = remember(palette, configKey, baseColor) {
        Color(
            PreviewTintPolicy.calculateCardTint(
                baseColor.toArgb(),
                palette,
                configKey,
            ),
        )
    }
    val cardColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "palette preview tint",
    )

    // Match StoryItem's painted background: a Material Card inherits the dialog's tonal
    // elevation and alters the base color when zero tint makes it equal to the theme surface.
    Column(
        modifier = modifier.width(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor)
            .border(1.dp, HarmonicTheme.colors.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Image(
                painter = painterResource(sample.drawable),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = sample.title,
                modifier = Modifier.padding(top = 8.dp),
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sample.source,
                modifier = Modifier.padding(top = 4.dp),
                color = HarmonicTheme.colors.storyDisabled,
                fontFamily = ProductSansFontFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
