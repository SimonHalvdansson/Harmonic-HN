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

    @Test fun unicodeNormalizationRetainsWholeStringLowercaseAndCharacterBoundaries() {
        val samples = listOf("İSTANBUL", "ΟΣ", "CAFÉ", "cafe\u0301", "机器学习", "\uD801\uDC00",
            "one\u00a0two\u2003three", "a\u0000b", "👋-Hello!!!", "\uD800text\uDC00", "  a...b\n c  ")
        for (sample in samples) {
            val normalized = legacyNormalize(sample)
            val entry = SettingsSearchEntry("sample", "screen", sample, emptyList())
            val index = SettingsSearchIndex(listOf(entry))
            assertEquals(if (normalized.isEmpty()) emptyList() else listOf(entry), index.search(normalized), sample)
            assertEquals(index.search(normalized), index.search(sample), sample)
        }
    }

    @Test fun punctuationAndWhitespaceNormalizationMatchesThePreviousSearchAcrossGeneratedQueries() {
        val alphabet = "AbZ19éİΣς中_-.,\t\n\u00a0\u2003\u0301\uD800\uDC00"
        val samples = List(160) { index ->
            buildString { repeat(1 + index % 40) { append(alphabet[(index * 17 + it * 7) % alphabet.length]) } }
        }
        val entries = samples.mapIndexed { index, value ->
            SettingsSearchEntry("entry-$index", "screen", value, listOf("Path $value"), "Keyword $value",
                aliases = listOf("Alias $value"))
        }
        val actual = SettingsSearchIndex(entries)
        val normalizedEntries = entries.map { it.copy(title = legacyNormalize(it.title),
            path = it.path.map(::legacyNormalize), keywords = legacyNormalize(it.keywords),
            aliases = it.aliases.map(::legacyNormalize)) }
        val reference = SettingsSearchIndex(normalizedEntries)
        for (sample in samples) {
            assertEquals(reference.search(legacyNormalize(sample)).map { it.id },
                actual.search(sample).map { it.id }, sample)
        }
    }

    private fun legacyNormalize(value: String): String =
        value.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("").trim().split(Regex("\\s+")).joinToString(" ")
}
