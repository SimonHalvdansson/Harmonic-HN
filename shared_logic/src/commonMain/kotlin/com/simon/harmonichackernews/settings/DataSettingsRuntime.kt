package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.platform.PresentationCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DataSettingsDialogState { IMPORT, RESET, LINKS }

data class DataSettingsRuntimeState(
    val snapshot: DataSettingsSnapshot,
    val dialog: DataSettingsDialogState? = null,
    val favoriteIds: List<Int>? = null,
    val overwriteBookmarksOnImport: Boolean = true,
    val revision: Int = 0,
)

sealed interface DataSettingsRuntimeEffect {
    data class CreateExportDocument(val filename: String, val content: String) : DataSettingsRuntimeEffect
    data object OpenImportDocument : DataSettingsRuntimeEffect
    data object OpenAppLinkSettings : DataSettingsRuntimeEffect
    data object SettingsReset : DataSettingsRuntimeEffect
    data class Message(val text: String) : DataSettingsRuntimeEffect
}

/** Portable state/actions/results for the Data settings screen. */
class DataSettingsRuntime(
    private val scope: CoroutineScope,
    private val service: DataSettingsService,
    private val today: () -> com.simon.harmonichackernews.platform.LocalCalendarDate,
) {
    private val mutableState = MutableStateFlow(DataSettingsRuntimeState(service.snapshot()))
    private val mutableEffects = MutableSharedFlow<DataSettingsRuntimeEffect>(extraBufferCapacity = 8)
    val state: StateFlow<DataSettingsRuntimeState> = mutableState.asStateFlow()
    val effects: SharedFlow<DataSettingsRuntimeEffect> = mutableEffects.asSharedFlow()

    fun showDialog(dialog: DataSettingsDialogState?) = publish(dialog = dialog)
    fun dismissFavorites() = publish(favoriteIds = null)

    fun addBookmarksToFavorites() = publish(favoriteIds = service.bookmarkIdsByNewest())

    fun exportBookmarks() {
        val content = service.exportBookmarks()
        if (content == null) {
            mutableEffects.tryEmit(DataSettingsRuntimeEffect.Message(PresentationCopy.EXPORT_EMPTY))
            return
        }
        val date = today()
        mutableEffects.tryEmit(
            DataSettingsRuntimeEffect.CreateExportDocument(
                DataSettingsPolicy.bookmarksFilename(date.year, date.month, date.day),
                content,
            ),
        )
    }

    fun requestImport(overwrite: Boolean) {
        publish(dialog = null, overwriteBookmarksOnImport = overwrite)
        mutableEffects.tryEmit(DataSettingsRuntimeEffect.OpenImportDocument)
    }

    fun importBookmarks(content: String) {
        when (val result = service.importBookmarks(content, state.value.overwriteBookmarksOnImport)) {
            BookmarkImportResult.Empty -> emitMessage(PresentationCopy.IMPORT_EMPTY)
            is BookmarkImportResult.Imported -> emitMessage(
                PresentationCopy.importedBookmarks(result.count, result.overwroteExisting),
            )
        }
        refresh()
    }

    fun clearHistory() {
        service.clearHistory()?.let(::emitMessage)
        refresh()
    }

    fun clearPostCache() {
        scope.launch {
            service.clearPostCache()?.let(::emitMessage)
            refresh()
        }
    }

    fun clearTintCache() {
        service.clearTintCache()
        emitMessage(PresentationCopy.TINT_CACHE_CLEARED)
        refresh()
    }

    fun resetSettings() {
        service.resetSettings()
        emitMessage(PresentationCopy.SETTINGS_RESET)
        publish(dialog = null)
        mutableEffects.tryEmit(DataSettingsRuntimeEffect.SettingsReset)
    }

    fun openAppLinkSettings() {
        publish(dialog = null)
        mutableEffects.tryEmit(DataSettingsRuntimeEffect.OpenAppLinkSettings)
    }

    fun refresh() = publish()

    private fun emitMessage(message: String) {
        mutableEffects.tryEmit(DataSettingsRuntimeEffect.Message(message))
    }

    private fun publish(
        dialog: DataSettingsDialogState? = state.value.dialog,
        favoriteIds: List<Int>? = state.value.favoriteIds,
        overwriteBookmarksOnImport: Boolean = state.value.overwriteBookmarksOnImport,
    ) {
        mutableState.value = DataSettingsRuntimeState(
            snapshot = service.snapshot(),
            dialog = dialog,
            favoriteIds = favoriteIds,
            overwriteBookmarksOnImport = overwriteBookmarksOnImport,
            revision = state.value.revision + 1,
        )
    }
}
