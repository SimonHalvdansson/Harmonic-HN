package com.simon.harmonichackernews.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Count actual content drawing while independently changing the retained pages' pixels. */
class NavigationCoveredDrawingTest {
    @Test
    fun coveredPagesStopDrawingAndPredictiveBackRevealsTheRetainedUpdatedParent() = SwingUtilities.invokeAndWait {
        var entries by mutableStateOf(emptyList<String>())
        var preview by mutableStateOf<ActivityBackPreview<String>?>(null)
        var completed by mutableStateOf(false)
        val tick = mutableIntStateOf(0)
        val colors = mutableStateOf(mapOf("root" to Color.Red, "first" to Color.Green, "second" to Color.Blue))
        val draws = mutableMapOf<String, Int>()
        val identities = mutableMapOf<String, Any>()
        val scene = ImageComposeScene(300, 600, Density(1f)) {
            Theme {
                ActivityNavigationStack(
                    entries, { it }, preview = preview, completedPredictiveBack = completed,
                    hideCoveredRoot = true,
                    root = { Probe("root", colors.value.getValue("root"), tick, draws, identities) },
                    content = { Probe(it, colors.value.getValue(it), tick, draws, identities) },
                )
            }
        }
        var time = 0L
        fun frames(count: Int = 40) = repeat(count) {
            tick.intValue++
            scene.render(time.also { time += 16_666_667L }).close()
        }
        fun pixel(x: Int = 150): Int = scene.render(time.also { time += 16_666_667L }).use { image ->
            IntArray(300 * 600).also { image.toComposeImageBitmap().readPixels(it) }[300 * 300 + x]
        }
        try {
            frames()
            entries = listOf("first")
            frames()
            val rootDraws = draws.getValue("root")
            val firstIdentity = identities.getValue("first")
            entries = listOf("first", "second")
            frames(6)
            assertEquals(rootDraws, draws.getValue("root"), "Settled first page covers root during the next open")
            frames()
            val coveredFirstDraws = draws.getValue("first")
            frames(8)
            assertEquals(coveredFirstDraws, draws.getValue("first"), "Covered retained page must stop drawing")
            assertEquals(0xff0000ff.toInt(), pixel())

            // State can change while retained. The preview must draw current content without
            // replacing the original composition, including after a canceled gesture.
            colors.value = colors.value + ("first" to Color.Magenta)
            frames(4)
            assertEquals(coveredFirstDraws, draws.getValue("first"))
            preview = ActivityBackPreview("second", "first", Modifier, Modifier.padding(start = 80.dp))
            frames(4)
            assertTrue(draws.getValue("first") > coveredFirstDraws)
            assertEquals(0xffff00ff.toInt(), pixel(20))
            assertSame(firstIdentity, identities.getValue("first"))
            preview = null
            frames(4)
            val canceledDraws = draws.getValue("first")
            frames(4)
            assertEquals(canceledDraws, draws.getValue("first"))
            assertEquals(0xff0000ff.toInt(), pixel())

            preview = ActivityBackPreview("second", "first", Modifier, Modifier.padding(start = 80.dp))
            frames(4)
            entries = listOf("first")
            completed = true
            frames(4)
            preview = null
            completed = false
            frames()
            assertEquals(0xffff00ff.toInt(), pixel())
            assertSame(firstIdentity, identities.getValue("first"))
            entries = emptyList()
            frames()
            assertEquals(0xffff0000.toInt(), pixel())
        } finally { scene.close() }
    }

    @Test
    fun fadeCoversOnlyAfterSettlingAndCustomSurfacesKeepTheirPredecessorDrawing() = SwingUtilities.invokeAndWait {
        var entries by mutableStateOf(listOf("first"))
        val tick = mutableIntStateOf(0)
        val draws = mutableMapOf<String, Int>()
        val identities = mutableMapOf<String, Any>()
        val scene = ImageComposeScene(300, 600, Density(1f)) {
            Theme {
                ActivityNavigationStack(
                    entries, { it }, hideCoveredRoot = true, fadeOnly = { it == "fade" },
                    surfaceTransitions = mapOf("custom" to { _, _, content ->
                        Box(Modifier.fillMaxSize().padding(end = 40.dp)) { content() }
                    }),
                    root = { Probe("root", Color.Red, tick, draws, identities) },
                    content = { Probe(it, if (it == "first") Color.Green else Color.Blue, tick, draws, identities) },
                )
            }
        }
        var time = 0L
        fun frames(count: Int = 40) = repeat(count) {
            tick.intValue++
            scene.render(time.also { time += 16_666_667L }).close()
        }
        fun pixel(x: Int): Int = scene.render(time.also { time += 16_666_667L }).use { image ->
            IntArray(300 * 600).also { image.toComposeImageBitmap().readPixels(it) }[300 * 300 + x]
        }
        try {
            frames()
            entries = listOf("first", "fade")
            val beforeFade = draws.getValue("first")
            frames(6)
            assertTrue(draws.getValue("first") > beforeFade, "Parent draws while the covering fade is translucent")
            frames()
            val settledFade = draws.getValue("first")
            frames(6)
            assertEquals(settledFade, draws.getValue("first"))
            assertEquals(0xff0000ff.toInt(), pixel(280))

            // Remove multiple entries and open a custom partial surface in one update.
            // Removed settled layers must not count as covers before reconciliation.
            entries = listOf("first", "custom")
            frames(6)
            frames()
            val beforeCustomFrames = draws.getValue("first")
            frames(6)
            assertTrue(draws.getValue("first") > beforeCustomFrames)
            assertEquals(0xff00ff00.toInt(), pixel(280))
            assertEquals(0xff0000ff.toInt(), pixel(20))
            entries = emptyList()
            frames(2)
            frames()
            assertEquals(0xffff0000.toInt(), pixel(150))
        } finally { scene.close() }
    }

    @Composable
    private fun Probe(
        id: String,
        color: Color,
        tick: State<Int>,
        draws: MutableMap<String, Int>,
        identities: MutableMap<String, Any>,
    ) {
        val identity = remember { Any() }
        SideEffect { identities[id] = identity }
        Box(Modifier.fillMaxSize().drawWithContent {
            tick.value
            draws[id] = draws.getOrElse(id) { 0 } + 1
            drawRect(color)
            drawContent()
        })
    }

    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        val palette = HarmonicThemeCatalog.resolve("material_light", false)
        HarmonicTheme(palette.colorScheme, palette.dark, content = content)
    }
}
