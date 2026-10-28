package com.simon.harmonichackernews.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavigationSurfaceInsetsTest {
    @Test fun backGestureDoesNotSqueezeRetainedPanesOrRemeasureTheirContent() = SwingUtilities.invokeAndWait {
        val offset = mutableFloatStateOf(0f)
        val measuredWidths = mutableListOf<Int>()
        val scene = ImageComposeScene(1_000, 700) {
            Box(Modifier.fillMaxSize().navigationSurfaceTranslation { offset.floatValue }) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(400.dp).fillMaxHeight().recalculateWindowInsets()) {
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets(24, 0, 24, 0))
                            .onSizeChanged { measuredWidths += it.width })
                    }
                    Box(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        var time = 0L
        fun frame() { time += 16_000_000; scene.render(time).close() }
        try {
            repeat(5) { frame() }
            assertTrue(measuredWidths.isNotEmpty())
            val initialWidths = measuredWidths.toList()
            // Incoming parallax, outgoing slide, cancellation and completion.
            for (x in listOf(-240f, -120f, -30f, 0f, 100f, 450f, 900f, 0f)) {
                offset.floatValue = x
                repeat(3) { frame() }
                assertEquals(initialWidths, measuredWidths, "Content width changed at offset $x")
            }
        } finally { scene.close() }
    }
}
