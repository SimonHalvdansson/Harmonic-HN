package com.simon.harmonichackernews.ui.stories

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoryLoadFailureItemTest {
    @Test
    fun entireRowRetriesInBothThemesAndWithLargeText() = SwingUtilities.invokeAndWait {
        for (dark in listOf(false, true)) for (fontScale in listOf(1f, 2f)) {
            var bounds = Rect.Zero
            var retries = 0
            val scene = ImageComposeScene(320, 400, Density(1f, fontScale)) {
                val palette = HarmonicThemeCatalog.resolve(if (dark) "dark" else "light", dark)
                HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                    StoryLoadFailureItem(
                        onRetry = { retries++ },
                        modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
                    )
                }
            }
            try {
                repeat(5) { scene.render(it * 16_000_000L).close() }
                assertTrue(bounds.height >= 48f && bounds.bottom < 400f)
                // Both the icon and the empty right side must work without a separate button.
                for (x in listOf(32f, 290f)) {
                    val point = Offset(x, bounds.center.y)
                    scene.sendPointerEvent(PointerEventType.Press, point)
                    scene.sendPointerEvent(PointerEventType.Release, point)
                }
                assertEquals(2, retries)
            } finally {
                scene.close()
            }
        }
    }
}
