package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_check
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import com.simon.harmonichackernews.ui.theme.CommentDepthPaletteCatalog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

data class FaviconProviderUiOption(
    val value: String,
    val label: String,
    val urlTemplate: String,
    val icon: Painter,
)

@Composable
fun SharedFaviconProviderDialog(
    selected: String,
    options: List<FaviconProviderUiOption>,
    onProviderSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Favicon provider") },
        edgeToEdgeContent = true,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp)
                    .selectableGroup(),
            ) {
                options.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 76.dp)
                            .selectable(
                                selected = provider.value == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onProviderSelected(provider.value)
                                    onDismiss()
                                },
                            )
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = provider.icon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(
                                text = provider.label,
                                color = HarmonicTheme.colors.storyNormal,
                                fontFamily = ProductSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                            )
                            Text(
                                text = provider.urlTemplate,
                                color = HarmonicTheme.colors.storyDisabled,
                                fontFamily = ProductSansFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                            )
                        }
                        SettingsRadioButton(
                            selected = provider.value == selected,
                            modifier = Modifier.padding(start = 12.dp),
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
fun SharedThreadDepthIndicatorsDialog(
    mode: String,
    indicatorColors: List<Color>,
    onModeSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val modes = listOf(
        CommentDepthPreferences.THEME_DEFAULT,
        CommentDepthPreferences.MATERIAL_YOU,
        CommentDepthPreferences.COLORS,
        CommentDepthPreferences.MONOCHROME,
        CommentDepthPreferences.NONE,
    )

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Thread depth indicators") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                    ) {
                        repeat(CommentDepthPaletteCatalog.colorCount) { index ->
                            val color = indicatorColors.getOrNull(index) ?: Color.Transparent
                            val indicatorColor by animateColorAsState(
                                targetValue = color,
                                label = "thread depth color",
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .padding(start = (12 * index).dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(3.dp)
                                        .background(indicatorColor),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Comment ${index + 1}",
                                    color = HarmonicTheme.colors.storyNormal,
                                    fontFamily = ProductSansFontFamily,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }
                }

                items(modes, key = { it }) { option ->
                    val selected = CommentDepthPreferences.sanitizeMode(mode) == option
                    OutlinedButton(
                        onClick = { onModeSelected(option) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) {
                                HarmonicTheme.colors.settingsHeaderSelected
                            } else {
                                Color.Transparent
                            },
                            contentColor = HarmonicTheme.colors.textPrimary,
                        ),
                    ) {
                        if (selected) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(CommentDepthPreferences.modeLabel(option))
                    }
                }
            }
        },
        confirmButton = {},
        showButtons = false,
    )
}
