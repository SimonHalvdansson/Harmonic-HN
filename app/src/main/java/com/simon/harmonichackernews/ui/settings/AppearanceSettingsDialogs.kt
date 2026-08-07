@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.simon.harmonichackernews.ui.settings

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.preference.PreferenceManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.AiModelCatalog
import com.simon.harmonichackernews.ui.theme.GoogleSansFlexRoundedFontFamily
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.FontUtils
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class ComposeThemeOption(
    val value: String,
    val label: String,
    val description: String,
    val automatic: Boolean = false,
    val dark: Boolean = false,
)

private val ComposeThemeOptions = listOf(
    ComposeThemeOption(
        "material_daynight",
        "Material You (auto)",
        "Follows the system theme",
        automatic = true,
    ),
    ComposeThemeOption(
        "material_light",
        "Material You (light)",
        "Softer Material light palette",
    ),
    ComposeThemeOption(
        "material_dark",
        "Material You (dark)",
        "Softer Material dark palette",
        dark = true,
    ),
    ComposeThemeOption(
        "darklight_daynight",
        "Dark/Light (auto)",
        "Classic Harmonic colors, automatic",
        automatic = true,
    ),
    ComposeThemeOption(
        "light",
        "Light",
        "Warm classic light palette",
    ),
    ComposeThemeOption(
        "dark",
        "Dark",
        "Classic dark palette",
        dark = true,
    ),
    ComposeThemeOption(
        "hacker",
        "Hacker",
        "Black with green text and accents",
        dark = true,
    ),
    ComposeThemeOption(
        "hacker_news",
        "HN",
        "Hacker News orange and paper tones",
    ),
    ComposeThemeOption(
        "amoledwhite_daynight",
        "Black/White (auto)",
        "Pure contrast, automatic",
        automatic = true,
    ),
    ComposeThemeOption(
        "amoled",
        "Black",
        "OLED-friendly black",
        dark = true,
    ),
    ComposeThemeOption(
        "white",
        "White",
        "Clean white background",
    ),
    ComposeThemeOption(
        "gray",
        "Gray",
        "Low-contrast dark gray",
        dark = true,
    ),
)

fun composeThemeLabel(value: String, fallback: String = SettingsUtils.DEFAULT_THEME): String {
    return ComposeThemeOptions.firstOrNull { it.value == value }?.label
        ?: ComposeThemeOptions.firstOrNull { it.value == fallback }?.label
        ?: value
}

