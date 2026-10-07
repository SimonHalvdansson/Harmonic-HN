package com.simon.harmonichackernews.ui.comments

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AskErrorRenderingTest {
    @Test
    fun cardsFitAndActionsWorkWithLargeTextInBothThemes() = SwingUtilities.invokeAndWait {
        val examples = listOf(
            "API Key missing" to false,
            "Context limit" to true,
            "API error: The service is temporarily unavailable. Please try again shortly." to false,
        )
        val evidence = System.getenv("HARMONIC_ERROR_EVIDENCE")?.let(::File)?.apply { mkdirs() }
        for (dark in listOf(false, true)) for (fontScale in listOf(1f, 1.5f)) {
            examples.forEachIndexed { index, (error, contextLimit) ->
                var bounds = Rect.Zero
                var clicks = 0
                val scene = ImageComposeScene(320, 500, Density(1f, fontScale)) {
                    val palette = HarmonicThemeCatalog.resolve(if (dark) "dark" else "material_light", dark)
                    HarmonicTheme(palette.colorScheme, palette.dark) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.pageBackground).padding(16.dp)) {
                            AskErrorCard(
                                discussionErrorPresentation(error, contextLimit), true, { clicks++ },
                                Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
                            )
                        }
                    }
                }
                try {
                    repeat(5) { scene.render(it * 16_000_000L).close() }
                    assertTrue(bounds.height > 100f && bounds.bottom < 500f,
                        "Error card must fit at font scale $fontScale: $bounds")
                    if (fontScale == 1f && evidence != null) {
                        scene.render(96_000_000L).use { image ->
                            image.encodeToData(EncodedImageFormat.PNG)!!.use {
                                File(evidence, "error-$index-${if (dark) "dark" else "light"}.png").writeBytes(it.bytes)
                            }
                        }
                    }
                    // The text action sits below the copy, inside the card's bottom padding.
                    val action = Offset(bounds.left + 94f, bounds.bottom - 40f)
                    scene.sendPointerEvent(PointerEventType.Press, action)
                    scene.sendPointerEvent(PointerEventType.Release, action)
                    assertEquals(1, clicks, "Action must remain tappable at font scale $fontScale")
                } finally {
                    scene.close()
                }
            }
        }
    }
}
