package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Platform loading treatment. iOS avoids Material 3's unsupported polygon morph implementation. */
@Composable
expect fun HarmonicLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
)
