package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.network.StoryPreviewResourceState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryPreviewCardStateTest {
    @Test
    fun unresolvedLinkReservesImageSpaceUntilMetadataArrives() {
        assertTrue(
            shouldReserveStoryPreviewImage(
                canLoadLinkPreview = true,
                displayedImageUrl = null,
                imageLoadFailed = false,
                previewResource = null,
            ),
        )
    }

    @Test
    fun confirmedImageMissCollapsesReservedSpace() {
        assertFalse(
            shouldReserveStoryPreviewImage(
                canLoadLinkPreview = true,
                displayedImageUrl = null,
                imageLoadFailed = false,
                previewResource = StoryPreviewResourceState(
                    storyId = 42,
                    pageUrl = "https://example.com/article",
                    imageUrlResolved = true,
                ),
            ),
        )
    }

    @Test
    fun contentOrDrawableFailureDoesNotLeaveAnInfinitePlaceholder() {
        assertFalse(
            shouldReserveStoryPreviewImage(
                canLoadLinkPreview = true,
                displayedImageUrl = null,
                imageLoadFailed = false,
                previewResource = StoryPreviewResourceState(
                    storyId = 42,
                    pageUrl = "https://example.com/article",
                    contentLoadFailed = true,
                ),
            ),
        )
        assertFalse(
            shouldReserveStoryPreviewImage(
                canLoadLinkPreview = true,
                displayedImageUrl = null,
                imageLoadFailed = true,
                previewResource = null,
            ),
        )
    }
}
