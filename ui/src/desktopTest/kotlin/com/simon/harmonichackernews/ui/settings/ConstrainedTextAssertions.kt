package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.text.TextLayoutResult
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verify the rendered text, rather than the unused width of its paragraph constraint. */
internal fun assertTextContentFits(layout: TextLayoutResult, message: String = "Text must remain readable") {
    // Compose 1.13 can retain a wider paragraph constraint after shrinking a short Text node.
    // didOverflowWidth then reports unused paragraph space, even though every glyph fits.
    assertFalse(layout.didOverflowHeight, "$message: height overflow")
    assertTrue(layout.lineCount > 0, "$message: no rendered lines")
    assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1),
        "$message: missing characters")
    repeat(layout.lineCount) { line ->
        assertFalse(layout.isLineEllipsized(line), "$message: ellipsized line $line")
        val inkWidth = layout.getLineRight(line) - layout.getLineLeft(line)
        assertTrue(inkWidth <= layout.size.width + .5f,
            "$message: line $line needs $inkWidth px in ${layout.size.width} px")
    }
}
