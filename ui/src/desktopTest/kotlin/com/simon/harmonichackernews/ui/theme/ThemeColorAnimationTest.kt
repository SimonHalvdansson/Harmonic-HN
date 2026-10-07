package com.simon.harmonichackernews.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeColorAnimationTest {
    private val blue = HarmonicThemeCatalog.scheme("blue", false).colorScheme
    private val orange = HarmonicThemeCatalog.scheme("orange", false).colorScheme

    @Test
    fun paletteBlendsAndFinishesEvenWhenHostRecreatesEquivalentTargets() = runTest {
        val target = mutableStateOf(blue)
        var displayed = blue
        withComposition({ displayed = animateThemeColorScheme(target.value) }) { frame ->
            assertSame(blue, displayed)
            target.value = orange
            repeat(6) { frame() }
            assertNotEquals(blue.primary, displayed.primary)
            assertNotEquals(orange.primary, displayed.primary)
            assertNotEquals(blue.surfaceContainer, displayed.surfaceContainer)
            assertNotEquals(orange.surfaceContainer, displayed.surfaceContainer)
            // Recreating a palette must not restart its animation each frame.
            repeat(24) {
                target.value = orange.copy()
                frame()
            }
            assertSame(orange, displayed)
        }
    }

    @Test
    fun interruptedPaletteStartsFromTheVisibleColorsAndCanReverse() = runTest {
        val target = mutableStateOf(blue)
        val uninterruptedTarget = mutableStateOf(blue)
        var displayed = blue
        var uninterrupted = blue
        withComposition({
            displayed = animateThemeColorScheme(target.value)
            uninterrupted = animateThemeColorScheme(uninterruptedTarget.value)
        }) { frame ->
            target.value = orange
            uninterruptedTarget.value = orange
            repeat(6) { frame() }
            target.value = blue
            frame()
            // The clock also advances the old animation on this frame. Retarget from that
            // same color, rather than jumping back to either endpoint.
            assertColorClose(uninterrupted.primary, displayed.primary)
            assertColorClose(uninterrupted.surfaceContainer, displayed.surfaceContainer)
            repeat(24) { frame() }
            assertSame(blue, displayed)
            target.value = orange
            repeat(24) { frame() }
            assertSame(orange, displayed)
        }
    }

    @Test
    fun appearanceKeyResetsColorsWithoutAnimatingAcrossLightAndDark() = runTest {
        val palette = mutableStateOf(HarmonicThemeCatalog.scheme("blue", false))
        var displayed: ColorScheme? = null
        withComposition({
            displayed = key(palette.value.dark) { animateThemeColorScheme(palette.value.colorScheme) }
        }) { frame ->
            palette.value = HarmonicThemeCatalog.scheme("orange", false)
            repeat(6) { frame() }
            palette.value = HarmonicThemeCatalog.scheme("orange", true)
            frame()
            assertSame(palette.value.colorScheme, displayed)
        }
    }

    private fun assertColorClose(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.005f)
        assertEquals(expected.green, actual.green, 0.005f)
        assertEquals(expected.blue, actual.blue, 0.005f)
    }

    private suspend fun TestScope.withComposition(
        content: @Composable () -> Unit,
        block: (frame: () -> Unit) -> Unit,
    ) {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val composition = Composition(EmptyApplier(), recomposer)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        var time = 0L
        fun frame() {
            Snapshot.sendApplyNotifications()
            runCurrent()
            time += 16_666_667L
            clock.sendFrame(time)
            runCurrent()
            Snapshot.sendApplyNotifications()
            runCurrent()
        }
        try {
            composition.setContent(content)
            frame()
            block(::frame)
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}
