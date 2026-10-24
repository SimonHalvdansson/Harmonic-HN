package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.ui.navigation.BrowserPaneState
import com.simon.harmonichackernews.ui.navigation.LocalBrowserPaneState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlin.math.roundToInt

/** One live composition and list state, with complementary slices during a sheet transfer. */
internal class SideBySideCommentsPortal {
    var controller by mutableStateOf<CommentsScreenController?>(null)
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    var contentSize by mutableStateOf(IntSize.Zero)
    var sheetExpansion by mutableFloatStateOf(1f)
    var liveInLeft by mutableStateOf(false)
    var leftSnapshot by mutableStateOf<ImageBitmap?>(null)
    var rightSnapshot by mutableStateOf<ImageBitmap?>(null)
    var horizontalBack by mutableStateOf(false)
    val horizontalProgress = Animatable(0f)
    var leftWidth by mutableFloatStateOf(0f)
    var hostLeft by mutableFloatStateOf(0f)
    var detailLeft by mutableFloatStateOf(0f)
}

internal val LocalSideBySideCommentsPortal = compositionLocalOf<SideBySideCommentsPortal?> { null }

// The gesture previews the destination; releasing is what completes the pane transfer.
private const val SideBySideBackPreviewTravel = 0.35f

@Composable
internal fun SideBySideCommentsHost(standalone: Boolean = false, content: @Composable (listOverlay: @Composable () -> Unit) -> Unit) {
    val portal = remember { SideBySideCommentsPortal() }
    val browserPane = remember(standalone) { BrowserPaneState(standalone) }
    val controller = portal.controller
    val predictive = controller?.predictiveBackActive == true && controller.sideBySideActive && !standalone
    val committed = controller?.committedSheetBackProgress ?: 0f
    LaunchedEffect(predictive, controller?.predictiveBackProgress, committed, controller?.sideBySideActive) {
        if (predictive) {
            portal.horizontalBack = true
            portal.horizontalProgress.snapTo(
                controller.predictiveBackProgress.coerceIn(0f, 1f) * SideBySideBackPreviewTravel,
            )
        } else if (portal.horizontalBack || committed > 0f && !standalone) {
            if (controller?.sideBySideActive != true) {
                portal.horizontalBack = false
                portal.horizontalProgress.snapTo(0f)
                return@LaunchedEffect
            }
            // A complete gesture can arrive between two Compose frames.
            if (!portal.horizontalBack) {
                portal.horizontalBack = true
                portal.horizontalProgress.snapTo(committed.coerceIn(0f, 1f) * SideBySideBackPreviewTravel)
            }
            portal.horizontalProgress.animateTo(
                if (committed > 0f) 1f else 0f,
                tween(250, easing = FastOutSlowInEasing),
            )
            if (committed == 0f) portal.horizontalBack = false
        }
    }
    val horizontalBack = portal.horizontalBack
    SideEffect {
        browserPane.backSlideActive = horizontalBack
        browserPane.backSlideProgress = { portal.horizontalProgress.value }
        browserPane.backSlideDistance = {
            browserPane.splitPosition?.invoke() ?: (portal.detailLeft - portal.hostLeft)
        }
    }
    CompositionLocalProvider(
        LocalSideBySideCommentsPortal provides portal,
        LocalBrowserPaneState provides browserPane,
    ) {
        Box(Modifier.fillMaxSize().clipToBounds().onGloballyPositioned { portal.hostLeft = it.positionInRoot().x }) {
            content { SideBySideCommentsListPane(portal) }
            if (portal.horizontalBack) {
                // The same movable list crosses the divider; it is never replaced by a preview.
                Box(Modifier.layout { measurable, constraints ->
                    val progress = portal.horizontalProgress.value
                    val destinationX = browserPane.backSlideDistance()
                    val destinationWidth = constraints.maxWidth - destinationX
                    val width = (portal.leftWidth + (destinationWidth - portal.leftWidth) * progress)
                        .roundToInt().coerceAtLeast(1)
                    val child = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        child.place((destinationX * progress).roundToInt(), 0)
                    }
                }) { portal.content?.invoke() }
            }
        }
    }
}

@Composable
private fun SideBySideCommentsListPane(portal: SideBySideCommentsPortal) {
    val controller = portal.controller ?: return
    if (!controller.sideBySideActive || controller.webViewFullscreen) return
    val standalone = LocalBrowserPaneState.current?.standalone == true
    val reveal = remember(controller) { Animatable(if (portal.sheetExpansion < 0.001f) 0f else 1f) }
    LaunchedEffect(reveal, portal.liveInLeft) {
        if (portal.liveInLeft) reveal.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier.fillMaxSize().zIndex(10f).onSizeChanged { portal.leftWidth = it.width.toFloat() }
            .clipToBounds()
            .graphicsLayer { translationX = if (standalone) 0f else size.width * (1f - reveal.value) }
            .then(if (portal.sheetExpansion > 0.001f) {
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }.clearAndSetSemantics { }
            } else Modifier)
            .drawWithContent {
                clipRect(top = if (standalone) 0f else size.height * portal.sheetExpansion.coerceIn(0f, 1f)) {
                    this@drawWithContent.drawContent()
                }
            },
    ) {
        if (portal.horizontalBack) Unit
        else if (portal.liveInLeft) portal.content?.invoke()
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
    val leftSettled = portal?.liveInLeft == true && portal.sheetExpansion <= 0.001f
    SideEffect {
        if (sheetCollapsed) entering = false
        // The departing sheet snapshot is only for lowering into split mode. Retaining it
        // would flash its fully tinted header while the next pull captures the left pane.
        if (sheetCollapsed && leftSettled) portal?.rightSnapshot = null
    }
    val targetLeft = controller.sideBySideActive &&
        (portal?.horizontalBack == true || entering || (portal?.sheetExpansion ?: 1f) <= 0.001f)
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
        // A fully collapsed pane has no drawable layer. It needs no departing snapshot.
        if (!portal.horizontalBack && controller.sideBySideActive && layer.size.width > 0 && layer.size.height > 0) {
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
        if (portal.horizontalBack) Unit
        else if (portal.liveInLeft) PaneSnapshot(portal.rightSnapshot)
        else portal.content?.invoke()
    }
}
