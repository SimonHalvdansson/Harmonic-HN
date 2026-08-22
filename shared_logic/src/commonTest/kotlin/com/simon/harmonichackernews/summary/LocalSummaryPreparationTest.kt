package com.simon.harmonichackernews.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalSummaryPreparationTest {
    @Test
    fun textWithinBudgetRetainsItsInternalWhitespace() {
        assertEquals(
            "one\t  two\nthree",
            LocalSummaryPreparation.prepareManagedText("  one\t  two\nthree  ", maximumWords = 3),
        )
    }

    @Test
    fun textOverBudgetNormalizesAsciiRegexWhitespaceInTheRetainedPrefix() {
        assertEquals(
            "one two three four five six",
            LocalSummaryPreparation.prepareManagedText(
                " one\t\t two\nthree\u000Bfour\u000Cfive\r six   seven and an unread tail ",
                maximumWords = 6,
            ),
        )
    }

    @Test
    fun nonAsciiWhitespaceInsideTextDoesNotSplitAWord() {
        assertEquals(
            "one\u2003two",
            LocalSummaryPreparation.prepareManagedText(
                "one\u2003two three four",
                maximumWords = 1,
            ),
        )
    }

    @Test
    fun exactBudgetDoesNotNormalizeSeparators() {
        assertEquals(
            "one\t\ttwo",
            LocalSummaryPreparation.prepareManagedText("one\t\ttwo", maximumWords = 2),
        )
    }

    @Test
    fun zeroBudgetReturnsEmptyTextAndNegativeBudgetIsRejected() {
        assertEquals("", LocalSummaryPreparation.prepareManagedText("one two", maximumWords = 0))
        assertFailsWith<IllegalArgumentException> {
            LocalSummaryPreparation.prepareManagedText("one two", maximumWords = -1)
        }
    }

    @Test
    fun veryLongTailDoesNotAffectTheRetainedPrefix() {
        val input = buildString {
            append("alpha beta gamma delta")
            repeat(20_000) { append(" ignored") }
        }

        assertEquals(
            "alpha beta gamma",
            LocalSummaryPreparation.prepareManagedText(input, maximumWords = 3),
        )
    }
}
