package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.settings.AiSummaryMode
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelPresentation
import com.simon.harmonichackernews.summary.LocalModelPresentationAction
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalModelsPanelAnimationTest {
    @Test
    fun dropdownKeepsCardsAnchoredAndMovesFollowingContentWithoutAnEmptyTail() = SwingUtilities.invokeAndWait {
        for (scale in listOf(1f, 2f)) {
            val rows = LocalModelCatalog.models.filter { it.downloadable }.take(3).map {
                LocalModelRowUiState(it, LocalModelPresentation(
                    summary = "Not downloaded",
                    enabled = true,
                    selectable = false,
                    selected = false,
                    action = LocalModelPresentationAction.DOWNLOAD_MODEL,
                    progress = null,
                ))
            }
            var followingTop = 0f
            val scene = ImageComposeScene((400 * scale).toInt(), (900 * scale).toInt(), Density(scale)) {
                val palette = HarmonicThemeCatalog.resolve("material_light", false)
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    Column {
                        AiSummaryModeTransition(AiSummaryMode.LOCAL) {
                            LocalModelsPanel(rows, { ColorPainter(Color.Blue) }, {}, { _, _ -> })
                        }
                        Box(Modifier.fillMaxWidth().height(100.dp).onGloballyPositioned {
                            followingTop = it.positionInRoot().y
                        })
                    }
                }
            }
            var time = 0L
            fun frame() {
                time += 16_000_000L
                scene.render(time).close()
            }
            fun descendants(node: SemanticsNode): List<SemanticsNode> =
                listOf(node) + node.children.flatMap(::descendants)
            fun textNode(text: String) = scene.semanticsOwners
                .flatMap { descendants(it.unmergedRootSemanticsNode) }
                .firstOrNull { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
                }
            fun toggle() {
                val center = checkNotNull(textNode("Downloadable models")).boundsInRoot.center
                scene.sendPointerEvent(PointerEventType.Press, center, timeMillis = time / 1_000_000)
                scene.sendPointerEvent(PointerEventType.Release, center, timeMillis = time / 1_000_000 + 1)
            }
            try {
                repeat(40) { frame() }
                val expandedTop = followingTop
                val cardTop = checkNotNull(textNode(rows.first().model.displayName)).positionInRoot.y
                toggle()
                val collapseTops = List(25) {
                    frame()
                    textNode(rows.first().model.displayName)?.let {
                        assertEquals(cardTop, it.positionInRoot.y, 1f,
                            "Collapsing must clip the cards from the bottom without sliding them upward")
                    }
                    followingTop
                }
                val collapsedTop = followingTop
                repeat(40) { frame() }
                assertEquals(collapsedTop, followingTop, 1f,
                    "Following settings must settle with the dropdown instead of animating an empty tail")
                assertTrue(collapsedTop < expandedTop)
                assertTrue(collapseTops.distinct().size > 2, "The list must still animate closed")
                assertTrue(collapseTops.zipWithNext().all { (a, b) -> b <= a + 1f })

                toggle()
                val expandTops = List(25) {
                    frame()
                    textNode(rows.first().model.displayName)?.let {
                        assertEquals(cardTop, it.positionInRoot.y, 1f,
                            "Expanding must reveal cards in their final positions")
                    }
                    followingTop
                }
                assertEquals(expandedTop, followingTop, 1f)
                assertTrue(expandTops.distinct().size > 2, "The list must still animate open")
                assertTrue(expandTops.zipWithNext().all { (a, b) -> b >= a - 1f })
            } finally {
                scene.close()
            }
        }
    }
}