@Composable
fun ThemeSelectionDialog(
    nighttime: Boolean,
    onDismiss: () -> Unit,
    onThemeChanged: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val key = if (nighttime) {
        SettingsUtils.PREF_THEME_NIGHTTIME
    } else {
        SettingsUtils.PREF_THEME
    }
    val fallback = if (nighttime) {
        SettingsUtils.DEFAULT_NIGHTTIME_THEME
    } else {
        SettingsUtils.DEFAULT_THEME
    }
    val selected = prefs.getString(key, fallback) ?: fallback
    val options = remember(nighttime) {
        if (nighttime) {
            ComposeThemeOptions.filter { it.dark && !it.automatic }
        } else {
            ComposeThemeOptions
        }
    }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            SettingsDialogTitle(if (nighttime) "Nighttime theme" else "Theme")
        },
        edgeToEdgeContent = true,
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 624.dp)
                    .selectableGroup(),
            ) {
                items(options, key = { it.value }) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 104.dp)
                            .selectable(
                                selected = selected == option.value,
                                role = Role.RadioButton,
                                onClick = {
                                    prefs.edit().putString(key, option.value).apply()
                                    onDismiss()
                                    onThemeChanged()
                                },
                            )
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThemePreview(
                            option = option,
                            modifier = Modifier
                                .width(104.dp)
                                .height(72.dp),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        ) {
                            Text(
                                text = option.label,
                                color = HarmonicTheme.colors.textPrimary,
                                fontFamily = ProductSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                            Text(
                                text = option.description,
                                color = HarmonicTheme.colors.storyDisabled,
                                fontFamily = ProductSansFontFamily,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        SettingsRadioButton(
                            selected = selected == option.value,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        showButtons = false,
    )
}

@Composable
private fun ThemePreview(
    option: ComposeThemeOption,
    modifier: Modifier = Modifier,
) {
    val (primaryPalette, secondaryPalette) = themePreviewPalettes(option.value)
    Box(
        modifier = modifier,
    ) {
        ThemePreviewLayer(
            palette = secondaryPalette ?: primaryPalette,
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
        if (secondaryPalette != null) {
            ThemePreviewLayer(
                palette = primaryPalette,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(ThemePreviewDiagonalShape),
            )
        }
    }
}

private data class ThemePreviewPalette(
    val background: Color,
    val surface: Color,
    val accent: Color,
    val text: Color,
    val secondaryText: Color,
    val dark: Boolean,
)

private val ThemePreviewDiagonalShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun themePreviewPalettes(
    theme: String,
): Pair<ThemePreviewPalette, ThemePreviewPalette?> {
    val dark = ThemePreviewPalette(
        background = colorResource(R.color.background),
        surface = colorResource(R.color.darkerBackground),
        accent = colorResource(R.color.colorPrimary),
        text = colorResource(R.color.darkStoryColorNormal),
        secondaryText = colorResource(R.color.darkTextColorDefault),
        dark = true,
    )
    val materialDark = ThemePreviewPalette(
        background = colorResource(R.color.material_you_neutral_900),
        surface = colorResource(R.color.material_you_neutral_variant20),
        accent = colorResource(R.color.material_you_third_400),
        text = colorResource(R.color.darkStoryColorNormal),
        secondaryText = colorResource(R.color.material_you_secondary_70),
        dark = true,
    )
    val grayBackground = colorResource(R.color.grayBackground)
    val darkText = colorResource(R.color.darkStoryColorNormal)
    val gray = ThemePreviewPalette(
        background = grayBackground,
        surface = Color(ColorUtils.blendARGB(grayBackground.toArgb(), darkText.toArgb(), 0.07f)),
        accent = colorResource(R.color.colorPrimary),
        text = darkText,
        secondaryText = colorResource(R.color.darkTextColorDefault),
        dark = true,
    )
    val black = ThemePreviewPalette(
        background = Color.Black,
        surface = Color.Black,
        accent = colorResource(R.color.colorPrimary),
        text = colorResource(R.color.darkStoryColorNormal),
        secondaryText = colorResource(R.color.darkTextColorDefault),
        dark = true,
    )
    val hacker = ThemePreviewPalette(
        background = colorResource(R.color.hackerBackground),
        surface = colorResource(R.color.hackerSurfaceContainer),
        accent = colorResource(R.color.hackerAccent),
        text = colorResource(R.color.hackerTextColor),
        secondaryText = colorResource(R.color.hackerTextColorDisabled),
        dark = true,
    )
    val lightBackground = colorResource(R.color.lightBackground)
    val lightText = colorResource(R.color.lightStoryColorNormal)
    val light = ThemePreviewPalette(
        background = lightBackground,
        surface = Color(
            ColorUtils.blendARGB(lightBackground.toArgb(), Color.White.toArgb(), 0.56f),
        ),
        accent = colorResource(R.color.colorPrimaryGreen),
        text = lightText,
        secondaryText = colorResource(R.color.lightTextColorDefault),
        dark = false,
    )
    val hackerNews = ThemePreviewPalette(
        background = colorResource(R.color.hackerNewsBackground),
        surface = colorResource(R.color.hackerNewsSurfaceContainer),
        accent = colorResource(R.color.hackerNewsAccent),
        text = colorResource(R.color.hackerNewsStoryColorNormal),
        secondaryText = colorResource(R.color.hackerNewsTextColorDisabled),
        dark = false,
    )
    val materialLight = ThemePreviewPalette(
        background = colorResource(R.color.material_you_neutral_50),
        surface = colorResource(R.color.material_you_neutral_variant95),
        accent = colorResource(R.color.material_you_third_400),
        text = lightText,
        secondaryText = colorResource(R.color.material_you_secondary_40),
        dark = false,
    )
    val white = ThemePreviewPalette(
        background = Color.White,
        surface = Color(
            ColorUtils.blendARGB(Color.White.toArgb(), lightText.toArgb(), 0.035f),
        ),
        accent = colorResource(R.color.colorPrimaryGreen),
        text = lightText,
        secondaryText = colorResource(R.color.lightTextColorDefault),
        dark = false,
    )
    return when (theme) {
        "material_daynight" -> materialLight to materialDark
        "darklight_daynight" -> light to dark
        "amoledwhite_daynight" -> white to black
        "material_light" -> materialLight to null
        "material_dark" -> materialDark to null
        "light" -> light to null
        "hacker" -> hacker to null
        "hacker_news" -> hackerNews to null
        "amoled" -> black to null
        "white" -> white to null
        "gray" -> gray to null
        else -> dark to null
    }
}

@Composable
private fun ThemePreviewLayer(
    palette: ThemePreviewPalette,
    modifier: Modifier = Modifier,
) {
    val outline = palette.text.copy(alpha = if (palette.dark) 70f / 255f else 42f / 255f)
    val cardOutline = palette.text.copy(alpha = if (palette.dark) 44f / 255f else 28f / 255f)
    Box(
        modifier = modifier
            .background(palette.background, RoundedCornerShape(16.dp))
            .border(1.dp, outline, RoundedCornerShape(16.dp))
            .padding(7.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(palette.surface, RoundedCornerShape(12.dp))
                .border(1.dp, cardOutline, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
            Box(
                Modifier
                    .width(38.dp)
                    .height(5.dp)
                    .background(palette.accent, RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(palette.text.copy(alpha = 220f / 255f), RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.height(4.dp)) {
                Box(
                    Modifier
                        .width(18.dp)
                        .height(4.dp)
                        .background(
                            palette.accent.copy(alpha = 200f / 255f),
                            RoundedCornerShape(4.dp),
                        ),
                )
                Spacer(Modifier.width(5.dp))
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(
                            palette.secondaryText.copy(alpha = 190f / 255f),
                            RoundedCornerShape(4.dp),
                        ),
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        palette.secondaryText.copy(
                            alpha = if (palette.dark) 64f / 255f else 46f / 255f,
                        ),
                    ),
            )
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.height(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(palette.accent, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                palette.text.copy(alpha = 190f / 255f),
                                RoundedCornerShape(4.dp),
                            ),
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .width(44.dp)
                            .height(4.dp)
                            .background(
                                palette.secondaryText.copy(alpha = 160f / 255f),
                                RoundedCornerShape(4.dp),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
fun NighttimeRangeDialog(
    onDismiss: () -> Unit,
    onRangeSelected: () -> Unit,
) {
    val context = LocalContext.current
    val current = remember(context) { Utils.getNighttimeHours(context) }
    val is24Hour = DateFormat.is24HourFormat(context)
    val fromState = rememberTimePickerState(
        initialHour = current[0],
        initialMinute = current[1],
        is24Hour = is24Hour,
    )
    val toState = rememberTimePickerState(
        initialHour = current[2],
        initialMinute = current[3],
        is24Hour = is24Hour,
    )
    var choosingTo by remember { mutableStateOf(false) }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle(if (choosingTo) "To:" else "From:") },
        text = {
            AnimatedContent(
                targetState = choosingTo,
                label = "nighttime time picker",
            ) { to ->
                TimePicker(state = if (to) toState else fromState)
            }
        },
        confirmButton = {
            SettingsDialogTextButton(
                onClick = {
                    if (!choosingTo) {
                        choosingTo = true
                    } else {
                        Utils.setNighttimeHours(
                            fromState.hour,
                            fromState.minute,
                            toState.hour,
                            toState.minute,
                            context,
                        )
                        onRangeSelected()
                        onDismiss()
                    }
                },
            ) {
                Text(if (choosingTo) "OK" else "Next")
            }
        },
        dismissButton = {
            SettingsDialogTextButton(
                onClick = {
                    if (choosingTo) {
                        choosingTo = false
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text(if (choosingTo) "Back" else "Cancel")
            }
        },
    )
}

@Composable
fun WelcomeSettingsDialog(
    styleChooser: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    var expressive by remember {
        mutableStateOf(
            !styleChooser ||
                SettingsUtils.getPreferredFont(context) != "productsans" ||
                SettingsUtils.shouldTintCardUsingPreview(context) ||
                SettingsUtils.getPreferredStoryPreviewImageMode(context) !=
                SettingsUtils.STORY_PREVIEW_IMAGE_OFF,
        )
    }
    AiModelCatalog.ensureInitialDefault(context)

    SettingsAlertDialog(
        onDismissRequest = {
            if (styleChooser) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = styleChooser,
            dismissOnClickOutside = styleChooser,
            usePlatformDefaultWidth = true,
        ),
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                if (!styleChooser) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(colorResource(R.color.ic_launcher_background)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = "Harmonic",
                                    modifier = Modifier.size(64.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = if (styleChooser) {
                            "Style"
                        } else {
                            "Welcome to Harmonic for Hacker News"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (styleChooser) 0.dp else 12.dp),
                        color = HarmonicTheme.colors.textPrimary,
                        fontFamily = GoogleSansFlexRoundedFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    Text(
                        text = if (styleChooser) {
                            "Choose a general style for the app. This changes the font, " +
                                "story preview images and palette tint."
                        } else {
                            "I hope you'll love the app and feel how much care was put " +
                                "into creating it. If you have an issue or want to submit " +
                                "a PR, you can visit the GitHub repository.\n\nBefore we " +
                                "get started you can select a UI preset below."
                        },
                        modifier = Modifier.padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = GoogleSansFlexRoundedFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
                item {
                    WelcomeStoryPreview(
                        expressive = expressive,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        StylePresetButton(
                            label = "Expressive",
                            selected = expressive,
                            fontFamily = GoogleSansFlexRoundedFontFamily,
                            onClick = { expressive = true },
                            modifier = Modifier.weight(1f),
                        )
                        StylePresetButton(
                            label = "Simple",
                            selected = !expressive,
                            fontFamily = ProductSansFontFamily,
                            onClick = { expressive = false },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (!styleChooser) {
                    item {
                        Text(
                            text = "There are a ton of customization options beyond " +
                                "this available in the settings for you to explore.",
                            modifier = Modifier.padding(top = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = GoogleSansFlexRoundedFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
                item {
                    Button(
                        onClick = {
                            if (expressive) {
                                prefs.edit()
                                    .putBoolean(
                                        SettingsUtils.PREF_TINT_CARD_USING_PREVIEW,
                                        true,
                                    )
                                    .putString(
                                        SettingsUtils.PREF_FONT,
                                        "googlesansflexrounded",
                                    )
                                    .putString(
                                        SettingsUtils.PREF_STORY_PREVIEW_IMAGE_MODE,
                                        SettingsUtils.STORY_PREVIEW_IMAGE_SMALL,
                                    )
                                    .apply()
                            } else {
                                prefs.edit()
                                    .putBoolean(
                                        SettingsUtils.PREF_TINT_CARD_USING_PREVIEW,
                                        false,
                                    )
                                    .putString(SettingsUtils.PREF_FONT, "productsans")
                                    .putString(
                                        SettingsUtils.PREF_STORY_PREVIEW_IMAGE_MODE,
                                        SettingsUtils.STORY_PREVIEW_IMAGE_OFF,
                                    )
                                    .apply()
                            }
                            FontUtils.init(context)
                            MainActivity.applyWelcomePresetToActiveUi()
                            if (!styleChooser) {
                                Utils.markWelcomeDialogShown(context)
                            }
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                            .height(56.dp),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    ) {
                        Text(
                            text = if (styleChooser) "Apply" else "Get started",
                            fontFamily = ProductSansFontFamily,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        showButtons = false,
    )
}

@Composable
private fun StylePresetButton(
    label: String,
    selected: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(50.dp),
            shapes = ButtonDefaults.shapes(shape = CircleShape),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
        ) {
            Text(
                text = label,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(50.dp),
            shapes = ButtonDefaults.shapes(shape = CircleShape),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Text(
                text = label,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun WelcomeStoryPreview(
    expressive: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (expressive) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Post title",
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = if (expressive) {
                    GoogleSansFlexRoundedFontFamily
                } else {
                    ProductSansFontFamily
                },
                fontWeight = FontWeight.Bold,
                fontSize = 17.5.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.quanta),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "53 points • domain • 2h",
                    color = HarmonicTheme.colors.storyDisabled,
                    fontFamily = if (expressive) {
                        GoogleSansFlexRoundedFontFamily
                    } else {
                        ProductSansFontFamily
                    },
                    fontSize = 13.sp,
                )
            }
        }
        if (expressive) {
            Image(
                painter = painterResource(R.drawable.palette1),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(64.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
        Column(
            modifier = Modifier.padding(start = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_comment),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "18",
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

private data class PalettePreviewSample(
    @DrawableRes val drawable: Int,
    val title: String,
    val meta: String,
)

private val PalettePreviewSamples = listOf(
    PalettePreviewSample(R.drawable.palette1, "Compiler release", "143 points"),
    PalettePreviewSample(R.drawable.palette2, "Design notes", "89 points"),
    PalettePreviewSample(R.drawable.palette3, "Database internals", "311 points"),
    PalettePreviewSample(R.drawable.palette4, "Ask HN", "54 comments"),
    PalettePreviewSample(R.drawable.palette5, "Launch write-up", "217 points"),
    PalettePreviewSample(R.drawable.web_preview, "Website preview", "example.com"),
)

@Composable
fun PaletteTintDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var mode by remember {
        mutableStateOf(SettingsUtils.getPreferredPaletteTintMode(context))
    }
    var strength by remember {
        mutableStateOf(SettingsUtils.getPreferredPaletteTintStrength(context))
    }
    var colorfulness by remember {
        mutableStateOf(SettingsUtils.getPreferredPaletteTintColorfulness(context))
    }
    var tone by remember {
        mutableStateOf(SettingsUtils.getPreferredPaletteTintTone(context))
    }
    val animationScope = rememberCoroutineScope()
    var resetAnimation by remember { mutableStateOf<Job?>(null) }

    fun persist(
        newMode: String = mode,
        newStrength: Int = strength,
        newColorfulness: Int = colorfulness,
        newTone: Int = tone,
    ) {
        resetAnimation?.cancel()
        resetAnimation = null
        mode = SettingsUtils.sanitizePaletteTintMode(newMode)
        strength = SettingsUtils.clampPaletteTintStrength(newStrength)
        colorfulness = SettingsUtils.clampPaletteTintColorfulness(newColorfulness)
        tone = SettingsUtils.clampPaletteTintTone(newTone)
        SettingsUtils.setPreferredPaletteTintSettings(
            context,
            mode,
            strength,
            colorfulness,
            tone,
        )
    }

    val configKey = SettingsUtils.buildPaletteTintConfigKey(
        mode,
        strength,
        colorfulness,
        tone,
    )

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Configure palette tint") },
        edgeToEdgeContent = true,
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 556.dp),
            ) {
                item {
                    PaletteSectionLabel(
                        text = "Preview",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                    ) {
                        items(PalettePreviewSamples, key = { it.drawable }) { sample ->
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
                        modifier = Modifier.padding(
                            start = 24.dp,
                            top = 18.dp,
                            end = 24.dp,
                        ),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .selectableGroup(),
                    ) {
                        listOf(
                            SettingsUtils.PALETTE_TINT_DEFAULT to "Muted",
                            SettingsUtils.PALETTE_TINT_VIBRANT to "Vibrant",
                            SettingsUtils.PALETTE_TINT_DOMINANT to "Dominant",
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
                        modifier = Modifier.padding(
                            start = 24.dp,
                            top = 16.dp,
                            end = 24.dp,
                        ),
                    )
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        PaletteAdjustment(
                            label = "Tint strength",
                            valueLabel = "$strength%",
                            value = strength.toFloat(),
                            valueRange = SettingsUtils.MIN_PALETTE_TINT_STRENGTH.toFloat()..
                                SettingsUtils.MAX_PALETTE_TINT_STRENGTH.toFloat(),
                            steps = 39,
                            onValueChange = {
                                persist(newStrength = (it / 5f).toInt() * 5)
                            },
                        )
                        PaletteAdjustment(
                            label = "Colorfulness",
                            valueLabel = "$colorfulness%",
                            value = colorfulness.toFloat(),
                            valueRange = SettingsUtils.MIN_PALETTE_TINT_COLORFULNESS.toFloat()..
                                SettingsUtils.MAX_PALETTE_TINT_COLORFULNESS.toFloat(),
                            steps = 39,
                            onValueChange = {
                                persist(newColorfulness = (it / 5f).toInt() * 5)
                            },
                        )
                        PaletteAdjustment(
                            label = "Brightness",
                            valueLabel = if (tone > 0) "+$tone" else tone.toString(),
                            value = tone.toFloat(),
                            valueRange = SettingsUtils.MIN_PALETTE_TINT_TONE.toFloat()..
                                SettingsUtils.MAX_PALETTE_TINT_TONE.toFloat(),
                            steps = 39,
                            onValueChange = { persist(newTone = it.toInt()) },
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            SettingsDialogTextButton(
                onClick = {
                    val startStrength = strength
                    val startColorfulness = colorfulness
                    val startTone = tone
                    mode = SettingsUtils.PALETTE_TINT_DEFAULT
                    SettingsUtils.clearPreferredPaletteTintMode(context)
                    resetAnimation?.cancel()
                    resetAnimation = animationScope.launch {
                        animate(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 200,
                                easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f),
                            ),
                        ) { progress, _ ->
                            strength = steppedPaletteValue(
                                startStrength,
                                SettingsUtils.DEFAULT_PALETTE_TINT_STRENGTH,
                                progress,
                                5,
                            )
                            colorfulness = steppedPaletteValue(
                                startColorfulness,
                                SettingsUtils.DEFAULT_PALETTE_TINT_COLORFULNESS,
                                progress,
                                5,
                            )
                            tone = (
                                startTone +
                                    (SettingsUtils.DEFAULT_PALETTE_TINT_TONE - startTone) *
                                    progress
                                ).roundToInt()
                        }
                        strength = SettingsUtils.DEFAULT_PALETTE_TINT_STRENGTH
                        colorfulness = SettingsUtils.DEFAULT_PALETTE_TINT_COLORFULNESS
                        tone = SettingsUtils.DEFAULT_PALETTE_TINT_TONE
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
): Int {
    return ((start + (target - start) * progress).roundToInt().toFloat() / step)
        .roundToInt() * step
}

@Composable
private fun PaletteSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
    val context = LocalContext.current
    val baseColor = HarmonicTheme.colors.surfaceContainerHigh
    val targetColor = remember(context, sample.drawable, configKey, baseColor) {
        ContextCompat.getDrawable(context, sample.drawable)?.let { drawable ->
            runCatching {
                Color(
                    PreviewImageTintUtils.calculateCardTint(
                        baseColor.toArgb(),
                        drawable,
                        configKey,
                    ),
                )
            }.getOrNull()
        } ?: baseColor
    }
    val cardColor by animateColorAsState(
        targetValue = targetColor,
        label = "palette preview tint",
    )

    Card(
        modifier = modifier.width(136.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            HarmonicTheme.colors.storyNormal.copy(alpha = 0.14f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Image(
                painter = painterResource(sample.drawable),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
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
