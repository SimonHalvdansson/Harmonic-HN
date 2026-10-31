package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringListEditorLayoutTest {
    @Test
    fun storyTitleDialogFollowsKeyboardResizesWithoutASecondAnimation() = verify("Filter by story title")

    @Test
    fun domainDialogFollowsKeyboardResizesWithoutASecondAnimation() = verify("Filter by domain")

    private fun verify(title: String) = SwingUtilities.invokeAndWait {
        for (items in listOf(emptyList(), List(12) { "Filter $it" })) {
            val visible = mutableStateOf(false)
            val scene = ImageComposeScene(400, 800, Density(1f)) {
                val palette = HarmonicThemeCatalog.resolve("material_light", false)
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    if (visible.value) {
                        StringListEditorDialog(
                            title = title,
                            subtitle = "Hide stories containing these words",
                            inputLabel = "Words",
                            initialItems = items,
                            emptyMessage = "No filters",
                            parseInput = { listOf(it) },
                            onItemsChanged = {},
                            onDismiss = { visible.value = false },
                        )
                    }
                }
            }
            var time = 0L
            fun frame() { scene.render(time.also { time += 16_666_667L }).close() }
            fun titleBounds(): Rect = scene.nodes().single {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == title } == true
            }.boundsInWindow
            try {
                // Repeat in the same scene to cover both first and subsequent openings.
                repeat(3) {
                    visible.value = true
                    frame()
                    val openingTitle = titleBounds()
                    repeat(30) { frame() }
                    val restingTitle = titleBounds()
                    assertEquals(openingTitle, restingTitle, "Opening the dialog must not change its initial size")
                    for (height in listOf(700, 600, 500, 400, 340, 400, 500, 600, 700, 800)) {
                        scene.constraints = Constraints(maxWidth = 400, maxHeight = height)
                        frame()
                        val first = titleBounds()
                        repeat(30) { frame() }
                        assertEquals(first, titleBounds(), "Dialog must follow the window without a second size animation ($height, ${items.size} filters)")
                        val field = scene.nodes().single { it.config.contains(SemanticsActions.SetText) }.boundsInWindow
                        assertTrue(field.top >= 0 && field.bottom <= height, "Input must remain visible at height $height: $field")
                    }
                    assertEquals(restingTitle, titleBounds())
                    visible.value = false
                    repeat(3) { frame() }
                }
            } finally { scene.close() }
        }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { owner ->
        fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
        descend(owner.rootSemanticsNode)
    }
}
