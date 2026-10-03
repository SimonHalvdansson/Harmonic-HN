package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WebContentOverlayRenderingTest {
    @Test
    fun downloadCanBeClickedAndLoadingDisappearsAfterCompletion() = SwingUtilities.invokeAndWait {
        for (dark in listOf(false, true)) {
            val state = WebContentOverlayState()
            val palette = HarmonicThemeCatalog.resolve(if (dark) "dark" else "light", dark)
            var clicks = 0
            val scene = ImageComposeScene(width = 400, height = 300)
            try {
                scene.setContent {
                    HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                        Box(Modifier.fillMaxSize().background(Color.White)) {
                            WebContentOverlay(state)
                        }
                    }
                }
                var time = 0L
                fun frame(): Color {
                    time += 16_000_000
                    return scene.render(time).use {
                        it.toComposeImageBitmap().toPixelMap()[100, 2]
                    }
                }
                repeat(10) { frame() }
                assertEquals(Color.White, frame())
                state.showDownload { clicks++ }
                repeat(10) { frame() }
                scene.sendPointerEvent(PointerEventType.Press, Offset(200f, 150f))
                scene.sendPointerEvent(PointerEventType.Release, Offset(200f, 150f))
                repeat(10) { frame() }
                assertEquals(1, clicks)
                state.beginLoad()
                state.updateProgress(60)
                repeat(40) { frame() }
                assertNotEquals(Color.White, frame())
                state.finishLoad(completeProgress = true)
                repeat(10) { frame() }
                assertEquals(Color.White, frame())
                scene.sendPointerEvent(PointerEventType.Press, Offset(200f, 150f))
                scene.sendPointerEvent(PointerEventType.Release, Offset(200f, 150f))
                repeat(10) { frame() }
                assertEquals(1, clicks, "New navigation must remove the old download target")
            } finally {
                scene.close()
            }
        }
    }
}
