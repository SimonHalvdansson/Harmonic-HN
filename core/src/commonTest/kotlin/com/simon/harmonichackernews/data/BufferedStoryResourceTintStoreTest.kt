package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.network.StoryResourceTintKind.PREVIEW_IMAGE
import com.simon.harmonichackernews.network.StoryResourceTintState
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BufferedStoryResourceTintStoreTest {
    private fun tint(id: Int) = StoryResourceTintState("https://example.com/$id.png", 1, "palette", id)
    private fun StoryResourceTintStore.read(id: Int) = read(id, PREVIEW_IMAGE, tint(id).sourceUrl, 1, "palette")

    @Test
    fun colorsAreImmediateAndBurstsUseOneBackgroundTransaction() = runTest {
        val store = CountingStore()
        val cache = BufferedStoryResourceTintStore(backgroundScope, { store }, StandardTestDispatcher(testScheduler))
        runCurrent()
        repeat(100) { cache.write(it, PREVIEW_IMAGE, tint(it)) }
        assertEquals(tint(42), cache.read(42))
        assertEquals(0, store.writes)
        runCurrent(); advanceTimeBy(101); runCurrent()
        assertEquals(1, store.writes)
        assertEquals(tint(42), StoryResourceTintRepository(store).read(42))
        val restored = BufferedStoryResourceTintStore(backgroundScope, { store }, StandardTestDispatcher(testScheduler))
        runCurrent()
        assertEquals(tint(42), restored.read(42))
        assertEquals(100, restored.count())
        assertEquals(1, store.writes)
    }

    @Test
    fun clearDuringLoadingCannotResurrectOldColorsAndLaterWritesSurvive() = runTest {
        val store = InMemoryKeyValueStore()
        StoryResourceTintRepository(store).write(1, PREVIEW_IMAGE, tint(1))
        val opening = CompletableDeferred<Unit>()
        val cache = BufferedStoryResourceTintStore(backgroundScope,
            { opening.await(); store }, StandardTestDispatcher(testScheduler))
        cache.write(2, PREVIEW_IMAGE, tint(2))
        cache.clear()
        cache.write(3, PREVIEW_IMAGE, tint(3))
        opening.complete(Unit)
        runCurrent(); advanceTimeBy(101); runCurrent()
        assertNull(cache.read(1)); assertNull(cache.read(2))
        assertEquals(tint(3), cache.read(3))
        assertEquals(1, StoryResourceTintRepository(store).count())
        assertEquals(tint(3), StoryResourceTintRepository(store).read(3))
    }

    @Test
    fun sparseUpdatesDoNotSerializeTheRestOfTheCacheAgain() = runTest {
        val store = CountingStore()
        val cache = BufferedStoryResourceTintStore(backgroundScope, { store }, StandardTestDispatcher(testScheduler))
        runCurrent()
        repeat(512) { cache.write(it, PREVIEW_IMAGE, tint(it)) }
        runCurrent(); advanceTimeBy(101); runCurrent()
        store.editedKeys.clear()
        cache.write(42, PREVIEW_IMAGE, tint(42).copy(tintColorArgb = 99))
        runCurrent(); advanceTimeBy(101); runCurrent()
        assertEquals(4, store.editedKeys.size)
        assertTrue(store.editedKeys.all { it.startsWith("story_resource_tint.preview_image.42.") })
        assertEquals(512, StoryResourceTintRepository(store).count())
        assertEquals(99, StoryResourceTintRepository(store).read(42)?.tintColorArgb)
    }

    @Test
    fun boundedCacheKeepsNewestEntriesAndRetriesFailedBatches() = runTest {
        val store = CountingStore()
        val cache = BufferedStoryResourceTintStore(backgroundScope, { store }, StandardTestDispatcher(testScheduler))
        runCurrent()
        repeat(600) { cache.write(it, PREVIEW_IMAGE, tint(it)) }
        assertEquals(512, cache.count())
        assertNull(cache.read(0))
        assertEquals(tint(599), cache.read(599))
        store.fail = true
        runCurrent(); advanceTimeBy(101); runCurrent()
        cache.clear()
        cache.write(700, PREVIEW_IMAGE, tint(700))
        store.fail = false
        advanceTimeBy(1_200); runCurrent()
        assertEquals(1, StoryResourceTintRepository(store).count())
        assertEquals(tint(700), StoryResourceTintRepository(store).read(700))
    }

    @Test
    fun migrationWaitsForDurabilityEvenOnRetryAndPreservesUnrelatedData() = runTest {
        val legacy = InMemoryKeyValueStore()
        val target = InMemoryKeyValueStore()
        legacy.putString("bookmark", "preserved")
        StoryResourceTintRepository(legacy).write(42, PREVIEW_IMAGE, tint(42))
        repeat(2) {
            assertFailsWith<IllegalStateException> {
                migrateStoryResourceTints(legacy, target) { error("disk unavailable") }
            }
            assertEquals(tint(42), StoryResourceTintRepository(legacy).read(42))
        }
        migrateStoryResourceTints(legacy, target) { }
        assertEquals(tint(42), StoryResourceTintRepository(target).read(42))
        assertEquals("preserved", legacy.getString("bookmark"))
        assertTrue(legacy.keys().none { it.startsWith("story_resource_tint.") })
        StoryResourceTintRepository(target).clear()
        migrateStoryResourceTints(legacy, target) { }
        assertEquals(0, StoryResourceTintRepository(target).count())
    }

    private class CountingStore(private val delegate: InMemoryKeyValueStore = InMemoryKeyValueStore()) : KeyValueStore by delegate {
        var writes = 0
        var fail = false
        val editedKeys = mutableListOf<String>()
        override fun update(block: KeyValueStore.Editor.() -> Unit) {
            if (fail) error("disk unavailable")
            writes++
            delegate.update {
                val editor = this
                block(object : KeyValueStore.Editor by editor {
                    override fun putString(key: String, value: String?) {
                        editedKeys += key
                        editor.putString(key, value)
                    }
                    override fun putInt(key: String, value: Int) {
                        editedKeys += key
                        editor.putInt(key, value)
                    }
                    override fun putStringSet(key: String, value: Set<String>?) {
                        editedKeys += key
                        editor.putStringSet(key, value)
                    }
                    override fun remove(key: String) {
                        editedKeys += key
                        editor.remove(key)
                    }
                })
            }
        }
    }
}
