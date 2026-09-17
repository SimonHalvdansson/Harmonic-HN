package com.simon.harmonichackernews.ui.common

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun HarmonicLoadingIndicator(
    modifier: Modifier,
    color: Color,
) {
    if (color == Color.Unspecified) {
        CircularProgressIndicator(modifier = modifier)
    } else {
        CircularProgressIndicator(modifier = modifier, color = color)
    }
}
