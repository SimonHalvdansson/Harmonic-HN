package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.unit.TextUnit
import com.simon.harmonichackernews.settings.TextPreferences

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.simon.harmonichackernews.ui.common.captureSharedTransformSourceContent
import com.simon.harmonichackernews.ui.theme.contrastingColor
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

internal data class StoryCategoryColors(val container: Color, val foreground: Color)

/** Retained by category ID so a returning inspector follows relayouts and renamed labels. */
internal class CategoryChipGeometry {
    var bounds by mutableStateOf<Rect?>(null)
    var layer by mutableStateOf<GraphicsLayer?>(null)
}

/** Keep chips in the card's own palette while retaining readable small text. */
internal fun tintedStoryCategoryColors(surface: Color, preferredForeground: Color): StoryCategoryColors {
    val ink = contrastingColor(preferredForeground, listOf(surface), 4.5f)
    val container = lerp(surface, ink, 0.12f)
    return StoryCategoryColors(container, contrastingColor(ink, listOf(container), 4.5f))
}

@Composable
internal fun StoryCategoryLabels(
    labels: List<String>,
    alpha: Float,
    tintedSurface: (() -> Color)? = null,
    animate: Boolean = true,
    topSpacing: Dp = 2.dp,
    bottomSpacing: Dp = 4.dp,
    inspection: StoryCategoryInspection? = null,
    categoryIds: List<String> = inspection?.labelScores?.map { it.categoryId }.orEmpty(),
    placementAnimation: StoryRowPlacementAnimation? = null,
    fontSize: TextUnit = 11.sp,
) {
    val colors = storyCategoryColors(tintedSurface)
    var inspector by remember { mutableStateOf<CategoryInspectorSource?>(null) }
    var sourceCovered by remember(inspector) { mutableStateOf(false) }
    val geometries = remember { mutableMapOf<String, CategoryChipGeometry>() }
    val content: @Composable (CategoryLabelContent) -> Unit = { visible ->
        if (visible.labels.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = topSpacing, bottom = bottomSpacing)
                    .graphicsLayer { this.alpha = alpha },
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                visible.labels.forEachIndexed { index, label ->
                    val score = visible.inspection?.labelScores?.getOrNull(index)
                    if (score != null) {
                        val geometry = geometries.getOrPut(score.categoryId) { CategoryChipGeometry() }
                        InspectableCategoryChip(label, score.categoryId, (score.similarity * 100).roundToInt(), colors,
                            fontSize = fontSize,
                            covered = sourceCovered && inspector?.categoryId == score.categoryId,
                            geometry = geometry,
                            onOpen = { _, _ ->
                                inspector = CategoryInspectorSource(checkNotNull(visible.inspection), score.categoryId,
                                    colors, geometry)
                            })
                    } else StoryRowMovingContent(placementAnimation, "category-$label") {
                        CategoryLabelText(label, colors,
                            modifier = Modifier.animateStoryRowTextSize(placementAnimation, fontSize.value),
                            categoryId = categoryIds.getOrNull(index), fontSize = fontSize)
                    }
                }
            }
        }
    }
    if (animate) AnimatedContent(
        targetState = CategoryLabelContent(labels, inspection),
        contentKey = { it.labels },
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            (fadeIn(tween(150, delayMillis = 20)) togetherWith fadeOut(tween(100))).using(
                SizeTransform(clip = false) { _, _ -> tween(240, easing = FastOutSlowInEasing) })
        },
        label = "story categories",
    ) { content(it) }
    else content(CategoryLabelContent(labels, inspection))
    inspector?.let { source ->
        CategoryInspectorDialog(source,
            onSourceReadyToCover = { sourceCovered = true },
            onSourceReadyToReveal = { sourceCovered = false },
            onDismiss = { inspector = null })
    }
}

private data class CategoryLabelContent(val labels: List<String>, val inspection: StoryCategoryInspection?)

@Composable
internal fun storyCategoryColors(tintedSurface: (() -> Color)?): StoryCategoryColors {
    val scheme = MaterialTheme.colorScheme
    return tintedSurface?.let { tintedStoryCategoryColors(it(), scheme.onSurface) }
        ?: StoryCategoryColors(scheme.secondaryContainer,
            contrastingColor(scheme.onSecondaryContainer, listOf(scheme.secondaryContainer), 4.5f))
}

@Composable
internal fun CategoryLabelText(label: String, colors: StoryCategoryColors, modifier: Modifier = Modifier, categoryId: String? = null, fontSize: TextUnit = 11.sp) {
    Row(modifier.background(colors.container, RoundedCornerShape(5.dp))
        .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(categorySymbol(categoryId)), null, Modifier.size((12f * fontSize.value / 11f).dp), tint = colors.foreground)
        Text(text = label, color = colors.foreground, fontFamily = ProductSansFontFamily,
            style = MaterialTheme.typography.labelSmall, fontSize = fontSize)
    }
}

@Composable
internal fun InspectableCategoryChip(
    label: String,
    categoryId: String,
    similarity: Int,
    colors: StoryCategoryColors,
    covered: Boolean,
    geometry: CategoryChipGeometry = remember { CategoryChipGeometry() },
    onOpen: (Rect?, GraphicsLayer?) -> Unit,
    fontSize: TextUnit = 11.sp,
) {
    var captureRequested by remember { mutableStateOf(false) }
    var pendingOpen by remember { mutableStateOf(false) }
    val requestOpen = { captureRequested = true; pendingOpen = true }
    LaunchedEffect(pendingOpen) {
        if (pendingOpen) {
            // Record only interacted chips. Two frames let the capture modifier draw before
            // the overlay covers it; ordinary feed scrolling allocates no snapshot layers.
            withFrameNanos { }
            withFrameNanos { }
            onOpen(geometry.bounds, geometry.layer)
            pendingOpen = false
        }
    }
    val capture = if (captureRequested) Modifier.captureSharedTransformSourceContent { geometry.layer = it } else Modifier
    Box(
        modifier = Modifier
            .testTag("category-label-$categoryId")
            .clip(RoundedCornerShape(5.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Inspect $label category",
                onLongClickLabel = "Inspect $label category",
                onClick = requestOpen,
                onLongClick = requestOpen,
            )
            .semantics { contentDescription = "$label category, similarity $similarity out of 100" }
            .graphicsLayer { this.alpha = if (covered) 0f else 1f },
        contentAlignment = Alignment.CenterStart,
    ) {
        CategoryLabelText(label, colors,
            Modifier.onGloballyPositioned { geometry.bounds = it.boundsInWindow() }.then(capture).clearAndSetSemantics { }, categoryId, fontSize)
    }
}

internal fun storyCategoryFontSize(storyTextSize: Float): TextUnit =
    (11f * storyTextSize / TextPreferences.DEFAULT_STORY_TEXT_SIZE).sp
