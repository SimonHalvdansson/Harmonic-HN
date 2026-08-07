package com.simon.harmonichackernews.ui.debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val ParticleCount = 1500
private const val ColorSteps = 96
private const val MaxTouchCharges = 32
private const val TimeStep = 75f / 30000f
private const val VelocityDamping = 0.8f
private const val Softening = 1e-6f
private const val WorldToView = 0.35f
private const val TouchCharge = 1000f

private data class GasFrame(
    val x: FloatArray,
    val y: FloatArray,
    val colorIndices: IntArray,
)

/** Compose Canvas version of the full-screen Ginibre log-gas Easter egg. */
@Composable
internal fun CoulombGasScreen() {
    val colors = HarmonicTheme.colors
    val lightTheme = colors.background.luminance() > colors.onSurface.luminance()
    val density = LocalDensity.current.density
    val palette = remember(lightTheme) { createPalette(lightTheme) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val simulation = remember(canvasSize) {
        canvasSize.takeIf { it.width > 0 && it.height > 0 }
            ?.let(::CoulombGasSimulation)
    }
    var frame by remember(simulation) { mutableStateOf<GasFrame?>(null) }

    LaunchedEffect(simulation) {
        val activeSimulation = simulation ?: return@LaunchedEffect
        while (isActive) {
            val frameStart = System.nanoTime()
            val nextFrame = withContext(Dispatchers.Default) {
                activeSimulation.step()
                activeSimulation.snapshot()
            }
            frame = nextFrame
            val elapsedMillis = (System.nanoTime() - frameStart) / 1_000_000L
            delay((16L - elapsedMillis).coerceAtLeast(1L))
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(simulation, canvasSize) {
                val activeSimulation = simulation ?: return@pointerInput
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        activeSimulation.setTouchCharges(
                            pressed.map { it.position },
                            canvasSize,
                        )
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    activeSimulation.clearTouchCharges()
                }
            },
    ) {
        drawRect(if (lightTheme) Color.White else Color.Black)
        val currentFrame = frame ?: return@Canvas
        val scale = min(size.width, size.height) * WorldToView
        val centerX = size.width * 0.5f
        val centerY = size.height * 0.5f
        val radius = max(2f, 1.4f * density)
        repeat(ParticleCount) { index ->
            drawCircle(
                color = palette[currentFrame.colorIndices[index]],
                radius = radius,
                center = Offset(
                    x = centerX + currentFrame.x[index] * scale,
                    y = centerY - currentFrame.y[index] * scale,
                ),
            )
        }
    }
}

private fun createPalette(lightTheme: Boolean): List<Color> {
    val brightness = if (lightTheme) 0.78f else 1f
    return List(ColorSteps) { index ->
        val progress = index / (ColorSteps - 1f)
        val hue = 225f * (1f - progress)
        Color.hsv(
            hue = hue,
            saturation = 0.88f,
            value = brightness * progress,
        )
    }
}

private class CoulombGasSimulation(size: IntSize) {
    private val x = FloatArray(ParticleCount)
    private val y = FloatArray(ParticleCount)
    private val velocityX = FloatArray(ParticleCount)
    private val velocityY = FloatArray(ParticleCount)
    private val accelerationX = FloatArray(ParticleCount)
    private val accelerationY = FloatArray(ParticleCount)
    private val colorIndices = IntArray(ParticleCount)
    private val touchLock = Any()
    private val touchX = FloatArray(MaxTouchCharges)
    private val touchY = FloatArray(MaxTouchCharges)
    private val simulationTouchX = FloatArray(MaxTouchCharges)
    private val simulationTouchY = FloatArray(MaxTouchCharges)
    private var touchCount = 0
    private var speedColorScale = 1f

    init {
        val random = Random.Default
        val scale = min(size.width, size.height) * WorldToView
        val halfWidth = size.width * 0.5f / scale
        val halfHeight = size.height * 0.5f / scale
        repeat(ParticleCount) { index ->
            x[index] = (random.nextFloat() * 2f - 1f) * halfWidth
            y[index] = (random.nextFloat() * 2f - 1f) * halfHeight
        }
    }

    fun step() {
        val confinement = -2f * ParticleCount
        repeat(ParticleCount) { index ->
            accelerationX[index] = confinement * x[index]
            accelerationY[index] = confinement * y[index]
        }

        for (first in 0 until ParticleCount - 1) {
            val firstX = x[first]
            val firstY = y[first]
            for (second in first + 1 until ParticleCount) {
                val dx = firstX - x[second]
                val dy = firstY - y[second]
                val forceScale = 2f / (dx * dx + dy * dy + Softening)
                val forceX = dx * forceScale
                val forceY = dy * forceScale
                accelerationX[first] += forceX
                accelerationY[first] += forceY
                accelerationX[second] -= forceX
                accelerationY[second] -= forceY
            }
        }

        val activeTouchCount = synchronized(touchLock) {
            touchX.copyInto(simulationTouchX, endIndex = touchCount)
            touchY.copyInto(simulationTouchY, endIndex = touchCount)
            touchCount
        }
        repeat(activeTouchCount) { charge ->
            repeat(ParticleCount) { index ->
                val dx = x[index] - simulationTouchX[charge]
                val dy = y[index] - simulationTouchY[charge]
                val forceScale = TouchCharge / (dx * dx + dy * dy + Softening)
                accelerationX[index] += dx * forceScale
                accelerationY[index] += dy * forceScale
            }
        }

        var speedSquareSum = 0f
        repeat(ParticleCount) { index ->
            val nextVelocityX = velocityX[index] + accelerationX[index] * TimeStep
            val nextVelocityY = velocityY[index] + accelerationY[index] * TimeStep
            x[index] += nextVelocityX * TimeStep
            y[index] += nextVelocityY * TimeStep
            velocityX[index] = nextVelocityX * VelocityDamping
            velocityY[index] = nextVelocityY * VelocityDamping
            speedSquareSum += velocityX[index] * velocityX[index] +
                velocityY[index] * velocityY[index]
        }
        val targetScale = max(0.05f, 2f * sqrt(speedSquareSum / ParticleCount))
        speedColorScale += (targetScale - speedColorScale) * 0.04f
        repeat(ParticleCount) { index ->
            val speed = sqrt(
                velocityX[index] * velocityX[index] +
                    velocityY[index] * velocityY[index],
            )
            val normalized = min(1f, speed / speedColorScale)
            colorIndices[index] = min(
                ColorSteps - 1,
                (normalized * (ColorSteps - 1)).toInt(),
            )
        }
    }

    fun snapshot(): GasFrame = GasFrame(
        x = x.copyOf(),
        y = y.copyOf(),
        colorIndices = colorIndices.copyOf(),
    )

    fun setTouchCharges(positions: List<Offset>, size: IntSize) {
        val scale = min(size.width, size.height) * WorldToView
        if (scale <= 0f) return
        synchronized(touchLock) {
            touchCount = min(positions.size, MaxTouchCharges)
            repeat(touchCount) { index ->
                touchX[index] = (positions[index].x - size.width * 0.5f) / scale
                touchY[index] = (size.height * 0.5f - positions[index].y) / scale
            }
        }
    }

    fun clearTouchCharges() {
        synchronized(touchLock) { touchCount = 0 }
    }
}
