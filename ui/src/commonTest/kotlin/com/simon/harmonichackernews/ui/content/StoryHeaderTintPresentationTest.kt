package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryResourceTintStore
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.network.StoryResourceTintState
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.settings.StoryPreviewTintState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoryHeaderTintPresentationTest {
    private val base = 0xffeeeeee.toInt()
    private val faviconTint = 0xffbbddbb.toInt()
    private val imageTint = 0xffbbbbdd.toInt()
    private val imageUrl = "https://example.com/preview.png"
    private val provider = FaviconUrlBuilder.PROVIDER_GOOGLE
    private val mode = "default"

    @Test
    fun mutableAndSnapshotHeadersKeepFaviconUntilPreviewPaletteArrives() {
        val story = Story("Tint transition", 42, true, false).apply {
            url = "https://example.com/story"
        }
        StoryPreviewTintState.applyFavicon(
            story, FaviconUrlBuilder.faviconUrl(story.url!!, provider), base, mode, faviconTint,
        )
        val pending = StoryPreviewResourceState(
            storyId = story.id,
            pageUrl = story.url!!,
            imageUrl = imageUrl,
            imageUrlResolved = true,
        )
        val ready = pending.copy(previewTint = StoryResourceTintState(
            sourceUrl = imageUrl,
            baseColorArgb = base,
            paletteConfigKey = StoryPreviewTintState.storedMode(mode),
            tintColorArgb = imageTint,
        ))
        for (snapshot in listOf(false, true)) {
            fun presentation(resource: StoryPreviewResourceState?) = if (snapshot) {
                storyHeaderTintPresentation(
                    StoryListItemSnapshot(story.toSnapshot(), story.presentationSnapshot()),
                    resource, provider, mode, base, StoryResourceTintStore.None,
                )
            } else {
                storyHeaderTintPresentation(story, resource, provider, mode, base, StoryResourceTintStore.None)
            }
            assertEquals(faviconTint, presentation(null).initialTintArgb)
            assertEquals(faviconTint, presentation(pending).initialTintArgb)
            assertEquals(StoryResourceTintKind.FAVICON, presentation(pending).initialTintKind)
            assertEquals(imageTint, presentation(ready).initialTintArgb)
            assertEquals(StoryResourceTintKind.PREVIEW_IMAGE, presentation(ready).initialTintKind)
            assertEquals(faviconTint, presentation(ready.copy(imageLoadFailed = true)).initialTintArgb)

            // An old preview palette must not displace the favicon while a replacement loads.
            assertEquals(faviconTint, presentation(ready.copy(imageUrl = "$imageUrl?new")).initialTintArgb)
        }
    }

    @Test
    fun pendingPreviewDoesNotReviveFaviconTintFromAnotherTheme() {
        val story = Story("Tint transition", 42, true, false).apply {
            url = "https://example.com/story"
            previewImageUrl = imageUrl
        }
        StoryPreviewTintState.applyFavicon(
            story, FaviconUrlBuilder.faviconUrl(story.url!!, provider), base + 1, mode, faviconTint,
        )
        assertNull(storyHeaderTintPresentation(
            story, null, provider, mode, base, StoryResourceTintStore.None,
        ).initialTintArgb)
    }
}
