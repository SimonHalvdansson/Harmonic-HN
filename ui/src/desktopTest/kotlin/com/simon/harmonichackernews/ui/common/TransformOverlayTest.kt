package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransformOverlayTest {
    @Test
    fun bodyActionsAndOutsideTapsWorkWithoutDismissalFromBlankBody() = SwingUtilities.invokeAndWait {
        var actions = 0
        var dismissals = 0
        val scene = ImageComposeScene(400, 800, Density(1f)) {
            TransformOverlay(
                contentKey = Unit, sourceBounds = null, dismissRequestVersion = 0,
                predictiveBackProgress = 0f, predictiveBackEdge = 0,
                maxWidth = 320.dp, horizontalPadding = 24.dp, verticalPadding = 24.dp,
                targetCornerRadius = 28.dp, containerColor = Color.White,
                onDismissRequest = { dismissals++ }, onDismissAnimationFinished = {},
            ) {
                Box(Modifier.height(200.dp)) {
                    Box(Modifier.size(80.dp).clickable { actions++ })
                }
            }
        }
        var time = 0L
        fun frame() { scene.render(time.also { time += 16_666_667L }).close() }
        fun tap(position: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, position, type = PointerType.Touch)
            frame()
            scene.sendPointerEvent(PointerEventType.Release, position, type = PointerType.Touch)
            frame()
        }
        try {
            repeat(30) { frame() }
            tap(Offset(80f, 340f))
            assertEquals(1, actions)
            assertEquals(0, dismissals)
            tap(Offset(240f, 440f))
            assertEquals(0, dismissals, "Blank dialog content must not dismiss")
            tap(Offset(200f, 100f))
            assertEquals(1, dismissals)
        } finally { scene.close() }
    }

    @Test
    fun sourceIsDrawnBeforeTheOverlayWindowCanBeRemoved() = SwingUtilities.invokeAndWait {
        for ((predictiveProgress, dismissImmediately) in listOf(0f to false, 0.6f to false, 0f to true)) {
            val dismiss = mutableIntStateOf(if (dismissImmediately) 1 else 0)
            val hidden = mutableStateOf(true)
            var restoredDraws = 0
            var completed = 0
            val scene = ImageComposeScene(400, 800, Density(1f)) {
                Box(Modifier.fillMaxSize().drawWithContent {
                    if (!hidden.value) restoredDraws++
                    drawContent()
                })
                TransformOverlay(
                    contentKey = Unit, sourceBounds = Rect(20f, 100f, 380f, 152f),
                    dismissRequestVersion = dismiss.intValue,
                    predictiveBackProgress = if (dismiss.intValue > 0) predictiveProgress else 0f,
                    predictiveBackEdge = 0, maxWidth = 320.dp,
                    horizontalPadding = 24.dp, verticalPadding = 24.dp,
                    targetCornerRadius = 28.dp, containerColor = Color.White,
                    onSourceReadyToReveal = { hidden.value = false },
                    onDismissRequest = { dismiss.intValue++ },
                    onDismissAnimationFinished = {
                        assertTrue(restoredDraws > 0, "Draw the restored source before removing its covering window")
                        completed++
                    },
                ) { Box(Modifier.height(200.dp).background(Color.White)) }
            }
            var time = 0L
            fun frame() { scene.render(time.also { time += 16_666_667L }).close() }
            try {
                repeat(30) { frame() }
                if (!dismissImmediately) dismiss.intValue++
                repeat(30) { frame() }
                assertEquals(1, completed)
                assertTrue(restoredDraws > 0)
            } finally { scene.close() }
        }
    }
}
