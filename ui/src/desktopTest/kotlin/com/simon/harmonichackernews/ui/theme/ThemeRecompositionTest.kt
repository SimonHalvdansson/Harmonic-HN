package com.simon.harmonichackernews.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.settings.SettingRow
import com.simon.harmonichackernews.ui.settings.SettingsPage
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertSame

class ThemeRecompositionTest {
    @Test
    fun settingsItemsAndTheirContentAreNotReconstructedDuringPaletteFrames() = SwingUtilities.invokeAndWait {
        val target = mutableStateOf(HarmonicThemeCatalog.scheme("blue", false).colorScheme)
        val listState = LazyListState()
        var items = 0
        var content = 0
        val scene = ImageComposeScene(360, 500, Density(1f)) {
            HarmonicTheme(target.value, false) {
                SettingsPage("Settings", false, {}, listState = listState) {
                    item {
                        SideEffect { items++ }
                        SettingRow("Title", "Summary", icon = null, onClick = null,
                            trailing = { SideEffect { content++ }; Text("Value") })
                    }
                }
            }
        }
        try {
            repeat(5) { scene.render(it * 16_666_667L).close() }
            target.value = HarmonicThemeCatalog.scheme("orange", false).colorScheme
            repeat(2) { scene.render((it + 5) * 16_666_667L).close() }
            val itemsBefore = items
            val contentBefore = content
            repeat(25) { scene.render((it + 7) * 16_666_667L).close() }
            assertEquals(itemsBefore, items)
            assertEquals(contentBefore, content)
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
        } finally {
            scene.close()
        }
    }

    @Test
    fun nestedPreviewsKeepTheirOwnPaletteWhileMaterialDefaultsAnimate() = SwingUtilities.invokeAndWait {
        val outerInitial = HarmonicThemeCatalog.scheme("blue", false).colorScheme
        val innerInitial = HarmonicThemeCatalog.scheme("violet", true).colorScheme
        val outer = mutableStateOf(outerInitial)
        val inner = mutableStateOf(innerInitial)
        var outerColor = Color.Unspecified
        var innerColor = Color.Unspecified
        var buttonColor = Color.Unspecified
        var screenState: Any? = null
        val scene = ImageComposeScene(200, 200, Density(1f)) {
            HarmonicTheme(outer.value, false) {
                screenState = androidx.compose.runtime.remember { Any() }
                outerColor = HarmonicTheme.colors.primary
                HarmonicTheme(inner.value, true) {
                    innerColor = HarmonicTheme.colors.primary
                    buttonColor = androidx.compose.material3.ButtonDefaults.buttonColors().containerColor
                    Button(onClick = {}) { Text("Preview") }
                }
            }
        }
        try {
            repeat(5) { scene.render(it * 16_666_667L).close() }
            val initialState = screenState
            outer.value = outerInitial.copy(primary = Color.Red)
            inner.value = innerInitial.copy(primary = Color.Green)
            repeat(8) { scene.render((it + 5) * 16_666_667L).close() }
            assertNotEquals(outerInitial.primary, outerColor)
            assertNotEquals(Color.Red, outerColor)
            assertNotEquals(innerInitial.primary, innerColor)
            assertNotEquals(Color.Green, innerColor)
            assertEquals(innerColor, buttonColor)
            repeat(22) { scene.render((it + 13) * 16_666_667L).close() }
            assertEquals(Color.Red, outerColor)
            assertEquals(Color.Green, innerColor)
            assertEquals(innerColor, buttonColor)
            assertSame(initialState, screenState)
        } finally {
            scene.close()
        }
    }

    @Test
    fun animationInvalidatesConsumersRatherThanTheScreenAndUnchangedRoles() = SwingUtilities.invokeAndWait {
        val initial = HarmonicThemeCatalog.scheme("blue", false).colorScheme
        val target = mutableStateOf(initial)
        var structure = 0
        var errorConsumer = 0
        var materialConsumer = 0
        var drawConsumer = 0
        var textLayouts = 0
        var drawn = Color.Unspecified
        var material = Color.Unspecified
        var selection = Color.Unspecified
        val scene = ImageComposeScene(200, 200, Density(1f)) {
            HarmonicTheme(target.value, false) {
                SideEffect { structure++ }
                Column {
                    Probe { HarmonicTheme.colors.error; SideEffect { errorConsumer++ } }
                    Probe {
                        material = MaterialTheme.colorScheme.primary
                        selection = LocalTextSelectionColors.current.handleColor
                        SideEffect { materialConsumer++ }
                    }
                    Probe {
                        val colors = HarmonicTheme.colors
                        SideEffect { drawConsumer++ }
                        Canvas(Modifier.size(20.dp)) { drawn = colors.primary; drawRect(drawn) }
                        Text("Stable text", color = { colors.primary }, onTextLayout = { textLayouts++ })
                    }
                }
            }
        }
        try {
            repeat(5) { scene.render(it * 16_666_667L).close() }
            target.value = initial.copy(primary = Color.Red)
            repeat(2) { scene.render((it + 5) * 16_666_667L).close() }
            // The preference edit may recompose callers once; subsequent animation frames must
            // leave structure, unchanged roles, and text measurement alone.
            val structuresBefore = structure
            val errorsBefore = errorConsumer
            val drawsBefore = drawConsumer
            val layoutsBefore = textLayouts
            repeat(6) { scene.render((it + 7) * 16_666_667L).close() }
            assertNotEquals(initial.primary, drawn)
            assertNotEquals(Color.Red, drawn)
            assertEquals(drawn, material)
            assertEquals(drawn, selection)
            repeat(22) { scene.render((it + 13) * 16_666_667L).close() }
            assertEquals(Color.Red, drawn)
            assertEquals(drawn, material)
            assertEquals(drawn, selection)
            assertEquals(structuresBefore, structure, "Theme frames must not invalidate screen structure")
            assertEquals(errorsBefore, errorConsumer, "Unchanged roles must not invalidate their consumers")
            assertEquals(drawsBefore, drawConsumer, "Draw-only reads must not recompose")
            assertEquals(layoutsBefore, textLayouts, "Text color must not remeasure text")
            assertTrue(materialConsumer > 2, "Unmodified Material consumers must still animate")
        } finally {
            scene.close()
        }
    }

    @Composable
    private fun Probe(content: @Composable () -> Unit) = content()
}
