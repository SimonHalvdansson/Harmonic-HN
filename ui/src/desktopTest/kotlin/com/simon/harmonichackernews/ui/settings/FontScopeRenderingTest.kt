package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_font_download
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.settings.AppFont
import com.simon.harmonichackernews.settings.AppearancePreferences
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.AppHeadlineFontFamily
import com.simon.harmonichackernews.ui.theme.AppTechnicalFontFamily
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.awt.EventQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FontScopeRenderingTest {
    @Test
    fun everyFontReachesUiAndContentRemainsIndependentOfScope() = EventQueue.invokeAndWait {
        val appearance = mutableStateOf(AppearancePreferences(followSystem = false, manualDark = false))
        var selectedFamily: FontFamily? = null
        val palette = HarmonicThemeCatalog.scheme("dynamic", false)
        val scene = ImageComposeScene(400, 900, Density(1f)) {
            HarmonicTheme(palette.colorScheme, false, appearance = appearance.value) {
                selectedFamily = rememberContentTypography(appearance.value.font).family
                Column {
                    Text("Material control", style = MaterialTheme.typography.bodyLarge)
                    Text("Explicit control", fontFamily = AppUiFontFamily)
                    SettingRow(title = "Settings row", icon = Res.drawable.ic_font_download, onClick = {})
                    HarmonicMenuText("Popup action")
                    Text("Heading", fontFamily = AppHeadlineFontFamily)
                    Text("Model identifier", fontFamily = AppTechnicalFontFamily)
                    HarmonicTheme(palette.colorScheme, false) {
                        Text("Nested preview", fontFamily = AppUiFontFamily)
                    }
                    val content = rememberContentTypography(appearance.value.font)
                    Text("Post title", fontFamily = content.family,
                        fontSize = content.storyTitleSize.sp)
                }
            }
        }
        var time = 0L
        fun render() = repeat(3) { scene.render(time.also { time += 16_000_000 }).close() }
        fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        fun layouts(): Map<String, TextLayoutResult> = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
            .flatMap { node -> mutableListOf<TextLayoutResult>().also {
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(it)
            } }.associateBy { it.layoutInput.text.text }
        try {
            render()
            val defaultFamily = layouts().getValue("Explicit control").layoutInput.style.fontFamily
            for (font in AppFont.entries) {
                appearance.value = appearance.value.copy(font = font.storedValue, applyFontEverywhere = true)
                render()
                val actual = layouts()
                for (label in listOf("Material control", "Explicit control", "Settings row", "Nested preview", "Post title", "Popup action", "Heading", "Model identifier")) {
                    assertEquals(selectedFamily, actual.getValue(label).layoutInput.style.fontFamily, "${font.label}: $label")
                }
            }
            appearance.value = appearance.value.copy(font = "georgia", applyFontEverywhere = false)
            render()
            val restored = layouts()
            assertEquals(defaultFamily, restored.getValue("Material control").layoutInput.style.fontFamily)
            assertEquals(defaultFamily, restored.getValue("Explicit control").layoutInput.style.fontFamily)
            assertEquals(defaultFamily, restored.getValue("Nested preview").layoutInput.style.fontFamily)
            assertNotEquals(defaultFamily, restored.getValue("Post title").layoutInput.style.fontFamily)
        } finally {
            scene.close()
        }
    }
}
