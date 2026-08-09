package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.navigation.EditorType

data class EditorSubmission(
    val title: String = "",
    val url: String = "",
    val text: String = "",
    val comment: String = "",
)

data class EditorValidation(
    val canSubmit: Boolean,
    val titleTooLong: Boolean,
)

fun EditorSubmission.validate(type: EditorType, titleMaxLength: Int): EditorValidation {
    val titleTooLong = title.length > titleMaxLength
    val canSubmit = if (type == EditorType.POST) {
        title.isNotEmpty() && !titleTooLong && (url.isNotEmpty() || text.isNotEmpty())
    } else {
        comment.isNotEmpty()
    }
    return EditorValidation(canSubmit = canSubmit, titleTooLong = titleTooLong)
}
