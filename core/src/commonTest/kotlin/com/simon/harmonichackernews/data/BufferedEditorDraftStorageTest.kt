package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.platform.EditorDraftField
import com.simon.harmonichackernews.platform.EditorDraftStorage
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BufferedEditorDraftStorageTest {
    @Test
    fun saveAndRestoreUseMemoryWhileWritesAreQueuedAndCoalesced() = runTest {
        val files = MemoryFiles()
        val storage = BufferedEditorDraftStorage(files, backgroundScope, 8, StandardTestDispatcher(testScheduler))
        storage.awaitRestored()
        storage.stage(EditorDraftField.TEXT, "first long draft")
        storage.write(EditorDraftField.TEXT, "latest long draft")
        storage.stage(EditorDraftField.TITLE, "short")
        assertEquals(0, files.writes)
        assertEquals("latest long draft", storage.read(EditorDraftField.TEXT))
        runCurrent()
        assertEquals(1, files.writes)
        assertEquals("latest long draft", files.read(EditorDraftField.TEXT))
        assertNull(files.read(EditorDraftField.TITLE))

        // A new process can restore the persisted text before its saveable fields are composed.
        val restored = BufferedEditorDraftStorage(files, backgroundScope, 8, StandardTestDispatcher(testScheduler))
        restored.awaitRestored()
        assertEquals("latest long draft", restored.read(EditorDraftField.TEXT))
    }

    @Test
    fun discardCannotBeUndoneByAnAlreadyQueuedSave() = runTest {
        val files = MemoryFiles()
        val storage = BufferedEditorDraftStorage(files, backgroundScope, 8, StandardTestDispatcher(testScheduler))
        storage.awaitRestored()
        storage.write(EditorDraftField.COMMENT, "a large comment")
        storage.clear()
        runCurrent()
        assertTrue(files.values.isEmpty())
        assertNull(storage.read(EditorDraftField.COMMENT))
        assertEquals(false, storage.write(EditorDraftField.COMMENT, "late save"))
    }

    @Test
    fun failedWriteKeepsDraftInMemoryAndReportsFailure() = runTest {
        val files = MemoryFiles().apply { writable = false }
        val storage = BufferedEditorDraftStorage(files, backgroundScope, 8, StandardTestDispatcher(testScheduler))
        storage.awaitRestored()
        storage.write(EditorDraftField.COMMENT, "a large comment")
        runCurrent()
        assertEquals(1, storage.failures.value)
        assertEquals("a large comment", storage.read(EditorDraftField.COMMENT))
        files.writable = true
        storage.write(EditorDraftField.COMMENT, "a large comment")
        runCurrent()
        assertEquals("a large comment", files.read(EditorDraftField.COMMENT))
    }

    private class MemoryFiles : EditorDraftStorage {
        val values = mutableMapOf<EditorDraftField, String>()
        var writes = 0
        var writable = true
        override fun write(field: EditorDraftField, text: String): Boolean {
            writes++
            if (writable) values[field] = text
            return writable
        }
        override fun read(field: EditorDraftField): String? = values[field]
        override fun clear() = values.clear()
    }
}
