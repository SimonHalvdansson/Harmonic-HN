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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import com.simon.harmonichackernews.ui.common.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
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
        Res.drawable.ic_ballot,
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
    showWidgetDebugInfo: Boolean,
    environment: DebugEnvironmentUiState,
    onBack: () -> Unit,
    onAlwaysShowTapToRefreshChanged: (Boolean) -> Unit,
    onShowWidgetDebugInfoChanged: (Boolean) -> Unit,
    onGlassSettingsRequested: () -> Unit,
    onOpenHnId: (Int) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenWithoutCache: () -> Unit,
    onCachePost: () -> Unit,
    onOpenLink: (String) -> Unit,
    onLinkPreviewsRequested: () -> Unit,
    onDialogRequested: (DebugSettingsDialog) -> Unit,
    onEasterEggRequested: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    fun navigate(action: () -> Unit) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        action()
    }

    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTap by remember { mutableStateOf<TimeMark?>(null) }

    SettingsPage(
        title = stringResource(Res.string.settings_section_debug),
        showNavigation = showNavigation,
        onBack = { navigate(onBack) },
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
                    title = "Show widget debug info",
                    summary = "Shows detailed refresh errors on home screen widgets",
                    icon = Res.drawable.ic_info,
                    checked = showWidgetDebugInfo,
                    onCheckedChange = onShowWidgetDebugInfoChanged,
                )
                SettingsDivider()
                DebugOpenSetting(
                    title = "Open HN item by ID",
                    label = "HN ID",
                    description = "Open HN ID",
                    icon = Res.drawable.ic_open_in_new,
                    numeric = true,
                    validate = { value ->
                        when {
                            value.isEmpty() || value.any { !it.isDigit() } -> "Enter a numeric HN ID"
                            value.toIntOrNull() == null -> "HN ID is too large"
                            value.toInt() <= 0 -> "Enter a positive HN ID"
                            else -> null
                        }
                    },
                    onOpen = { onOpenHnId(it.toInt()) },
                )
                SettingsDivider()
                DebugOpenSetting(
                    title = "Open user profile",
                    label = "Username",
                    description = "Open user profile",
                    icon = Res.drawable.ic_person,
                    validate = { value -> if (value.isBlank()) "Enter an HN username" else null },
                    onOpen = onOpenUserProfile,
                )
                SettingsDivider()
                SettingRow(
                    title = "Open without cache",
                    icon = Res.drawable.ic_cached,
                    onClick = { navigate(onOpenWithoutCache) },
                )
            }
        }

        item {
            SettingsCategory("Appearance") {
                SettingRow(
                    title = stringResource(Res.string.settings_section_glass),
                    icon = Res.drawable.ic_palette,
                    onClick = { navigate(onGlassSettingsRequested) },
                )
            }
        }

        item {
            SettingsCategory("Sample content") {
                DebugLinkRows(DebugSampleContentLinks) { url -> navigate { onOpenLink(url) } }
                SettingsDivider()
                SettingRow(
                    title = "Cached post",
                    icon = Res.drawable.ic_cached,
                    onClick = { navigate(onCachePost) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Link previews",
                    icon = Res.drawable.ic_preview,
                    onClick = { navigate(onLinkPreviewsRequested) },
                )
            }
        }

        item {
            SettingsCategory("Dialogs") {
                SettingRow(
                    title = "Welcome dialog",
                    icon = Res.drawable.ic_explore,
                    onClick = { navigate { onDialogRequested(DebugSettingsDialog.WELCOME) } },
                )
                SettingsDivider()
                SettingRow(
                    title = "Changelog",
                    icon = Res.drawable.ic_history,
                    onClick = { navigate { onDialogRequested(DebugSettingsDialog.CHANGELOG) } },
                )
                SettingsDivider()
                SettingRow(
                    title = "Debug notifications",
                    icon = Res.drawable.ic_notifications,
                    onClick = { navigate { onDialogRequested(DebugSettingsDialog.NOTIFICATIONS) } },
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
                            navigate(onEasterEggRequested)
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
private fun DebugOpenSetting(
    title: String,
    label: String,
    description: String,
    icon: DrawableResource,
    numeric: Boolean = false,
    validate: (String) -> String?,
    onOpen: (String) -> Unit,
) {
    val currentOnOpen by rememberUpdatedState(onOpen)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var value by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun open() {
        val trimmed = value.trim()
        error = validate(trimmed)
        if (error == null) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            currentOnOpen(trimmed)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(settingsItemBackgroundColor())
            .padding(
                start = HarmonicDimens.compose_settings_row_horizontal_padding,
                top = 12.dp,
                end = HarmonicDimens.compose_settings_row_horizontal_padding,
                bottom = 12.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = HarmonicTheme.colors.drawable,
            )
            Spacer(Modifier.width(32.dp))
            Text(
                title,
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, top = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = if (numeric) it.filter(Char::isDigit) else it
                    error = null
                },
                modifier = Modifier.weight(1f),
                label = { Text(label, fontFamily = ProductSansFontFamily) },
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
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { open() }),
            )
            OutlinedButton(
                onClick = { open() },
                border = BorderStroke(1.dp, HarmonicTheme.colors.drawable.copy(alpha = 0.2f)),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .padding(start = 10.dp, top = 12.dp)
                    .size(48.dp),
            ) {
                Icon(
                    painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = description,
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
