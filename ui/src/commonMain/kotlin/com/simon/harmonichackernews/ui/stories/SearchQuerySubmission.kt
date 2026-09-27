package com.simon.harmonichackernews.ui.stories

/** Keeps a submitted search in sync with the IME's final autocorrection until editing resumes. */
internal class SearchQuerySubmission(initialDraft: String) {
    private var draft = initialDraft
    private var submittedQuery: String? = null

    fun synchronizeDraft(value: String) {
        draft = value
    }

    fun startEditing() {
        submittedQuery = null
    }

    /** Returns a replacement query only for text committed after the Search action. */
    fun updateDraft(value: String): String? {
        draft = value
        return if (submittedQuery != null && submittedQuery != value) {
            submittedQuery = value
            value
        } else {
            null
        }
    }

    fun submit(): String {
        submittedQuery = draft
        return draft
    }
}
