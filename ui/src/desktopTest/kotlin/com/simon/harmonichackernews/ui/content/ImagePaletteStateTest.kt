package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.palette.HarmonicPaletteExtractor
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Real painter sampling and background extraction, without network images or persisted caches. */
class ImagePaletteStateTest {
    @Test
    fun disabledAndMissingPaintersStayEmptyAndInputChangesProduceExactTints() = runBlocking {
        val red = CountingPainter(Color.Red)
        val blue = CountingPainter(Color.Blue)
        val enabled = mutableStateOf(false)
        val painter = mutableStateOf<Painter?>(red)
        val base = mutableStateOf(0xffffffff.toInt())
        val config = mutableStateOf("default")
        var tint: Int? = null
        val scene = onEdt {
            ImageComposeScene(96, 96, Density(1f)) {
                val result = rememberPainterPaletteTint(painter.value, base.value, config.value, enabled.value)
                SideEffect { tint = result }
            }
        }
        var time = 0L
        suspend fun frame() {
            onEdt { scene.render(time.also { time += 16_666_667L }).close() }
            delay(1)
        }
        suspend fun awaitTint(expected: Int) = withTimeout(5_000) {
            while (onEdt { tint } != expected) frame()
        }
        try {
            repeat(4) { frame() }
            assertEquals(0, red.draws.get())
            assertNull(onEdt { tint })
            onEdt { enabled.value = true }
            awaitTint(expected(Color.Red, base.value, config.value))
            onEdt { painter.value = blue; base.value = 0xff202020.toInt(); config.value = "muted" }
            awaitTint(expected(Color.Blue, base.value, config.value))
            onEdt { painter.value = null }
            frame()
            assertNull(onEdt { tint })
            onEdt { painter.value = red }
            awaitTint(expected(Color.Red, base.value, config.value))
            onEdt { enabled.value = false }
            frame()
            assertNull(onEdt { tint })
        } finally { onEdt { scene.close() } }
    }

    @Test
    fun disablingCancelsQueuedExtractionAndReenablingUsesTheCurrentPainter() = runBlocking {
        // Occupy the production runner so the first sampled painter cannot finish extraction.
        val release = CompletableDeferred<Unit>()
        val entered = List(4) { CompletableDeferred<Unit>() }
        val blockers = mutableListOf<Job>()
        val red = CountingPainter(Color.Red)
        val blue = CountingPainter(Color.Blue)
        val enabled = mutableStateOf(false)
        val painter = mutableStateOf<Painter?>(red)
        var tint: Int? = null
        val scene = onEdt {
            ImageComposeScene(96, 96, Density(1f)) {
                val result = rememberPainterPaletteTint(painter.value, 0xffffffff.toInt(), "default", enabled.value)
                SideEffect { tint = result }
            }
        }
        var time = 0L
        suspend fun frame() {
            onEdt { scene.render(time.also { time += 16_666_667L }).close() }
            delay(1)
        }
        try {
            entered.forEach { started ->
                blockers += launch(Dispatchers.Default) {
                    paletteExtractionRunner.run { started.complete(Unit); release.await() }
                }
            }
            entered.forEach { withTimeout(5_000) { it.await() } }
            onEdt { enabled.value = true }
            withTimeout(5_000) { while (red.draws.get() == 0) frame() }
            assertNull(onEdt { tint })
            onEdt { enabled.value = false }
            repeat(3) { frame() }
            release.complete(Unit)
            blockers.joinAll()
            repeat(8) { frame() }
            assertNull(onEdt { tint }, "A canceled result must not be published while disabled")
            onEdt { painter.value = blue; enabled.value = true }
            val expected = expected(Color.Blue, 0xffffffff.toInt(), "default")
            withTimeout(5_000) { while (onEdt { tint } != expected) frame() }
            assertEquals(1, red.draws.get())
            assertEquals(1, blue.draws.get())
        } finally {
            release.complete(Unit)
            blockers.joinAll()
            onEdt { scene.close() }
        }
    }

    private fun expected(color: Color, base: Int, config: String): Int = PreviewTintPolicy.calculateCardTint(
        base,
        HarmonicPaletteExtractor().extract(IntArray(96 * 96) { color.toArgb() }).toPreviewTintPalette(),
        config,
    )

    private class CountingPainter(private val color: Color) : Painter() {
        val draws = AtomicInteger()
        override val intrinsicSize = Size(96f, 96f)
        override fun DrawScope.onDraw() { draws.incrementAndGet(); drawRect(color) }
    }

    private fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }
}
