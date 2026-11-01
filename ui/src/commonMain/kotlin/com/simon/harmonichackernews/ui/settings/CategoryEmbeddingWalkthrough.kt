package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.ui.common.OutlinedButton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private enum class EmbeddingExplanation(
    val title: String,
    val body: String,
    val illustrationDescription: String,
    val caption: String,
) {
    Encode(
        "Turn text into numbers",
        "EmbeddingGemma runs on your device. It turns a story’s headline and useful preview text into 768 numbers: an embedding that represents meaning. Without a useful preview, it uses the headline alone. The vector is normalized to unit length.",
        "A headline about a new planet becomes a vector of numbers through the on-device model.",
        "Same story, represented as a vector",
    ),
    Center(
        "Remove the average",
        "Reference embeddings share a common background direction. We subtract their average vector from each normalized story and reference vector, then normalize again. This makes topic differences stand out. Headlines and previews use separate reference averages.",
        "Before subtraction, story vectors point in a similar direction. After subtracting the reference average, their topic directions spread apart around a new origin.",
        "Subtract the reference average, then normalize · 2D sketch",
    ),
    Compare(
        "Compare topic directions",
        "Each category’s center is the normalized average of its centered example vectors. We compare the story’s direction with each center using cosine similarity. Closer directions score higher. Headline-only stories use headline references; stories with previews use preview references.",
        "The story arrow points closest to Space, then Science, and away from Cars. Smaller angles mean more similar topics.",
        "Smaller angle = closer match · 2D sketch",
    ),
    Label(
        "Keep the strongest labels",
        "Scores are shown on a 0–100 scale, not as probabilities. Selected categories must meet your cutoff and score at least three quarters as high as the best match. We keep the strongest ones, up to your maximum label count.",
        "Example scores: Space 42 and Science 34 pass a cutoff of 24 and the three-quarters rule. Cars scores 18 and is below the cutoff. With room for two labels, Space and Science are kept.",
        "Example: cutoff 24, maximum 2 labels",
    ),
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun CategoryEmbeddingWalkthrough(stepIndex: Int, onStepChanged: (Int) -> Unit) {
    val steps = EmbeddingExplanation.entries
    val scheme = MaterialTheme.colorScheme
    val slideDistance = with(LocalDensity.current) { 48.dp.roundToPx() }
    val layoutDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val slideMotion = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val sizeMotion = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val enterFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.TopCenter) {
        Surface(Modifier.widthIn(max = 600.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            color = scheme.surfaceContainerLow) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Step ${stepIndex + 1} of ${steps.size}", fontFamily = ProductSansFontFamily,
                        fontSize = 12.sp, color = scheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        steps.forEachIndexed { index, _ ->
                            Box(Modifier.size(6.dp).background(
                                if (index == stepIndex) scheme.primary else scheme.outlineVariant, CircleShape))
                        }
                    }
                }
                // Move the illustration and its explanation together; keep the controls in place.
                AnimatedContent(
                    targetState = stepIndex,
                    modifier = Modifier.fillMaxWidth().clipToBounds(),
                    transitionSpec = {
                        val direction = (if (targetState > initialState) 1 else -1) * layoutDirection
                        ((slideInHorizontally(slideMotion) { direction * slideDistance } + fadeIn(enterFade)) togetherWith
                            (slideOutHorizontally(slideMotion) { -direction * slideDistance } + fadeOut(exitFade)))
                            .using(SizeTransform { _, _ -> sizeMotion })
                    },
                    contentAlignment = Alignment.TopStart,
                    label = "embedding explanation step",
                ) { visibleIndex ->
                    val step = steps[visibleIndex]
                    Column(Modifier.fillMaxWidth()) {
                        EmbeddingStepIllustration(step, Modifier.padding(top = 16.dp).fillMaxWidth().aspectRatio(320f / 180f))
                        Text(step.caption, Modifier.padding(top = 8.dp), fontFamily = ProductSansFontFamily,
                            fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
                        Column(Modifier.fillMaxWidth().heightIn(min = 206.dp).padding(top = 20.dp)) {
                            Text(step.title, fontFamily = ProductSansFontFamily, fontSize = 22.sp,
                                lineHeight = 28.sp, fontWeight = FontWeight.Medium)
                            Text(step.body, Modifier.padding(top = 8.dp), fontFamily = ProductSansFontFamily,
                                fontSize = 14.sp, lineHeight = 21.sp, color = scheme.onSurfaceVariant)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { onStepChanged(stepIndex - 1) }, enabled = stepIndex > 0) {
                        Text("Previous", fontFamily = ProductSansFontFamily)
                    }
                    Button(onClick = { onStepChanged(stepIndex + 1) }, enabled = stepIndex < steps.lastIndex) {
                        Text("Next", fontFamily = ProductSansFontFamily)
                    }
                }
            }
        }
    }
}

