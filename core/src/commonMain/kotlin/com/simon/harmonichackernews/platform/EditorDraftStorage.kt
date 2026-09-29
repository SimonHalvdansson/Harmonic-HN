package com.simon.harmonichackernews.platform

enum class EditorDraftField { TITLE, URL, TEXT, COMMENT }

/** Overflow storage for one editor's saved state. Hosts may stage writes before state saving. */
interface EditorDraftStorage {
    fun stage(field: EditorDraftField, text: String) = Unit
    fun write(field: EditorDraftField, text: String): Boolean
    fun read(field: EditorDraftField): String?
    fun clear()
}
