package com.simon.harmonichackernews.ui.stories

import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo

class StoryPreviewNavigationEventHandler(
    private val controller: () -> StoriesScreenController?,
) : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, isBackEnabled = false) {
    override fun onBackCancelled() {
        controller()?.takeIf { it.isStoryPreviewShowing() }?.cancelStoryPreviewPredictiveBack()
    }

    override fun onBackProgressed(event: NavigationEvent) {
        controller()?.takeIf { it.isStoryPreviewShowing() }?.updateStoryPreviewPredictiveBack(
            event.progress, event.swipeEdge, event.touchY,
        )
    }

    override fun onBackStarted(event: NavigationEvent) {
        controller()?.takeIf { it.isStoryPreviewShowing() }?.startStoryPreviewPredictiveBack(
            event.progress, event.swipeEdge, event.touchY,
        )
    }

    override fun onBackCompleted() {
        val current = controller()?.takeIf { it.isStoryPreviewShowing() } ?: return
        if (current.isStoryPreviewPredictiveBackActive()) {
            current.commitStoryPreviewPredictiveBack()
        } else {
            current.requestDismissStoryPreview()
        }
    }
}
