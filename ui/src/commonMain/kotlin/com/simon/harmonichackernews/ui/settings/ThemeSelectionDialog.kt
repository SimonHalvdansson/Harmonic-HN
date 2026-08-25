package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

data class ThemeUiOption(
    val value: String,
    val label: String,
    val description: String,
    val automatic: Boolean = false,
    val dark: Boolean = false,
)

data class ThemePreviewPalette(
    val background: Color,
    val surface: Color,
    val accent: Color,
    val text: Color,
    val secondaryText: Color,
    val dark: Boolean,
)

val HarmonicThemeOptions = listOf(
    ThemeUiOption(
        "material_daynight",
        "Material You (auto)",
        "Follows the system theme",
        automatic = true,
    ),
    ThemeUiOption("material_light", "Material You (light)", "Softer Material light palette"),
    ThemeUiOption(
        "material_dark",
        "Material You (dark)",
        "Softer Material dark palette",
        dark = true,
    ),
    ThemeUiOption(
        "darklight_daynight",
        "Dark/Light (auto)",
        "Classic Harmonic colors, automatic",
        automatic = true,
    ),
    ThemeUiOption("light", "Light", "Warm classic light palette"),
    ThemeUiOption("dark", "Dark", "Classic dark palette", dark = true),
    ThemeUiOption("hacker", "Hacker", "Black with green text and accents", dark = true),
    ThemeUiOption("hacker_news", "HN", "Hacker News orange and paper tones"),
    ThemeUiOption(
        "amoledwhite_daynight",
        "Black/White (auto)",
        "Pure contrast, automatic",
        automatic = true,
    ),
    ThemeUiOption("amoled", "Black", "OLED-friendly black", dark = true),
    ThemeUiOption("white", "White", "Clean white background"),
    ThemeUiOption("gray", "Gray", "Low-contrast dark gray", dark = true),
)

fun harmonicThemeLabel(value: String, fallback: String): String =
    HarmonicThemeOptions.firstOrNull { it.value == value }?.label
        ?: HarmonicThemeOptions.firstOrNull { it.value == fallback }?.label
        ?: value

@Composable
fun ThemeSelectionDialog(
    nighttime: Boolean,
    selected: String,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    previewPalettes: @Composable (String) -> Pair<ThemePreviewPalette, ThemePreviewPalette?>,
) {
    val options = remember(nighttime) {
        if (nighttime) {
            HarmonicThemeOptions.filter { it.dark && !it.automatic }
        } else {
            HarmonicThemeOptions
        }
    }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle(if (nighttime) "Nighttime theme" else "Theme") },
        edgeToEdgeContent = true,
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 624.dp).selectableGroup(),
            ) {
                items(options, key = { it.value }) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 104.dp)
                            .selectable(
                                selected = selected == option.value,
                                role = Role.RadioButton,
                                onClick = { onThemeSelected(option.value) },
                            )
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThemePreview(
                            palettes = previewPalettes(option.value),
                            modifier = Modifier.width(104.dp).height(72.dp),
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
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

private val ThemePreviewDiagonalShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun ThemePreview(
    palettes: Pair<ThemePreviewPalette, ThemePreviewPalette?>,
    modifier: Modifier = Modifier,
) {
    val (primaryPalette, secondaryPalette) = palettes
    Box(modifier = modifier) {
        ThemePreviewLayer(
            palette = secondaryPalette ?: primaryPalette,
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )
        if (secondaryPalette != null) {
            ThemePreviewLayer(
                palette = primaryPalette,
                modifier = Modifier.fillMaxWidth().height(72.dp).clip(ThemePreviewDiagonalShape),
            )
        }
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
            Box(Modifier.width(38.dp).height(5.dp).background(palette.accent, RoundedCornerShape(8.dp)))
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(6.dp).background(
                    palette.text.copy(alpha = 220f / 255f),
                    RoundedCornerShape(4.dp),
                ),
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.height(4.dp)) {
                Box(
                    Modifier.width(18.dp).height(4.dp).background(
                        palette.accent.copy(alpha = 200f / 255f),
                        RoundedCornerShape(4.dp),
                    ),
                )
                Spacer(Modifier.width(5.dp))
                Box(
                    Modifier.width(36.dp).height(4.dp).background(
                        palette.secondaryText.copy(alpha = 190f / 255f),
                        RoundedCornerShape(4.dp),
                    ),
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(
                    palette.secondaryText.copy(
                        alpha = if (palette.dark) 64f / 255f else 46f / 255f,
                    ),
                ),
            )
            Spacer(Modifier.height(5.dp))
            Row(modifier = Modifier.height(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(3.dp).height(14.dp)
                        .background(palette.accent, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Box(
                        Modifier.fillMaxWidth().height(4.dp).background(
                            palette.text.copy(alpha = 190f / 255f),
                            RoundedCornerShape(4.dp),
                        ),
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier.width(44.dp).height(4.dp).background(
                            palette.secondaryText.copy(alpha = 160f / 255f),
                            RoundedCornerShape(4.dp),
                        ),
                    )
                }
            }
        }
    }
}
