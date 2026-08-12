package com.simon.harmonichackernews.presentation

/** Opaque layout coordinate supplied and interpreted by the rendering layer. */
data class LayoutCoordinate(val value: Int)

/** Opaque layout distance supplied and interpreted by the rendering layer. */
data class LayoutDistance(val value: Int) {
    init {
        require(value >= 0) { "A layout distance cannot be negative" }
    }
}

/** Signed layout movement supplied and interpreted by the rendering layer. */
data class LayoutDelta(val value: Int)

/** Platform-neutral ARGB color payload at the presentation boundary. */
data class ArgbColor(val value: Int)

enum class BackGestureEdge {
    LEFT,
    RIGHT,
    UNKNOWN;

    companion object {
        fun fromLegacyValue(value: Int): BackGestureEdge = when (value) {
            0 -> LEFT
            1 -> RIGHT
            else -> UNKNOWN
        }
    }
}

data class BackGesture(
    val progress: Float = 0f,
    val edge: BackGestureEdge = BackGestureEdge.UNKNOWN,
    val pointerY: Float = 0f,
) {
    init {
        require(progress in 0f..1f) { "Back progress must be between zero and one" }
    }
}
