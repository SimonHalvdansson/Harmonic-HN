package com.simon.harmonichackernews.ui.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
class EditorSubmissionState {
    var submitting by mutableStateOf(false)
        private set

    fun updateSubmitting(value: Boolean) {
        submitting = value
    }
}
