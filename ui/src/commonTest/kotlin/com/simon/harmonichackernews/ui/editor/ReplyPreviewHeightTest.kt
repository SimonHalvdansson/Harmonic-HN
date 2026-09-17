package com.simon.harmonichackernews.ui.editor

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplyPreviewHeightTest {
    @Test
    fun roomyEditorPreservesExistingPreview() {
        assertEquals(180.dp, replyPreviewHeight(500.dp, 180.dp, 144.dp))
        assertEquals(112.dp, replyPreviewHeight(500.dp, 112.dp, 144.dp))
        assertEquals(180.dp, replyPreviewHeight(324.dp, 180.dp, 144.dp))
    }

    @Test
    fun constrainedEditorUsesDialogInsteadOfClippedPreview() {
        assertEquals(0.dp, replyPreviewHeight(220.dp, 180.dp, 144.dp))
    }

    @Test
    fun veryShortEditorGivesAllAvailableSpaceToReply() {
        assertEquals(0.dp, replyPreviewHeight(100.dp, 180.dp, 144.dp))
        assertEquals(0.dp, replyPreviewHeight(0.dp, 180.dp, 144.dp))
    }

    @Test
    fun readablePreviewStillFitsAlongsideReply() {
        assertEquals(112.dp, replyPreviewHeight(256.dp, 180.dp, 144.dp))
        assertEquals(0.dp, replyPreviewHeight(255.dp, 180.dp, 144.dp))
    }

    @Test
    fun largerTextGetsMoreSpace() {
        assertEquals(0.dp, replyPreviewHeight(220.dp, 180.dp, 192.dp))
    }
}
