package com.simon.harmonichackernews.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryCacheRepositoryTest {
    @Test
    fun storeCompactsIndexesAndHydratesAStory() {
        val files = FakeFiles()
        val metadata = FakeMetadata()
        val repository = StoryCacheRepository(files, metadata)

        assertFalse(repository.hasStoryPayload(42))
        assertTrue(repository.storeStory(42, storyJson(42, "Portable cache"), 1_000))
        assertTrue(repository.hasStoryPayload(42))
        assertTrue(files.contains(StoryCacheKeys.FULL_NAMESPACE, "42.json"))
        assertTrue(files.contains(StoryCacheKeys.SUMMARY_NAMESPACE, "42.json"))
        assertEquals(setOf(42), StoryCacheIndex.storyIds(metadata.getStringSet(StoryCacheKeys.INDEX)))

        val story = Story().apply { id = 42 }
        assertTrue(repository.hydrateStory(story))
        assertEquals("Portable cache", story.title)
        assertEquals("alice", story.by)
        assertEquals(12, story.score)
        assertTrue(story.loaded)
    }

    @Test
    fun hydrateRebuildsADeletedSummaryFromTheFullPayload() {
        val files = FakeFiles()
        val repository = StoryCacheRepository(files, FakeMetadata())
        repository.storeStory(7, storyJson(7, "Rebuilt"), 2_000)
        files.remove(StoryCacheKeys.SUMMARY_NAMESPACE, "7.json")

        val story = Story().apply { id = 7 }
        assertTrue(repository.hydrateStory(story))
        assertEquals("Rebuilt", story.title)
        assertTrue(files.contains(StoryCacheKeys.SUMMARY_NAMESPACE, "7.json"))
    }

    @Test
    fun evictionRemovesStoryArticleAndMetadataTogether() {
        val files = FakeFiles()
        val metadata = FakeMetadata()
        val repository = StoryCacheRepository(files, metadata, maximumStories = 1)
        repository.storeStory(1, storyJson(1, "First"), 100)
        files.write(StoryCacheKeys.ARTICLE_NAMESPACE, "1.html", "article".encodeToByteArray())
        repository.recordArticleMetadata(1, "https://example.com/first", "text/html; charset=utf-16")

        repository.storeStory(2, storyJson(2, "Second"), 200)

        assertFalse(repository.hasStoryPayload(1))
        assertTrue(repository.hasStoryPayload(2))
        assertNull(repository.loadStoryPayload(1))
        assertFalse(files.contains(StoryCacheKeys.SUMMARY_NAMESPACE, "1.json"))
        assertFalse(files.contains(StoryCacheKeys.ARTICLE_NAMESPACE, "1.html"))
        assertNull(repository.articleUrl(1))
        assertEquals(setOf(2), repository.cachedItemIds())
    }

    @Test
    fun articleValidationCharsetAndClearAreSharedPolicies() {
        val files = FakeFiles()
        val metadata = FakeMetadata()
        val repository = StoryCacheRepository(files, metadata)
        files.write(StoryCacheKeys.ARTICLE_NAMESPACE, "9.html", "cached".encodeToByteArray())
        repository.recordArticleMetadata(9, "https://example.com", "text/html; charset=iso-8859-1")

        assertEquals("cached", repository.loadArticle(9, nowMillis = 9_999))
        assertEquals("iso-8859-1", files.lastCharset)
        assertEquals(9_999L, files.lastTouch)
        assertEquals(1, repository.clear())
        assertTrue(repository.cachedItemIds().isEmpty())
        assertTrue(metadata.keys().none { it.startsWith(StoryCacheKeys.ARTICLE_URL) })

        files.write(StoryCacheKeys.ARTICLE_NAMESPACE, "10.html", byteArrayOf())
        repository.recordArticleMetadata(10, "https://invalid.example", null)
        assertNull(repository.loadArticle(10, nowMillis = 10_000))
        assertFalse(files.contains(StoryCacheKeys.ARTICLE_NAMESPACE, "10.html"))
        assertNull(repository.articleUrl(10))
    }

    @Test
    fun articleCharsetFallsBackToUtf8() {
        assertEquals("UTF-8", ArticleCacheMetadata.charsetName(null))
        assertEquals("UTF-8", ArticleCacheMetadata.charsetName("text/html"))
        assertEquals("windows-1252", ArticleCacheMetadata.charsetName("text/html; Charset=\"windows-1252\""))
    }

    @Test
    fun recentStoryAvailabilityReusesItsValidatedCacheEntry() {
        val files = FakeFiles()
        val repository = StoryCacheRepository(files, FakeMetadata())
        repository.storeStory(42, storyJson(42, "Cached"), cachedAtMillis = 1_000)

        assertTrue(repository.hasRecentStories(nowMillis = 2_000))
        val readsAfterValidation = files.readTextCount
        assertTrue(repository.hasRecentStories(nowMillis = 3_000))

        assertEquals(readsAfterValidation, files.readTextCount)
    }

    private fun storyJson(id: Int, title: String): String =
        """{"id":$id,"type":"story","title":"$title","author":"alice","points":12,"created_at_i":123,"url":"https://example.com/$id","children":[]}"""

    private class FakeFiles : StoryCacheFileStore {
        private val values = mutableMapOf<Pair<String, String>, ByteArray>()
        var lastCharset: String? = null
        var lastTouch: Long? = null
        var readTextCount: Int = 0

        override fun read(namespace: String, key: String): ByteArray? = values[namespace to key]

        override fun readText(namespace: String, key: String, charsetName: String): String? {
            readTextCount++
            lastCharset = charsetName
            return read(namespace, key)?.decodeToString()
        }

        override fun write(namespace: String, key: String, value: ByteArray): Boolean {
            values[namespace to key] = value
            return true
        }

        override fun remove(namespace: String, key: String): Boolean =
            values.remove(namespace to key) != null

        override fun list(namespace: String): List<CacheFileInfo> = values
            .filterKeys { it.first == namespace }
            .map { (path, value) -> CacheFileInfo(path.second, value.size.toLong()) }

        override fun clear(namespace: String) {
            values.keys.filter { it.first == namespace }.forEach { values.remove(it) }
        }

        override fun touch(namespace: String, key: String, modifiedAtMillis: Long) {
            if (contains(namespace, key)) lastTouch = modifiedAtMillis
        }

        fun contains(namespace: String, key: String): Boolean = (namespace to key) in values
    }

    private class FakeMetadata : StoryCacheMetadataStore {
        private val strings = mutableMapOf<String, String?>()
        private val sets = mutableMapOf<String, Set<String>>()

        override fun getString(key: String): String? = strings[key]

        override fun putString(key: String, value: String?) {
            strings[key] = value
        }

        override fun remove(key: String) {
            strings.remove(key)
            sets.remove(key)
        }

        override fun getStringSet(key: String): Set<String> = sets[key].orEmpty()

        override fun putStringSet(key: String, value: Set<String>) {
            sets[key] = value
        }

        override fun keys(): Set<String> = strings.keys + sets.keys
    }
}
