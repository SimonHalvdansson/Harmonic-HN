package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.DataSettingsSnapshot

enum class DataSettingsAction {
    AddBookmarksToFavorites,
    ExportBookmarks,
    ImportBookmarks,
    ClearHistory,
    ClearPostCache,
    ClearTintCache,
    OpenLinksSettings,
    ResetSettings,
}

@Composable
fun SharedDataSettingsScreen(
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
