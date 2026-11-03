/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrain
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Adapts Compose's animateContentSize measurement pattern for modal content. Changes in available
 * space already follow live IME/window insets and must not start another trailing animation.
 * Apply inside the elevated Surface so clipping does not cut off its shadow.
 */
internal fun Modifier.animateSettingsDialogContentSize(): Modifier =
    clipToBounds().then(SettingsDialogSizeAnimationElement())

private data class SettingsDialogSizeAnimationElement(
    val durationMillis: Int = 280,
) : ModifierNodeElement<SettingsDialogSizeAnimationNode>() {
    override fun create() = SettingsDialogSizeAnimationNode(durationMillis)

    override fun update(node: SettingsDialogSizeAnimationNode) {
        node.durationMillis = durationMillis
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "animateSettingsDialogContentSize"
    }
}

private class SettingsDialogSizeAnimationNode(
    var durationMillis: Int,
) : Modifier.Node(), LayoutModifierNode {
    private var previousConstraints: Constraints? = null
    private var animation: Animatable<IntSize, AnimationVector2D>? = null
    private var animationJob: Job? = null
    private var requestedSize: IntSize? = null

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        val measuredSize = IntSize(placeable.width, placeable.height)
        if (isLookingAhead) {
            return layout(measuredSize.width, measuredSize.height) { placeable.placeRelative(0, 0) }
        }

        val current = if (animation == null || previousConstraints != constraints) {
            // A fresh animation starts at the actual available size. Cancelling and replacing the
            // old instance prevents a cancelled job's final frame from changing the new size.
            animationJob?.cancel()
            animationJob = null
            Animatable(measuredSize, IntSize.VectorConverter, IntSize(1, 1)).also {
                animation = it
                requestedSize = measuredSize
            }
        } else {
            animation!!
        }
        previousConstraints = constraints

        if (measuredSize != requestedSize) {
            // Store the target before dispatching: several measures can precede the coroutine's
            // first frame, and they must not keep restarting the same animation.
            requestedSize = measuredSize
            animationJob?.cancel()
            animationJob = coroutineScope.launch {
                current.animateTo(measuredSize, tween(durationMillis, easing = FastOutSlowInEasing))
            }
        }
        val displayedSize = constraints.constrain(current.value)
        return layout(displayedSize.width, displayedSize.height) { placeable.placeRelative(0, 0) }
    }

    override fun onReset() {
        resetAnimation()
    }

    override fun onDetach() {
        resetAnimation()
    }

    private fun resetAnimation() {
        animationJob?.cancel()
        animationJob = null
        animation = null
        previousConstraints = null
        requestedSize = null
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.maxIntrinsicHeight(width)
}
