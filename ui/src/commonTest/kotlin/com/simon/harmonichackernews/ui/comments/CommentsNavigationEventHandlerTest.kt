package com.simon.harmonichackernews.ui.comments

import androidx.navigationevent.NavigationEvent
import com.simon.harmonichackernews.presentation.CommentsBackTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentsNavigationEventHandlerTest {
    @Test fun browserChangesDuringAGestureDoNotChangeItsDestination() {
        var target = CommentsBackTarget.CLOSE_WEBSITE
        val calls = mutableListOf<String>()
        val handler = CommentsNavigationEventHandler(
            target = { target }, controller = { null },
            onWebsiteGestureProgress = { progress, _ -> calls += "preview:$progress" },
            onWebsiteGestureFinished = { calls += "cancel" },
            onHideCustomView = {}, onDisableReaderMode = {},
            onWebHistoryBack = { calls += "history" }, onCloseStory = {},
        )
        handler.onBackStarted(event(0f))
        target = CommentsBackTarget.WEB_HISTORY
        handler.onBackProgressed(event(0.6f))
        handler.onBackCancelled()
        handler.onBackCompleted()
        assertEquals(listOf("preview:0.0", "preview:0.6", "cancel", "history"), calls)
    }

    private fun event(progress: Float) = NavigationEvent(
        touchX = 100f * progress, touchY = 400f, progress = progress,
        swipeEdge = NavigationEvent.EDGE_LEFT,
    )
}
