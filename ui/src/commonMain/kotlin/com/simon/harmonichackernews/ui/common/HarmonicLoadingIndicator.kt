package com.simon.harmonichackernews.ui.common

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Shared Material Expressive indicator for loading and pull-to-refresh progress. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HarmonicLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    progress: (() -> Float)? = null,
) {
    if (progress != null) {
        if (color == Color.Unspecified) {
            LoadingIndicator(progress = progress, modifier = modifier)
        } else {
            LoadingIndicator(progress = progress, modifier = modifier, color = color)
        }
    } else if (color == Color.Unspecified) {
        LoadingIndicator(modifier = modifier)
    } else {
        LoadingIndicator(modifier = modifier, color = color)
    }
}
