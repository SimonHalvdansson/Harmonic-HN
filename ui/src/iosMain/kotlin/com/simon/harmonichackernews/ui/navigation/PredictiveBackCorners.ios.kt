package com.simon.harmonichackernews.ui.navigation

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

internal actual class LayoutCornerResolver actual constructor() {
    actual fun resolve(node: CompositionLocalConsumerModifierNode): LayoutCorners = LayoutCorners()
}
