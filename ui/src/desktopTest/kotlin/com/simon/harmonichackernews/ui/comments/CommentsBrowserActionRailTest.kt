package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsBrowserActionRailTest {
    @Test
    fun readerAvailabilityAnimatesBothIntoAndOutOfTheRail() = SwingUtilities.invokeAndWait {
        val available = mutableStateOf(false)
        val scheduler = TestCoroutineScheduler()
        val dispatcher = UnconfinedTestDispatcher(scheduler)
        var height = 0
        val scene = ImageComposeScene(100, 400, Density(1f), coroutineContext = dispatcher) {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    CommentsBrowserActionRail(
                        readerModeAvailable = available.value,
                        readerModeEnabled = false,
                        showInvert = false,
                        onAction = {},
                        onBrowserBack = {},
                        modifier = Modifier.width(84.dp).onSizeChanged { height = it.height },
                    )
                }
            }
        }
        fun frames(count: Int) = repeat(count) {
            scheduler.advanceTimeBy(16)
            scheduler.runCurrent()
            scene.render(scheduler.currentTime * 1_000_000).close()
        }
        try {
            frames(20)
            val withoutReader = height
            available.value = true
            frames(6)
            val appearing = height
            frames(20)
            val withReader = height
            assertTrue(withReader > withoutReader)
            assertTrue(appearing in (withoutReader + 1) until withReader,
                "The reader button must grow into its slot rather than appear instantly")

            available.value = false
            frames(4)
            assertTrue(height in (withoutReader + 1) until withReader,
                "The reader button must shrink away rather than disappear instantly")
            frames(20)
            assertEquals(withoutReader, height)
        } finally {
            scene.close()
        }
    }
}
