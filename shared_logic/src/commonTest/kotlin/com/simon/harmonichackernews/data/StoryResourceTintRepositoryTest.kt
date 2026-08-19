package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.network.StoryResourceTintState
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
