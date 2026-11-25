package com.simon.harmonichackernews.settings

enum class CommentTranslationMode(val label: String, val description: String) {
    Automatic("Automatic", "Translate visible comments and prepare the next reply or sibling thread."),
    Button("Button", "Tap the translation icon in the comment header."),
    Details("In dialog", "Long press the preview to translate it in the comment dialog."),
}

data class CommentTranslationPreferences(
    val enabled: Boolean = false,
    val mode: CommentTranslationMode = CommentTranslationMode.Button,
    val language: String = "",
    val showOriginalWhileTranslating: Boolean = true,
)
