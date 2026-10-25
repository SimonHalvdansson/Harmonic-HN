package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp

/** Blocks card-body taps while treating its transparent rounded corners as outside taps.
 * Only input is affected: clipping this container would also clip transition layers and shadows.
 */
@Composable
internal fun Modifier.modalCardTapBoundary(
    cornerRadius: Dp,
    onOutsideTap: () -> Unit,
): Modifier {
    val outsideTap by rememberUpdatedState(onOutsideTap)
    return pointerInput(cornerRadius) {
        detectTapGestures { position ->
            val radius = cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
            val card = RoundRect(0f, 0f, size.width.toFloat(), size.height.toFloat(), CornerRadius(radius))
            if (position !in card) outsideTap()
        }
    }
}
