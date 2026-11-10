package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_open_in_browser
import com.simon.harmonichackernews.ui.theme.pageBackground
import org.jetbrains.compose.resources.painterResource

/** Distances are pixels; only unconsumed touch drags at the top of the list feed this state. */
internal class BrowserPullState(private val maxDistance: Float, private val threshold: Float) {
    var distance by mutableFloatStateOf(0f)
        private set
    var pulling by mutableStateOf(false)
        private set
    var opening by mutableStateOf(false)
        private set
    val ready: Boolean get() = distance >= threshold

    fun drag(delta: Float): Float {
        if (delta == 0f || (distance == 0f && delta < 0f)) return 0f
        pulling = true
        opening = false
        val previous = distance
        distance = (distance + delta * DragResistance).coerceIn(0f, maxDistance)
        // Own further downward movement at the cap so overscroll cannot stretch the list too.
        return if (delta > 0f) delta else (distance - previous) / DragResistance
    }

    fun release(): Boolean {
        val shouldOpen = ready
        reset()
        opening = shouldOpen
        return shouldOpen
    }

    fun reset() {
        pulling = false
        opening = false
        distance = 0f
    }

    private companion object { const val DragResistance = 0.65f }
}

/** Keep this wrapper composed when disabled so toggling browser settings never replaces the list. */
@Composable
internal fun PullToOpenBrowserBox(
    enabled: Boolean,
    topInset: Dp,
    onOpen: () -> Unit,
    onPullActiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val state = remember(density) {
        with(density) { BrowserPullState(200.dp.toPx(), 128.dp.toPx()) }
    }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnPullActiveChanged by rememberUpdatedState(onPullActiveChanged)
    DisposableEffect(Unit) { onDispose { currentOnPullActiveChanged(false) } }
    LaunchedEffect(enabled) { if (!enabled) state.reset() }
    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!currentEnabled || source != NestedScrollSource.UserInput || available.y >= 0f) {
                    return Offset.Zero
                }
                return Offset(0f, state.drag(available.y))
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!currentEnabled || source != NestedScrollSource.UserInput || available.y <= 0f) {
                    return Offset.Zero
                }
                return Offset(0f, state.drag(available.y))
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!currentEnabled || state.distance == 0f) return Velocity.Zero
                // Reset synchronously before launching the browser: returning always shows comments at rest.
                if (state.release()) currentOnOpen()
                return available
            }
        }
    }
    val offset by animateFloatAsState(
        targetValue = if (enabled) state.distance else 0f,
        animationSpec = if (state.pulling || state.opening || !enabled) snap() else tween(160),
        label = "pull to open browser",
    )
    SideEffect { currentOnPullActiveChanged(enabled && offset > 0f) }
    val background = MaterialTheme.colorScheme.pageBackground
    Box(modifier.then(if (enabled) Modifier.clipToBounds().nestedScroll(connection) else Modifier)) {
        if (enabled && offset > 0f) {
            val revealHeight = with(density) { offset.toDp() }
            Box(
                Modifier.fillMaxWidth().offset(y = topInset)
                    .height((revealHeight - topInset).coerceAtLeast(0.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // Reveal the naturally sized prompt instead of squeezing its children to fit the gap.
                Column(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val color = if (state.ready) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(painterResource(Res.drawable.ic_open_in_browser), null, Modifier.requiredSize(32.dp), tint = color)
                    Text("Open in browser", style = MaterialTheme.typography.titleMedium, color = color)
                    Text(
                        "Release to open",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxSize().then(
                if (enabled) Modifier.graphicsLayer { translationY = offset }.background(background)
                else Modifier,
            ),
            content = content,
        )
    }
}
