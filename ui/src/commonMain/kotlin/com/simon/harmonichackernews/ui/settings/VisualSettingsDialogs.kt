package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_search
import com.simon.harmonichackernews.settings.CommentsProvider
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.GoogleSansCodeFontFamily
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

data class FaviconProviderUiOption(
    val value: String,
    val label: String,
    val urlTemplate: String,
    val icon: Painter,
)

@Composable
fun FaviconProviderDialog(
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
                                color = HarmonicTheme.colors.contentPrimary,
                                fontFamily = ProductSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                            )
                            Text(
                                text = provider.urlTemplate,
                                color = HarmonicTheme.colors.mutedText,
                                fontFamily = GoogleSansCodeFontFamily,
                                fontSize = 11.sp,
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
fun CommentsProviderDialog(
    selected: CommentsProvider,
    onProviderSelected: (CommentsProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    val providers = listOf(CommentsProvider.OFFICIAL, CommentsProvider.ALGOLIA)
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Comments provider") },
        edgeToEdgeContent = true,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .selectableGroup(),
            ) {
                providers.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 76.dp)
                            .selectable(
                                selected = provider == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onProviderSelected(provider)
                                    onDismiss()
                                },
                            )
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProviderBrandMark(provider)
                        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(
                                text = provider.label,
                                color = HarmonicTheme.colors.contentPrimary,
                                fontFamily = ProductSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                            )
                            Text(
                                text = if (provider == CommentsProvider.OFFICIAL) {
                                    "Direct from news.ycombinator.com"
                                } else {
                                    "Fast threaded results from Algolia's HN search API"
                                },
                                color = HarmonicTheme.colors.mutedText,
                                fontFamily = ProductSansFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                            )
                        }
                        SettingsRadioButton(
                            selected = provider == selected,
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
private fun ProviderBrandMark(provider: CommentsProvider) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                if (provider == CommentsProvider.OFFICIAL) {
                    Color(0xFFFF6600)
                } else {
                    Color(0xFF003DFF)
                },
                androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (provider == CommentsProvider.OFFICIAL) {
            Text(
                text = "Y",
                color = Color.White,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
            )
        } else {
            Icon(
                painter = painterResource(Res.drawable.ic_search),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}
