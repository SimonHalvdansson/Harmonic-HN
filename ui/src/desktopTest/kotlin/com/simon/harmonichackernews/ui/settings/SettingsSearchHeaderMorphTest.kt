package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchHeaderMorphTest {
    @Test fun headerKeepsOneOpaqueAlignedAccessibleIconWhileItsTextCrossfades() = SwingUtilities.invokeAndWait {
        for (dark in listOf(false, true)) {
            val progress = mutableFloatStateOf(0f)
            val navigation = SettingsNavigationStore(twoPane = false)
            navigation.searchTransition.expansion = { progress.floatValue }
            val palette = HarmonicThemeCatalog.scheme("dynamic", dark)
            var backs = 0
            val scene = ImageComposeScene(400, 500) {
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    SettingsSearchScreen(navigation, emptyList(), { backs++ })
                }
            }
            var time = 0L
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            try {
                var iconId: Int? = null
                for (fraction in listOf(0f, .1f, .2f, .3f, .4f, .75f, 1f, .5f, 0f)) {
                    progress.floatValue = fraction
                    repeat(5) { scene.render(time.also { time += 16_666_667L }).close() }
                    val nodes = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
                    val icon = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "settings-search-navigation-icon" }
                    iconId?.let { assertEquals(it, icon.id, "Keep the same vector through reversal") }
                    iconId = icon.id
                    val bounds = icon.boundsInRoot
                    assertEquals(24f, bounds.width, .01f)
                    assertEquals(32f - fraction * 4f, bounds.center.x, 1f)
                    assertEquals(30f, bounds.center.y, .01f)
                    val button = nodes.single {
                        it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf("Back") &&
                            it.config.getOrNull(SemanticsActions.OnClick) != null
                    }
                    assertTrue(button.touchBoundsInRoot.width >= 48f && button.touchBoundsInRoot.height >= 48f)
                    scene.render(time.also { time += 16_666_667L }).use { image ->
                        val pixels = image.toComposeImageBitmap().toPixelMap()
                        val tint = palette.colorScheme.onSurfaceVariant
                        var fullTintPixels = 0
                        for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                                val pixel = pixels[x, y]
                                if (abs(pixel.red - tint.red) + abs(pixel.green - tint.green) + abs(pixel.blue - tint.blue) < .03f) {
                                    fullTintPixels++
                                }
                            }
                        }
                        assertTrue(fullTintPixels > 8, "The icon must keep its full tint during the text cross-fade: $fraction")
                        System.getenv("HARMONIC_SEARCH_MORPH_EVIDENCE")?.let { folder ->
                            image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                                File(folder, "header-${if (dark) "dark" else "light"}-${(fraction * 100).toInt()}.png")
                                    .writeBytes(data.bytes)
                            }
                        }
                    }
                    if (fraction == 1f) assertTrue(button.config[SemanticsActions.OnClick].action!!.invoke())
                }
                assertEquals(1, backs)
            } finally { scene.close() }
        }
    }
}
