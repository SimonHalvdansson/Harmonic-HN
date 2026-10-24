package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.simon.harmonichackernews.ui.theme.GoogleSansFlexRoundedFontFamily
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.ui.common.setHarmonicLogoStroke
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun rememberWelcomeAnimationTime(): State<Double> {
    val time = rememberSaveable { mutableDoubleStateOf(0.0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        val motionScale = currentCoroutineContext()[MotionDurationScale]
        var elapsed = time.doubleValue.coerceAtLeast(0.0)
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { motionScale?.scaleFactor ?: 1f }.collectLatest { durationScale ->
                if (durationScale == 0f) {
                    time.doubleValue = -1.0
                } else {
                    var previous = withFrameNanos { it }
                    while (currentCoroutineContext().isActive) {
                        val now = withFrameNanos { it }
                        elapsed += (now - previous) / 1_000_000.0 / durationScale
                        previous = now
                        time.doubleValue = elapsed
                    }
                }
            }
        }
    }
    return time
}

@Composable
internal fun WelcomeAnimatedHeader(
    time: State<Double>,
    title: String = "Welcome to Harmonic",
    subtitle: String = "Choose a style to get started.",
    launcherIcon: @Composable () -> Unit,
) {
    val paths = remember { headerPaths() }
    val logoStrokes = remember { List(2) { Path() } }
    val scheme = MaterialTheme.colorScheme
    val bubble = lerp(scheme.primaryContainer, scheme.onPrimaryContainer, .08f)
    val reply = lerp(scheme.secondaryContainer, scheme.pageBackground, .25f)
    val marks = lerp(scheme.primaryContainer, scheme.onPrimaryContainer, .35f)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(360f / 190f).testTag("welcome-artwork")) {
            val artworkWidth = maxWidth
            Canvas(Modifier.matchParentSize()) {
                val scale = size.width / 360f
                withTransform({ scale(scale, scale, Offset.Zero) }) {
                    for (shape in WelcomeHeaderMotion.Shape.entries) {
                        val p = WelcomeHeaderMotion.pose(shape, time.value)
                        if (p.alpha == 0f) continue
                        withTransform({
                            translate(shape.x, shape.y + p.enterY)
                            rotate(p.enterAngle, Offset.Zero)
                            scale(p.scale, p.scale, Offset.Zero)
                            translate(p.x, p.y)
                            rotate(p.angle, Offset.Zero)
                        }) {
                            when (shape) {
                                WelcomeHeaderMotion.Shape.Conversation -> {
                                    drawPath(paths.conversation, bubble, alpha = p.alpha)
                                    for (x in listOf(-19f, -6f, 7f)) drawCircle(marks, 3.5f, Offset(x, 0f), p.alpha)
                                }
                                WelcomeHeaderMotion.Shape.Orbit -> drawCircle(scheme.primaryContainer, 17f, Offset.Zero, p.alpha)
                                WelcomeHeaderMotion.Shape.Reply -> {
                                    drawRoundRect(reply, Offset(-45f, -25f), Size(90f, 50f), androidx.compose.ui.geometry.CornerRadius(25f), alpha = p.alpha)
                                    drawPath(paths.replyMarks, marks, alpha = p.alpha, style = Stroke(6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                                }
                                WelcomeHeaderMotion.Shape.Spark -> drawPath(paths.spark, Color(0xFFF6B99A), alpha = p.alpha)
                                WelcomeHeaderMotion.Shape.Bloom -> drawPath(paths.bloom, Color(0xFFFFDBC9), alpha = p.alpha)
                            }
                        }
                    }
                }
            }
            // Retain the host artwork for reduced motion; its disk fits inside the rotating bloom.
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.graphicsLayer {
                    val p = WelcomeHeaderMotion.pose(WelcomeHeaderMotion.Shape.Bloom, time.value)
                    val designScale = artworkWidth.toPx() / 360f
                    val iconScale = 114f * designScale / size.width
                    alpha = p.alpha
                    scaleX = iconScale * p.scale
                    scaleY = iconScale * p.scale
                    translationY = (1f + p.enterY + p.y) * designScale
                    rotationZ = p.enterAngle
                    shape = CircleShape
                    clip = true
                }) {
                    launcherIcon()
                    Canvas(Modifier.matchParentSize()) {
                        val millis = time.value
                        // Preserve the host's original artwork when reduced motion is requested.
                        if (millis < 0) return@Canvas
                        drawCircle(Color(0xFFFFDBC9))
                        withTransform({ scale(size.width / 512f, size.height / 512f, Offset.Zero) }) {
                            logoStrokes.forEachIndexed { index, path ->
                                path.setHarmonicLogoStroke(index, WelcomeHeaderMotion.logoBend(index, millis))
                                drawPath(
                                    path, Color(0xFF341000),
                                    style = Stroke(33f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(
            Modifier.graphicsLayer {
                alpha = WelcomeHeaderMotion.textAlpha(time.value)
                translationY = (1f - alpha) * 8.dp.toPx()
            }, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title, Modifier.widthIn(max = 240.dp),
                color = scheme.onSurface, fontFamily = GoogleSansFlexRoundedFontFamily,
                fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp,
                textAlign = TextAlign.Center, style = LocalSettingsPlatformStyle.current.textStyle,
            )
            Text(
                subtitle, Modifier.padding(top = 10.dp),
                color = scheme.onSurfaceVariant, fontFamily = GoogleSansFlexRoundedFontFamily,
                fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                style = LocalSettingsPlatformStyle.current.textStyle,
            )
        }
    }
}

private data class HeaderPaths(val conversation: Path, val replyMarks: Path, val spark: Path, val bloom: Path)

private fun headerPaths(): HeaderPaths {
    fun path(value: String) = PathParser().parsePathString(value).toPath()
    return HeaderPaths(
        conversation = path("M-31-24h43c14 0 25 11 25 25s-11 25-25 25H-7l-12 10c-3 2-6 0-5-3l2-9c-14-3-23-13-23-26 0-12 5-22 14-22Z"),
        replyMarks = path("M-19-6H17M-19 7H3"),
        spark = path("M0-14C3-5 5-3 14 0 5 3 3 5 0 14-3 5-5 3-14 0-5-3-3-5 0-14Z"),
        bloom = Path().apply {
            repeat(192) { i ->
                val a = i / 192.0 * PI * 2
                val r = 61 + 3.8 * cos(8 * a)
                val x = (r * cos(a)).toFloat()
                val y = (r * sin(a)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        },
    )
}
