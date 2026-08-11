package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.resources.*

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.BookmarkImportPolicy
import com.simon.harmonichackernews.data.SavedItemCodec
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.platform.AndroidTextDocuments
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.NighttimeScheduleKeys
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore
import com.simon.harmonichackernews.utils.HistoriesUtils
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.settings.UserPreferenceKeys
import com.simon.harmonichackernews.utils.Utils
import java.util.Calendar

@Composable
fun DataSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onRequestRestart: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val savedItems = remember(context) {
        SavedItemsRepository(AndroidKeyValueStore.global(context))
    }
    val refresh = rememberPreferenceRefresh()
    var localRefresh by remember { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var overwriteBookmarksOnImport by rememberSaveable { mutableStateOf(true) }
    var favoriteIds by remember { mutableStateOf<IntArray?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        uri?.let { outputUri ->
            runCatching {
                AndroidTextDocuments.write(
                    context,
                    outputUri,
                    SavedItemCodec.encode(savedItems.loadItems(SavedItemSource.BOOKMARKS)),
                )
            }.onFailure {
                Toast.makeText(context, "Write error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { inputUri ->
            runCatching {
                importBookmarks(
                    context = context,
                    uri = inputUri,
                    overwrite = overwriteBookmarksOnImport,
                    savedItems = savedItems,
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

    val bookmarkCount = savedItems.loadItems(SavedItemSource.BOOKMARKS).size
    SharedDataSettingsScreen(
        state = DataSettingsUiState(
            bookmarksEnabled = prefs.getBoolean(UserPreferenceKeys.BOOKMARKS_ENABLED, true),
            bookmarkCount = bookmarkCount,
            loggedIn = AccountUtils.hasAccountDetails(context),
            historyCount = HistoriesUtils.loadHistories(context, false).size,
            postCacheCount = Utils.getCachedPostCount(context),
            tintCacheCount = StoryPreviewImageLoader
                .getCachedPreviewImageTintColorCount(context),
            showChangelog = prefs.getBoolean("pref_show_changelog", true),
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onBookmarksEnabledChanged = {
            prefs.edit().putBoolean(UserPreferenceKeys.BOOKMARKS_ENABLED, it).apply()
        },
        onShowChangelogChanged = {
            prefs.edit().putBoolean("pref_show_changelog", it).apply()
        },
        onAction = { action ->
            when (action) {
                DataSettingsAction.AddBookmarksToFavorites -> {
                    favoriteIds = savedItems.loadItems(
                        SavedItemSource.BOOKMARKS,
                        sortedByCreated = true,
                    ).map { it.id }.toIntArray()
                }
                DataSettingsAction.ExportBookmarks -> {
                    if (bookmarkCount == 0) {
                        Toast.makeText(context, "No bookmarks to export", Toast.LENGTH_SHORT).show()
                    } else {
                        exportLauncher.launch(bookmarksFilename())
                    }
                }
                DataSettingsAction.ImportBookmarks -> dialog = "import"
                DataSettingsAction.ClearHistory -> {
                    val oldCount = HistoriesUtils.loadHistories(context, false).size
                    HistoriesUtils.clearHistories(context)
                    localRefresh++
                    if (oldCount > 0) {
                        Utils.toast(
                            "Cleared $oldCount ${if (oldCount == 1) "entry" else "entries"}",
                            context,
                        )
                    }
                }
                DataSettingsAction.ClearPostCache -> {
                    val oldCount = Utils.clearPostCache(context)
                    localRefresh++
                    if (oldCount > 0) {
                        Utils.toast(
                            "Cleared $oldCount cached ${if (oldCount == 1) "post" else "posts"}",
                            context,
                        )
                    }
                }
                DataSettingsAction.ClearTintCache -> {
                    PreviewImageTintUtils.clearTintColorCaches(context)
                    localRefresh++
                    Utils.toast("Tint cache cleared", context)
                }
                DataSettingsAction.OpenLinksSettings -> dialog = "links"
                DataSettingsAction.ResetSettings -> dialog = "reset"
            }
        },
        contentVersion = refresh + localRefresh,
    )

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

    favoriteIds?.let { ids ->
        AddBookmarksToFavoritesDialog(
            bookmarkIds = ids,
            onDismiss = {
                favoriteIds = null
                localRefresh++
            },
        )
    }
}

private fun importBookmarks(
    context: Context,
    uri: Uri,
    overwrite: Boolean,
    savedItems: SavedItemsRepository,
): Int {
    val content = AndroidTextDocuments.read(context, uri)
    val result = BookmarkImportPolicy.apply(
        content = content,
        current = savedItems.loadItems(SavedItemSource.BOOKMARKS),
        overwrite = overwrite,
    )
    if (result == null) {
        Toast.makeText(context, "File contained no bookmarks", Toast.LENGTH_SHORT).show()
        return 0
    }
    savedItems.saveItems(SavedItemSource.BOOKMARKS, result.items)
    return result.importedCount
}

private fun resetAllSettings(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply()
    AiSummaryApiKeyStore.clearApiKey(context)
    context.getSharedPreferences(
        Utils.GLOBAL_SHARED_PREFERENCES_KEY,
        Context.MODE_PRIVATE,
    ).edit()
        .remove(NighttimeScheduleKeys.FROM_HOUR)
        .remove(NighttimeScheduleKeys.FROM_MINUTE)
        .remove(NighttimeScheduleKeys.TO_HOUR)
        .remove(NighttimeScheduleKeys.TO_MINUTE)
        .apply()
    Utils.toast("Settings reset", context)
}

private fun bookmarksFilename(): String {
    val calendar = Calendar.getInstance()
    return "HarmonicBookmarks${calendar.get(Calendar.YEAR)}-" +
        "${calendar.get(Calendar.MONTH) + 1}-${calendar.get(Calendar.DAY_OF_MONTH)}.txt"
}
