@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import com.simon.harmonichackernews.ui.common.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.simon.harmonichackernews.resources.*
import com.kmpalette.extensions.resource.rememberResourcePaletteState
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.ui.common.HarmonicFilterButton
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.theme.GoogleSansFlexRoundedFontFamily
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
fun NighttimeRangeDialog(
    initialHours: IntArray,
    is24Hour: Boolean,
    onRangeSelected: (fromHour: Int, fromMinute: Int, toHour: Int, toMinute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    require(initialHours.size == 4)
    val fromState = rememberTimePickerState(
        initialHour = initialHours[0],
        initialMinute = initialHours[1],
        is24Hour = is24Hour,
    )
    val toState = rememberTimePickerState(
        initialHour = initialHours[2],
        initialMinute = initialHours[3],
        is24Hour = is24Hour,
    )
    var choosingTo by remember { mutableStateOf(false) }
    val timePickerColors = TimePickerDefaults.colors(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.onPrimary,
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.outline,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.onPrimary,
        MaterialTheme.colorScheme.onSurface,
    )

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle(if (choosingTo) "To:" else "From:") },
        text = {
            AnimatedContent(targetState = choosingTo, label = "nighttime time picker") { to ->
                TimePicker(
                    state = if (to) toState else fromState,
                    colors = timePickerColors,
                )
            }
        },
        confirmButton = {
            SettingsDialogTextButton(
                onClick = {
                    if (!choosingTo) {
                        choosingTo = true
                    } else {
                        onRangeSelected(
                            fromState.hour,
                            fromState.minute,
                            toState.hour,
                            toState.minute,
                        )
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
                    if (choosingTo) choosingTo = false else onDismiss()
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
    initialExpressive: Boolean,
    paletteTintConfigKey: String,
    onApplyPreset: (expressive: Boolean) -> Unit,
    onDismiss: () -> Unit,
    filterButtonColors: HarmonicFilterButtonColors,
    launcherIcon: @Composable () -> Unit,
) {
    var expressive by remember(initialExpressive) { mutableStateOf(initialExpressive) }
    val welcomeTextStyle = LocalSettingsPlatformStyle.current.textStyle

    SettingsAlertDialog(
        onDismissRequest = { if (styleChooser) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = styleChooser,
            dismissOnClickOutside = styleChooser,
            usePlatformDefaultWidth = true,
        ),
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                if (!styleChooser) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            launcherIcon()
                        }
                    }
                }
                item {
                    Text(
                        text = if (styleChooser) "Style" else "Welcome to Harmonic",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (styleChooser) 0.dp else 12.dp),
                        color = HarmonicTheme.colors.textPrimary,
                        fontFamily = GoogleSansFlexRoundedFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        style = welcomeTextStyle,
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
                        style = welcomeTextStyle,
                    )
                }
                item {
                    WelcomeStoryPreview(
                        expressive = expressive,
                        paletteTintConfigKey = paletteTintConfigKey,
                        textStyle = welcomeTextStyle,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().selectableGroup().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        StylePresetButton(
                            label = "Expressive",
                            selected = expressive,
                            position = 0,
                            colors = filterButtonColors,
                            fontFamily = GoogleSansFlexRoundedFontFamily,
                            onClick = { expressive = true },
                            modifier = Modifier.weight(1f),
                        )
                        StylePresetButton(
                            label = "Simple",
                            selected = !expressive,
                            position = 1,
                            colors = filterButtonColors,
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
                            style = welcomeTextStyle,
                        )
                    }
                }
                item {
                    Button(
                        onClick = { onApplyPreset(expressive) },
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    ) {
                        Text(
                            text = if (styleChooser) "Apply" else "Get started",
                            fontFamily = ProductSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = welcomeTextStyle,
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
    position: Int,
    colors: HarmonicFilterButtonColors,
    fontFamily: FontFamily,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HarmonicFilterButton(
        label = label,
        selected = selected,
        position = position,
        colors = colors,
        onClick = onClick,
        modifier = modifier,
        fontFamily = fontFamily,
        lastPosition = 1,
    )
}

@Composable
private fun WelcomeStoryPreview(
    expressive: Boolean,
    paletteTintConfigKey: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    val baseColor = HarmonicTheme.colors.storyCardBackground
    val paletteState = rememberResourcePaletteState { maximumColorCount(16) }
    LaunchedEffect(Unit) { paletteState.generate(Res.drawable.palette1) }
    val targetExpressiveColor = remember(
        paletteState.palette,
        paletteTintConfigKey,
        baseColor,
    ) {
        Color(
            PreviewTintPolicy.calculateCardTint(
                baseColor = baseColor.toArgb(),
                palette = paletteState.palette?.toPreviewTintPalette(),
                modeOrConfigKey = paletteTintConfigKey,
            ),
        )
    }
    val expressiveColor by animateColorAsState(
        targetValue = targetExpressiveColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "welcome KMPalette tint",
    )
    AnimatedContent(
        targetState = expressive,
        modifier = modifier,
        label = "welcome style preview",
    ) { isExpressive ->
        WelcomeStoryPreviewContent(isExpressive, expressiveColor, textStyle)
    }
}

@Composable
private fun WelcomeStoryPreviewContent(
    expressive: Boolean,
    expressiveColor: Color,
    textStyle: androidx.compose.ui.text.TextStyle,
) {
    val fontFamily = if (expressive) {
        GoogleSansFlexRoundedFontFamily
    } else {
        ProductSansFontFamily
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (expressive) expressiveColor else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Post title",
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.5.sp,
                style = textStyle,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(Res.drawable.quanta),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "53 points • domain • 2h",
                    color = HarmonicTheme.colors.storyDisabled,
                    fontFamily = fontFamily,
                    fontSize = 13.sp,
                    style = textStyle,
                )
            }
        }
        if (expressive) {
            Image(
                painter = painterResource(Res.drawable.palette1),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .width(72.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        Column(
            modifier = Modifier.padding(start = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_comment),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "18",
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                style = textStyle,
            )
        }
    }
}
