@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.FaviconLoader
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.ThemeUtils

private data class FaviconProviderOption(
    val value: String,
    val label: String,
)

private val FaviconProviderOptions = listOf(
    FaviconProviderOption(SettingsUtils.FAVICON_PROVIDER_GOOGLE, "Google"),
    FaviconProviderOption(SettingsUtils.FAVICON_PROVIDER_DUCKDUCKGO, "DuckDuckGo"),
    FaviconProviderOption(SettingsUtils.FAVICON_PROVIDER_TWENTY, "Twenty icons"),
)

@Composable
fun FaviconProviderDialog(
    onProviderSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val selected = SettingsUtils.getPreferredFaviconProvider(context)

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
                FaviconProviderOptions.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 76.dp)
                            .selectable(
                                selected = provider.value == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    prefs.edit()
                                        .putString(
                                            SettingsUtils.PREF_FAVICON_PROVIDER,
                                            provider.value,
                                        )
                                        .apply()
                                    onProviderSelected(provider.value)
                                    onDismiss()
                                },
                            )
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(
                                SettingsUtils.getFaviconProviderIconResource(provider.value),
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        ) {
                            Text(
                                text = provider.label,
                                color = HarmonicTheme.colors.storyNormal,
                                fontFamily = ProductSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                            )
                            Text(
                                text = FaviconLoader.getFaviconUrlSchema(provider.value),
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

private val ThreadDepthModes = listOf(
    CommentDepthIndicatorUtils.MODE_THEME_DEFAULT,
    CommentDepthIndicatorUtils.MODE_MATERIAL_YOU,
    CommentDepthIndicatorUtils.MODE_COLORS,
    CommentDepthIndicatorUtils.MODE_MONOCHROME,
    CommentDepthIndicatorUtils.MODE_NONE,
)

@Composable
fun ThreadDepthIndicatorsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var mode by remember {
        mutableStateOf(
            SettingsUtils.getPreferredCommentDepthIndicatorMode(context),
        )
    }
    val theme = ThemeUtils.getPreferredTheme(context)
    val showIndicators = CommentDepthIndicatorUtils.shouldShowIndicators(mode)

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Thread depth indicators") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 8.dp),
                    ) {
                        repeat(CommentDepthIndicatorUtils.COMMENT_DEPTH_COLOR_COUNT) { index ->
                            val targetColor = colorResource(
                                CommentDepthIndicatorUtils.getColorResource(
                                    context,
                                    mode,
                                    theme,
                                    index,
                                ),
                            )
                            val indicatorColor by animateColorAsState(
                                targetValue = targetColor,
                                label = "thread depth color",
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .padding(start = (12 * index).dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AnimatedVisibility(
                                    visible = showIndicators,
                                    enter = expandHorizontally(),
                                    exit = shrinkHorizontally(),
                                ) {
                                    Row {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(3.dp)
                                                .background(indicatorColor),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                    }
                                }
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

                items(ThreadDepthModes, key = { it }) { option ->
                    val selected = CommentDepthIndicatorUtils.sanitizeMode(mode) == option
                    OutlinedButton(
                        onClick = {
                            SettingsUtils.setPreferredCommentDepthIndicatorMode(
                                context,
                                option,
                            )
                            mode = option
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(CommentDepthIndicatorUtils.getModeLabel(option))
                    }
                }
            }
        },
        confirmButton = {},
        showButtons = false,
    )
}
