package com.simon.harmonichackernews.ui.editor

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.simon.harmonichackernews.platform.EditorDraftField
import com.simon.harmonichackernews.platform.EditorDraftStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorTextFieldSaverTest {
    private val scope = SaverScope { true }

    @Test
    fun ordinaryDraftsKeepTheOriginalSaverFormatWithoutStorageAccess() {
        val storage = MemoryStorage()
        val saver = editorTextFieldSaver(EditorDraftField.COMMENT, storage)
        val value = TextFieldValue("A normal reply", TextRange(12, 2))
        val saved = with(saver) { scope.save(value) }

        assertEquals(with(TextFieldValue.Saver) { scope.save(value) }, saved)
        assertEquals(value, saver.restore(requireNotNull(saved)))
        assertEquals(0, storage.writes)
        assertEquals(0, storage.reads)
    }

    @Test
    fun largeTextAndReversedSelectionRestoreWithANewSaverAndBoundedState() {
        val storage = MemoryStorage()
        val value = TextFieldValue("Draft ø🙂\n".repeat(60_000), TextRange(400_000, 12))
        val saver = editorTextFieldSaver(EditorDraftField.COMMENT, storage)
        val saved = requireNotNull(with(saver) { scope.save(value) })

        assertTrue(saved.toString().length < 100)
        val restoredSaver = editorTextFieldSaver(EditorDraftField.COMMENT, storage)
        assertEquals(value, restoredSaver.restore(saved))
        assertEquals(saved, with(restoredSaver) { scope.save(value) })
        assertEquals(1, storage.writes)
    }

    @Test
    fun editedLargeFieldsStayIndependentAndCanShrinkBackToInlineState() {
        val storage = MemoryStorage()
        val titleSaver = editorTextFieldSaver(EditorDraftField.TITLE, storage)
        val bodySaver = editorTextFieldSaver(EditorDraftField.TEXT, storage)
        val large = TextFieldValue("a".repeat(MAX_INLINE_EDITOR_FIELD_CHARS + 1))
        val title = requireNotNull(with(titleSaver) { scope.save(large) })
        with(bodySaver) { scope.save(large.copy(text = large.text + " first")) }
        val edited = large.copy(text = large.text + " edited")
        val body = requireNotNull(with(bodySaver) { scope.save(edited) })

        assertEquals(large, titleSaver.restore(title))
        assertEquals(edited, bodySaver.restore(body))
        val short = TextFieldValue("short again")
        assertEquals(
            with(TextFieldValue.Saver) { scope.save(short) },
            with(bodySaver) { scope.save(short) },
        )
    }

    @Test
    fun failedStorageNeverFallsBackToSavingOversizedTextAndReportsRecoveryFailure() {
        val storage = MemoryStorage().apply { writable = false }
        val failures = mutableListOf<EditorDraftStorageFailure>()
        val saver = editorTextFieldSaver(EditorDraftField.COMMENT, storage, failures::add)
        val value = TextFieldValue("a".repeat(MAX_INLINE_EDITOR_FIELD_CHARS + 1))
        assertNull(with(saver) { scope.save(value) })
        assertEquals(listOf(EditorDraftStorageFailure.SAVE), failures)

        storage.writable = true
        val saved = requireNotNull(with(saver) { scope.save(value) })
        storage.clear()
        assertNull(editorTextFieldSaver(EditorDraftField.COMMENT, storage, failures::add).restore(saved))
        assertEquals(listOf(EditorDraftStorageFailure.SAVE, EditorDraftStorageFailure.RESTORE), failures)
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
