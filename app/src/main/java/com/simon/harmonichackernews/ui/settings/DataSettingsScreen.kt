package com.simon.harmonichackernews.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Bookmark
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.settings.AddBookmarksToFavoritesDialogFragment
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore
import com.simon.harmonichackernews.utils.HistoriesUtils
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import java.util.Calendar
import java.util.HashSet

@Composable
fun DataSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onRequestRestart: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var localRefresh by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var overwriteBookmarksOnImport by remember { mutableStateOf(true) }

    @Suppress("UNUSED_VARIABLE")
    val observedRefresh = refresh + localRefresh

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                Utils.writeInFile(
                    context,
                    uri,
                    SettingsUtils.readStringFromSharedPreferences(
                        context,
                        Utils.KEY_SHARED_PREFERENCES_BOOKMARKS,
                    ),
                )
            }.onFailure {
                Toast.makeText(context, "Write error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                importBookmarks(
                    context = context,
                    uri = uri,
                    overwrite = overwriteBookmarksOnImport,
                )
            }.onSuccess { importedCount ->
                localRefresh++
                val action = if (overwriteBookmarksOnImport) "Loaded " else "Added "
                Toast.makeText(
                    context,
                    action + formatBookmarkCount(importedCount),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure {
                Toast.makeText(context, "Read error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val bookmarksEnabled = prefs.getBoolean(SettingsUtils.PREF_BOOKMARKS_ENABLED, true)
    val bookmarkCount = Utils.loadBookmarks(context, false).size
    val hasBookmarks = bookmarkCount > 0
    val loggedIn = AccountUtils.hasAccountDetails(context)

    SettingsPage(
        title = "Data",
        showNavigation = showNavigation,
        onBack = onBack,
    ) {
        item {
            SettingsCategory("Bookmarks") {
                SwitchSettingRow(
                    title = "Enable bookmarks",
                    icon = R.drawable.ic_bookmark,
                    checked = bookmarksEnabled,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_BOOKMARKS_ENABLED, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Add all bookmarks to HN favorites",
                    summary = when {
                        !hasBookmarks -> "No bookmarks"
                        !loggedIn -> "Login needed"
                        else -> formatBookmarkCount(bookmarkCount)
                    },
                    icon = R.drawable.ic_star,
                    enabled = bookmarksEnabled && hasBookmarks && loggedIn,
                    onClick = {
                        val ids = Utils.loadBookmarks(context, true)
                            .map(Bookmark::id)
                            .toIntArray()
                        (context as? AppCompatActivity)?.let { activity ->
                            AddBookmarksToFavoritesDialogFragment.newInstance(ids)
                                .show(
                                    activity.supportFragmentManager,
                                    "AddBookmarksToFavoritesDialogFragment",
                                )
                        }
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Export bookmarks",
                    summary = "Save newline separated .txt file with IDs",
                    icon = R.drawable.ic_bookmark,
                    enabled = bookmarksEnabled,
                    onClick = {
                        if (!hasBookmarks) {
                            Toast.makeText(
                                context,
                                "No bookmarks to export",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            exportLauncher.launch(bookmarksFilename())
                        }
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Import bookmarks",
                    icon = R.drawable.ic_bookmark_filled,
                    enabled = bookmarksEnabled,
                    onClick = { dialog = "import" },
                )
            }
        }

        item {
            SettingsCategory("Storage") {
                SettingRow(
                    title = "Clear clicked stories (${HistoriesUtils.loadHistories(context, false).size})",
                    icon = R.drawable.ic_close,
                    onClick = {
                        val oldCount = HistoriesUtils.loadHistories(context, false).size
                        HistoriesUtils.clearHistories(context)
                        localRefresh++
                        if (oldCount > 0) {
                            Utils.toast(
                                "Cleared $oldCount ${if (oldCount == 1) "entry" else "entries"}",
                                context,
                            )
                        }
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Clear post cache (${Utils.getCachedPostCount(context)})",
                    icon = R.drawable.ic_cached,
                    onClick = {
                        val oldCount = Utils.clearPostCache(context)
                        localRefresh++
                        if (oldCount > 0) {
                            Utils.toast(
                                "Cleared $oldCount cached " +
                                    if (oldCount == 1) "post" else "posts",
                                context,
                            )
                        }
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Clear tint cache (" +
                        StoryPreviewImageLoader.getCachedPreviewImageTintColorCount(context) +
                        ")",
                    icon = R.drawable.ic_palette,
                    onClick = {
                        PreviewImageTintUtils.clearTintColorCaches(context)
                        localRefresh++
                        Utils.toast("Tint cache cleared", context)
                    },
                )
            }
        }

        item {
            SettingsCategory("Other") {
                SettingRow(
                    title = "Open Hacker News links in Harmonic",
                    icon = R.drawable.ic_web_asset,
                    onClick = { dialog = "links" },
                )
                SettingsDivider()
                SettingRow(
                    title = "Reset all settings",
                    icon = R.drawable.ic_refresh,
                    onClick = { dialog = "reset" },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Show update changelogs",
                    icon = R.drawable.ic_system_update_alt,
                    checked = prefs.getBoolean("pref_show_changelog", true),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_show_changelog", it).apply()
                    },
                )
            }
        }
    }

    when (dialog) {
        "import" -> ItemsDialog(
            title = "Import bookmarks",
            options = listOf(
                "Overwrite current bookmarks",
                "Add to current bookmarks",
            ),
            onDismiss = { dialog = null },
            onSelected = { selectedIndex ->
                overwriteBookmarksOnImport = selectedIndex == 0
                dialog = null
                importLauncher.launch(arrayOf("text/plain"))
            },
        )

        "reset" -> MessageActionDialog(
            title = "Reset all settings?",
            message = "This restores app settings to their defaults. Bookmarks, history, " +
                "favorites, user tags, account details and cached posts are not deleted.",
            positiveLabel = "Reset",
            negativeLabel = "Cancel",
            onPositive = {
                resetAllSettings(context)
                onRequestRestart()
                dialog = null
            },
            onNegative = { dialog = null },
            onDismiss = { dialog = null },
        )

        "links" -> MessageActionDialog(
            message = "Since Harmonic does not own the domain news.ycombinator.com, " +
                "intercepting links needs to be enabled by the user manually.\n\n" +
                "Go to \"Open by default\" → \"Add link\" in the linked app settings page.",
            neutralLabel = "Go to settings",
            onNeutral = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    },
                )
                Toast.makeText(
                    context,
                    "The option should be under \"Open by default\"",
                    Toast.LENGTH_LONG,
                ).show()
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

private fun importBookmarks(
    context: Context,
    uri: Uri,
    overwrite: Boolean,
): Int {
    val content = Utils.readFileContent(context, uri)
    val imported = Utils.loadBookmarks(true, content)
    if (imported.isEmpty()) {
        Toast.makeText(context, "File contained no bookmarks", Toast.LENGTH_SHORT).show()
        return 0
    }

    if (overwrite) {
        SettingsUtils.saveStringToSharedPreferences(
            context,
            Utils.KEY_SHARED_PREFERENCES_BOOKMARKS,
            content,
        )
        return imported.size
    }

    val current = Utils.loadBookmarks(context, false)
    val currentIds = HashSet(current.map(Bookmark::id))
    var added = 0
    imported.forEach { bookmark ->
        if (currentIds.add(bookmark.id)) {
            current.add(bookmark)
            added++
        }
    }
    Utils.saveBookmarks(context, current)
    return added
}

private fun resetAllSettings(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply()
    AiSummaryApiKeyStore.clearApiKey(context)
    context.getSharedPreferences(
        Utils.GLOBAL_SHARED_PREFERENCES_KEY,
        Context.MODE_PRIVATE,
    ).edit()
        .remove(Utils.KEY_NIGHTTIME_FROM_HOUR)
        .remove(Utils.KEY_NIGHTTIME_FROM_MINUTE)
        .remove(Utils.KEY_NIGHTTIME_TO_HOUR)
        .remove(Utils.KEY_NIGHTTIME_TO_MINUTE)
        .apply()
    Utils.toast("Settings reset", context)
}

private fun formatBookmarkCount(count: Int): String =
    if (count == 1) "1 bookmark" else "$count bookmarks"

private fun bookmarksFilename(): String {
    val calendar = Calendar.getInstance()
    return "HarmonicBookmarks${calendar.get(Calendar.YEAR)}-" +
        "${calendar.get(Calendar.MONTH) + 1}-${calendar.get(Calendar.DAY_OF_MONTH)}.txt"
}
