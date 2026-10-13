package com.simon.harmonichackernews.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.text.TextRange
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.Serializable
import com.simon.harmonichackernews.platform.EditorDraftField
import com.simon.harmonichackernews.platform.EditorDraftStorage

enum class EditorDraftStorageFailure { SAVE, RESTORE }

// Four inline fields together contribute at most 64 KiB of text to Android saved state.
internal const val MAX_INLINE_EDITOR_FIELD_CHARS = 8 * 1024
@Serializable
internal data class EditorFieldRestoration(
    val text: String? = null,
    val selectionStart: Int,
    val selectionEnd: Int,
)

@Composable
internal fun rememberEditorTextFieldSaver(
    field: EditorDraftField,
    storage: EditorDraftStorage?,
    onFailure: (EditorDraftStorageFailure) -> Unit,
): Saver<TextFieldState, Any> {
    val currentFailure = rememberUpdatedState(onFailure)
    return remember(field, storage) {
        editorTextFieldSaver(field, storage) { currentFailure.value(it) }
    }
}

internal fun editorTextFieldSaver(
    field: EditorDraftField,
    storage: EditorDraftStorage?,
    onFailure: (EditorDraftStorageFailure) -> Unit = {},
): Saver<TextFieldState, Any> {
    var persistedText: String? = null
    return Saver(
        save = { value ->
            val text = value.text.toString()
            // Buffered storage may have reported a disk failure after accepting the last save.
            // Let it retry unchanged text without rewriting successful synchronous saves.
            if (text == persistedText) storage?.stage(field, text)
            if (storage == null || text.length <= MAX_INLINE_EDITOR_FIELD_CHARS) {
                // Undo history stays session-local so deleted large text cannot inflate state.
                encodeToSavedState(EditorFieldRestoration(text, value.selection.start, value.selection.end))
            } else if (text == persistedText || storage.write(field, text)) {
                persistedText = text
                encodeToSavedState(EditorFieldRestoration(selectionStart = value.selection.start, selectionEnd = value.selection.end))
            } else {
                // Never fall back to putting the oversized text into the Bundle.
                onFailure(EditorDraftStorageFailure.SAVE)
                null
            }
        },
        restore = { saved ->
            val state = decodeFromSavedState<EditorFieldRestoration>(saved as SavedState)
            val text = state.text ?: storage?.read(field)?.also { persistedText = it }
            if (text == null) {
                onFailure(EditorDraftStorageFailure.RESTORE)
                null
            } else TextFieldState(text, TextRange(state.selectionStart, state.selectionEnd))
        },
    )
}
