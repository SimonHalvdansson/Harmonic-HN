package com.simon.harmonichackernews.ui.comments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AskSuggestionsTest {
    private val starters = listOf("Explain this comment", "Give me a concrete example", "What assumptions are they making?")

    @Test
    fun usedQuestionsDisappearButOtherStarterAndGeneratedQuestionsRemain() {
        val generated = "What evidence would support this?"
        assertEquals(starters.drop(1) + generated,
            remainingDiscussionQuestions(starters + generated, listOf(starters.first())))
        assertEquals(listOf(starters.last()),
            remainingDiscussionQuestions(starters + generated, listOf(starters[0], generated, starters[1])))
    }

    @Test
    fun customQuestionKeepsSuggestionsAndResetRestoresThem() {
        assertEquals(starters, remainingDiscussionQuestions(starters, listOf("Why does that happen?")))
        assertEquals(starters, remainingDiscussionQuestions(starters, emptyList()))
        assertTrue(remainingDiscussionQuestions(starters, starters).isEmpty())
    }

    @Test
    fun matchingIgnoresCaseAndSurroundingWhitespaceAndRemovesDuplicates() {
        assertEquals(starters.drop(1), remainingDiscussionQuestions(
            starters + listOf("give me a concrete example", " "), listOf(" EXPLAIN THIS COMMENT "),
        ))
    }
}
