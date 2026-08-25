package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class DebugSettingsDialog {
    WELCOME,
    CHANGELOG,
    NOTIFICATIONS,
}

data class DebugEnvironmentUiState(
    val appVersion: String,
    val appBuild: String,
    val buildVersion: String,
    val platformVersion: String,
    val platformLabel: String = "Android version",
)

data class DebugLink(
    val title: String,
    val icon: DrawableResource,
    val url: String,
)

val DebugSampleContentLinks = listOf(
    DebugLink(
        "Link post",
        Res.drawable.ic_ballot,
        "https://news.ycombinator.com/item?id=47938725",
    ),
    DebugLink(
        "Reference links post",
        Res.drawable.ic_link,
        "https://news.ycombinator.com/item?id=48352939",
    ),
    DebugLink(
        "YouTube comment",
        Res.drawable.ic_link,
        "https://news.ycombinator.com/item?id=34225887",
    ),
    DebugLink(
        "Very long comment",
        Res.drawable.ic_comment,
        "https://news.ycombinator.com/item?id=49103136",
    ),
    DebugLink(
        "Poll",
        Res.drawable.ic_comment,
        "https://news.ycombinator.com/item?id=39572682",
    ),
    DebugLink(
        "Internal HN link",
        Res.drawable.ic_link,
        "https://news.ycombinator.com/item?id=30676384",
    ),
)

@Composable
fun DebugSettingsScreen(
    showNavigation: Boolean,
    contentVersion: Int,
    alwaysShowTapToRefresh: Boolean,
    showAiSummaryDebugInfo: Boolean,
    environment: DebugEnvironmentUiState,
    onBack: () -> Unit,
    onAlwaysShowTapToRefreshChanged: (Boolean) -> Unit,
    onShowAiSummaryDebugInfoChanged: (Boolean) -> Unit,
    onOpenHnId: (Int) -> Unit,
    onOpenWithoutCache: () -> Unit,
    onCachePost: () -> Unit,
    onOpenLink: (String) -> Unit,
    onLinkPreviewsRequested: () -> Unit,
    onDialogRequested: (DebugSettingsDialog) -> Unit,
    onEasterEggRequested: () -> Unit,
) {
    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTap by remember { mutableStateOf<TimeMark?>(null) }

    SettingsPage(
        title = stringResource(Res.string.settings_section_debug),
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = contentVersion,
    ) {
        item {
            SettingsCategory("Debug tools") {
                SwitchSettingRow(
                    title = "Always show tap to refresh",
                    icon = Res.drawable.ic_refresh,
                    checked = alwaysShowTapToRefresh,
                    onCheckedChange = onAlwaysShowTapToRefreshChanged,
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Additional AI summary debug info",
                    icon = Res.drawable.ic_info,
                    checked = showAiSummaryDebugInfo,
                    onCheckedChange = onShowAiSummaryDebugInfoChanged,
                )
                SettingsDivider()
                DebugHnIdSetting(onOpenId = onOpenHnId)
                SettingsDivider()
                SettingRow(
                    title = "Open without cache",
                    icon = Res.drawable.ic_cached,
                    onClick = onOpenWithoutCache,
                )
            }
        }

        item {
            SettingsCategory("Sample content") {
                DebugLinkRows(DebugSampleContentLinks, onOpenLink)
                SettingsDivider()
                SettingRow(
                    title = "Cached post",
                    icon = Res.drawable.ic_cached,
                    onClick = onCachePost,
                )
                SettingsDivider()
                SettingRow(
                    title = "Link previews",
                    icon = Res.drawable.ic_link,
                    onClick = onLinkPreviewsRequested,
                )
            }
        }

        item {
            SettingsCategory("Dialogs") {
                SettingRow(
                    title = "Welcome dialog",
                    icon = Res.drawable.ic_explore,
                    onClick = { onDialogRequested(DebugSettingsDialog.WELCOME) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Changelog",
                    icon = Res.drawable.ic_history,
                    onClick = { onDialogRequested(DebugSettingsDialog.CHANGELOG) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Debug notifications",
                    icon = Res.drawable.ic_notifications,
                    onClick = { onDialogRequested(DebugSettingsDialog.NOTIFICATIONS) },
                )
            }
        }

        item {
            SettingsCategory("Environment") {
                SettingRow(
                    title = "App version",
                    summary = environment.appVersion,
                    icon = Res.drawable.ic_deployed_code,
                    onClick = {
                        val now = TimeSource.Monotonic.markNow()
                        versionTapCount = if (
                            lastVersionTap?.elapsedNow()?.inWholeMilliseconds?.let {
                                it in 0L..<800L
                            } == true
                        ) {
                            versionTapCount + 1
                        } else {
                            1
                        }
                        lastVersionTap = now
                        if (versionTapCount == 5) {
                            versionTapCount = 0
                            lastVersionTap = null
                            onEasterEggRequested()
                        }
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "App build",
                    summary = environment.appBuild,
                    icon = Res.drawable.ic_tag,
                    onClick = null,
                )
                SettingsDivider()
                SettingRow(
                    title = "Build version",
                    summary = environment.buildVersion,
                    icon = Res.drawable.ic_build,
                    onClick = null,
                )
                SettingsDivider()
                SettingRow(
                    title = environment.platformLabel,
                    summary = environment.platformVersion,
                    icon = Res.drawable.ic_android,
                    onClick = null,
                )
            }
        }
    }
}

@Composable
private fun DebugHnIdSetting(onOpenId: (Int) -> Unit) {
    val currentOnOpenId by rememberUpdatedState(onOpenId)
    val keyboardController = LocalSoftwareKeyboardController.current
    var value by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun openId() {
        val trimmed = value.trim()
        val id = trimmed.toIntOrNull()
        when {
            trimmed.isEmpty() || trimmed.any { !it.isDigit() } ->
                error = "Enter a numeric HN ID"
            id == null -> error = "HN ID is too large"
            id <= 0 -> error = "Enter a positive HN ID"
            else -> {
                error = null
                keyboardController?.hide()
                currentOnOpenId(id)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmonicTheme.colors.settingsSegment)
            .padding(start = 24.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Row {
            Icon(
                painterResource(Res.drawable.ic_open_in_new),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = HarmonicTheme.colors.drawable,
            )
            Spacer(Modifier.width(32.dp))
            Text(
                "Open HN item by ID",
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, top = 12.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it.filter(Char::isDigit)
                    error = null
                },
                modifier = Modifier.weight(1f),
                label = { Text("HN ID", fontFamily = ProductSansFontFamily) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { message ->
                    { Text(message, fontFamily = ProductSansFontFamily) }
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = ProductSansFontFamily,
                    fontSize = 16.sp,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { openId() }),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedIconButton(
                onClick = { openId() },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = "Open HN ID",
                    tint = HarmonicTheme.colors.drawable,
                )
            }
        }
    }
}

@Composable
private fun DebugLinkRows(
    links: List<DebugLink>,
    onOpenLink: (String) -> Unit,
) {
    links.forEachIndexed { index, link ->
        SettingRow(
            title = link.title,
            icon = link.icon,
            onClick = { onOpenLink(link.url) },
        )
        if (index != links.lastIndex) SettingsDivider()
    }
}
