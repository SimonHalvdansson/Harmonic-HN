package com.simon.harmonichackernews.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onSizeChanged
import com.simon.harmonichackernews.presentation.ReadAloudState
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.cardBackground
import javax.swing.SwingUtilities
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadAloudMiniPlayerTest {
    @Test
    fun opaquePlayerAnimatesVisibilityAndDispatchesPlaybackActions() = SwingUtilities.invokeAndWait {
        for (dark in listOf(false, true)) {
            val palette = HarmonicThemeCatalog.resolve(if (dark) "dark" else "light", dark)
            val state = mutableStateOf(ReadAloudState())
            val enabled = mutableStateOf(true)
            val actions = mutableListOf<String>()
            var height = 0
            val scene = ImageComposeScene(width = 400, height = 200)
            try {
                scene.setContent {
                    HarmonicTheme(palette.colorScheme, palette.dark) {
                        Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                            ReadAloudMiniPlayer(
                                state = state.value,
                                enabled = enabled.value,
                                modifier = Modifier.onSizeChanged { height = it.height },
                                onAction = { actions += it.getValue("action").jsonPrimitive.content },
                            )
                        }
                    }
                }
                var time = 0L
                fun frame(): Color {
                    time += 16_000_000
                    return scene.render(time).use { it.toComposeImageBitmap().toPixelMap()[5, 10] }
                }
                fun settle() { repeat(30) { frame() } }
                fun click(x: Float) {
                    val point = Offset(x, height / 2f)
                    scene.sendPointerEvent(PointerEventType.Press, point)
                    scene.sendPointerEvent(PointerEventType.Release, point)
                    settle()
                }
                settle()
                assertEquals(0, height)
                state.value = ReadAloudState(title = "A reader article", playing = true, count = 12)
                repeat(6) { frame() }
                val enteringHeight = height
                settle()
                val fullHeight = height
                assertTrue(enteringHeight in 1 until fullHeight, "Showing must animate the reserved height")
                assertEquals(palette.colorScheme.cardBackground, frame(), "Web content must not show through")
                click(316f)
                assertEquals("pause", actions.last())
                state.value = state.value.copy(playing = false)
                settle()
                click(316f)
                assertEquals("play", actions.last())

                enabled.value = false
                repeat(6) { frame() }
                assertTrue(height in 1 until fullHeight, "Hiding the preference must animate out")
                // Reversing a hide must keep the same article and restore its controls.
                enabled.value = true
                settle()
                assertEquals(fullHeight, height)
                click(368f)
                assertEquals("stop", actions.last())
                // Sample the resting surface, outside the pointer hover indication.
                scene.sendPointerEvent(PointerEventType.Move, Offset(200f, 185f))
                settle()
                state.value = ReadAloudState()
                repeat(6) { frame() }
                assertTrue(height in 1 until fullHeight, "Clearing playback must retain the player during exit")
                assertEquals(palette.colorScheme.cardBackground, frame())
                settle()
                assertEquals(0, height)
                assertEquals(Color.Magenta, frame())
            } finally {
                scene.close()
            }
        }
    }
}
