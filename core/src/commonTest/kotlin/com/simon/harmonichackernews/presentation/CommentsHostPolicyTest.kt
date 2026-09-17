package com.simon.harmonichackernews.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class CommentsHostPolicyTest {
    @Test
    fun retainedCommentsOverlayDoesNotClaimBackWhileAnotherDestinationIsActive() {
        assertEquals(
            CommentsBackTarget.NONE,
            CommentsBackPolicy.target(
                CommentsBackContext(
                    hostActive = false,
                    linkPreviewVisible = false,
                    commentActionVisible = true,
                    customWebContentVisible = false,
                    readerModeEnabled = false,
                    websiteVisible = false,
                    webHistoryAvailable = false,
                    closeWebsiteOnBack = false,
                ),
            ),
        )
    }

    @Test
    fun activeCommentsOverlayStillHasPriorityWithinTheCommentsDestination() {
        assertEquals(
            CommentsBackTarget.COMMENT_ACTION,
            CommentsBackPolicy.target(
                CommentsBackContext(
                    hostActive = true,
                    linkPreviewVisible = false,
                    commentActionVisible = true,
                    customWebContentVisible = false,
                    readerModeEnabled = false,
                    websiteVisible = false,
                    webHistoryAvailable = false,
                    closeWebsiteOnBack = false,
                ),
            ),
        )
    }
}
