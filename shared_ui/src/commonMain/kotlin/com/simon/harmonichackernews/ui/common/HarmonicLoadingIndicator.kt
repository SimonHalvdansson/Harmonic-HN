package com.simon.harmonichackernews.ui.common

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Shared Material 3 Expressive loading treatment. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HarmonicLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    if (color == Color.Unspecified) {
        LoadingIndicator(modifier = modifier)
    } else {
        LoadingIndicator(modifier = modifier, color = color)
    }
}
