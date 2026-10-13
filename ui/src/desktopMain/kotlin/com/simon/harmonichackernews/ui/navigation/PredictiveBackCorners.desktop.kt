package com.simon.harmonichackernews.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun Modifier.withLayoutCorners(
    block: @Composable Modifier.(LayoutCorners) -> Modifier,
): Modifier = block(LayoutCorners())
