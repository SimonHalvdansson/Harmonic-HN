package com.simon.harmonichackernews.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.simon.harmonichackernews.platform.EditorDraftField
import com.simon.harmonichackernews.platform.EditorDraftStorage
import com.simon.harmonichackernews.data.BufferedEditorDraftStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorTextFieldSaverTest {
    private val scope = SaverScope { true }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun unchangedBufferedDraftRetriesAFailedDiskWriteOnTheNextStateSave() = runTest {
        val files = MemoryStorage().apply { writable = false }
        val storage = BufferedEditorDraftStorage(
            files, backgroundScope, MAX_INLINE_EDITOR_FIELD_CHARS, StandardTestDispatcher(testScheduler),
        )
        storage.awaitRestored()
        val saver = editorTextFieldSaver(EditorDraftField.COMMENT, storage)
        val value = TextFieldState("a".repeat(MAX_INLINE_EDITOR_FIELD_CHARS + 1))
        val saved = requireNotNull(with(saver) { scope.save(value) })
        runCurrent()
        assertEquals(1, storage.failures.value)
        assertNull(files.read(EditorDraftField.COMMENT))
        files.writable = true
        assertEquals(saved, with(saver) { scope.save(value) })
        runCurrent()
        assertEquals(value.text.toString(), files.read(EditorDraftField.COMMENT))
    }

    @Test
    fun ordinaryDraftsKeepTheOriginalSaverFormatWithoutStorageAccess() {
        val storage = MemoryStorage()
        val saver = editorTextFieldSaver(EditorDraftField.COMMENT, storage)
        val value = TextFieldState("A normal reply", TextRange(12, 2))
        val saved = with(saver) { scope.save(value) }

        assertEquals(with(TextFieldValue.Saver) { scope.save(TextFieldValue(value.text.toString(), value.selection)) }, saved)
        assertFieldEquals(value, saver.restore(requireNotNull(saved)))
        assertEquals(0, storage.writes)
        assertEquals(0, storage.reads)
    }

    @Test
    fun largeTextAndReversedSelectionRestoreWithANewSaverAndBoundedState() {
        val storage = MemoryStorage()
        val value = TextFieldState("Draft ø🙂\n".repeat(60_000), TextRange(400_000, 12))
        val saver = editorTextFieldSaver(EditorDraftField.COMMENT, storage)
        val saved = requireNotNull(with(saver) { scope.save(value) })

        assertTrue(saved.toString().length < 100)
        val restoredSaver = editorTextFieldSaver(EditorDraftField.COMMENT, storage)
        assertFieldEquals(value, restoredSaver.restore(saved))
        assertEquals(saved, with(restoredSaver) { scope.save(value) })
        assertEquals(1, storage.writes)
    }

    @Test
    fun editedLargeFieldsStayIndependentAndCanShrinkBackToInlineState() {
        val storage = MemoryStorage()
        val titleSaver = editorTextFieldSaver(EditorDraftField.TITLE, storage)
        val bodySaver = editorTextFieldSaver(EditorDraftField.TEXT, storage)
        val large = TextFieldState("a".repeat(MAX_INLINE_EDITOR_FIELD_CHARS + 1))
        val title = requireNotNull(with(titleSaver) { scope.save(large) })
        with(bodySaver) { scope.save(TextFieldState(large.text.toString() + " first")) }
        val edited = TextFieldState(large.text.toString() + " edited")
        val body = requireNotNull(with(bodySaver) { scope.save(edited) })

        assertFieldEquals(large, titleSaver.restore(title))
        assertFieldEquals(edited, bodySaver.restore(body))
        val short = TextFieldState("short again")
        assertEquals(
            with(TextFieldValue.Saver) { scope.save(TextFieldValue(short.text.toString(), short.selection)) },
            with(bodySaver) { scope.save(short) },
        )
    }

    @Test
    fun failedStorageNeverFallsBackToSavingOversizedTextAndReportsRecoveryFailure() {
        val storage = MemoryStorage().apply { writable = false }
        val failures = mutableListOf<EditorDraftStorageFailure>()
        val saver = editorTextFieldSaver(EditorDraftField.COMMENT, storage, failures::add)
        val value = TextFieldState("a".repeat(MAX_INLINE_EDITOR_FIELD_CHARS + 1))
        assertNull(with(saver) { scope.save(value) })
        assertEquals(listOf(EditorDraftStorageFailure.SAVE), failures)

        storage.writable = true
        val saved = requireNotNull(with(saver) { scope.save(value) })
        storage.clear()
        assertNull(editorTextFieldSaver(EditorDraftField.COMMENT, storage, failures::add).restore(saved))
        assertEquals(listOf(EditorDraftStorageFailure.SAVE, EditorDraftStorageFailure.RESTORE), failures)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun oldInlineDraftsRestoreAndUndoHistoryNeverInflatesSavedState() {
        val saver = editorTextFieldSaver(EditorDraftField.COMMENT, MemoryStorage())
        val legacy = TextFieldValue("Old draft", TextRange(7, 2))
        val legacySaved = requireNotNull(with(TextFieldValue.Saver) { scope.save(legacy) })
        assertFieldEquals(TextFieldState(legacy.text, legacy.selection), saver.restore(legacySaved))

        val state = TextFieldState("x".repeat(500_000))
        state.edit { replace(0, length, "Short replacement") }
        assertTrue(state.undoState.canUndo)
        val saved = requireNotNull(with(saver) { scope.save(state) })
        assertTrue(saved.toString().length < 100)
        val restored = requireNotNull(saver.restore(saved))
        assertFieldEquals(state, restored)
        assertEquals(false, restored.undoState.canUndo)
    }

    private fun assertFieldEquals(expected: TextFieldState, actual: TextFieldState?) {
        requireNotNull(actual)
        assertEquals(expected.text.toString(), actual.text.toString())
        assertEquals(expected.selection, actual.selection)
    }

    private class MemoryStorage : EditorDraftStorage {
        private val values = mutableMapOf<EditorDraftField, String>()
        var writes = 0
        var reads = 0
        var writable = true
        override fun write(field: EditorDraftField, text: String): Boolean {
            writes++
            if (!writable) return false
            values[field] = text
            return true
        }
        override fun read(field: EditorDraftField): String? {
            reads++
            return values[field]
        }
        override fun clear() = values.clear()
    }
}
