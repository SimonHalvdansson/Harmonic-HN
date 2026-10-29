package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch

internal val LocalStatusBarScrollToTopRegistry = staticCompositionLocalOf<StatusBarScrollToTopRegistry?> { null }
internal val LocalStatusBarScrollToTopEnabled = staticCompositionLocalOf { true }

internal class StatusBarScrollToTopTarget(val scroll: () -> Unit) {
    var bounds = Rect.Zero
    var enabled = false
    var priority = 0
}

internal class StatusBarScrollToTopRegistry {
    val targets = mutableListOf<StatusBarScrollToTopTarget>()
    var onChanged: () -> Unit = {}
}

/** Select the foremost target in each pane, including targets already at the top. */
internal fun statusBarScrollToTopTargets(
    targets: List<StatusBarScrollToTopTarget>,
    viewport: Rect,
    multiplePanes: Boolean,
): List<StatusBarScrollToTopTarget> {
    val selected = mutableListOf<StatusBarScrollToTopTarget>()
    for (target in targets.asReversed().sortedByDescending { it.priority }) {
        val bounds = target.bounds.intersect(viewport)
        if (!target.enabled || bounds.width <= 0f || bounds.height <= 0f) continue
        if (selected.any { other ->
                val otherBounds = other.bounds.intersect(viewport)
                bounds.left < otherBounds.right && bounds.right > otherBounds.left
            }) continue
        selected += target
        // UIKit requires exactly one scrollsToTop scroll view on iPhone.
        if (!multiplePanes) break
    }
    return selected
}

/** Hosts without a native status-bar gesture leave the registry unset. */
@Composable
internal fun Modifier.statusBarScrollToTop(
    enabled: Boolean = true,
    priority: Int = 0,
    onScrollToTop: suspend () -> Unit,
): Modifier {
    val registry = LocalStatusBarScrollToTopRegistry.current ?: return this
    val scope = rememberCoroutineScope()
    val currentScroll by rememberUpdatedState(onScrollToTop)
    val target = remember(registry) { StatusBarScrollToTopTarget { scope.launch { currentScroll() } } }
    val active = enabled && LocalStatusBarScrollToTopEnabled.current
    DisposableEffect(registry, target) {
        registry.targets += target
        registry.onChanged()
        onDispose {
            registry.targets -= target
            registry.onChanged()
        }
    }
    SideEffect {
        if (target.enabled != active || target.priority != priority) {
            target.enabled = active
            target.priority = priority
            registry.onChanged()
        }
    }
    return onGloballyPositioned {
        val bounds = it.boundsInWindow()
        if (target.bounds != bounds) {
            target.bounds = bounds
            registry.onChanged()
        }
    }
}
