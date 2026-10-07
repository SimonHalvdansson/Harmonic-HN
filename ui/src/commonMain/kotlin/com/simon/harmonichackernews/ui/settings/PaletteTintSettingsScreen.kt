package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.common.HazeGlassAppearance
import com.simon.harmonichackernews.ui.common.LocalHazeGlassEnabled
import com.simon.harmonichackernews.ui.common.sharedHazeBackground
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import com.simon.harmonichackernews.ui.content.StoryRow
import com.simon.harmonichackernews.ui.content.StoryRowModel
import com.simon.harmonichackernews.ui.content.StoryRowStyle
import com.simon.harmonichackernews.ui.content.contentTween
import com.simon.harmonichackernews.ui.content.preloadResourcePreview
import com.simon.harmonichackernews.ui.content.rememberResourcePreview
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationTransitionDurationMillis
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.rememberResourceEnvironment
import org.jetbrains.compose.resources.stringResource

private val PalettePreviewSamples = listOf(
    StoryRowModel(
        index = "1.", title = "How machines learn to see", previewText = "",
        points = 28, domain = "mit.edu", domainWithoutTopLevel = "mit",
        age = "2h", commentCount = 42, previewImageFallback = Res.drawable.palette1,
    ),
    StoryRowModel(
        index = "2.", title = "How New York’s skyline was built", previewText = "",
        points = 96, domain = "nyc.gov", domainWithoutTopLevel = "nyc",
        age = "4h", commentCount = 28, previewImageFallback = Res.drawable.palette2,
    ),
    StoryRowModel(
        index = "3.", title = "Mapping buildings with 3D scans", previewText = "",
        points = 73, domain = "ieee.org", domainWithoutTopLevel = "ieee",
        age = "3h", commentCount = 16, previewImageFallback = Res.drawable.palette3,
    ),
    StoryRowModel(
        index = "4.", title = "Rendering impossible architecture", previewText = "",
        points = 54, domain = "blender.org", domainWithoutTopLevel = "blender",
        age = "5h", commentCount = 37, previewImageFallback = Res.drawable.palette4,
    ),
    StoryRowModel(
        index = "5.", title = "Photographing a rocket launch at night", previewText = "",
        points = 85, domain = "nasa.gov", domainWithoutTopLevel = "nasa",
        age = "1h", commentCount = 61, previewImageFallback = Res.drawable.palette5,
    ),
)

/** Prepare bundled previews after Appearance has opened, before the palette page is requested. */
@Composable
internal fun PreloadPalettePreviewResources() {
    val environment = rememberResourceEnvironment()
    LaunchedEffect(environment) {
        delay(ActivityNavigationTransitionDurationMillis.toLong())
        for (sample in PalettePreviewSamples) {
            preloadResourcePreview(requireNotNull(sample.previewImageFallback), environment)
        }
    }
}

