package com.simon.harmonichackernews.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
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
import androidx.compose.runtime.rememberUpdatedState
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
 * Root visibility is independent of input/accessibility and is retained with an outgoing run.
 */
@Composable
internal fun <T, K : Any> ActivityNavigationStack(
    entries: List<T>,
    entryKey: (T) -> K,
    completedPredictiveBack: Boolean,
    root: @Composable () -> Unit,
    content: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    rootVisible: Boolean = true,
    preview: ActivityBackPreview<K>? = null,
    animateInitialEntry: Boolean = false,
    replaceDisjointEntries: Boolean = false,
    hideCoveredRoot: Boolean = false,
    animateRootOnPop: Boolean = true,
    onLayersEmpty: () -> Unit = {},
) {
    val layers = remember {
        mutableStateListOf<ActivityStackLayer<T, K>>().apply {
            entries.forEachIndexed { index, entry ->
                add(ActivityStackLayer(entry, entryKey(entry), !animateInitialEntry || index < entries.lastIndex))
            }
        }
    }
    val keys = entries.map(entryKey)
    val currentEntries by rememberUpdatedState(entries)
    val notifyLayersEmpty by rememberUpdatedState(onLayersEmpty)
    val replacesRun = replaceDisjointEntries && keys.isNotEmpty() &&
        layers.none { it.key in keys }
    var retainedRootVisible by remember { mutableStateOf(rootVisible) }
    val drawRoot = if (keys.isEmpty() && layers.isNotEmpty()) retainedRootVisible else rootVisible
    LaunchedEffect(entries, rootVisible, completedPredictiveBack) {
        if (entries.isNotEmpty()) retainedRootVisible = rootVisible
        if (replacesRun) layers.removeAll { it.key !in keys }
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
            if (completedPredictiveBack && !inStack) layer.exitCompleted = true
            layer.visibility.targetState = inStack
        }
    }
    val offsetPx = with(LocalDensity.current) { ActivityNavigationTransitionOffset.roundToPx() }
    val rootOffset by animateFloatAsState(
        targetValue = if (rootVisible && entries.isNotEmpty()) -offsetPx.toFloat() else 0f,
        animationSpec = if (
            completedPredictiveBack || (entries.isEmpty() && (!animateRootOnPop || layers.isEmpty()))
        ) snap() else tween(
            ActivityNavigationTransitionDurationMillis, easing = activityNavigationEasing(),
        ),
        label = "navigation root offset",
    )
    // A requested entry is not necessarily drawing yet. Its composition and enter transition
    // can begin on later frames, so a timer from the stack update can hide the source too soon.
    // Retain the source until the actual covering layer has finished entering.
    val coveringLayer = layers.firstOrNull { it.key == keys.lastOrNull() }
    val rootCovered = hideCoveredRoot && rootVisible && preview == null &&
        !completedPredictiveBack && coveringLayer?.visibility?.let {
            it.isIdle && it.currentState && it.targetState
        } == true
    val rootIsPreviewParent = preview != null && preview.parent == null && drawRoot
    val background = HarmonicTheme.colors.background
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .drawWithContent { if (drawRoot && !rootCovered) drawContent() }
                .then(if (rootIsPreviewParent) Modifier.background(background) else Modifier)
                .graphicsLayer {
                    translationX = if (preview == null && !completedPredictiveBack) rootOffset else 0f
                }
                .then(if (drawRoot && entries.isEmpty()) Modifier else Modifier.clearAndSetSemantics { }),
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
                val completedExit = layer.exitCompleted || (completedPredictiveBack && !inStack)
                val skipExit = completedPredictiveBack || layer.exitCompleted
                val offset by animateFloatAsState(
                    targetValue = if (inStack && !isCurrent) -offsetPx.toFloat() else 0f,
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
                    // Retire the host's scene and its parent z-order in the same update as
                    // the final layer. Reporting this from a later composition exposes the
                    // Stories root for a frame while Settings is still behind the old scene.
                    if (layers.isEmpty() && currentEntries.isEmpty()) notifyLayersEmpty()
                }
                AnimatedVisibility(
                    visibleState = layer.visibility,
                    modifier = Modifier.fillMaxSize().zIndex(index + 1f)
                        .graphicsLayer { alpha = if (completedExit || (replacesRun && !inStack)) 0f else 1f }
                        // Only the immediate predecessor is revealed; older pages cannot leak at its edges.
                        .then(if (layer.key == preview?.parent) Modifier.background(background) else Modifier)
                        .then(if (isCurrent) Modifier else Modifier.clearAndSetSemantics { }),
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                ) {
                    ActivityNavigationTransitionViewport(
                        transition = transition,
                        transitionOffsetPx = offsetPx,
                        baseTranslationX = if (preview == null && !skipExit) offset else 0f,
                        skipExitAnimation = skipExit,
                        modifier = Modifier.fillMaxSize(),
                        contentModifier = backModifier,
                    ) {
                        Box(Modifier.fillMaxSize().background(background)) { content(layer.entry) }
                    }
                }
            }
        }
        if (preview != null) {
            Box(Modifier.fillMaxSize().zIndex(Float.MAX_VALUE).consumeAllPointerGestures())
        }
    }
}
