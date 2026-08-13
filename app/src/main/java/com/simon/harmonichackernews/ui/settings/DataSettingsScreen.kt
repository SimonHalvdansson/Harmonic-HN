package com.simon.harmonichackernews.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.platform.AndroidTextDocuments
import com.simon.harmonichackernews.settings.DataSettingsCounts
import com.simon.harmonichackernews.settings.DataSettingsDialogState
import com.simon.harmonichackernews.settings.DataSettingsRuntimeEffect
import com.simon.harmonichackernews.presentation.UserMessageDuration
import com.simon.harmonichackernews.platform.PresentationCopy

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
    val runtime = remember(appComposition, scope) {
        appComposition.createDataSettingsRuntime(scope) {
            appComposition.platform.timeFormatting
                .localDate(kotlin.time.Clock.System.now().toEpochMilliseconds())
        }
    }
    val runtimeState by runtime.state.collectAsState()
    var pendingExportContent by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        uri?.let { outputUri ->
            runCatching {
                val content = pendingExportContent ?: return@runCatching
                AndroidTextDocuments.write(
                    context,
                    outputUri,
                    content,
                )
                pendingExportContent = null
            }.onFailure {
                appComposition.userMessages.show(PresentationCopy.WRITE_ERROR)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { inputUri ->
            runCatching {
                runtime.importBookmarks(AndroidTextDocuments.read(context, inputUri))
            }.onFailure {
                appComposition.userMessages.show(PresentationCopy.READ_ERROR)
            }
        }
    }

    LaunchedEffect(runtime) {
        runtime.effects.collect { effect ->
            when (effect) {
                is DataSettingsRuntimeEffect.CreateExportDocument -> {
                    pendingExportContent = effect.content
                    exportLauncher.launch(effect.filename)
                }
                DataSettingsRuntimeEffect.OpenImportDocument ->
                    importLauncher.launch(arrayOf("text/plain"))
                DataSettingsRuntimeEffect.OpenAppLinkSettings -> {
                    appComposition.userMessages.show(
                        "The option should be under \"Open by default\"",
                        UserMessageDuration.LONG,
                    )
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                }
                DataSettingsRuntimeEffect.RestartApp -> onRequestRestart()
                is DataSettingsRuntimeEffect.Message ->
                    appComposition.userMessages.show(effect.text)
            }
        }
    }

    val dataSnapshot = runtimeState.snapshot
    val bookmarkCount = dataSnapshot.bookmarkCount
    SharedDataSettingsRoute(
        repository = settingsRepository,
        counts = DataSettingsCounts(
            bookmarks = bookmarkCount,
            history = dataSnapshot.historyCount,
            postCache = dataSnapshot.postCacheCount,
            tintCache = dataSnapshot.tintCacheCount,
        ),
        loggedIn = dataSnapshot.loggedIn,
        showNavigation = showNavigation,
        onBack = onBack,
        onAction = { action ->
            when (action) {
                DataSettingsAction.AddBookmarksToFavorites -> {
                    runtime.addBookmarksToFavorites()
                }
                DataSettingsAction.ExportBookmarks -> {
                    runtime.exportBookmarks()
                }
                DataSettingsAction.ImportBookmarks ->
                    runtime.showDialog(DataSettingsDialogState.IMPORT)
                DataSettingsAction.ClearHistory -> runtime.clearHistory()
                DataSettingsAction.ClearPostCache -> runtime.clearPostCache()
                DataSettingsAction.ClearTintCache -> runtime.clearTintCache()
                DataSettingsAction.OpenLinksSettings ->
                    runtime.showDialog(DataSettingsDialogState.LINKS)
                DataSettingsAction.ResetSettings ->
                    runtime.showDialog(DataSettingsDialogState.RESET)
            }
        },
        contentVersion = runtimeState.revision,
    )

    when (runtimeState.dialog) {
        DataSettingsDialogState.IMPORT -> ItemsDialog(
            title = "Import bookmarks",
            options = listOf(
                "Overwrite current bookmarks",
                "Add to current bookmarks",
            ),
            onDismiss = { runtime.showDialog(null) },
            onSelected = { selectedIndex ->
                runtime.requestImport(overwrite = selectedIndex == 0)
            },
        )

        DataSettingsDialogState.RESET -> MessageActionDialog(
            title = "Reset all settings?",
            message = "This restores app settings to their defaults. Bookmarks, history, " +
                "favorites, user tags, account details and cached posts are not deleted.",
            positiveLabel = "Reset",
            negativeLabel = "Cancel",
            onPositive = runtime::resetSettings,
            onNegative = { runtime.showDialog(null) },
            onDismiss = { runtime.showDialog(null) },
        )

        DataSettingsDialogState.LINKS -> MessageActionDialog(
            message = "Since Harmonic does not own the domain news.ycombinator.com, " +
                "intercepting links needs to be enabled by the user manually.\n\n" +
                "Go to \"Open by default\" → \"Add link\" in the linked app settings page.",
            neutralLabel = "Go to settings",
            onNeutral = runtime::openAppLinkSettings,
            onDismiss = { runtime.showDialog(null) },
        )
        null -> Unit
    }

    runtimeState.favoriteIds?.let { ids ->
        AddBookmarksToFavoritesDialog(
            bookmarkIds = ids.toIntArray(),
            onDismiss = {
                runtime.dismissFavorites()
            },
        )
    }
}
