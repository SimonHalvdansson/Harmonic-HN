package com.simon.harmonichackernews.platform

enum class EditorDraftField { TITLE, URL, TEXT, COMMENT }

/** Overflow storage for one editor's saved state, accessed only when saving or restoring. */
interface EditorDraftStorage {
    fun write(field: EditorDraftField, text: String): Boolean
    fun read(field: EditorDraftField): String?
    fun clear()
}
