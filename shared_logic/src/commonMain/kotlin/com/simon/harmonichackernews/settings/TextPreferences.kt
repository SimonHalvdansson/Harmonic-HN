package com.simon.harmonichackernews.settings

import kotlin.math.roundToInt

object TextPreferences {
    const val DEFAULT_STORY_TEXT_SIZE = 17.5f
    const val DEFAULT_COMMENT_TEXT_SIZE = 15f
    const val MIN_STORY_TEXT_SIZE = 14.5f
    const val MAX_STORY_TEXT_SIZE = 20.5f
    const val MIN_COMMENT_TEXT_SIZE = 12f
    const val MAX_COMMENT_TEXT_SIZE = 18f
    const val MIN_TEXT_SIZE_OFFSET = -6
    const val MAX_TEXT_SIZE_OFFSET = 6
    const val TEXT_SIZE_OFFSET_STEP = 0.5f
    const val DEFAULT_READER_MODE_FONT_SIZE = 18
    const val MIN_READER_MODE_FONT_SIZE = 14
    const val MAX_READER_MODE_FONT_SIZE = 24

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

    fun storyTextSizeOffset(value: Float): Int =
        ((clampStoryTextSize(value) - DEFAULT_STORY_TEXT_SIZE) / TEXT_SIZE_OFFSET_STEP)
            .roundToInt()
            .coerceIn(MIN_TEXT_SIZE_OFFSET, MAX_TEXT_SIZE_OFFSET)

    fun storyTextSizeForOffset(offset: Int): Float = clampStoryTextSize(
        DEFAULT_STORY_TEXT_SIZE +
            offset.coerceIn(MIN_TEXT_SIZE_OFFSET, MAX_TEXT_SIZE_OFFSET) * TEXT_SIZE_OFFSET_STEP,
    )

    fun commentTextSizeOffset(value: Float): Int =
        ((clampCommentTextSize(value) - DEFAULT_COMMENT_TEXT_SIZE) / TEXT_SIZE_OFFSET_STEP)
            .roundToInt()
            .coerceIn(MIN_TEXT_SIZE_OFFSET, MAX_TEXT_SIZE_OFFSET)

    fun commentTextSizeForOffset(offset: Int): Float = clampCommentTextSize(
        DEFAULT_COMMENT_TEXT_SIZE +
            offset.coerceIn(MIN_TEXT_SIZE_OFFSET, MAX_TEXT_SIZE_OFFSET) * TEXT_SIZE_OFFSET_STEP,
    )

    fun clampReaderModeFontSize(value: Int): Int =
        value.coerceIn(MIN_READER_MODE_FONT_SIZE, MAX_READER_MODE_FONT_SIZE)
}
