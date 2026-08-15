package com.simon.harmonichackernews.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Opaque-to-clear iOS safe-area material that keeps scrolling content legible. */
@Composable
internal fun IosStatusBarProtection(color: Color) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(statusBarHeight + 16.dp)
            .background(
                Brush.verticalGradient(
                    0f to color.copy(alpha = 0.96f),
                    0.58f to color.copy(alpha = 0.78f),
                    1f to color.copy(alpha = 0f),
                ),
            ),
    )
}
