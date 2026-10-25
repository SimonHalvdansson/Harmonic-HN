package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.pageBackground
import java.io.File
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoriesRailMenuWelcomeHintTest {
    @Test
    fun sideCaretAndScrollableContentFitDifferentRailWindowsAndTextSizes() = SwingUtilities.invokeAndWait {
        for ((width, height, rail, anchorTop, fontScale) in listOf(
            Window(466, 678, 84, 168), Window(678, 466, 84, 168),
            Window(360, 300, 72, 120, 1.6f), Window(600, 240, 112, 110, 2f),
        )) for (dark in listOf(false, true)) {
            var visible by mutableStateOf(true)
            val palette = HarmonicThemeCatalog.scheme("dynamic", dark)
            val scene = ImageComposeScene(width, height, Density(1f, fontScale)) {
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    Box(Modifier.fillMaxSize().background(palette.colorScheme.pageBackground)) {
                        Box(Modifier.align(AbsoluteAlignment.TopRight).width(rail.dp)
                            .padding(end = 12.dp, top = anchorTop.dp), contentAlignment = Alignment.TopCenter) {
                            StoriesMenuWelcomeHint(
                                visible = visible,
                                onDismiss = { visible = false },
                                contentInsets = WindowInsets(top = 2, right = rail, bottom = 8),
                            ) {
                                IconButton(onClick = {}, modifier = Modifier.size(56.dp)
                                    .semantics { contentDescription = "More options" }) { Text("Menu") }
                            }
                        }
                    }
                }
            }
            try {
                var time = 0L
                fun render() { scene.render(time.also { time += 16_666_667L }).close() }
                fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
                fun allNodes() = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
                fun hints() = allNodes().filter { it.config.getOrNull(SemanticsProperties.TestTag) == "stories-welcome-hint" }
                render()
                val deadline = System.nanoTime() + 3_000_000_000L
                while (hints().isEmpty() && System.nanoTime() < deadline) { Thread.sleep(10); render() }
                assertTrue(hints().isNotEmpty())
                repeat(30) { render() }
                val hint = hints().single().boundsInWindow
                val menu = allNodes().first {
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("More options") == true
                }.boundsInWindow
                assertTrue(hint.left >= 16 && hint.right <= width - rail - 16,
                    "Keep the card in safe content: $hint")
                assertTrue(hint.top >= 18 && hint.bottom <= height - 24, "Fit the visible height: $hint")
                assertTrue(hint.right < menu.left && menu.center.y in hint.top + 20..hint.bottom - 20,
                    "Put the card beside the menu with room for its caret: hint=$hint, menu=$menu")
                val initialAction = allNodes().single {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Got it" } == true
                }.boundsInWindow
                assertTrue(initialAction.height > 0 && initialAction.top >= hint.top && initialAction.bottom <= hint.bottom,
                    "Keep the dismiss action visible even before scrolling large text")
                scene.render(time).use { image ->
                    val tip = image.toComposeImageBitmap().toPixelMap()[(hint.right + 2).toInt(), menu.center.y.toInt()]
                    val container = palette.colorScheme.surfaceContainer
                    assertTrue(abs(tip.red - container.red) + abs(tip.green - container.green) +
                        abs(tip.blue - container.blue) < .1f, "The caret must point horizontally at the rail menu")
                }
                System.getenv("HARMONIC_HINT_CAPTURE_DIR")?.let { directory ->
                    scene.render(time).use { image -> image.encodeToData()?.use { data ->
                        File(directory).mkdirs()
                        File(directory, "rail-hint-${width}x$height-$fontScale-$dark.png").writeBytes(data.bytes)
                    } }
                }
                // A short display can scroll its large text while keeping the action visible.
                repeat(3) {
                    scene.sendPointerEvent(PointerEventType.Scroll, hint.center, scrollDelta = Offset(0f, 1000f))
                    repeat(30) { render() }
                }
                val action = allNodes().single {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Got it" } == true
                }.boundsInWindow
                assertTrue(action.height > 0 && action.top >= hint.top && action.bottom <= hint.bottom)
                scene.sendPointerEvent(PointerEventType.Press, action.center)
                scene.sendPointerEvent(PointerEventType.Release, action.center)
                repeat(30) { render() }
                assertFalse(visible, "The action remains reachable after scrolling")
            } finally {
                scene.close()
            }
        }
    }

    private data class Window(val width: Int, val height: Int, val rail: Int, val anchorTop: Int, val fontScale: Float = 1f)
}
