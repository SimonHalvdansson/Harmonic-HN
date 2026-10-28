package com.simon.harmonichackernews.summary

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AppleSummaryPromptBudgetTest {
    @Test fun longUnbrokenArticlesAreBoundedAndRetriedAfterNativeOverflow() = runTest {
        val seen = mutableListOf<String>()
        val original = "語😀".repeat(8_000)
        val result = AppleSummaryPromptBudget.generate(original, preserveInput = false) {
            seen += it
            assertFalse(it.last().isHighSurrogate())
            if (it.length > 1_000) error(AppleSummaryPromptBudget.CONTEXT_LIMIT_MESSAGE)
            "summary"
        }
        assertEquals("summary", result)
        assertTrue(seen.first().length <= 6_000)
        assertTrue(seen.size > 1)
        assertTrue(seen.zipWithNext().all { (a, b) -> a.length > b.length && a.startsWith(b) })
    }

    @Test fun oversizedInstructionsStopAtTheMinimumArticleInsteadOfRetryingForever() = runTest {
        val lengths = mutableListOf<Int>()
        assertFailsWith<IllegalStateException> {
            AppleSummaryPromptBudget.generate("x".repeat(20_000), false) {
                lengths += it.length
                error(AppleSummaryPromptBudget.CONTEXT_LIMIT_MESSAGE)
            }
        }
        assertEquals(400, lengths.last())
        assertTrue(lengths.size <= 6)
    }

    @Test fun conversationsAreNeverTruncated() = runTest {
        val payload = "{\"question\":\"${"x".repeat(10_000)}\"}"
        var attempts = 0
        assertFailsWith<IllegalStateException> {
            AppleSummaryPromptBudget.generate(payload, true) {
                attempts++
                assertEquals(payload, it)
                error(AppleSummaryPromptBudget.CONTEXT_LIMIT_MESSAGE)
            }
        }
        assertEquals(1, attempts)
    }

    @Test fun cancellationAndUnrelatedErrorsAreNotRetried() = runTest {
        for (failure in listOf(CancellationException("cancelled"), IllegalStateException("unavailable"))) {
            var attempts = 0
            val actual = assertFails {
                AppleSummaryPromptBudget.generate("x".repeat(10_000), false) { attempts++; throw failure }
            }
            assertSame(failure, actual)
            assertEquals(1, attempts)
        }
    }
}
