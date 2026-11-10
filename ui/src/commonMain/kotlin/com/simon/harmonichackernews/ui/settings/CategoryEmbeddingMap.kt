package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import kotlin.math.cos
import kotlin.math.sin

internal data class CategoryMapLayout(
    val method: String,
    val points: List<Offset>,
    val centers: List<Offset>,
    val labels: List<Rect>,
)

/** Used only when retargeting an interrupted transition, never allocated per animation frame. */
internal fun sampleCategoryMap(from: CategoryMapLayout, to: CategoryMapLayout, fraction: Float) = CategoryMapLayout(
    to.method,
    from.points.zip(to.points) { a, b -> lerp(a, b, fraction) },
    from.centers.zip(to.centers) { a, b -> lerp(a, b, fraction) },
    from.labels.zip(to.labels) { a, b -> lerp(a, b, fraction) },
)

/** Compute collision-free label positions once for each method at the current viewport size. */
internal fun categoryMapLayout(data: CategoryExplorationData, projection: CategoryProjection, size: IntSize,
    textSizes: List<IntSize>, density: Float): CategoryMapLayout {
    val padding = 28f * density
    val points = projection.positions.map { xy -> Offset(padding + xy[0] * (size.width - 2 * padding),
        padding + xy[1] * (size.height - 2 * padding)) }
    val centers = ArticleCategories.defaults.map { category ->
        val indices = data.points.indices.filter { data.points[it].category == category.id }
        Offset(indices.map { points[it].x }.average().toFloat(), indices.map { points[it].y }.average().toFloat())
    }
    val placed = mutableListOf<Rect>()
    centers.forEachIndexed { index, origin ->
        val width = textSizes[index].width + 10f * density
        val height = textSizes[index].height + 6f * density
        var candidate = Rect.Zero
        for (attempt in 0..64) {
            val angle = attempt * 2.399963f
            val radius = if (attempt == 0) 0f else (10 + attempt * 1.5f) * density
            val left = (origin.x + cos(angle) * radius - width / 2).coerceIn(4f * density,
                (size.width - width - 4f * density).coerceAtLeast(4f * density))
            val top = (origin.y + sin(angle) * radius - height / 2).coerceIn(4f * density,
                (size.height - height - 4f * density).coerceAtLeast(4f * density))
            candidate = Rect(left, top, left + width, top + height)
            if (placed.none { it.inflate(3f * density).overlaps(candidate) }) break
        }
        placed += candidate
    }
    return CategoryMapLayout(projection.id, points, centers, placed)
}

@Composable
internal fun CategoryEmbeddingMap(data: CategoryExplorationData, projection: CategoryProjection, colors: List<Color>,
    highlighted: String?, selected: Int?, modifier: Modifier = Modifier, onSelect: (Int?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val font = AppUiFontFamily
    val density = LocalDensity.current.density
    val categoryIndices = remember { ArticleCategories.defaults.mapIndexed { i, c -> c.id to i }.toMap() }
    val labels = remember(measurer, colors, font) { ArticleCategories.defaults.mapIndexed { index, category ->
        measurer.measure(category.name, TextStyle(fontFamily = font, fontSize = 10.sp,
            fontWeight = FontWeight.Medium, color = colors[index]))
    } }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val layouts = remember(data, viewport, labels, density) {
        if (viewport.width == 0 || viewport.height == 0) emptyMap()
        else data.methods.associate { it.id to categoryMapLayout(data, it, viewport, labels.map { label -> label.size }, density) }
    }
    val target = layouts[projection.id]
    var endpoints by remember { mutableStateOf<Pair<CategoryMapLayout, CategoryMapLayout>?>(null) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        val previous = endpoints
        if (previous == null || previous.second.method == target.method) {
            endpoints = target to target
            progress.snapTo(1f)
        } else {
            // Start from the actual displayed positions when another method is tapped mid-flight.
            val start = sampleCategoryMap(previous.first, previous.second, progress.value)
            progress.snapTo(0f)
            endpoints = start to target
            progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
    }
    val currentOnSelect by rememberUpdatedState(onSelect)
    Canvas(modifier.clip(RoundedCornerShape(20.dp)).onSizeChanged { viewport = it }.semantics {
        contentDescription = "${projection.id} projection of ${data.points.size} default examples, colored by category. Use the category buttons below to highlight a topic."
    }.pointerInput(data, layouts) {
        detectTapGestures { tap ->
            val (from, to) = endpoints ?: return@detectTapGestures
            val fraction = progress.value
            val closest = from.points.indices.minByOrNull { index ->
                (lerp(from.points[index], to.points[index], fraction) - tap).getDistanceSquared()
            }
            currentOnSelect(closest?.takeIf { index ->
                (lerp(from.points[index], to.points[index], fraction) - tap).getDistance() < 20.dp.toPx()
            }?.let { data.points[it].id })
        }
    }) {
        val (from, to) = endpoints ?: target?.let { it to it } ?: return@Canvas
        val fraction = progress.value // Read only in Draw: the points don't recompose on every frame.
        val padding = 28.dp.toPx()
        repeat(5) { i ->
            val part = (i + 1) / 6f
            drawLine(scheme.outlineVariant.copy(alpha = 0.2f), Offset(size.width * part, padding), Offset(size.width * part, size.height - padding), 1.dp.toPx())
            drawLine(scheme.outlineVariant.copy(alpha = 0.2f), Offset(padding, size.height * part), Offset(size.width - padding, size.height * part), 1.dp.toPx())
        }
        data.points.forEachIndexed { index, point ->
            val offset = lerp(from.points[index], to.points[index], fraction)
            val color = colors[categoryIndices.getValue(point.category)]
            drawCircle(color.copy(alpha = if (highlighted == null || highlighted == point.category) 0.78f else 0.12f), 2.7.dp.toPx(), offset)
            if (selected == point.id) drawCircle(scheme.onSurface, 6.dp.toPx(), offset, style = Stroke(1.5.dp.toPx()))
        }
        labels.forEachIndexed { index, label ->
            val rect = lerp(from.labels[index], to.labels[index], fraction)
            val origin = lerp(from.centers[index], to.centers[index], fraction)
            val active = highlighted == null || highlighted == ArticleCategories.defaults[index].id
            drawLine(colors[index].copy(alpha = if (active) 0.45f else 0.12f), origin, rect.center, 1.dp.toPx())
            drawRoundRect(scheme.surfaceContainerLow.copy(alpha = 0.94f), rect.topLeft, Size(rect.width, rect.height), CornerRadius(5.dp.toPx()))
            drawText(label, topLeft = rect.topLeft + Offset(5.dp.toPx(), 3.dp.toPx()), alpha = if (active) 1f else 0.35f)
        }
    }
}
