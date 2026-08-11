package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Uses the platform's newest Material 3 loading treatment when it is available. */
@Composable
expect fun HarmonicLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
)
