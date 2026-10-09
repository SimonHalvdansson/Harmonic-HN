package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.summary.AskSource

/** The conversation context is fixed; source pixels, bounds and color follow the live summary. */
data class PostAskState(
    val subject: AskSource.Post,
    val source: GraphicsLayer,
    val bounds: () -> Rect?,
    val color: () -> Color,
) {
    var coveringSource by mutableStateOf(false)
        internal set
}

@Composable
internal fun PostAskOverlay(
    controller: CommentsScreenController,
    settings: CommentDisplaySettings,
    onOpenLink: (String) -> Unit,
) {
    val state = controller.postAsk ?: return
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by rememberAskProgress(entered && controller.askOpen, controller.askBackProgress)
    var rootBounds by remember { mutableStateOf<Rect?>(null) }
    SideEffect {
        controller.askSurfaceVisible = progress > 0f || controller.askOpen
        state.coveringSource = rootBounds != null
    }
    LaunchedEffect(progress, controller.askOpen) {
        if (!controller.askOpen && progress == 0f) controller.completePostAskDismiss()
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { rootBounds = it.boundsInWindow() }) {
        val root = rootBounds ?: return@Box
        AskSurface(
            controller = controller,
            subject = state.subject,
            origin = state.bounds()?.translate(-root.left, -root.top) ?: Rect.Zero,
            progress = progress,
            source = state.source,
            settings = settings,
            color = state.color(),
            onOpenLink = onOpenLink,
        )
    }
}
