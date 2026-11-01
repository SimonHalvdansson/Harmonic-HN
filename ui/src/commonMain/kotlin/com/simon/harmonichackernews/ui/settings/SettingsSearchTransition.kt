package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.simon.harmonichackernews.ui.navigation.PredictiveBackAnimation
import kotlin.math.roundToInt

internal val LocalSettingsSearchTransition = staticCompositionLocalOf<SettingsSearchTransitionState?> { null }
internal val SettingsSearchBarHeight = 60.dp
private val SearchExpansionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Window coordinates keep the pill aligned in scrolled lists, split panes and inset hosts. */
internal class SettingsSearchTransitionState {
    var sourceBounds by mutableStateOf<Rect?>(null)
    var viewportBounds by mutableStateOf(Rect.Zero)
    var backAnimation by mutableStateOf<SettingsSearchBackAnimation?>(null)
    var expansion: () -> Float = { 1f }

    fun sourceInViewport(): Rect? = sourceBounds?.translate(-viewportBounds.topLeft)
}

internal fun Modifier.settingsSearchHeaderMotion(state: SettingsSearchTransitionState, topInset: Int): Modifier =
    layout { measurable, constraints ->
        val fraction = state.expansion()
        val source = state.sourceInViewport()
        val left = ((source?.left ?: 0f) * (1f - fraction)).roundToInt()
        val right = ((state.viewportBounds.width - (source?.right ?: state.viewportBounds.width)) *
            (1f - fraction)).roundToInt()
        val width = (constraints.maxWidth - left - right).coerceIn(0, constraints.maxWidth)
        val field = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, field.height) { field.placeRelative(left, 0) }
    }.graphicsLayer {
        translationY = ((state.sourceInViewport()?.top ?: topInset.toFloat()) - topInset) *
            (1f - state.expansion())
    }

/** Search expands as a component; ordinary settings destinations retain activity-style motion. */
@Composable
internal fun SettingsSearchTransitionViewport(
    state: SettingsSearchTransitionState,
    transition: Transition<EnterExitState>,
    skipExit: Boolean,
    content: @Composable () -> Unit,
) {
    val expansion = transition.animateFloat(
        transitionSpec = {
            if (skipExit && targetState == EnterExitState.PostExit) snap()
            else tween(if (targetState == EnterExitState.Visible) 360 else 280, easing = SearchExpansionEasing)
        },
        label = "settings search expansion",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    state.expansion = { state.backAnimation?.expansion?.value ?: expansion.value }
    val path = androidx.compose.runtime.remember { Path() }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { state.viewportBounds = it.boundsInWindow() }
            .drawWithContent {
                val source = state.sourceInViewport()
                val fraction = state.expansion().coerceIn(0f, 1f)
                if (source == null || fraction >= 1f) {
                    drawContent()
                } else if (fraction > 0f) {
                    path.reset()
                    path.addRoundRect(RoundRect(
                        left = lerp(source.left, 0f, fraction),
                        top = lerp(source.top, 0f, fraction),
                        right = lerp(source.right, size.width, fraction),
                        bottom = lerp(source.bottom, size.height, fraction),
                        cornerRadius = CornerRadius(source.height / 2f * (1f - fraction)),
                    ))
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
            },
        content = { content() },
    )
}

internal class SettingsSearchBackAnimation : PredictiveBackAnimation {
    val expansion = Animatable(1f)

    suspend fun animate(progress: Float) {
        // A modest preview keeps the field readable; release completes the return to the pill.
        expansion.snapTo(1f - SearchExpansionEasing.transform(progress.coerceIn(0f, 1f)) * 0.65f)
    }

    override suspend fun finish() {
        expansion.animateTo(0f, tween(280, easing = SearchExpansionEasing))
    }

    override suspend fun cancel() {
        expansion.animateTo(1f, tween(220, easing = SearchExpansionEasing))
    }

    override suspend fun finishImmediately() { expansion.snapTo(0f) }
}
