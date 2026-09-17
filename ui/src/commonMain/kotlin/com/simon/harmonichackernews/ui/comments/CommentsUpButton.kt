package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simon.harmonichackernews.ui.common.TranslucentBackButton

/** A fixed, host-positioned escape hatch from comments back to the stories destination. */
@Composable
fun CommentsUpButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    modalScrimAlpha: Float = 0f,
    modalScrimActive: Boolean = modalScrimAlpha > 0f,
) = TranslucentBackButton(
    onClick = onClick,
    modifier = modifier,
    modalScrimAlpha = modalScrimAlpha,
    modalScrimActive = modalScrimActive,
)
