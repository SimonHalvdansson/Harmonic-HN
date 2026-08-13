package com.simon.harmonichackernews.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.data.BookmarkImportPolicy
import com.simon.harmonichackernews.data.SavedItemCodec
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.platform.AndroidTextDocuments
import com.simon.harmonichackernews.settings.BookmarkExportDecision
import com.simon.harmonichackernews.settings.DataSettingsCounts
import com.simon.harmonichackernews.settings.DataSettingsPolicy
import com.simon.harmonichackernews.utils.AndroidStoryCache
import com.simon.harmonichackernews.presentation.UserMessageDuration
import java.util.Calendar
import kotlinx.coroutines.launch

@Composable
fun DataSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onRequestRestart: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appComposition = LocalHarmonicUiDependencies.current
    val settingsRepository = appComposition.settings
    val settingsReset = appComposition.settingsReset
    val savedItems = appComposition.savedItems
    val historyStore = appComposition.platform.capabilities.history.getOrNull()
    var localRefresh by remember { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf<DataSettingsDialog?>(null) }
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
                appComposition.userMessages.show("Write error")
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
                if (importedCount == null) {
                    appComposition.userMessages.show("File contained no bookmarks")
                } else {
                    localRefresh++
                    val action = if (overwriteBookmarksOnImport) "Loaded " else "Added "
                    appComposition.userMessages.show(
                        action + formatBookmarkCount(importedCount),
                    )
                }
            }.onFailure {
                appComposition.userMessages.show("Read error")
            }
        }
    }

    val bookmarkCount = savedItems.loadItems(SavedItemSource.BOOKMARKS).size
    SharedDataSettingsRoute(
        repository = settingsRepository,
        counts = DataSettingsCounts(
            bookmarks = bookmarkCount,
            history = historyStore?.load()?.size ?: 0,
            postCache = AndroidStoryCache.itemCount(context),
            tintCache = appComposition.storyResourceTints.count(),
        ),
        loggedIn = appComposition.platform.accounts.load() != null,
        showNavigation = showNavigation,
        onBack = onBack,
        onAction = { action ->
            when (action) {
                DataSettingsAction.AddBookmarksToFavorites -> {
                    favoriteIds = savedItems.loadItems(
                        SavedItemSource.BOOKMARKS,
                        sortedByCreated = true,
                    ).map { it.id }.toIntArray()
                }
                DataSettingsAction.ExportBookmarks -> {
                    when (DataSettingsPolicy.exportDecision(bookmarkCount)) {
                        BookmarkExportDecision.Empty ->
                            appComposition.userMessages.show("No bookmarks to export")
                        BookmarkExportDecision.Ready -> exportLauncher.launch(bookmarksFilename())
                    }
                }
                DataSettingsAction.ImportBookmarks -> dialog = DataSettingsDialog.Import
                DataSettingsAction.ClearHistory -> {
                    val oldCount = historyStore?.load()?.size ?: 0
                    historyStore?.clear()
                    localRefresh++
                    DataSettingsPolicy.clearedItemsMessage(oldCount, "entry", "entries")
                        ?.let { appComposition.userMessages.show(it) }
                }
                DataSettingsAction.ClearPostCache -> {
                    val oldCount = AndroidStoryCache.clear(context)
                    scope.launch {
                        appComposition.previewResources.clear()
                        localRefresh++
                        DataSettingsPolicy.clearedItemsMessage(
                            oldCount,
                            "cached post",
                            "cached posts",
                        )?.let { appComposition.userMessages.show(it) }
                    }
                }
                DataSettingsAction.ClearTintCache -> {
                    appComposition.storyResourceTints.clear()
                    localRefresh++
                    appComposition.userMessages.show("Tint cache cleared")
                }
                DataSettingsAction.OpenLinksSettings -> dialog = DataSettingsDialog.Links
                DataSettingsAction.ResetSettings -> dialog = DataSettingsDialog.Reset
            }
        },
        contentVersion = localRefresh,
    )

    when (dialog) {
        DataSettingsDialog.Import -> ItemsDialog(
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

        DataSettingsDialog.Reset -> MessageActionDialog(
            title = "Reset all settings?",
            message = "This restores app settings to their defaults. Bookmarks, history, " +
                "favorites, user tags, account details and cached posts are not deleted.",
            positiveLabel = "Reset",
            negativeLabel = "Cancel",
            onPositive = {
                settingsReset.execute()
                appComposition.userMessages.show("Settings reset")
                onRequestRestart()
                dialog = null
            },
            onNegative = { dialog = null },
            onDismiss = { dialog = null },
        )

        DataSettingsDialog.Links -> MessageActionDialog(
            message = "Since Harmonic does not own the domain news.ycombinator.com, " +
                "intercepting links needs to be enabled by the user manually.\n\n" +
                "Go to \"Open by default\" → \"Add link\" in the linked app settings page.",
            neutralLabel = "Go to settings",
            onNeutral = {
                appComposition.userMessages.show(
                    "The option should be under \"Open by default\"",
                    UserMessageDuration.LONG,
                )
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    },
                )
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
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
): Int? {
    val content = AndroidTextDocuments.read(context, uri)
    val result = BookmarkImportPolicy.apply(
        content = content,
        current = savedItems.loadItems(SavedItemSource.BOOKMARKS),
        overwrite = overwrite,
    )
    if (result == null) return null
    savedItems.saveItems(SavedItemSource.BOOKMARKS, result.items)
    return result.importedCount
}

private fun bookmarksFilename(): String {
    val calendar = Calendar.getInstance()
    return DataSettingsPolicy.bookmarksFilename(
        year = calendar.get(Calendar.YEAR),
        month = calendar.get(Calendar.MONTH) + 1,
        day = calendar.get(Calendar.DAY_OF_MONTH),
    )
}

private enum class DataSettingsDialog { Import, Reset, Links }
