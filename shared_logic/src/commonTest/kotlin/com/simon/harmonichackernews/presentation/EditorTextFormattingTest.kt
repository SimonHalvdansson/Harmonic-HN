package com.simon.harmonichackernews.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class EditorTextFormattingTest {
    @Test
    fun italicFormattingWrapsSelectionsAndPlacesACursorBetweenEmptyMarkers() {
        assertEquals(
            EditorTextEdit("one *two* three", 5, 8),
            formatEditorItalic("one two three", 4, 7),
        )
        assertEquals(
            EditorTextEdit("one **two", 5),
            formatEditorItalic("one two", 4, 4),
        )
    }

    @Test
    fun formattingNormalizesReversedAndOutOfBoundsSelections() {
        assertEquals(
            EditorTextEdit("*text*", 1, 5),
            formatEditorItalic("text", 99, -5),
        )
    }

    @Test
    fun codeFormattingIndentsEverySelectedLineAndKeepsParagraphSpacing() {
        assertEquals(
            EditorTextEdit("before\n\n  alpha\n  beta\n\nafter", 8, 22),
            formatEditorCodeBlock(
                text = "before\nalpha\nbeta\nafter",
                selectionStart = 9,
                selectionEnd = 14,
            ),
        )
    }

    @Test
    fun codeFormattingInsertsAnEditablePlaceholderAtTheCursor() {
        assertEquals(
            EditorTextEdit("paragraph\n\n  code", 13, 17),
            formatEditorCodeBlock("paragraph", 9, 9),
        )
    }
}
