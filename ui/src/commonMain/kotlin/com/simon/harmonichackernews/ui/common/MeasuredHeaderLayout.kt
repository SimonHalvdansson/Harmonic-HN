package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density

internal class MeasuredHeaderState {
    private var measuredHeight = 0
    val height = mutableIntStateOf(0)
    var invalidateContentMeasurement: (() -> Unit)? = null

    fun measured(heightPx: Int) {
        if (measuredHeight == heightPx) return
        measuredHeight = heightPx
        height.intValue = heightPx
        // Snapshot notifications arrive after layout. Mark the consumer dirty now so an
        // unchanged viewport constraint cannot reuse the lazy list's previous measurement.
        invalidateContentMeasurement?.invoke()
    }
}

internal fun Modifier.measuredHeaderContent(state: MeasuredHeaderState): Modifier =
    this then HeaderContentElement(state)

private data class HeaderContentElement(val state: MeasuredHeaderState) : ModifierNodeElement<HeaderContentNode>() {
    override fun create() = HeaderContentNode(state)
    override fun update(node: HeaderContentNode) = node.update(state)
}

private class HeaderContentNode(private var state: MeasuredHeaderState) : Modifier.Node(), LayoutModifierNode {
    private val invalidate = { invalidateMeasurement() }
    override fun onAttach() { state.invalidateContentMeasurement = invalidate }
    override fun onDetach() {
        if (state.invalidateContentMeasurement === invalidate) state.invalidateContentMeasurement = null
    }

    fun update(value: MeasuredHeaderState) {
        if (state === value) return
        if (isAttached) onDetach()
        state = value
        if (isAttached) onAttach()
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val child = measurable.measure(constraints)
        return layout(child.width, child.height) { child.place(0, 0) }
    }
}

/** The lazy list reads this padding during measurement, after its header has been measured. */
internal class MeasuredHeaderPadding(
    private val padding: PaddingValues,
    private val headerHeight: IntState,
    private val density: Density,
) : PaddingValues by padding {
    override fun calculateTopPadding() =
        padding.calculateTopPadding() + with(density) { headerHeight.intValue.toDp() }
}

/** Measure the overlaid header first, so content can reserve its height in the same layout pass. */
@Composable
internal fun MeasuredHeaderLayout(
    headerHeight: MeasuredHeaderState,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    foreground: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, content = {
        Box { header() }
        Box { content() }
        if (foreground != null) Box { foreground() }
    }) { measurables, constraints ->
        val header = measurables[0].measure(constraints.copy(minHeight = 0))
        headerHeight.measured(header.height)
        val content = measurables[1].measure(constraints)
        val foreground = measurables.getOrNull(2)?.measure(constraints)
        layout(content.width, content.height) {
            content.placeRelative(0, 0)
            header.placeRelative(0, 0)
            foreground?.placeRelative(0, 0)
        }
    }
}
