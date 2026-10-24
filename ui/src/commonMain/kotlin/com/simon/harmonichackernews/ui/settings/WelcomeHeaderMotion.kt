package com.simon.harmonichackernews.ui.settings

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Same design coordinates and clock as tools/welcome-animation/motion.mjs. */
internal object WelcomeHeaderMotion {
    const val Arrival = 1.2f
    const val Drift = 1.3f
    const val RotationSeconds = 35f
    const val SettledMillis = 1200f * Arrival

    enum class Shape(val x: Float, val y: Float, val cue: Float) {
        Conversation(91f, 66f, 130f),
        Orbit(104f, 143f, 340f),
        Reply(269f, 124f, 240f),
        Spark(266f, 46f, 430f),
        Bloom(180f, 96f, 0f),
    }

    data class Pose(
        val alpha: Float, val scale: Float, val enterY: Float, val enterAngle: Float,
        val x: Float, val y: Float, val angle: Float,
    )

    // A negative playhead represents reduced motion: complete artwork, no moving layers.
    fun pose(shape: Shape, millis: Double): Pose {
        if (millis < 0) return Pose(1f, 1f, 0f, 0f, 0f, 0f, 0f)
        val p = ((millis - shape.cue * Arrival) / (600 * Arrival)).toFloat().coerceIn(0f, 1f)
        val arrival = when (p) {
            0f -> 0f
            1f -> 1f
            else -> 1f - exp(-8f * p) * (cos(10f * p) + .8f * sin(10f * p))
        }
        val idle = (millis - SettledMillis).coerceAtLeast(0.0)
        val ramp = smooth((idle / 1400).toFloat()) * Drift
        val spinRamp = (idle / 1400).coerceIn(0.0, 1.0)
        val spinTime = if (idle < 1400) 1400 * (spinRamp * spinRamp * spinRamp - .5 * spinRamp * spinRamp * spinRamp * spinRamp) else idle - 700
        fun wave(period: Double) = sin(idle / 1000 * 2 * PI / period).toFloat()
        var x = 0f
        var y = 0f
        var angle = 0f
        when (shape) {
            Shape.Bloom -> { y = -1.4f * wave(10.0); angle = (spinTime / (RotationSeconds * 1000) * 360).toFloat() }
            Shape.Conversation -> { x = 1.8f * wave(10.0); y = -2.8f * wave(7.5); angle = -2f * wave(10.0) }
            Shape.Reply -> { x = -1.5f * wave(7.5); y = 2.4f * wave(10.0); angle = 2f * wave(7.5) }
            Shape.Orbit -> { x = 1.6f * wave(6.0); y = -2f * wave(10.0) }
            Shape.Spark -> { y = -1.8f * wave(6.0); angle = 7f * wave(10.0) }
        }
        return Pose(
            alpha = smooth(p / .24f), scale = .65f + .35f * arrival,
            enterY = 14f * (1f - arrival),
            enterAngle = (if (shape == Shape.Reply) 8f else -8f) * (1f - arrival),
            x = x * ramp, y = y * ramp,
            angle = if (shape == Shape.Bloom) angle else angle * ramp,
        )
    }

    fun textAlpha(millis: Double): Float = if (millis < 0) 1f else smooth(((millis - 210 * Arrival) / (460 * Arrival)).toFloat())
    private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}
