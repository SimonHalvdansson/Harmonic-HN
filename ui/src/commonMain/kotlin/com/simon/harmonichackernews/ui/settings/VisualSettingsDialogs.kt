package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_algolia
import com.simon.harmonichackernews.resources.ic_favicon_provider_duckduckgo
import com.simon.harmonichackernews.resources.ic_favicon_provider_google
import com.simon.harmonichackernews.resources.ic_favicon_provider_twenty
import com.simon.harmonichackernews.settings.CommentsProvider
import com.simon.harmonichackernews.settings.FaviconPreferences
import com.simon.harmonichackernews.settings.FaviconProviderCatalog
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun FaviconProviderSetting(
    selected: String,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    val providers = FaviconProviderCatalog.options
    SegmentedSetting(
        title = "Favicon provider",
        options = providers.map { it to it.label },
        selected = providers.first { it.value == FaviconPreferences.sanitizeProvider(selected) },
        enabled = enabled,
        buttonHeight = 68.dp,
        optionContent = { provider, checked ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val icon = when (provider.value) {
                    FaviconPreferences.DUCK_DUCK_GO -> Res.drawable.ic_favicon_provider_duckduckgo
                    FaviconPreferences.TWENTY -> Res.drawable.ic_favicon_provider_twenty
                    else -> Res.drawable.ic_favicon_provider_google
                }
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
                Text(
                    text = provider.label,
                    color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    fontFamily = AppUiFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        },
        onSelected = { onSelected(it.value) },
    )
}

@Composable
fun CommentsProviderDialog(
    selected: CommentsProvider,
    onProviderSelected: (CommentsProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    val providers = listOf(CommentsProvider.ALGOLIA, CommentsProvider.OFFICIAL)
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
                                onClick = { onProviderSelected(provider) },
                            )
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProviderBrandMark(provider)
                        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(
                                text = provider.label,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = AppUiFontFamily,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = AppUiFontFamily,
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
        confirmButton = { SettingsDialogTextButton(onClick = onDismiss) { Text("Done") } },
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
                fontFamily = AppUiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
            )
        } else {
            Icon(
                painter = painterResource(Res.drawable.ic_algolia),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}
