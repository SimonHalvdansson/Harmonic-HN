package com.simon.harmonichackernews.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DomainSnapshotsTest {
    @Test
    fun storyDomainAndPresentationStateAreSeparated() {
        val story = Story().apply {
            id = 42
            by = "alice"
            title = "KMP"
            time = 1_700_000_000
            kids = intArrayOf(1, 2)
            loaded = true
            clicked = true
            previewImageUrl = "https://example.com/image.png"
            previewImageUrlLoaded = true
            previewImageLoaded = true
        }

        val domain = story.toSnapshot()
        val presentation = story.presentationSnapshot()

        assertEquals(listOf(1, 2), domain.childIds)
        assertEquals("alice", domain.author)
        assertFalse(Json.encodeToString(domain).contains("previewImage"))
        assertEquals(true, presentation.loaded)
        assertEquals("https://example.com/image.png", presentation.previewImage.url)
    }

    @Test
    fun commentSnapshotRoundTripsWithoutExpansionState() {
        val source = Comment().apply {
            id = 7
            by = "bob"
            text = "Hello"
            expanded = true
            depth = 4
        }

        val restored = Comment().applySnapshot(source.toSnapshot())

        assertEquals(7, restored.id)
        assertEquals("bob", restored.by)
        assertFalse(restored.expanded)
        assertEquals(4, source.presentationSnapshot().depth)
    }

    @Test
    fun relativeTimeFormattingAcceptsAnExplicitClockValue() {
        val story = Story().apply { time = 100 }

        assertEquals("1m", story.formatTime(nowMillis = 160_000))
    }
}
