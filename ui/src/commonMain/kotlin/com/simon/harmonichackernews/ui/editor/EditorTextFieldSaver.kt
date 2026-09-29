package com.simon.harmonichackernews.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.simon.harmonichackernews.platform.EditorDraftField
import com.simon.harmonichackernews.platform.EditorDraftStorage

enum class EditorDraftStorageFailure { SAVE, RESTORE }

// Four inline fields together contribute at most 64 KiB of text to Android saved state.
internal const val MAX_INLINE_EDITOR_FIELD_CHARS = 8 * 1024
private const val FILE_MARKER = "editor-draft-file-v1"

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
                // Keep the existing draft format. Undo history is intentionally session-local:
                // saving it could put large deleted/replaced text back into Android's Bundle.
                with(TextFieldValue.Saver) { save(TextFieldValue(text, value.selection)) }
            } else if (text == persistedText || storage.write(field, text)) {
                persistedText = text
                listOf(FILE_MARKER, value.selection.start, value.selection.end)
            } else {
                // Never fall back to putting the oversized text into the Bundle.
                onFailure(EditorDraftStorageFailure.SAVE)
                null
            }
        },
        restore = { saved ->
            if ((saved as? List<*>)?.firstOrNull() == FILE_MARKER) {
                storage?.read(field)?.let { text ->
                    persistedText = text
                    TextFieldState(text, TextRange(saved[1] as Int, saved[2] as Int))
                } ?: run {
                    onFailure(EditorDraftStorageFailure.RESTORE)
                    null
                }
            } else {
                TextFieldValue.Saver.restore(saved)?.let { TextFieldState(it.text, it.selection) }
            }
        },
    )
}
