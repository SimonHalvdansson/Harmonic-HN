package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass

/** One live composition and list state, with complementary slices during a sheet transfer. */
internal class SideBySideCommentsPortal {
    var controller by mutableStateOf<CommentsScreenController?>(null)
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    var contentSize by mutableStateOf(IntSize.Zero)
    var sheetExpansion by mutableFloatStateOf(1f)
    var liveInLeft by mutableStateOf(false)
    var leftSnapshot by mutableStateOf<ImageBitmap?>(null)
    var rightSnapshot by mutableStateOf<ImageBitmap?>(null)
}

internal val LocalSideBySideCommentsPortal = compositionLocalOf<SideBySideCommentsPortal?> { null }

@Composable
internal fun SideBySideCommentsHost(content: @Composable (listOverlay: @Composable () -> Unit) -> Unit) {
    val portal = remember { SideBySideCommentsPortal() }
    CompositionLocalProvider(LocalSideBySideCommentsPortal provides portal) {
        content { SideBySideCommentsListPane(portal) }
    }
}

@Composable
private fun SideBySideCommentsListPane(portal: SideBySideCommentsPortal) {
    val controller = portal.controller ?: return
    if (!controller.sideBySideActive || controller.webViewFullscreen) return
    val reveal = remember(controller) { Animatable(if (portal.sheetExpansion < 0.001f) 0f else 1f) }
    LaunchedEffect(reveal, portal.liveInLeft) {
        if (portal.liveInLeft) reveal.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier.fillMaxSize().zIndex(10f)
            .clipToBounds()
            .graphicsLayer { translationX = size.width * (1f - reveal.value) }
            .then(if (portal.sheetExpansion > 0.001f) {
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }.clearAndSetSemantics { }
            } else Modifier)
            .drawWithContent {
                clipRect(top = size.height * portal.sheetExpansion.coerceIn(0f, 1f)) {
                    this@drawWithContent.drawContent()
                }
            },
    ) {
        if (portal.liveInLeft) portal.content?.invoke()
        else PaneSnapshot(portal.leftSnapshot)
    }
}

/** Retains local row/menu state and the lazy-list position when its outlet changes. */
@Composable
internal fun rememberSideBySideCommentsContent(
    controller: CommentsScreenController,
    content: @Composable () -> Unit,
): @Composable () -> Unit {
    val portal = LocalSideBySideCommentsPortal.current
    val layer = rememberGraphicsLayer()
    val currentContent = androidx.compose.runtime.rememberUpdatedState(content)
    val retained = remember(controller) {
        movableContentOf {
            Box(
                Modifier.fillMaxSize()
                    .onSizeChanged { portal?.contentSize = it }
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) { currentContent.value() }
        }
    }
    var entering by remember(controller, controller.sideBySideActive) {
        mutableStateOf(controller.sideBySideActive && controller.sheetSlideOffset > 0.001f)
    }
    val sheetCollapsed = controller.sheetSlideOffset <= 0.001f
    SideEffect { if (sheetCollapsed) entering = false }
    val targetLeft = controller.sideBySideActive && (entering || (portal?.sheetExpansion ?: 1f) <= 0.001f)
    LaunchedEffect(portal, controller, targetLeft, controller.sideBySideActive) {
        if (portal == null) return@LaunchedEffect
        if (!controller.sideBySideActive) {
            portal.leftSnapshot = null
            portal.rightSnapshot = null
        }
        if (portal.liveInLeft == targetLeft) return@LaunchedEffect
        // Freeze the departing pane at its own width, then move the one live list to the
        // destination. A bitmap is intentional: a recorded layer would retain references to
        // child layers that reflow when the live composition changes panes.
        if (controller.sideBySideActive && portal.contentSize != IntSize.Zero) {
            val snapshot = layer.toImageBitmap()
            if (portal.liveInLeft) {
                portal.leftSnapshot = snapshot
                portal.rightSnapshot = null
            } else {
                portal.rightSnapshot = snapshot
                portal.leftSnapshot = null
            }
        }
        portal.liveInLeft = targetLeft
    }
    SideEffect {
        portal?.controller = controller
        portal?.content = retained
    }
    DisposableEffect(portal, controller) {
        onDispose {
            if (portal?.controller === controller) {
                portal.controller = null
                portal.content = null
                portal.leftSnapshot = null
                portal.rightSnapshot = null
                portal.liveInLeft = false
            }
        }
    }
    return retained
}

@Composable
private fun PaneSnapshot(snapshot: ImageBitmap?) {
    Box(Modifier.fillMaxSize().clearAndSetSemantics { }.drawWithContent {
        snapshot?.let { drawImage(it) }
    })
}

/** Both panes draw at their native widths; only the departing pane uses a frozen image. */
@Composable
internal fun SideBySideCommentsSheetMirror(portal: SideBySideCommentsPortal, peekHeightPx: Float) {
    Box(Modifier.fillMaxSize().clearAndSetSemantics { }.graphicsLayer {
        translationY = peekHeightPx * (1f - portal.sheetExpansion)
    }) {
        if (portal.liveInLeft) PaneSnapshot(portal.rightSnapshot)
        else portal.content?.invoke()
    }
}
