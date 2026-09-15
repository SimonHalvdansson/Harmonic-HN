package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.platform.EditorDraftField
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileEditorDraftStorageTest {
    @Test
    fun largeDraftSurvivesNewStorageInstancesAndReplacementWithoutAccumulatingVersions() {
        val directory = createTempDirectory("harmonic-editor-").toFile()
        try {
            val root = Path(directory.path, "session")
            val storage = FileEditorDraftStorage(root)
            val draft = "Long draft ø🙂\n".repeat(50_000)
            assertTrue(storage.write(EditorDraftField.TEXT, draft))
            assertTrue(storage.write(EditorDraftField.COMMENT, "other field"))
            assertEquals(draft, FileEditorDraftStorage(root).read(EditorDraftField.TEXT))
            assertTrue(storage.write(EditorDraftField.TEXT, draft + "edited"))
            val restored = FileEditorDraftStorage(root)
            assertEquals(draft + "edited", restored.read(EditorDraftField.TEXT))
            assertEquals("other field", restored.read(EditorDraftField.COMMENT))
            assertEquals(setOf("TEXT", "COMMENT"), directory.resolve("session").list()!!.toSet())
            restored.clear()
            assertFalse(directory.resolve("session").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun unusedStorageDoesNotCreateDirectoriesAndIoFailuresAreRecoverable() {
        val directory = createTempDirectory("harmonic-editor-").toFile()
        try {
            val unused = directory.resolve("unused")
            FileEditorDraftStorage(Path(unused.path)).clear()
            assertFalse(unused.exists())

            val blocker = directory.resolve("not-a-directory")
            Files.writeString(blocker.toPath(), "keep")
            val storage = FileEditorDraftStorage(Path(blocker.path, "session"))
            assertFalse(storage.write(EditorDraftField.TEXT, "draft"))
            assertNull(storage.read(EditorDraftField.TEXT))
            storage.clear()
            assertEquals("keep", blocker.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
