package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.ui.navigation.PredictiveNavigationBackHandler
import kotlinx.coroutines.CancellationException

@Composable
fun SearchLinkPreviewBackHandler(controller: CommentsScreenController) {
    PredictiveNavigationBackHandler(enabled = controller.linkPreviewOverlay != null) { events ->
        var predictiveBackStarted = false
        try {
            events.collect { event ->
                if (predictiveBackStarted) {
                    controller.updateLinkPreviewPredictiveBack(
                        event.progress,
                        event.swipeEdge,
                        event.touchY,
                    )
                } else {
                    predictiveBackStarted = true
                    controller.startLinkPreviewPredictiveBack(
                        event.progress,
                        event.swipeEdge,
                        event.touchY,
                    )
                }
            }
            if (predictiveBackStarted) {
                controller.commitLinkPreviewPredictiveBack()
            } else {
                controller.requestDismissLinkPreview()
            }
        } catch (_: CancellationException) {
            if (predictiveBackStarted) controller.cancelLinkPreviewPredictiveBack()
        }
    }
}
