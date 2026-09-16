package com.simon.harmonichackernews.ui.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MathPreviewTextTest {
    @Test
    fun separatesInlineVariablesFromAbstractProse() {
        val parts = previewMathSegments("The ${'$'}k${'$'}-server ratio is ${'$'}k${'$'}.")
        assertEquals(listOf("k", "k"), parts.mapNotNull { it.latex })
        assertEquals("The ", parts.first().source)
        assertEquals("-server ratio is ", parts[2].source)
        assertEquals(".", parts.last().source)
    }

    @Test
    fun supportsDisplayMathAndBracketDelimiters() {
        val parts = previewMathSegments("""Inline \(x_i^2\), display \[\frac{1}{2}\] and ${'$'}${'$'}\sum_i x_i${'$'}${'$'}.""")
        assertEquals(listOf("x_i^2", "\\frac{1}{2}", "\\sum_i x_i"), parts.mapNotNull { it.latex })
        assertEquals(listOf(false, true, true), parts.filter { it.latex != null }.map { it.display })
    }

    @Test
    fun preservesEscapedDollarsAndUnfinishedFormulas() {
        val text = """Costs \${'$'}5; unfinished ${'$'}x and \(y."""
        assertEquals(listOf(PreviewMathSegment(text)), previewMathSegments(text))
        assertTrue(previewMathSegments("").isEmpty())
    }

    @Test
    fun escapedDelimiterInsideFormulaDoesNotEndIt() {
        val text = """A ${'$'}x + \${'$'}${'$'} term."""
        assertEquals(listOf("""x + \${'$'}"""), previewMathSegments(text).mapNotNull { it.latex })
    }
}
