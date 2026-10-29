package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_history
import org.jetbrains.compose.resources.painterResource
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeaderChipScrollingTest {
    @Test
    fun verticalWheelMovesBothRowsInBothDirectionsWithoutSelectingOptions() = SwingUtilities.invokeAndWait {
        for (search in listOf(false, true)) for (direction in LayoutDirection.entries) {
            withRow(search, direction) {
                wheel(Offset(0f, 2f))
                assertTrue(scrollPosition() > 0f, "Vertical wheel must advance the options (search=$search, $direction)")
                wheel(Offset(0f, -20f))
                assertEquals(0f, scrollPosition(), "Reverse wheel must return to the beginning")
                assertEquals(0, selections)
            }
        }
    }

    @Test
    fun nativeHorizontalGesturesStillScrollBothRows() = SwingUtilities.invokeAndWait {
        for (search in listOf(false, true)) {
            withRow(search) {
                wheel(Offset(3f, 0f))
                assertTrue(scrollPosition() > 0f, "Native horizontal scrolling must still work (search=$search)")
                assertEquals(0, selections)
            }
        }
    }

    @Test
    fun scrollbarCanBeDraggedToReachHiddenOptions() = SwingUtilities.invokeAndWait {
        for (search in listOf(false, true)) {
            withRow(search) {
                val y = row().boundsInRoot.bottom + 3f
                scene.sendPointerEvent(PointerEventType.Press, Offset(28f, y), type = PointerType.Mouse,
                    buttons = PointerButtons(isPrimaryPressed = true))
                scene.sendPointerEvent(PointerEventType.Move, Offset(250f, y), type = PointerType.Mouse,
                    buttons = PointerButtons(isPrimaryPressed = true))
                scene.sendPointerEvent(PointerEventType.Release, Offset(250f, y), type = PointerType.Mouse)
                settle()
                assertTrue(scrollPosition() > 0f, "Dragging the scrollbar must expose hidden chips (search=$search)")
                assertEquals(0, selections)
            }
        }
    }

    private fun withRow(
        search: Boolean,
        direction: LayoutDirection = LayoutDirection.Ltr,
        block: RowFixture.() -> Unit,
    ) {
        val fixture = RowFixture(search, direction)
        try { fixture.settle(); fixture.block() } finally { fixture.scene.close() }
    }

    private class RowFixture(search: Boolean, direction: LayoutDirection) {
        var selections = 0
        private var time = 0L
        private val labels = listOf("Top Stories", "Last 24 hours", "Last 48 hours", "Last week", "New Stories")
        val scene = ImageComposeScene(300, 180, Density(1f)) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    // ImageComposeScene renders synchronously on the EDT. Load offscreen icons
                    // before wheel animation coroutines can request them during lazy measurement.
                    (labels.map { StoryType.fromLabel(it).menuIcon } + Res.drawable.ic_history)
                        .forEach { painterResource(it) }
                    if (search) {
                        val colors = MaterialTheme.colorScheme
                        StorySearchHeader(
                            state = StorySearchPresentationState(
                                active = false, draft = "", suppressAutoFocus = true,
                                sortLabel = "Relevance", dateLabel = "All time", pointsLabel = "Any points",
                                commentsLabel = "Any comments", sortLabels = listOf("Relevance", "Date"),
                                dateLabels = listOf("All time"), pointsLabels = listOf("Any points"),
                                commentsLabels = listOf("Any comments"), onlyRead = false,
                            ),
                            sideStart = 16.dp, sideEnd = 16.dp,
                            iconColor = colors.onSurfaceVariant, menuColor = colors.surface,
                            menuTextColor = colors.onSurface, fontFamily = FontFamily.Default,
                            onDraftChanged = {}, onSearch = {}, onClose = {}, consumeFocusRequest = { false },
                            onOptionSelected = { _, _ -> selections++ }, onToggleOnlyRead = { selections++ },
                        )
                    } else {
                        StoryTypeChips(
                            labels = labels,
                            selectedIndex = 0, fontFamily = FontFamily.Default,
                            contentPadding = PaddingValues(horizontal = 16.dp), onSelected = { selections++ },
                        )
                    }
                }
            }
        }

        fun settle() = repeat(30) { scene.render(time.also { time += 16_666_667L }).close() }
        private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        fun row() = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }.single {
            it.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
        }
        fun scrollPosition() = row().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        fun wheel(delta: Offset) {
            scene.sendPointerEvent(
                PointerEventType.Scroll, row().boundsInRoot.center,
                scrollDelta = delta, type = PointerType.Mouse, timeMillis = time / 1_000_000,
            )
            settle()
        }
    }
}
