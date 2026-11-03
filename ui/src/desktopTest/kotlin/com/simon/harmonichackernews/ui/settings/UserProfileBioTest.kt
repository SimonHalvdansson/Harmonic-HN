package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserProfileBioTest {
    @Test
    fun plainTextStaysSteadyWhileLinksAndButtonWidthAnimate() = SwingUtilities.invokeAndWait {
        val scene = ImageComposeScene(360, 240, Density(1f)) {
            val palette = HarmonicThemeCatalog.scheme("dynamic", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                UserProfileBio("test", "A plain opening paragraph.<p>Visit example.com or <a href=\"https://example.org\">this existing link</a>.", {})
            }
        }
        var time = 0L
        fun frame() = scene.render(time.also { time += 16_666_667L })
        fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        fun tagged(tag: String) = scene.semanticsOwners.flatMap { nodes(it.unmergedRootSemanticsNode) }
            .single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun linkCount(): Int {
            val text = tagged("user-profile-bio").config[SemanticsProperties.Text].single()
            return text.getLinkAnnotations(0, text.length).size
        }
        try {
            repeat(30) { frame().close() }
            val original = frame().use { image ->
                val pixels = image.toComposeImageBitmap().toPixelMap()
                List(360 * 20) { pixels[it % 360, it / 360] }
            }
            assertEquals(1, linkCount(), "Existing HTML links remain clickable without opting in")
            for (enabled in listOf(true, false)) {
                val button = tagged("user-profile-links-toggle")
                val before = button.boundsInRoot.width
                assertTrue(button.config[SemanticsActions.OnClick].action!!.invoke())
                val widths = mutableListOf<Float>()
                repeat(30) {
                    frame().use { image ->
                        val pixels = image.toComposeImageBitmap().toPixelMap()
                        assertEquals(original, List(360 * 20) { pixels[it % 360, it / 360] },
                            "Non-link text must keep identical pixels throughout the toggle")
                    }
                    widths += tagged("user-profile-links-toggle").boundsInRoot.width
                }
                assertEquals(if (enabled) 2 else 1, linkCount())
                val after = widths.last()
                assertTrue(widths.filter { it > minOf(before, after) && it < maxOf(before, after) }.distinct().size >= 3,
                    "The button must animate through intermediate widths in both directions")
            }
        } finally { scene.close() }
    }
}
