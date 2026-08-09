package com.simon.harmonichackernews.settings

object TextPreferences {
    const val DEFAULT_STORY_TEXT_SIZE = 17.5f
    const val DEFAULT_COMMENT_TEXT_SIZE = 15f
    const val MIN_STORY_TEXT_SIZE = 14.5f
    const val MAX_STORY_TEXT_SIZE = 20.5f
    const val MIN_COMMENT_TEXT_SIZE = 12f
    const val MAX_COMMENT_TEXT_SIZE = 18f

    fun sanitizeFont(font: String?): String = when (font) {
        "productsans",
        "googlesansflexrounded",
        "googlesans",
        "devicedefault",
        "verdana",
        "jetbrainsmono",
        "googlesanscode",
        "georgia",
        "robotoslab" -> font
        else -> "googlesansflexrounded"
    }

    fun clampStoryTextSize(value: Float): Float =
        value.coerceIn(MIN_STORY_TEXT_SIZE, MAX_STORY_TEXT_SIZE)

    fun clampCommentTextSize(value: Float): Float =
        value.coerceIn(MIN_COMMENT_TEXT_SIZE, MAX_COMMENT_TEXT_SIZE)
}
