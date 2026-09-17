package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.DataSettingsDialogState
import com.simon.harmonichackernews.settings.DataSettingsRuntime
import com.simon.harmonichackernews.settings.DataSettingsSnapshot
import com.simon.harmonichackernews.summary.formatDecimalBytes
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

enum class DataSettingsAction {
    AddBookmarksToFavorites,
    ExportBookmarks,
    ImportBookmarks,
    ClearHistory,
    ClearPostCache,
    ClearTintCache,
    ClearAiModels,
    OpenLinksSettings,
    ResetSettings,
}

fun DataSettingsRuntime.handleDataSettingsAction(action: DataSettingsAction) {
    when (action) {
        DataSettingsAction.AddBookmarksToFavorites -> addBookmarksToFavorites()
        DataSettingsAction.ExportBookmarks -> exportBookmarks()
        DataSettingsAction.ImportBookmarks -> showDialog(DataSettingsDialogState.IMPORT)
        DataSettingsAction.ClearHistory -> clearHistory()
        DataSettingsAction.ClearPostCache -> clearPostCache()
        DataSettingsAction.ClearTintCache -> clearTintCache()
        DataSettingsAction.ClearAiModels -> showDialog(DataSettingsDialogState.AI_MODELS)
        DataSettingsAction.OpenLinksSettings -> showDialog(DataSettingsDialogState.LINKS)
        DataSettingsAction.ResetSettings -> showDialog(DataSettingsDialogState.RESET)
    }
}

@Composable
fun DataSettingsScreen(
    state: DataSettingsSnapshot,
    showNavigation: Boolean,
    showAppLinkSettings: Boolean = true,
    onBack: () -> Unit,
    onBookmarksEnabledChanged: (Boolean) -> Unit,
    onShowChangelogChanged: (Boolean) -> Unit,
    onAction: (DataSettingsAction) -> Unit,
    contentVersion: Int = 0,
) {
    val hasBookmarks = state.bookmarkCount > 0
    SettingsPage(
        title = stringResource(Res.string.settings_section_data),
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = contentVersion,
    ) {
        item {
            SettingsCategory("Bookmarks") {
                SwitchSettingRow(
                    title = "Enable bookmarks",
                    icon = Res.drawable.ic_bookmark,
                    checked = state.bookmarksEnabled,
                    onCheckedChange = onBookmarksEnabledChanged,
                )
                SettingsDivider()
                SettingRow(
                    title = "Add all bookmarks to HN favorites",
                    summary = when {
                        !hasBookmarks -> "No bookmarks"
                        !state.loggedIn -> "Login needed"
                        else -> formatBookmarkCount(state.bookmarkCount)
                    },
                    icon = Res.drawable.ic_star,
                    enabled = state.bookmarksEnabled && hasBookmarks && state.loggedIn,
                    onClick = { onAction(DataSettingsAction.AddBookmarksToFavorites) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Export bookmarks",
                    summary = "Save newline separated .txt file with IDs",
                    icon = Res.drawable.ic_bookmark,
                    enabled = state.bookmarksEnabled,
                    onClick = { onAction(DataSettingsAction.ExportBookmarks) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Import bookmarks",
                    icon = Res.drawable.ic_bookmark_filled,
                    enabled = state.bookmarksEnabled,
                    onClick = { onAction(DataSettingsAction.ImportBookmarks) },
                )
            }
        }
        item {
            SettingsCategory("Storage") {
                SettingRow(
                    title = "Clear clicked stories (${state.historyCount})",
                    icon = Res.drawable.ic_close,
                    onClick = { onAction(DataSettingsAction.ClearHistory) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Clear post cache (${state.postCacheCount})",
                    icon = Res.drawable.ic_cached,
                    onClick = { onAction(DataSettingsAction.ClearPostCache) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Clear tint cache (${state.tintCacheCount})",
                    icon = Res.drawable.ic_palette,
                    onClick = { onAction(DataSettingsAction.ClearTintCache) },
                )
                state.aiModelBytes?.let { bytes ->
                    SettingsDivider()
                    SettingRow(
                        title = "Delete AI models (${formatDecimalBytes(bytes)})",
                        icon = Res.drawable.ic_auto_awesome,
                        enabled = bytes > 0L,
                        onClick = { onAction(DataSettingsAction.ClearAiModels) },
                    )
                }
            }
        }
        item {
            SettingsCategory("Other") {
                if (showAppLinkSettings) {
                    SettingRow(
                        title = "Open Hacker News links in Harmonic",
                        icon = Res.drawable.ic_web_asset,
                        onClick = { onAction(DataSettingsAction.OpenLinksSettings) },
                    )
                    SettingsDivider()
                }
                SettingRow(
                    title = "Reset all settings",
                    icon = Res.drawable.ic_refresh,
                    onClick = { onAction(DataSettingsAction.ResetSettings) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Show update changelogs",
                    icon = Res.drawable.ic_system_update_alt,
                    checked = state.showChangelog,
                    onCheckedChange = onShowChangelogChanged,
                )
            }
        }
    }
}

fun formatBookmarkCount(count: Int): String =
    if (count == 1) "1 bookmark" else "$count bookmarks"

@Composable
fun ClearAiModelsConfirmationDialog(
    modelNames: List<String> = emptyList(),
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Delete downloaded AI models?") },
        text = {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    text = "Downloaded and partially downloaded local AI models will be " +
                        "removed. You can download them again later.",
                    color = HarmonicTheme.colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                if (modelNames.isNotEmpty()) {
                    Text(
                        text = "Models to delete",
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                        color = HarmonicTheme.colors.textPrimary,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    )
                    modelNames.forEach { modelName ->
                        Text(
                            text = "•  $modelName",
                            color = HarmonicTheme.colors.textPrimary,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = onClear) {
                Text("Delete")
            }
        },
    )
}

@Composable
fun ImportBookmarksDialog(
    onImport: (overwrite: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Import bookmarks") },
        edgeToEdgeContent = true,
        text = {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                Text(
                    text = "Choose what should happen to your existing bookmarks.",
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                    color = HarmonicTheme.colors.storyDisabled,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                ImportBookmarksOption(
                    title = "Replace bookmarks",
                    summary = "Remove the current list, then import the file",
                    icon = Res.drawable.ic_refresh,
                    onClick = { onImport(true) },
                )
                ImportBookmarksOption(
                    title = "Add to bookmarks",
                    summary = "Keep the current list and add new IDs from the file",
                    icon = Res.drawable.ic_add,
                    onClick = { onImport(false) },
                )
            }
        },
        confirmButton = {},
        showButtons = false,
    )
}

@Composable
private fun ImportBookmarksOption(
    title: String,
    summary: String,
    icon: org.jetbrains.compose.resources.DrawableResource,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = title,
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )
            Text(
                text = summary,
                color = HarmonicTheme.colors.storyDisabled,
                fontFamily = ProductSansFontFamily,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
        }
    }
}
