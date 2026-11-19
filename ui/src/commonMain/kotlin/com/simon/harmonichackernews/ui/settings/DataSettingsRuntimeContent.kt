package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.DataSettingsCounts
import com.simon.harmonichackernews.settings.DataSettingsDialogState
import com.simon.harmonichackernews.settings.DataSettingsRuntime
import com.simon.harmonichackernews.settings.DataSettingsRuntimeState

@Composable
fun DataSettingsRuntimeContent(
    repository: AppSettingsRepository,
    runtime: DataSettingsRuntime,
    state: DataSettingsRuntimeState,
    showNavigation: Boolean,
    showAppLinkSettings: Boolean = true,
    onBack: () -> Unit,
    appLinkSettingsDialog: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    val snapshot = state.snapshot
    DataSettingsRoute(
        repository = repository,
        counts = DataSettingsCounts(
            bookmarks = snapshot.bookmarkCount,
            history = snapshot.historyCount,
            hiddenPostCount = snapshot.hiddenPostCount,
            postCache = snapshot.postCacheCount,
            tintCache = snapshot.tintCacheCount,
            aiModelsBytes = snapshot.aiModelBytes,
        ),
        loggedIn = snapshot.loggedIn,
        showNavigation = showNavigation,
        showAppLinkSettings = showAppLinkSettings,
        onBack = onBack,
        onAction = runtime::handleDataSettingsAction,
        contentVersion = state.revision,
    )

    val dismissDialog = { runtime.showDialog(null) }
    when (state.dialog) {
        DataSettingsDialogState.IMPORT -> ImportBookmarksDialog(
            onDismiss = dismissDialog,
            onImport = runtime::requestImport,
        )
        DataSettingsDialogState.RESET -> ResetSettingsConfirmationDialog(
            onReset = runtime::resetSettings,
            onDismiss = dismissDialog,
        )
        DataSettingsDialogState.LINKS -> appLinkSettingsDialog(dismissDialog)
        DataSettingsDialogState.AI_MODELS -> ClearAiModelsConfirmationDialog(
            modelNames = snapshot.aiModelNames,
            onClear = runtime::clearAiModels,
            onDismiss = dismissDialog,
        )
        null -> Unit
    }
    state.favoriteIds?.let { ids ->
        AddBookmarksToFavoritesDialog(ids.toIntArray(), runtime::dismissFavorites)
    }
}
