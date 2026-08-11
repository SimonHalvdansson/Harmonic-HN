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
    fun recentEntriesAreFilteredAndOrderedDeterministically() {
        val entries = StoryCacheIndex.recentEntries(
            encodedEntries = setOf("3-950", "2-900", "1-100"),
            nowMillis = 1_000,
            maxAgeMillis = 100,
        )

        assertEquals(listOf(2, 3), entries.map(StoryCacheEntry::storyId))
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
