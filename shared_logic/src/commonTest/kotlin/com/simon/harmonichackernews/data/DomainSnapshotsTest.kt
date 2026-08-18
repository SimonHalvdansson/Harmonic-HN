package com.simon.harmonichackernews.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

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

    @Test
    fun commentsHeaderEnrichmentIsDeepCopiedIntoImmutablePresentation() {
        val option = PollOption().apply {
            id = 5
            text = "Kotlin"
            points = 12
            loaded = true
        }
        val repo = RepoInfo().apply {
            name = "harmonic"
            owner = "simon"
            stars = 99
        }
        val huggingFace = HuggingFaceModelInfo().apply {
            name = "Kimi-K3"
            logoUrl = "https://huggingface.co/example/logo.png"
            likes = 10_810
        }
        val story = Story().apply {
            id = 42
            pollOptionArrayList = arrayListOf(option)
            repoInfo = repo
            huggingFaceInfo = huggingFace
        }

        val snapshot = story.presentationSnapshot()
        option.text = "Changed"
        repo.name = "changed"
        huggingFace.name = "changed"
        huggingFace.logoUrl = "https://example.com/changed.png"
        story.pollOptionArrayList = null
        story.repoInfo = null
        story.huggingFaceInfo = null

        assertEquals("Kotlin", snapshot.pollOptions.single().text)
        assertEquals("harmonic", snapshot.repoInfo?.name)
        assertEquals("Kimi-K3", snapshot.huggingFaceInfo?.name)
        assertEquals(
            "https://huggingface.co/example/logo.png",
            snapshot.huggingFaceInfo?.logoUrl,
        )
        assertEquals("10.8K likes", snapshot.huggingFaceInfo?.formatLikes())
        assertNull(story.repoInfo)
    }
}
