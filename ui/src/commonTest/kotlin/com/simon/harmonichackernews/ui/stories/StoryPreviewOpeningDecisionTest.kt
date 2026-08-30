package com.simon.harmonichackernews.ui.stories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoryPreviewOpeningDecisionTest {
    @Test
    fun waitsForUsableSnapshotsAndTargetBounds() {
        assertNull(
            storyPreviewOpeningDecision(
                current = null,
                snapshotsReady = true,
                snapshotsUnavailable = false,
                hasTargetBounds = false,
                dismissRequested = false,
            ),
        )
        assertEquals(
            StoryPreviewOpeningDecision.Animate,
            storyPreviewOpeningDecision(
                current = null,
                snapshotsReady = true,
                snapshotsUnavailable = false,
                hasTargetBounds = true,
                dismissRequested = false,
            ),
        )
    }

    @Test
    fun keepsAnimationDecisionWhileEnrichedContentReplacesLayers() {
        assertEquals(
            StoryPreviewOpeningDecision.Animate,
            storyPreviewOpeningDecision(
                current = StoryPreviewOpeningDecision.Animate,
                snapshotsReady = false,
                snapshotsUnavailable = true,
                hasTargetBounds = true,
                dismissRequested = false,
            ),
        )
    }

    @Test
    fun snapsOpenOnlyWhenInitialSnapshotsCannotBeCaptured() {
        assertEquals(
            StoryPreviewOpeningDecision.SnapToOpen,
            storyPreviewOpeningDecision(
                current = null,
                snapshotsReady = false,
                snapshotsUnavailable = true,
                hasTargetBounds = true,
                dismissRequested = false,
            ),
        )
    }

    @Test
    fun ignoresUnavailableSnapshotForEmptyOptionalLayer() {
        assertEquals(
            false,
            storyPreviewOptionalSnapshotUnavailable(
                layerHasContent = false,
                snapshotUnavailable = true,
            ),
        )
        assertEquals(
            true,
            storyPreviewOptionalSnapshotUnavailable(
                layerHasContent = true,
                snapshotUnavailable = true,
            ),
        )
    }
}
