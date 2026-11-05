package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.core.DeferredTargetAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.approachLayout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.round

internal const val StorySettingsMotionDurationMillis = 360

/** Animate the glyphs without changing the destination wrapping on every frame. */
@Composable
internal fun Modifier.animateStoryRowTextSize(animation: StoryRowPlacementAnimation?, targetSize: Float): Modifier {
    if (animation == null) return this
    val size = animateFloatAsState(targetSize,
        tween(StorySettingsMotionDurationMillis, easing = ContentMotionEasing), label = "story settings text size")
    return graphicsLayer {
        scaleX = size.value / targetSize
        scaleY = scaleX
        transformOrigin = TransformOrigin(0f, 0f)
    }
}

/** Created only by the settings preview. Feed rows never allocate slots or run lookahead. */
class StoryRowPlacementAnimation internal constructor(
    internal val scope: LookaheadScope,
    internal val visibility: StorySettingsVisibility? = null,
) {
    private val slots = mutableMapOf<String, @Composable (@Composable () -> Unit) -> Unit>()

    @Composable
    internal fun Content(key: String, animateSize: Boolean, content: @Composable () -> Unit) {
        val slot = slots.getOrPut(key) {
            movableContentOf { render: @Composable () -> Unit ->
                val motion = Modifier.animateStoryRowPlacement(this, animateSize, centered = key == "comments")
                Box(motion.testTag("story-motion-$key")
                    .storySettingsAlpha(this, if (key.startsWith("category-")) "categories" else key),
                    propagateMinConstraints = true) { render() }
            }
        }
        slot(content)
    }
}

@Composable
internal fun Modifier.animateStoryRowPlacement(
    animation: StoryRowPlacementAnimation?,
    animateSize: Boolean = false,
    centered: Boolean = false,
): Modifier {
    if (animation == null) return this
    val offset = remember { DeferredTargetAnimation(IntOffset.VectorConverter) }
    val size = if (animateSize) remember { DeferredTargetAnimation(IntSize.VectorConverter) } else null
    val coroutineScope = rememberCoroutineScope()
    val nativeMetadata = animation.visibility?.metadataAnimating == true
    val spec: FiniteAnimationSpec<IntOffset> = if (nativeMetadata) snap() else
        remember { tween(StorySettingsMotionDurationMillis, easing = ContentMotionEasing) }
    val sizeSpec: FiniteAnimationSpec<IntSize> = if (nativeMetadata) snap() else
        remember { tween(StorySettingsMotionDurationMillis, easing = ContentMotionEasing) }
    return with(animation.scope) {
        // Start both targets in the same lookahead pass. Text keeps its destination wrapping;
        // the retained image also measures at the interpolated size so its crop stays continuous.
        approachLayout(
            isMeasurementApproachInProgress = { target ->
                size?.updateTarget(target, coroutineScope, sizeSpec)
                size?.isIdle == false
            },
            isPlacementApproachInProgress = { destination ->
                val center = if (centered) IntOffset(destination.size.width / 2, destination.size.height / 2) else IntOffset.Zero
                offset.updateTarget(lookaheadScopeCoordinates.localLookaheadPositionOf(destination).round() + center, coroutineScope, spec)
                !offset.isIdle
            },
        ) { measurable, constraints ->
            val animatedSize = size?.updateTarget(lookaheadSize, coroutineScope, sizeSpec)
            val child = measurable.measure(animatedSize?.let { Constraints.fixed(it.width, it.height) } ?: constraints)
            layout(child.width, child.height) {
                val coordinates = coordinates
                if (coordinates == null) child.place(0, 0) else {
                    val center = if (centered) IntOffset(child.width / 2, child.height / 2) else IntOffset.Zero
                    val target = lookaheadScopeCoordinates.localLookaheadPositionOf(coordinates).round() + center
                    val position = offset.updateTarget(target, coroutineScope, spec)
                    val origin = lookaheadScopeCoordinates.localPositionOf(coordinates, Offset.Zero).round()
                    val delta = position - origin - center
                    child.place(delta.x, delta.y)
                }
            }
        }
    }
}

@Composable
internal fun StoryRowMovingContent(
    animation: StoryRowPlacementAnimation?,
    key: String,
    animateSize: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (animation == null) content() else animation.Content(key, animateSize, content)
}
