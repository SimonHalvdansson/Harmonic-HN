package com.simon.harmonichackernews.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryCacheIndexTest {
    @Test
    fun recordDeduplicatesAndEvictsTheOldestValidEntry() {
        val result = StoryCacheIndex.record(
            encodedEntries = setOf("1-100", "2-200", "broken"),
            storyId = 3,
            cachedAtMillis = 300,
            maximumEntries = 2,
        )

        assertEquals(setOf(2, 3), StoryCacheIndex.storyIds(result.encodedEntries))
        assertEquals(listOf(1), result.evictedStoryIds)
    }

    @Test
    fun recordBulkEvictionPreservesOldestThenStoryIdOrdering() {
        val result = StoryCacheIndex.record(
            encodedEntries = linkedSetOf("1-100", "2-100", "3-50", "4-200", "2-25"),
            storyId = 5,
            cachedAtMillis = 300,
            maximumEntries = 2,
        )

        assertEquals(setOf(4, 5), StoryCacheIndex.storyIds(result.encodedEntries))
        assertEquals(listOf(2, 3, 1), result.evictedStoryIds)
    }

    @Test
    fun recentEntriesAreFilteredAndOrderedDeterministically() {
        val entries = StoryCacheIndex.recentEntries(
            encodedEntries = setOf("3-950", "2-900", "1-100"),
            nowMillis = 1_000,
            maxAgeMillis = 100,
        )

        assertEquals(listOf(2, 3), entries.map(StoryCacheEntry::storyId))
    }

    @Test
    fun recentEntriesPreserveNumericParsingAndRejectOverflowOrMalformedValues() {
        val entries = StoryCacheIndex.recentEntries(
            encodedEntries = linkedSetOf(
                "2147483647-9223372036854775807",
                "+7-+12",
                "0008-000000013",
                "6-13",
                "2147483648-1",
                "1-9223372036854775808",
                "0-1",
                "1--1",
                "1-+",
                "1-2-3",
            ),
            nowMillis = Long.MAX_VALUE,
            maxAgeMillis = Long.MAX_VALUE,
        )

        assertEquals(
            listOf(
                StoryCacheEntry(7, 12),
                StoryCacheEntry(6, 13),
                StoryCacheEntry(8, 13),
                StoryCacheEntry(Int.MAX_VALUE, Long.MAX_VALUE),
            ),
            entries,
        )
    }

    @Test
    fun articleSnapshotSizePolicyRejectsEmptyAndOversizedFiles() {
        assertFalse(ArticleSnapshotPolicy.isValidSize(0))
        assertTrue(ArticleSnapshotPolicy.isValidSize(ArticleSnapshotPolicy.MAX_BYTES))
        assertFalse(ArticleSnapshotPolicy.isValidSize(ArticleSnapshotPolicy.MAX_BYTES + 1))
    }

    @Test
    fun cacheFileNamesAreParsedWithoutPlatformFileApis() {
        assertEquals(42, CacheFileNamePolicy.storyId("story-42.json", "story-", ".json"))
        assertEquals(null, CacheFileNamePolicy.storyId("story-invalid.json", "story-", ".json"))
        assertEquals(null, CacheFileNamePolicy.storyId("42.txt", suffix = ".json"))
    }
}
