package com.simon.harmonichackernews.settings

/** Keep both panes usable and preserve host defaults until a ratio is explicitly chosen. */
object SplitRatioPreferences {
    const val Minimum = 0.3f
    const val Maximum = 0.7f
    val Range = Minimum..Maximum

    /** A small magnet around the crease; callers retain the raw drag position to escape it. */
    fun snapToCenter(value: Float, isFoldable: Boolean): Float =
        if (isFoldable && value in 0.47f..0.53f) 0.5f else value

    fun sanitize(value: Float): Float? =
        value.takeIf { it.isFinite() }?.coerceIn(Minimum, Maximum)
}

/** Based on the current window so resizing and rotation behave consistently on every host. */
enum class SplitOrientation {
    Portrait, Landscape;

    companion object {
        fun forWindow(width: Int, height: Int): SplitOrientation =
            if (width > height) Landscape else Portrait
    }
}
