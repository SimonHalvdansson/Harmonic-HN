package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchIndexTest {
    private val font = SettingsSearchEntry("font", "appearance", "Font", listOf("Appearance"), "typeface typography")
    private val size = SettingsSearchEntry("size", "comments", "Text size", listOf("Comments", "Display"), "font bigger smaller")
    private val hidden = SettingsSearchEntry("hidden", "comments", "Hide comments", listOf("Comments", "Interactions"), "flagged dead delayed show all")
    private val index = SettingsSearchIndex(listOf(size, hidden, font))

    @Test fun emptyAndPunctuationOnlyQueriesHaveNoResults() {
        for (query in listOf("", "  ", "---", "!!!")) assertTrue(index.search(query).isEmpty())
    }

    @Test fun exactTitleRanksAboveKeywordsAndPartialMatches() {
        assertEquals(listOf(font, size), index.search("font"))
        assertEquals(listOf(font, size), index.search("fon"))
    }

    @Test fun allWordsMustMatchAcrossTitleKeywordsAndPathInAnyOrder() {
        assertEquals(listOf(size), index.search("comments bigger"))
        assertEquals(listOf(size), index.search("BIGGER comments"))
        assertEquals(listOf(hidden), index.search("dead comments"))
        assertTrue(index.search("dead font").isEmpty())
    }

    @Test fun caseWhitespaceAndPunctuationAreNormalized() {
        assertEquals(listOf(size), index.search("  TEXT-size \n"))
        assertEquals(listOf(hidden), index.search("DeAd,   COMMENTS"))
    }

    @Test fun duplicateTermsDoNotChangeRankingAndTiesAreStable() {
        assertEquals(index.search("font"), index.search("font FONT"))
        assertEquals(listOf(hidden, size), index.search("comments"))
        val other = font.copy(id = "other")
        assertEquals(listOf(font, other), SettingsSearchIndex(listOf(font, other)).search("typeface"))
    }
}