@Composable
fun PaletteTintSettingsScreen(
    initialMode: String,
    initialStrength: Int,
    initialColorfulness: Int,
    initialTone: Int,
    initialAvoidBackgroundColor: Boolean,
    previewStyle: StoryRowStyle,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onSettingsChanged: (
        mode: String,
        strength: Int,
        colorfulness: Int,
        tone: Int,
        avoidBackgroundColor: Boolean,
    ) -> Unit,
    onReset: () -> Unit,
    previewDark: Boolean = false,
    onTogglePreview: (() -> Unit)? = null,
) {
    var mode by rememberSaveable { mutableStateOf(PaletteTintPreferences.sanitizeMode(initialMode)) }
    var strength by rememberSaveable {
        mutableIntStateOf(PaletteTintPreferences.clampStrength(initialStrength))
    }
    var colorfulness by rememberSaveable {
        mutableIntStateOf(PaletteTintPreferences.clampColorfulness(initialColorfulness))
    }
    var tone by rememberSaveable { mutableIntStateOf(PaletteTintPreferences.clampTone(initialTone)) }
    var avoidBackgroundColor by rememberSaveable { mutableStateOf(initialAvoidBackgroundColor) }
    val hazeState = rememberHazeState()
    val resetButtonShape = RoundedCornerShape(16.dp)
    val density = LocalDensity.current
    var resetButtonHeight by remember { mutableStateOf(56.dp) }
    val animationScope = rememberCoroutineScope()
    var resetAnimation by remember { mutableStateOf<Job?>(null) }
    val resetAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    fun persist(
        newMode: String = mode,
        newStrength: Int = strength,
        newColorfulness: Int = colorfulness,
        newTone: Int = tone,
        newAvoidBackgroundColor: Boolean = avoidBackgroundColor,
    ) {
        resetAnimation?.cancel()
        resetAnimation = null
        mode = PaletteTintPreferences.sanitizeMode(newMode)
        strength = PaletteTintPreferences.clampStrength(newStrength)
        colorfulness = PaletteTintPreferences.clampColorfulness(newColorfulness)
        tone = PaletteTintPreferences.clampTone(newTone)
        avoidBackgroundColor = newAvoidBackgroundColor
        onSettingsChanged(mode, strength, colorfulness, tone, avoidBackgroundColor)
    }

    fun reset() {
        val startStrength = strength
        val startColorfulness = colorfulness
        val startTone = tone
        mode = PaletteTintPreferences.DEFAULT
        avoidBackgroundColor = PaletteTintPreferences.DEFAULT_AVOID_BACKGROUND_COLOR
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

    val configKey = PaletteTintPreferences.configKey(
        mode, strength, colorfulness, tone, avoidBackgroundColor,
    )
    Box(Modifier.fillMaxSize()) {
        SettingsPage(
            modifier = Modifier.sharedHazeSource(hazeState),
            extraBottomPadding = resetButtonHeight + 16.dp,
            title = stringResource(Res.string.settings_section_palette_tint),
            showNavigation = showNavigation,
            onBack = onBack,
            contentVersion = configKey.hashCode(),
            headerContent = onTogglePreview?.let { toggle ->
                {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        TextButton(onClick = toggle, modifier = Modifier.align(Alignment.CenterEnd)) {
                            Text(if (previewDark) "Preview light theme" else "Preview dark theme")
                        }
                    }
                }
            },
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
                        Modifier.background(itemBackgroundColor()).padding(horizontal = 24.dp),
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
                SettingsCategory("Behavior") {
                    SwitchSettingRow(
                        title = "Avoid background color",
                        summary = "Keep tinted cards distinct from the background",
                        icon = Res.drawable.ic_palette,
                        checked = avoidBackgroundColor,
                        onCheckedChange = { persist(newAvoidBackgroundColor = it) },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = ::reset,
            modifier = Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
                .padding(16.dp)
                .widthIn(min = 140.dp)
                .onSizeChanged { resetButtonHeight = with(density) { it.height.toDp() } }
                .shadow(if (LocalHazeGlassEnabled.current) 2.dp else 6.dp, resetButtonShape, clip = false)
                .sharedHazeBackground(
                    glassAppearance = HazeGlassAppearance.FloatingButton,
                    hazeState = hazeState,
                    surfaceColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                    shape = resetButtonShape,
                )
                .semantics { contentDescription = "Reset palette settings" },
            shape = resetButtonShape,
            containerColor = Color.Transparent,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
            ),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = { Icon(painterResource(Res.drawable.ic_refresh), contentDescription = null) },
            text = { Text("Reset", fontFamily = ProductSansFontFamily, fontWeight = FontWeight.SemiBold) },
        )
    }
}

private fun interpolatedPaletteValue(start: Int, target: Int, progress: Float): Int =
    (start + (target - start) * progress).roundToInt()

private val EmptyPalettePreviewImage by lazy { ImageBitmap(1, 1) }

@Composable
private fun PaletteStoryPreview(model: StoryRowModel, style: StoryRowStyle) {
    val preview = rememberResourcePreview(requireNotNull(model.previewImageFallback))
    val palette = preview?.palette
    val baseColor = HarmonicTheme.targetColorScheme.cardBackground
    val tint = remember(palette, style.paletteTintConfigKey, baseColor) {
        PreviewTintPolicy.calculateCardTint(
            baseColor.toArgb(),
            palette,
            style.paletteTintConfigKey,
        )
    }
    val animatedTint by animateColorAsState(
        targetValue = Color(tint),
        animationSpec = contentTween(),
        label = "palette settings sample tint",
    )
    StoryRow(
        model = model.copy(
            previewImageTintArgb = animatedTint.toArgb(),
            // Reserve the thumbnail's layout while decoding without a blocking resource painter.
            previewImageFallback = null,
            previewImageBitmap = preview?.image ?: EmptyPalettePreviewImage,
        ),
        // Keep the list compact and show the sample images even when feed previews are disabled.
        style = style.copy(previewImageMode = StoryPreviewMode.SMALL, showPreviewText = false),
        listItem = true,
        // Only tint changes here; avoid constructing animations for fixed text and row geometry.
        animateChanges = false,
        pageBackground = MaterialTheme.colorScheme.pageBackground,
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
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Text(
                text = valueLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
