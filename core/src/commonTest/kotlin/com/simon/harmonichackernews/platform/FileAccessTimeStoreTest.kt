package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileAccessTimeStoreTest {
    @Test
    fun removeTreeBatchesMatchingMetadataIntoOneUpdate() {
        val store = RecordingKeyValueStore()
        store.putLong("file_access:cache/articles", 1L)
        store.putLong("file_access:cache/articles/1.html", 2L)
        store.putLong("file_access:cache/articles\\2.html", 3L)
        store.putLong("file_access:cache/article-summaries/1.txt", 4L)

        FileAccessTimeStore(store).removeTree("cache/articles")

        assertEquals(1, store.updateCount)
        assertEquals(0, store.directRemoveCount)
        assertFalse(store.contains("file_access:cache/articles"))
        assertFalse(store.contains("file_access:cache/articles/1.html"))
        assertFalse(store.contains("file_access:cache/articles\\2.html"))
        assertTrue(store.contains("file_access:cache/article-summaries/1.txt"))
    }

    private class RecordingKeyValueStore(
        private val delegate: InMemoryKeyValueStore = InMemoryKeyValueStore(),
    ) : KeyValueStore by delegate {
        var updateCount = 0
            private set
        var directRemoveCount = 0
            private set

        override fun remove(key: String) {
            directRemoveCount += 1
            delegate.remove(key)
        }

        override fun update(block: KeyValueStore.Editor.() -> Unit) {
            updateCount += 1
            delegate.update(block)
        }
    }
}
