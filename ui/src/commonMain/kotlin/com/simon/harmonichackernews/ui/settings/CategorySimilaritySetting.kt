@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.ui.theme.themeBackground
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@Composable
internal fun CategorySimilaritySetting(value: Float, onValueChange: (Float) -> Unit, onReset: () -> Unit, resetEnabled: Boolean) {
    val curve = CategoryCoverageReference.curve
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant
    val surface = HarmonicTheme.colors.cardBackground
    val state = remember {
        // Whole-point keyboard/accessibility steps, with no ticks in the custom track.
        SliderState(value, steps = ((ArticleCategories.MAX_CUTOFF - ArticleCategories.MIN_CUTOFF) * 100).roundToInt() - 1,
            trackRange = ArticleCategories.MIN_CUTOFF..ArticleCategories.MAX_CUTOFF)
            .apply { shouldAutoSnap = false }
    }
    state.value = value
    Column(Modifier.fillMaxWidth().themeBackground { cardBackground }.settingsSearchTarget("Similarity cutoff")
        .padding(horizontal = 24.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Similarity cutoff", Modifier.weight(1f), fontFamily = ProductSansFontFamily, fontSize = 16.sp)
            Text("${(value * 100).roundToInt()} / 100", fontFamily = ProductSansFontFamily, fontSize = 15.sp)
            IconButton(onClick = onReset, enabled = resetEnabled) {
                val buttonColors = IconButtonDefaults.iconButtonColors()
                val resetColor = animateColorAsState(
                    if (resetEnabled) buttonColors.contentColor else buttonColors.disabledContentColor,
                    tween(150), label = "cutoff reset color")
                val painter = painterResource(Res.drawable.ic_refresh)
                Canvas(Modifier.size(20.dp).semantics { contentDescription = "Reset similarity cutoff" }) {
                    with(painter) { draw(size, colorFilter = ColorFilter.tint(resetColor.value)) }
                }
            }
        }
        Slider(state = state, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Similarity cutoff" },
            thumb = { Box(Modifier.size(width = 12.dp, height = 48.dp).background(primary, RoundedCornerShape(6.dp))) },
            track = { slider ->
                Spacer(Modifier.fillMaxWidth().height(56.dp).drawWithCache {
                    val baseline = size.height / 2
                    val path = Path().apply {
                        curve.forEachIndexed { index, point ->
                            val x = (point.cutoff - ArticleCategories.MIN_CUTOFF) /
                                (ArticleCategories.MAX_CUTOFF - ArticleCategories.MIN_CUTOFF) * size.width
                            val y = baseline - point.fraction * 24.dp.toPx()
                            if (index == 0) moveTo(x, y) else lineTo(x, y)
                        }
                    }
                    onDrawBehind {
                        scale(if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, 1f) {
                            val cutoff = ((slider.value - ArticleCategories.MIN_CUTOFF) /
                                (ArticleCategories.MAX_CUTOFF - ArticleCategories.MIN_CUTOFF)).coerceIn(0f, 1f) * size.width
                            drawPath(path, primary.copy(alpha = 0.7f), style = Stroke(1.5.dp.toPx()))
                            drawRoundRect(muted, Offset(0f, baseline - 3.dp.toPx()),
                                Size(size.width, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                            clipRect(right = (cutoff - 8.dp.toPx()).coerceAtLeast(0f)) {
                                drawRoundRect(primary, Offset(0f, baseline - 3.dp.toPx()),
                                    Size(size.width, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                            }
                            // Keep the usual Material gap around the handle.
                            drawRect(surface, Offset((cutoff - 8.dp.toPx()).coerceAtLeast(0f), baseline - 4.dp.toPx()),
                                Size(16.dp.toPx().coerceAtMost(size.width), 8.dp.toPx()))
                        }
                    }
                })
            })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${(ArticleCategories.MIN_CUTOFF * 100).roundToInt()}", fontFamily = ProductSansFontFamily,
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(ArticleCategories.MAX_CUTOFF * 100).roundToInt()}", fontFamily = ProductSansFontFamily,
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
