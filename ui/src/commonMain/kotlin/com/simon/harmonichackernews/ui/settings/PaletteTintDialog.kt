package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmpalette.extensions.resource.rememberResourcePaletteState
import com.kmpalette.palette.graphics.Palette
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.PreviewTintPalette
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.PreviewTintSwatch
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
    val meta: String,
)

private val PalettePreviewSamples = listOf(
    PalettePreviewSample(Res.drawable.palette1, "Compiler release", "143 points"),
    PalettePreviewSample(Res.drawable.palette2, "Design notes", "89 points"),
    PalettePreviewSample(Res.drawable.palette3, "Database internals", "311 points"),
    PalettePreviewSample(Res.drawable.palette4, "Ask HN", "54 comments"),
    PalettePreviewSample(Res.drawable.palette5, "Launch write-up", "217 points"),
    PalettePreviewSample(Res.drawable.web_preview, "Website preview", "example.com"),
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
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 556.dp)) {
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
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).selectableGroup(),
                    ) {
                        listOf(
                            PaletteTintPreferences.DEFAULT to "Muted",
                            PaletteTintPreferences.VIBRANT to "Vibrant",
                            PaletteTintPreferences.DOMINANT to "Dominant",
                        ).forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 40.dp)
                                    .selectable(
                                        selected = mode == option.first,
                                        role = Role.RadioButton,
                                        onClick = { persist(newMode = option.first) },
                                    )
                                    .padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SettingsRadioButton(selected = mode == option.first)
                                Text(
                                    text = option.second,
                                    modifier = Modifier.padding(start = 8.dp),
                                    color = HarmonicTheme.colors.storyNormal,
                                    fontFamily = ProductSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                            }
                        }
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
                            steps = 39,
                            onValueChange = { persist(newStrength = (it / 5f).toInt() * 5) },
                        )
                        PaletteAdjustment(
                            label = "Colorfulness",
                            valueLabel = "$colorfulness%",
                            value = colorfulness.toFloat(),
                            valueRange = PaletteTintPreferences.MIN_COLORFULNESS.toFloat()..
                                PaletteTintPreferences.MAX_COLORFULNESS.toFloat(),
                            steps = 39,
                            onValueChange = {
                                persist(newColorfulness = (it / 5f).toInt() * 5)
                            },
                        )
                        PaletteAdjustment(
                            label = "Brightness",
                            valueLabel = if (tone > 0) "+$tone" else tone.toString(),
                            value = tone.toFloat(),
                            valueRange = PaletteTintPreferences.MIN_TONE.toFloat()..
                                PaletteTintPreferences.MAX_TONE.toFloat(),
                            steps = 39,
                            onValueChange = { persist(newTone = it.toInt()) },
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
                            strength = steppedPaletteValue(
                                startStrength,
                                PaletteTintPreferences.DEFAULT_STRENGTH,
                                boundedProgress,
                                5,
                            )
                            colorfulness = steppedPaletteValue(
                                startColorfulness,
                                PaletteTintPreferences.DEFAULT_COLORFULNESS,
                                boundedProgress,
                                5,
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

private fun steppedPaletteValue(
    start: Int,
    target: Int,
    progress: Float,
    step: Int,
): Int = ((start + (target - start) * progress).roundToInt().toFloat() / step)
    .roundToInt() * step

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
    steps: Int,
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
            modifier = Modifier.fillMaxWidth(),
            valueRange = valueRange,
            steps = steps,
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
    val paletteState = rememberResourcePaletteState { maximumColorCount(16) }
    LaunchedEffect(sample.title) { paletteState.generate(sample.drawable) }
    val palette = paletteState.palette
    val targetColor = remember(palette, configKey, baseColor) {
        Color(
            PreviewTintPolicy.calculateCardTint(
                baseColor.toArgb(),
                palette?.toPreviewTintPalette(),
                configKey,
            ),
        )
    }
    val cardColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "palette preview tint",
    )

    Card(
        modifier = modifier.width(136.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, HarmonicTheme.colors.storyNormal.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Image(
                painter = painterResource(sample.drawable),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            )
            Text(
                text = sample.title,
                modifier = Modifier.padding(top = 8.dp),
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
            )
            Text(
                text = sample.meta,
                color = HarmonicTheme.colors.storyDisabled,
                fontFamily = ProductSansFontFamily,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

internal fun Palette.toPreviewTintPalette(): PreviewTintPalette = PreviewTintPalette(
    vibrant = vibrantSwatch?.toPreviewTintSwatch(),
    lightVibrant = lightVibrantSwatch?.toPreviewTintSwatch(),
    darkVibrant = darkVibrantSwatch?.toPreviewTintSwatch(),
    dominant = dominantSwatch?.toPreviewTintSwatch(),
    muted = mutedSwatch?.toPreviewTintSwatch(),
    lightMuted = lightMutedSwatch?.toPreviewTintSwatch(),
    darkMuted = darkMutedSwatch?.toPreviewTintSwatch(),
)

private fun Palette.Swatch.toPreviewTintSwatch(): PreviewTintSwatch =
    PreviewTintSwatch(hue = hsl[0], saturation = hsl[1])
