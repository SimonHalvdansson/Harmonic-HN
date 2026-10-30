package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainHeight

@Composable
internal fun AskLayout(
    header: @Composable () -> Unit,
    composer: @Composable (Modifier) -> Unit,
    safeInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (bottomSpacer: Modifier) -> Unit,
) {
    val composerHeight = remember { mutableIntStateOf(0) }
    // Only the trailing spacer reads this measurement. No composition depends on it.
    val bottomSpacer = Modifier.layout { measurable, constraints ->
        val height = constraints.constrainHeight(composerHeight.intValue)
        val spacer = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
        layout(spacer.width, height) { spacer.placeRelative(0, 0) }
    }
    Layout(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(
            safeInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        ),
        content = {
            Box { header() }
            Box { content(bottomSpacer) }
            Box { composer(Modifier.windowInsetsPadding(safeInsets.only(WindowInsetsSides.Bottom))) }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val headerPlaceable = measurables[0].measure(loose)
        val bodyHeight = (constraints.maxHeight - headerPlaceable.height).coerceAtLeast(0)
        // Resolve the floating composer before measuring the content's end padding, in this frame.
        val composerPlaceable = measurables[2].measure(loose.copy(maxHeight = bodyHeight))
        composerHeight.intValue = composerPlaceable.height
        val body = measurables[1].measure(constraints.copy(minHeight = bodyHeight, maxHeight = bodyHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            headerPlaceable.placeRelative(0, 0)
            body.placeRelative(0, headerPlaceable.height)
            composerPlaceable.placeRelative(0, constraints.maxHeight - composerPlaceable.height)
        }
    }
}
