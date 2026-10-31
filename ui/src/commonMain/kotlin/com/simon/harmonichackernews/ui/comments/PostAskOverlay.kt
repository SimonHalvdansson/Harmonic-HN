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
import kotlinx.coroutines.flow.first

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
    val progress = rememberAskProgress(entered && controller.askOpen, controller.askBackProgress)
    val visible by remember(progress, controller) { computedStateOf { progress.value > 0f || controller.askOpen } }
    var rootBounds by remember { mutableStateOf<Rect?>(null) }
    SideEffect {
        controller.askSurfaceVisible = visible
        state.coveringSource = rootBounds != null
    }
    LaunchedEffect(controller, progress) {
        snapshotFlow { !controller.askOpen && progress.value == 0f }.first { it }
        controller.completePostAskDismiss()
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { rootBounds = it.boundsInWindow() }) {
        val root = rootBounds ?: return@Box
        AskSurface(
            controller = controller,
            subject = state.subject,
            origin = state.bounds()?.translate(-root.left, -root.top) ?: Rect.Zero,
            progress = { progress.value },
            source = state.source,
            settings = settings,
            color = state.color(),
            onOpenLink = onOpenLink,
        )
    }
}
