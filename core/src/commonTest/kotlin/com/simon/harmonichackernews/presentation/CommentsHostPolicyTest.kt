package com.simon.harmonichackernews.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class CommentsHostPolicyTest {
    @Test
    fun fullWidthBrowserRestoresTheSplitBeforeClosingOrLeavingTheStory() {
        for (split in listOf(false, true)) for (close in listOf(false, true)) {
            val fullWidth = readingWebsite.copy(
                sideBySideActive = split, closeWebsiteOnBack = close, restoreTwoPaneAvailable = true,
            )
            assertEquals(CommentsBackTarget.RESTORE_TWO_PANE, CommentsBackPolicy.target(fullWidth))
            assertEquals(CommentsBackTarget.LINK_PREVIEW,
                CommentsBackPolicy.target(fullWidth.copy(linkPreviewVisible = true)))
            if (!close) assertEquals(CommentsBackTarget.WEB_HISTORY,
                CommentsBackPolicy.target(fullWidth.copy(webHistoryAvailable = true)))
        }
    }

    private val readingWebsite = CommentsBackContext(
        linkPreviewVisible = false,
        commentActionVisible = false,
        customWebContentVisible = false,
        readerModeEnabled = true,
        websiteVisible = true,
        webHistoryAvailable = false,
        closeWebsiteOnBack = false,
        readerModeDefault = true,
    )

    @Test
    fun sideBySideReturnsToCommentsWhenHistoryIsExhaustedRegardlessOfPreference() {
        for (close in listOf(false, true)) {
            assertEquals(CommentsBackTarget.CLOSE_WEBSITE, CommentsBackPolicy.target(
                readingWebsite.copy(sideBySideActive = true, closeWebsiteOnBack = close),
            ))
        }
    }

    @Test
    fun sideBySideHonorsGoBackToCommentsBeforeWebHistory() {
        assertEquals(CommentsBackTarget.CLOSE_WEBSITE, CommentsBackPolicy.target(
            readingWebsite.copy(sideBySideActive = true, webHistoryAvailable = true, closeWebsiteOnBack = true),
        ))
        assertEquals(CommentsBackTarget.WEB_HISTORY, CommentsBackPolicy.target(
            readingWebsite.copy(sideBySideActive = true, webHistoryAvailable = true, closeWebsiteOnBack = false),
        ))
        assertEquals(CommentsBackTarget.LINK_PREVIEW, CommentsBackPolicy.target(
            readingWebsite.copy(sideBySideActive = true, linkPreviewVisible = true),
        ))
    }

    @Test
    fun defaultReaderModeLetsBackLeaveTheStory() {
        assertEquals(CommentsBackTarget.NONE, CommentsBackPolicy.target(readingWebsite))
    }

    @Test
    fun defaultReaderModeRespectsCloseWebsiteOnBack() {
        assertEquals(
            CommentsBackTarget.CLOSE_WEBSITE,
            CommentsBackPolicy.target(readingWebsite.copy(closeWebsiteOnBack = true)),
        )
    }

    @Test
    fun defaultReaderModeNavigatesWebHistoryBeforeClosingTheWebsite() {
        assertEquals(
            CommentsBackTarget.WEB_HISTORY,
            CommentsBackPolicy.target(
                readingWebsite.copy(webHistoryAvailable = true, closeWebsiteOnBack = true),
            ),
        )
    }

    @Test
    fun manuallyEnabledReaderModeStillExitsBeforeNavigatingBack() {
        assertEquals(
            CommentsBackTarget.READER_MODE,
            CommentsBackPolicy.target(readingWebsite.copy(readerModeDefault = false)),
        )
    }

    @Test
    fun defaultReaderModeStillDismissesOverlaysFirst() {
        assertEquals(
            CommentsBackTarget.LINK_PREVIEW,
            CommentsBackPolicy.target(readingWebsite.copy(linkPreviewVisible = true)),
        )
        assertEquals(
            CommentsBackTarget.COMMENT_ACTION,
            CommentsBackPolicy.target(readingWebsite.copy(commentActionVisible = true)),
        )
        assertEquals(
            CommentsBackTarget.CUSTOM_WEB_CONTENT,
            CommentsBackPolicy.target(readingWebsite.copy(customWebContentVisible = true)),
        )
    }

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
