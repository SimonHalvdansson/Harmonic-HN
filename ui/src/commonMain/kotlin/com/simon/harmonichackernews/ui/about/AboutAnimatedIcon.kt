package com.simon.harmonichackernews.ui.about

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/** The three splash studies, replayed only on request; the host's icon remains the idle artwork. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutAnimatedIcon(painter: Painter, modifier: Modifier = Modifier) = CompositionLocalProvider(
    LocalRippleConfiguration provides RippleConfiguration(
        color = Color(0xFF341000),
        rippleAlpha = RippleAlpha(
            draggedAlpha = 0.02f,
            focusedAlpha = 0.02f,
            hoveredAlpha = 0.01f,
            pressedAlpha = 0.02f,
        ),
    ),
) {
    val scope = rememberCoroutineScope()
    val playhead = remember { Animatable(LogoPlaybackDurationMillis.toFloat()) }
    val playback = remember { IconPlayback() }
    var motion by remember { mutableStateOf(LogoMotion.Ink) }
    val strokes = remember { logoStrokes() }
    val fragment = remember { Path() }

    Box(
        modifier.clip(CircleShape).clickable(
            role = Role.Button,
            onClickLabel = "Replay logo animation",
        ) {
            // Cycle through the studies in order without queueing animations behind earlier taps.
            playback.job?.cancel()
            playback.job = scope.launch {
                if (currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f) {
                    playhead.snapTo(LogoPlaybackDurationMillis.toFloat())
                    return@launch
                }
                motion = LogoMotion.entries[playback.nextMotionIndex]
                playback.nextMotionIndex = (playback.nextMotionIndex + 1) % LogoMotion.entries.size
                playhead.snapTo(0f)
                playhead.animateTo(
                    LogoPlaybackDurationMillis.toFloat(),
                    tween(LogoPlaybackDurationMillis, easing = LinearEasing),
                )
            }
        },
    ) {
        Image(
            painter = painter,
            contentDescription = "Harmonic app icon",
            modifier = Modifier.matchParentSize(),
        )
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                // Brief handoffs preserve the platform's launcher artwork at rest.
                alpha = minOf(playhead.value / 60f, (LogoPlaybackDurationMillis - playhead.value) / 120f)
                    .coerceIn(0f, 1f)
            },
        ) {
            drawCircle(Color(0xFFFFDBC9))
            // The splash's visible disk spans 512 design units (its padded viewport is 1024).
            withTransform({ scale(size.width / 512f, size.height / 512f, Offset.Zero) }) {
                // Stretch the original 600 ms study uniformly, including its stagger and settling.
                val time = ((playhead.value - 60f) * 600f / LogoMotionDurationMillis).coerceIn(0f, 600f)
                strokes.forEachIndexed { index, stroke ->
                    val age = time - index * when (motion) {
                        LogoMotion.Ink -> 75f
                        LogoMotion.Counterpoint -> 15f
                        LogoMotion.Gather -> 20f
                    }
                    val growth = smooth(age / if (motion == LogoMotion.Counterpoint) 470f else 440f)
                    val settle = if (motion == LogoMotion.Gather) 1f - smooth(time / 420f) else 0f
                    val center = when (motion) {
                        LogoMotion.Ink -> 0f
                        LogoMotion.Counterpoint -> if (index == 0) 0f else 1f
                        LogoMotion.Gather -> if (index == 0) 0.39f else 0.61f
                    }
                    fragment.reset()
                    stroke.getSegment(
                        stroke.length * center * (1f - growth),
                        stroke.length * (center + (1f - center) * growth),
                        fragment,
                        startWithMoveTo = true,
                    )
                    val direction = if (index == 0) -1f else 1f
                    withTransform({
                        // motion.js stores translations in the padded 1024-unit viewport.
                        translate(direction * 18f * settle, direction * 31.5f * settle)
                        rotate(direction * 13f * settle, Offset(256f, 256f))
                    }) {
                        drawPath(
                            fragment,
                            Color(0xFF341000),
                            alpha = smooth(age / 40f),
                            style = Stroke(33f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }
        }
    }
}

private class IconPlayback(var job: Job? = null, var nextMotionIndex: Int = 0)

private const val LogoMotionDurationMillis = 900
private const val LogoPlaybackDurationMillis = LogoMotionDurationMillis + 120

private enum class LogoMotion { Ink, Counterpoint, Gather }

private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

// Source geometry and timing: docs/design/splash-animation/motion.js (all three variants).
// Keep the two continuous cubic strokes and rounded ends used by the Android splash.
private fun logoStrokes(): List<PathMeasure> = listOf(
    Path().apply {
        moveTo(83f, 298f)
        cubicTo(120f, 233f, 142f, 168f, 205f, 168f)
        cubicTo(269f, 168f, 281f, 304f, 350f, 304f)
        cubicTo(390f, 304f, 410f, 248f, 429f, 214f)
    },
    Path().apply {
        moveTo(83f, 298f)
        cubicTo(113f, 245f, 129f, 207f, 159f, 207f)
        cubicTo(223f, 207f, 215f, 344f, 305f, 344f)
        cubicTo(370f, 344f, 401f, 263f, 429f, 214f)
    },
).map { path -> PathMeasure().apply { setPath(path, forceClosed = false) } }
