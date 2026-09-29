package com.simon.harmonichackernews.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalFoundationApi::class)
class EditorFormattingStateTest {
    @Test
    fun formattingIsOneUndoableEditAndRestoresReversedSelection() {
        val state = TextFieldState("A useful reply", TextRange(8, 2))
        applyItalicFormatting(state)
        val formatted = state.text.toString()
        val formattedSelection = state.selection
        assertEquals("A *useful* reply", formatted)

        state.undoState.undo()
        assertEquals("A useful reply", state.text.toString())
        assertEquals(TextRange(8, 2), state.selection)
        state.undoState.redo()
        assertEquals(formatted, state.text.toString())
        assertEquals(formattedSelection, state.selection)
    }

    @Test
    fun codeFormattingAndItalicFormattingHaveSeparateUndoEntries() {
        val state = TextFieldState("first\nsecond", TextRange(0, 12))
        applyCodeBlockFormatting(state)
        val code = state.text.toString()
        assertEquals("  first\n  second", code)
        applyItalicFormatting(state)
        state.undoState.undo()
        assertEquals(code, state.text.toString())
        state.undoState.undo()
        assertEquals("first\nsecond", state.text.toString())
        assertEquals(TextRange(0, 12), state.selection)
    }
}
