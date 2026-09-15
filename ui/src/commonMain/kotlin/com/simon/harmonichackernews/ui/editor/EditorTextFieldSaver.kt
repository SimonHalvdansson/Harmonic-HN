package com.simon.harmonichackernews.ui.editor

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
): Saver<TextFieldValue, out Any> {
    val currentFailure = rememberUpdatedState(onFailure)
    return remember(field, storage) {
        if (storage == null) TextFieldValue.Saver
        else editorTextFieldSaver(field, storage) { currentFailure.value(it) }
    }
}

internal fun editorTextFieldSaver(
    field: EditorDraftField,
    storage: EditorDraftStorage,
    onFailure: (EditorDraftStorageFailure) -> Unit = {},
): Saver<TextFieldValue, Any> {
    var persistedText: String? = null
    return Saver(
        save = { value ->
            if (value.text.length <= MAX_INLINE_EDITOR_FIELD_CHARS) {
                with(TextFieldValue.Saver) { save(value) }
            } else if (value.text == persistedText || storage.write(field, value.text)) {
                persistedText = value.text
                listOf(FILE_MARKER, value.selection.start, value.selection.end)
            } else {
                // Never fall back to putting the oversized text into the Bundle.
                onFailure(EditorDraftStorageFailure.SAVE)
                null
            }
        },
        restore = { saved ->
            if ((saved as? List<*>)?.firstOrNull() == FILE_MARKER) {
                storage.read(field)?.let { text ->
                    persistedText = text
                    TextFieldValue(text, TextRange(saved[1] as Int, saved[2] as Int))
                } ?: run {
                    onFailure(EditorDraftStorageFailure.RESTORE)
                    null
                }
            } else {
                TextFieldValue.Saver.restore(saved)
            }
        },
    )
}
