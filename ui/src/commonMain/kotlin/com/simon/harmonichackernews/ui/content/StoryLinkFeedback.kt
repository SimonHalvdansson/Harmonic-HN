package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.LayoutDirection

/** One link ripple, clipped to the text/image area and any full-width metadata footer. */
internal class StoryLinkFeedback {
    var main by mutableStateOf(Rect.Zero)
    var footer by mutableStateOf<Rect?>(null)
}

internal fun Modifier.clipStoryLinkFeedback(geometry: StoryLinkFeedback): Modifier = drawWithCache {
    fun physicalBounds(bounds: Rect): Rect = if (layoutDirection == LayoutDirection.Rtl) {
        Rect(size.width - bounds.right, bounds.top, size.width - bounds.left, bounds.bottom)
    } else bounds
    val path = Path().apply {
        addRect(physicalBounds(geometry.main))
        geometry.footer?.let { addRect(physicalBounds(it)) }
    }
    onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
}
