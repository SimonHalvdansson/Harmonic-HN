package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.common.HarmonicTopAppBar
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class AskComposerTest {
    @Test
    fun darkInputIsDarkerThanTheAskSurfaceInBothDarkPalettes() = SwingUtilities.invokeAndWait {
        for (theme in listOf("dark", "material_dark")) {
            val palette = HarmonicThemeCatalog.resolve(theme, true)
            val surface = palette.colors.contentCardBackground
            val scene = ImageComposeScene(360, 200, Density(1f)) {
                HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                    Box(Modifier.fillMaxSize().background(surface)) {
                        AskComposer("", {}, false, false, true, {}, {},
                            Modifier.align(Alignment.BottomCenter), surfaceColor = surface)
                    }
                }
            }
            try {
                repeat(5) { scene.render(it * 16_000_000L).close() }
                scene.render(96_000_000L).use { image ->
                    val pixels = image.toComposeImageBitmap().toPixelMap()
                    assertTrue(pixels[180, 128].luminance() < pixels[180, 50].luminance(),
                        "The input must be darker than the surrounding Ask surface in $theme")
                }
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun floatingComposerGrowsAndDispatchesOnlyAvailableActions() = SwingUtilities.invokeAndWait {
        val evidence = System.getenv("HARMONIC_COMPOSER_EVIDENCE")?.let(::File)?.apply { mkdirs() }
        for (dark in listOf(false, true)) for (fontScale in listOf(1f, 1.5f)) {
            val draft = mutableStateOf("")
            val running = mutableStateOf(false)
            val enabled = mutableStateOf(true)
            var bounds = Rect.Zero
            var sends = 0
            var stops = 0
            var backs = 0
            val scene = ImageComposeScene(360, 500, Density(1f, fontScale)) {
                val palette = HarmonicThemeCatalog.resolve(if (dark) "dark" else "material_light", dark)
                HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                    Column(Modifier.fillMaxSize().background(HarmonicTheme.colors.background)) {
                        HarmonicTopAppBar("Ask about this comment", { backs++ },
                            toolbarHeight = 64.dp * fontScale.coerceAtLeast(1f))
                        Box(Modifier.weight(1f)) {
                            AskComposer(
                                draft.value, { draft.value = it }, false, running.value, enabled.value,
                                { sends++ }, { stops++ },
                                Modifier.align(Alignment.BottomCenter)
                                    .onGloballyPositioned { bounds = it.boundsInRoot() },
                            )
                        }
                    }
                }
            }
            var time = 0L
            fun frame(name: String? = null): Color =
                scene.render(time.also { time += 16_000_000 }).use { image ->
                    if (name != null && evidence != null) image.encodeToData(EncodedImageFormat.PNG)!!.use {
                        File(evidence, "$name-${if (dark) "dark" else "light"}-$fontScale.png").writeBytes(it.bytes)
                    }
                    // Sample the button background outside the icon's bounds.
                    image.toComposeImageBitmap().toPixelMap()[332, 460]
                }
            fun click(point: Offset = Offset(bounds.right - 44f, bounds.bottom - 40f)) {
                scene.sendPointerEvent(PointerEventType.Press, point)
                scene.sendPointerEvent(PointerEventType.Release, point)
                frame()
            }
            try {
                repeat(5) { frame() }
                val initialHeight = bounds.height
                click()
                assertEquals(0, sends, "Empty drafts must not send")
                val disabledColor = frame()
                draft.value = "Can you explain this?\nA concrete example would help."
                repeat(3) { frame() }
                val intermediateColor = frame()
                repeat(12) { frame() }
                val enabledColor = frame()
                fun colorDistance(a: Color, b: Color) =
                    abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
                assertTrue(colorDistance(intermediateColor, disabledColor) > 0.01f,
                    "Typing should start fading in the accent color")
                assertTrue(colorDistance(intermediateColor, enabledColor) > 0.01f,
                    "The enabled color must transition smoothly rather than jump")
                assertTrue(bounds.height > initialHeight, "The floating field must grow for multiline drafts")
                frame("composer")
                click()
                assertEquals(1, sends)
                draft.value = ""
                running.value = true
                repeat(12) { frame() }
                frame("stop")
                click()
                assertEquals(1, stops, "Stop must work even with an empty draft")
                enabled.value = false
                repeat(5) { frame() }
                click()
                assertEquals(1, stops, "Resetting disables the action")
                click(Offset(44f, 32f))
                assertEquals(1, backs, "The Settings-style Up control must remain usable")
            } finally {
                scene.close()
            }
        }
    }
}
