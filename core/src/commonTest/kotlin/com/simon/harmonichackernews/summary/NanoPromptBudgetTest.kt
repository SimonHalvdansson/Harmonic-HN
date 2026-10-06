package com.simon.harmonichackernews.summary

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NanoPromptBudgetTest {
    @Test
    fun articleAdmissionReservesHalfTheInputBudgetIncludingInstructions() = runTest {
        for ((total, halfInput) in listOf(8192 to 1999, 4096 to 1536)) {
            val text = "article and summary"
            val accepted = NanoPromptBudget.fit(text, total, true, reserveDiscussionSpace = true) { halfInput }
            assertEquals(halfInput, accepted.tokens)
            assertFailsWith<IllegalArgumentException> {
                NanoPromptBudget.fit(text, total, true, reserveDiscussionSpace = true) { halfInput + 1 }
            }
            // An initial request using 95% is rejected, although it could technically run.
            val nearlyFull = (halfInput * 2 * 0.95).toInt()
            assertFailsWith<IllegalArgumentException> {
                NanoPromptBudget.fit(text, total, true, reserveDiscussionSpace = true) { nearlyFull }
            }
            // Later turns may use the discussion space once a small article was admitted.
            assertEquals(nearlyFull, NanoPromptBudget.fit(text, total, true) { nearlyFull }.tokens)
        }
    }

    @Test
    fun countsInstructionsAndReservesOutputWithinTheDeviceLimit() = runTest {
        val input = NanoPromptBudget.fit("x".repeat(2500), 4096, true) { it.length + 500 }
        assertEquals(3000, input.tokens)
        assertEquals(1096, input.outputTokens)
        assertEquals(4096, input.tokens + input.outputTokens)
    }

    @Test
    fun shortRequestsCanGenerateMoreThan256Tokens() = runTest {
        val input = NanoPromptBudget.fit("question", 8192, true) { 200 }
        assertEquals(4096, input.outputTokens)
    }

    @Test
    fun oversizeConversationsAreRejectedIntact() = runTest {
        val text = "{\"question\":\"" + "x".repeat(4000) + "\"}"
        val seen = mutableListOf<String>()
        assertFailsWith<IllegalArgumentException> {
            NanoPromptBudget.fit(text, 8192, true) { seen += it; 4100 }
        }
        assertEquals(listOf(text), seen)
    }

    @Test
    fun articlesAreShortenedAndRecountedIncludingNonEnglishText() = runTest {
        val original = "語😀".repeat(1600)
        val seen = mutableListOf<String>()
        val input = NanoPromptBudget.fit(original, 8192, false) {
            seen += it
            assertFalse(it.lastOrNull()?.isHighSurrogate() == true)
            it.length + 300
        }
        assertTrue(seen.size > 1)
        assertTrue(original.startsWith(input.text))
        assertTrue(input.tokens < 4000)
    }

    @Test
    fun oversizedInstructionsFailRatherThanLoopForever() = runTest {
        assertFailsWith<IllegalArgumentException> {
            NanoPromptBudget.fit("short", 4096, false) { 5000 }
        }
    }
}
