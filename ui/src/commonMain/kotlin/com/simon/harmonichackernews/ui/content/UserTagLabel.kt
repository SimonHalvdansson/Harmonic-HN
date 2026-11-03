package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/** Colors only the appended user tag, preserving the rest of the metadata's contrast. */
internal fun annotatedUserTagLabel(label: String, tag: String, color: Color): AnnotatedString {
    val suffix = " ($tag)"
    val start = if (tag.isBlank()) -1 else label.lastIndexOf(suffix)
    return AnnotatedString.Builder(label).apply {
        if (start >= 0) addStyle(SpanStyle(color = color), start + 1, start + suffix.length)
    }.toAnnotatedString()
}
