package com.simon.harmonichackernews.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import com.simon.harmonichackernews.ui.common.consumeAllPointerGestures
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlinx.coroutines.flow.first

/** Identities are captured at gesture start, so a committed pop cannot move the new top away. */
internal data class ActivityBackPreview<K : Any>(
    val source: K,
    val parent: K?,
    val enterModifier: Modifier,
    val exitModifier: Modifier,
)

private class ActivityStackLayer<T, K : Any>(
    entry: T,
    val key: K,
    initiallyVisible: Boolean,
) {
    var entry by mutableStateOf(entry)
    val visibility = MutableTransitionState(initiallyVisible).apply { targetState = true }
    var exitCompleted by mutableStateOf(false)
}

/**
 * One lifetime and rendering policy for full-screen stacks, including nested Settings.
 * Entries keep their composition identity until their exit finishes. A predictive pop latches
 * completion on the outgoing entry; clearing the host's gesture cannot replay its exit.
 * The ordered layers are the sole source of z-order, including while removed entries exit.
 */
@Composable
internal fun <T, K : Any> ActivityNavigationStack(
    entries: List<T>,
    entryKey: (T) -> K,
    completedPredictiveBack: Boolean = false,
    root: @Composable () -> Unit,
    content: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    preview: ActivityBackPreview<K>? = null,
    completedExitKeys: Set<K> = emptySet(),
    fadeOnly: (T) -> Boolean = { false },
    hideCoveredRoot: Boolean = false,
    reflowKey: Any? = Unit,
) {
    // Reflow changes pane ownership, not navigation history. Reconcile it without retained exits.
    val layers = remember(reflowKey) {
        mutableStateListOf<ActivityStackLayer<T, K>>().apply {
            entries.forEach { entry ->
                add(ActivityStackLayer(entry, entryKey(entry), initiallyVisible = true))
            }
        }
    }
    val keys = entries.map(entryKey)
    val completingPop = completedPredictiveBack || layers.any {
        it.key in completedExitKeys && it.key !in keys
    }
    LaunchedEffect(entries, completedPredictiveBack, completedExitKeys) {
        entries.forEach { entry ->
            val retained = layers.firstOrNull { it.key == entryKey(entry) }
            if (retained == null) {
                layers += ActivityStackLayer(entry, entryKey(entry), initiallyVisible = false)
            } else {
                retained.entry = entry
            }
        }
        layers.forEach { layer ->
            val inStack = layer.key in keys
            if ((completedPredictiveBack || layer.key in completedExitKeys) && !inStack) layer.exitCompleted = true
            layer.visibility.targetState = inStack
        }
    }
    val offsetPx = with(LocalDensity.current) { ActivityNavigationTransitionOffset.roundToPx() }
    val rootOffset by animateFloatAsState(
        targetValue = if (entries.isNotEmpty() && !fadeOnly(entries.last())) -offsetPx.toFloat() else 0f,
        animationSpec = if (
            completingPop || (entries.isEmpty() && layers.isEmpty())
        ) snap() else tween(
            ActivityNavigationTransitionDurationMillis, easing = activityNavigationEasing(),
        ),
        label = "navigation root offset",
    )
    // A requested entry is not necessarily drawing yet. Its composition and enter transition
    // can begin on later frames, so a timer from the stack update can hide the source too soon.
    // Retain the source until the actual covering layer has finished entering.
    val coveringLayer = layers.firstOrNull { it.key == keys.lastOrNull() }
    val rootCovered = hideCoveredRoot && preview == null &&
        !completingPop && coveringLayer?.visibility?.let {
            it.isIdle && it.currentState && it.targetState
        } == true
    val rootIsPreviewParent = preview != null && preview.parent == null
    val background = HarmonicTheme.colors.background
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .drawWithContent { if (!rootCovered && (preview == null || rootIsPreviewParent)) drawContent() }
                .then(if (rootIsPreviewParent) Modifier.background(background) else Modifier)
                .graphicsLayer {
                    translationX = if (preview == null && !completingPop) rootOffset else 0f
                }
                .then(if (entries.isEmpty()) Modifier else Modifier.clearAndSetSemantics { }),
        ) {
            Box(Modifier.fillMaxSize().then(if (rootIsPreviewParent) preview.enterModifier else Modifier)) {
                root()
            }
        }
        layers.forEachIndexed { index, layer ->
            // Key the whole layer, including AnimatedVisibility, to preserve native views and scroll.
            key(layer.key) {
                val inStack = layer.key in keys
                val isCurrent = layer.key == keys.lastOrNull()
                val completedExit = layer.exitCompleted || ((completedPredictiveBack || layer.key in completedExitKeys) && !inStack)
                val skipExit = completingPop || layer.exitCompleted
                val usesFade = fadeOnly(layer.entry)
                val offset by animateFloatAsState(
                    targetValue = if (inStack && !isCurrent && entries.lastOrNull()?.let(fadeOnly) != true) -offsetPx.toFloat() else 0f,
                    animationSpec = if (skipExit) snap() else tween(
                        ActivityNavigationTransitionDurationMillis, easing = activityNavigationEasing(),
                    ),
                    label = "navigation entry offset",
                )
                val backModifier = when (layer.key) {
                    preview?.source -> preview.exitModifier
                    preview?.parent -> preview.enterModifier
                    else -> Modifier
                }
                LaunchedEffect(layer) {
                    snapshotFlow {
                        layer.visibility.isIdle && !layer.visibility.currentState && !layer.visibility.targetState
                    }.first { it }
                    layers.remove(layer)
                }
                AnimatedVisibility(
                    visibleState = layer.visibility,
                    modifier = Modifier.fillMaxSize().zIndex(index + 1f)
                        .graphicsLayer { alpha = if (completedExit) 0f else 1f }
                        .drawWithContent {
                            if (preview == null || layer.key == preview.source || layer.key == preview.parent) {
                                drawContent()
                            }
                        }
                        // Only the immediate predecessor is revealed; older pages cannot leak at its edges.
                        .then(if (layer.key == preview?.parent) Modifier.background(background) else Modifier)
                        .then(if (isCurrent) Modifier else Modifier.clearAndSetSemantics { }),
                    enter = if (usesFade) fadeIn(tween(220)) else EnterTransition.None,
                    exit = if (usesFade && !skipExit) fadeOut(tween(180)) else ExitTransition.None,
                ) {
                    val surface: @Composable () -> Unit = {
                        Box(Modifier.fillMaxSize().background(background)) {
                            // An opaque surface alone does not consume taps in blank areas.
                            Box(Modifier.fillMaxSize().consumeAllPointerGestures())
                            content(layer.entry)
                        }
                    }
                    if (usesFade) {
                        surface()
                    } else {
                        ActivityNavigationTransitionViewport(
                            transition = transition,
                            transitionOffsetPx = offsetPx,
                            baseTranslationX = if (preview == null && !skipExit) offset else 0f,
                            skipExitAnimation = skipExit,
                            modifier = Modifier.fillMaxSize(),
                            contentModifier = backModifier,
                            content = surface,
                        )
                    }
                }
            }
        }
        if (preview != null) {
            Box(Modifier.fillMaxSize().zIndex(Float.MAX_VALUE).consumeAllPointerGestures())
        }
    }
}
