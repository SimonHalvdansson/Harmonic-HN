package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalLayoutApi::class)
class AskLayoutTest {
    @Test
    fun contentDrawsBehindSystemBarWhileComposerAndLastMessageAvoidBarAndKeyboard() = SwingUtilities.invokeAndWait {
        val safeInsets = MutableWindowInsets(WindowInsets(top = 12, bottom = 24))
        val scroll = ScrollState(0)
        val scene = ImageComposeScene(400, 500, Density(1f)) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                AskLayout(
                    header = { Box(Modifier.fillMaxWidth().height(40.dp)) },
                    composer = { modifier ->
                        Box(modifier.fillMaxWidth().height(56.dp).background(Color.Red))
                    },
                    safeInsets = safeInsets,
                ) { bottomPadding ->
                    Column(Modifier.fillMaxSize().background(Color.Blue)
                        .verticalScroll(scroll).padding(bottom = bottomPadding)) {
                        Box(Modifier.fillMaxWidth().height(800.dp))
                        Box(Modifier.fillMaxWidth().height(20.dp).background(Color.Green))
                    }
                }
            }
        }
        var time = 0L
        fun frame() = scene.render(time.also { time += 16_000_000 }).use {
            it.toComposeImageBitmap().toPixelMap()
        }
        try {
            // Model the safe bottom changing from navigation-bar to keyboard height and back.
            for (bottomInset in listOf(24, 180, 24)) {
                safeInsets.insets = WindowInsets(top = 12, bottom = bottomInset)
                repeat(5) { frame() }
                runBlocking { scroll.scrollTo(scroll.maxValue) }
                repeat(3) { frame() }
                val pixels = frame()
                val composerBottom = 500 - bottomInset
                val composerTop = composerBottom - 56
                assertEquals(Color.Red, pixels[200, composerBottom - 1], "Prompt must stay above the inset")
                assertEquals(Color.Green, pixels[200, composerTop - 1], "Last message must clear the prompt")
                assertEquals(Color.Blue, pixels[200, 499], "Viewport must draw through the bottom inset")
            }
            runBlocking { scroll.scrollTo(scroll.maxValue - 80) }
            repeat(3) { frame() }
            assertEquals(Color.Green, frame()[200, 490], "Messages must be able to scroll behind the system bar")
        } finally {
            scene.close()
        }
    }
}
