package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.platform.EditorDraftField
import com.simon.harmonichackernews.platform.EditorDraftStorage
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/** The host supplies a private, durable directory unique to this editor session. */
class FileEditorDraftStorage(
    private val root: Path,
    private val fileSystem: FileSystem = SystemFileSystem,
) : EditorDraftStorage {
    private var used = false

    override fun write(field: EditorDraftField, text: String): Boolean {
        used = true
        val temporary = Path(root, "${field.name}.tmp")
        return try {
            fileSystem.createDirectories(root)
            fileSystem.sink(temporary).buffered().use { it.write(text.encodeToByteArray()) }
            fileSystem.atomicMove(temporary, Path(root, field.name))
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { fileSystem.delete(temporary, mustExist = false) }
        }
    }

    override fun read(field: EditorDraftField): String? {
        used = true
        return try {
            fileSystem.source(Path(root, field.name)).buffered().use {
                it.readByteArray().decodeToString(throwOnInvalidSequence = true)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun clear() {
        // Editors which only contain ordinary short drafts never touch the filesystem.
        if (!used) return
        runCatching {
            EditorDraftField.entries.forEach { field ->
                fileSystem.delete(Path(root, field.name), mustExist = false)
                fileSystem.delete(Path(root, "${field.name}.tmp"), mustExist = false)
            }
            fileSystem.delete(root, mustExist = false)
        }
    }
}
