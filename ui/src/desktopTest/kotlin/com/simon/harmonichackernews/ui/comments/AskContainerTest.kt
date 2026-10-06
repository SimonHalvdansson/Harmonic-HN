package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AskContainerTest {
    @Test
    fun touchSwipeStartingOnPlainMessageScrolls() = SwingUtilities.invokeAndWait {
        val scroll = ScrollState(0)
        val scene = ImageComposeScene(400, 300, Density(1f)) {
            AskContainer(Rect.Zero, 1f, Color.White, rememberGraphicsLayer()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    repeat(12) {
                        BasicText("My question $it", Modifier.fillMaxWidth().height(80.dp)
                            .background(Color.LightGray))
                    }
                }
            }
        }
        var time = 0L
        fun frame() { scene.render(time.also { time += 16_000_000 }).close() }
        fun pointer(type: PointerEventType, y: Float) {
            scene.sendPointerEvent(type, Offset(200f, y), timeMillis = time / 1_000_000,
                type = PointerType.Touch)
            frame()
        }
        try {
            repeat(5) { frame() }
            pointer(PointerEventType.Press, 220f)
            repeat(8) { pointer(PointerEventType.Move, 220f - (it + 1) * 20f) }
            pointer(PointerEventType.Release, 60f)
            assertTrue(scroll.value > 80, "A swipe on a sent message must scroll the conversation")
        } finally {
            scene.close()
        }
    }

    @Test
    fun predictiveShrinkFadesForegroundAndCancelRestoresIt() = SwingUtilities.invokeAndWait {
        val progress = mutableFloatStateOf(1f)
        val scene = ImageComposeScene(400, 300, Density(1f)) {
            Box(Modifier.fillMaxSize().background(Color.Red)) {
                AskContainer(Rect(60f, 80f, 340f, 220f), progress.floatValue,
                    Color.White, rememberGraphicsLayer()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(100.dp).background(Color.Black))
                    }
                }
            }
        }
        var time = 0L
        fun frame() = scene.render(time.also { time += 16_000_000 }).use {
            it.toComposeImageBitmap().toPixelMap()
        }
        try {
            repeat(5) { frame() }
            assertEquals(Color.Black, frame()[200, 150])
            progress.floatValue = 0.65f
            repeat(5) { frame() }
            val preview = frame()
            assertTrue(preview[200, 150].red in 0.35f..0.50f,
                "Shrinking content must visibly fade against its surface")
            assertEquals(Color.White, preview[90, 110], "The surface must remain opaque")
            progress.floatValue = 1f
            repeat(5) { frame() }
            assertEquals(Color.Black, frame()[200, 150])
        } finally {
            scene.close()
        }
    }
}