/** Schematic vectors, using the same palette and text rendering as the category map. */
@Composable
private fun EmbeddingStepIllustration(step: EmbeddingExplanation, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val font = ProductSansFontFamily
    Canvas(modifier.semantics { contentDescription = step.illustrationDescription }) {
        withTransform({ scale(size.width / 320f, size.height / 180f, pivot = Offset.Zero) }) {
            fun label(text: String, x: Float, y: Float, color: Color = scheme.onSurfaceVariant, size: Int = 12) {
                drawText(measurer.measure(text, TextStyle(fontFamily = font, fontSize = size.sp, color = color),
                    density = Density(1f)), topLeft = Offset(x, y))
            }
            fun panel(x: Float, y: Float, width: Float, height: Float, color: Color = scheme.surfaceContainerHighest) {
                drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(14f))
            }
            fun arrow(start: Offset, end: Offset, color: Color, width: Float = 3f) {
                drawLine(color, start, end, width, cap = StrokeCap.Round)
                val angle = atan2(end.y - start.y, end.x - start.x)
                for (side in listOf(-1f, 1f)) {
                    drawLine(color, end, end - Offset(cos(angle + side * 0.55f), sin(angle + side * 0.55f)) * 8f,
                        width, cap = StrokeCap.Round)
                }
            }
            when (step) {
                EmbeddingExplanation.Encode -> {
                    panel(0f, 24f, 132f, 120f, scheme.primaryContainer)
                    label("STORY", 12f, 36f, scheme.onPrimaryContainer, 10)
                    label("A new telescope", 12f, 59f, scheme.onPrimaryContainer, 13)
                    label("finds a planet", 12f, 77f, scheme.onPrimaryContainer, 13)
                    drawLine(scheme.onPrimaryContainer.copy(alpha = 0.25f), Offset(12f, 110f), Offset(112f, 110f), 3f)
                    drawLine(scheme.onPrimaryContainer.copy(alpha = 0.25f), Offset(12f, 121f), Offset(93f, 121f), 3f)
                    arrow(Offset(143f, 82f), Offset(181f, 82f), scheme.primary)
                    panel(194f, 24f, 126f, 120f)
                    val values = listOf(0.3f, 0.8f, 0.45f, 0.65f, 0.2f, 0.9f, 0.55f, 0.35f)
                    values.forEachIndexed { index, value ->
                        val x = 207f + index * 13f
                        drawRoundRect(scheme.primary.copy(alpha = 0.3f + value * 0.7f),
                            Offset(x, 110f - value * 62f), Size(7f, value * 62f), CornerRadius(3f))
                    }
                    label("768 numbers", 216f, 120f, scheme.onSurface, 12)
                    label("On-device model", 114f, 157f)
                }
                EmbeddingExplanation.Center -> {
                    panel(0f, 4f, 146f, 150f)
                    panel(174f, 4f, 146f, 150f)
                    label("Before", 12f, 14f, scheme.onSurface)
                    label("After", 186f, 14f, scheme.onSurface)
                    val origin = Offset(20f, 133f)
                    val average = Offset(88f, 88f)
                    val directions = listOf(Offset(0.38f, -0.925f), Offset(0.934f, 0.357f), Offset(-0.731f, -0.682f))
                    val colors = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
                    directions.forEachIndexed { index, direction -> arrow(origin, average + direction * 32f, colors[index]) }
                    arrow(origin, average, scheme.onSurfaceVariant.copy(alpha = 0.6f), 2f)
                    label("Average", 55f, 137f, scheme.onSurfaceVariant, 11)
                    arrow(Offset(152f, 80f), Offset(167f, 80f), scheme.onSurfaceVariant, 2f)
                    val centered = Offset(245f, 91f)
                    drawLine(scheme.outlineVariant, Offset(188f, 91f), Offset(306f, 91f), 1f)
                    drawLine(scheme.outlineVariant, Offset(245f, 40f), Offset(245f, 142f), 1f)
                    directions.forEachIndexed { index, direction -> arrow(centered, centered + direction * 50f, colors[index]) }
                    drawCircle(scheme.onSurface, 3f, centered)
                    label("Shared background removed", 79f, 163f, scheme.primary, 12)
                }
                EmbeddingExplanation.Compare -> {
                    val origin = Offset(30f, 146f)
                    drawLine(scheme.outlineVariant, origin, Offset(306f, 146f), 1f)
                    drawLine(scheme.outlineVariant, origin, Offset(30f, 13f), 1f)
                    // Category directions and a nearby story direction share the centered origin.
                    arrow(origin, Offset(165f, 31f), scheme.primary, 2f)
                    arrow(origin, Offset(252f, 78f), scheme.tertiary, 2f)
                    arrow(origin, Offset(277f, 132f), scheme.secondary, 2f)
                    listOf(Offset(165f, 31f), Offset(252f, 78f), Offset(277f, 132f)).forEachIndexed { index, center ->
                        val color = listOf(scheme.primary, scheme.tertiary, scheme.secondary)[index]
                        drawCircle(color.copy(alpha = 0.12f), 20f, center)
                        listOf(Offset(-10f, -5f), Offset(8f, -9f), Offset(11f, 8f), Offset(-5f, 12f)).forEach {
                            drawCircle(color.copy(alpha = 0.5f), 2.5f, center + it)
                        }
                        drawCircle(color, 4f, center)
                    }
                    arrow(origin, Offset(136f, 24f), scheme.onSurface, 4f)
                    label("Story", 80f, 4f, scheme.onSurface)
                    label("Space", 187f, 25f, scheme.primary)
                    label("Science", 245f, 50f, scheme.tertiary)
                    label("Cars", 278f, 153f, scheme.secondary)
                    label("Category centers", 30f, 162f)
                }
                EmbeddingExplanation.Label -> {
                    val start = 78f
                    val width = 220f
                    val cutoff = start + width * 24f / 100f
                    label("Cutoff 24", cutoff - 22f, 0f, scheme.onSurface, 11)
                    label("0", start, 14f, size = 10)
                    label("100", start + width - 18f, 14f, size = 10)
                    listOf("Space" to 42, "Science" to 34, "Cars" to 18).forEachIndexed { index, (name, score) ->
                        val y = 33f + index * 42f
                        val passes = index < 2
                        label(name, 0f, y + 2f, if (passes) scheme.onSurface else scheme.onSurfaceVariant)
                        panel(start, y, width, 22f)
                        drawRoundRect(if (passes) scheme.primary else scheme.outline,
                            Offset(start, y), Size(width * score / 100f, 22f), CornerRadius(6f))
                        label("$score", start + width * score / 100f - 22f, y + 3f,
                            if (passes) scheme.onPrimary else scheme.surface, 11)
                    }
                    drawLine(scheme.onSurfaceVariant, Offset(cutoff, 23f), Offset(cutoff, 143f), 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                    panel(57f, 153f, 85f, 26f, scheme.primaryContainer)
                    panel(152f, 153f, 105f, 26f, scheme.primaryContainer)
                    label("Space", 82f, 158f, scheme.onPrimaryContainer)
                    label("Science", 181f, 158f, scheme.onPrimaryContainer)
                }
            }
        }
    }
}
