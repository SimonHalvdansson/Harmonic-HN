package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** Darkens a control drawn above modal transition content without changing its layer order. */
@Composable
fun BoxScope.ModalControlScrim(
    alpha: Float,
    shape: Shape,
    active: Boolean = alpha > 0f,
    onDismiss: (() -> Unit)? = null,
) {
    if (!active && alpha <= 0f) return
    Spacer(
        Modifier
            .matchParentSize()
            .clip(shape)
            .background(Color.Black.copy(alpha = alpha.coerceIn(0f, 1f)))
            .then(when {
                !active -> Modifier
                onDismiss != null -> Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                else -> Modifier.consumeAllPointerGestures()
            }),
    )
}
