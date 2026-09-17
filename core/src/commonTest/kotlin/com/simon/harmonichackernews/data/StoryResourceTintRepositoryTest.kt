package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.network.StoryResourceTintState
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryResourceTintRepositoryTest {
    @Test
    fun tintIsPortableAndStrictlyKeyedByResourceAndPaletteInputs() {
        val repository = StoryResourceTintRepository(InMemoryKeyValueStore())
        val tint = StoryResourceTintState(
            sourceUrl = "https://example.com/preview.png",
            baseColorArgb = 0xff102030.toInt(),
            paletteConfigKey = "default",
            tintColorArgb = 0xff405060.toInt(),
        )

        repository.write(42, StoryResourceTintKind.PREVIEW_IMAGE, tint)

        assertEquals(
            tint,
            repository.read(
                42,
                StoryResourceTintKind.PREVIEW_IMAGE,
                tint.sourceUrl,
                tint.baseColorArgb,
                tint.paletteConfigKey,
            ),
        )
        assertNull(
            repository.read(
                42,
                StoryResourceTintKind.PREVIEW_IMAGE,
                tint.sourceUrl,
                tint.baseColorArgb,
                "vibrant",
            ),
        )
        assertNull(
            repository.read(
                42,
                StoryResourceTintKind.FAVICON,
                tint.sourceUrl,
                tint.baseColorArgb,
                tint.paletteConfigKey,
            ),
        )
    }

    @Test
    fun writingReplacementInvalidatesPreviouslyCachedTuple() {
        val repository = StoryResourceTintRepository(InMemoryKeyValueStore())
        val original = StoryResourceTintState(
            sourceUrl = "https://example.com/original.png",
            baseColorArgb = 0xff102030.toInt(),
            paletteConfigKey = "default",
            tintColorArgb = 0xff405060.toInt(),
        )
        val replacement = original.copy(
            sourceUrl = "https://example.com/replacement.png",
            tintColorArgb = 0xff708090.toInt(),
        )

        repository.write(42, StoryResourceTintKind.PREVIEW_IMAGE, original)
        repository.write(42, StoryResourceTintKind.PREVIEW_IMAGE, replacement)

        assertNull(
            repository.read(
                42,
                StoryResourceTintKind.PREVIEW_IMAGE,
                original.sourceUrl,
                original.baseColorArgb,
                original.paletteConfigKey,
            ),
        )
        assertEquals(
            replacement,
            repository.read(
                42,
                StoryResourceTintKind.PREVIEW_IMAGE,
                replacement.sourceUrl,
                replacement.baseColorArgb,
                replacement.paletteConfigKey,
            ),
        )
    }

    @Test
    fun clearRemovesEveryTintKeyInOneStoreUpdate() {
        val store = UpdateTrackingKeyValueStore()
        val repository = StoryResourceTintRepository(store)
        val tint = StoryResourceTintState(
            sourceUrl = "https://example.com/preview.png",
            baseColorArgb = 0xff102030.toInt(),
            paletteConfigKey = "default",
            tintColorArgb = 0xff405060.toInt(),
        )
        store.putString("unrelated", "preserved")
        repository.write(42, StoryResourceTintKind.PREVIEW_IMAGE, tint)
        store.resetTracking()

        repository.clear()

        assertEquals(1, store.updateCount)
        assertEquals(0, store.directRemoveCount)
        assertEquals("preserved", store.getString("unrelated"))
        assertFalse(store.keys().any { it.startsWith("story_resource_tint.") })
        assertEquals(0, repository.count())
    }

    @Test
    fun persistentTintCacheEvictsEntriesBeyondItsBound() {
        val store = InMemoryKeyValueStore()
        val repository = StoryResourceTintRepository(store)

        repeat(513) { index ->
            val storyId = index + 1
            repository.write(
                storyId,
                StoryResourceTintKind.PREVIEW_IMAGE,
                StoryResourceTintState(
                    sourceUrl = "https://example.com/$storyId.png",
                    baseColorArgb = 0xff102030.toInt(),
                    paletteConfigKey = "default",
                    tintColorArgb = 0xff405060.toInt(),
                ),
            )
        }

        assertEquals(512, repository.count())
        assertTrue(store.keys().count { it.startsWith("story_resource_tint.") } <= 2_049)
    }

    private class UpdateTrackingKeyValueStore(
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

        fun resetTracking() {
            updateCount = 0
            directRemoveCount = 0
        }
    }
}
