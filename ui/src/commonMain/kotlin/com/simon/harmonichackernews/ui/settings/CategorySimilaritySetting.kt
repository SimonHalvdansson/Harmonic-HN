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
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@Composable
internal fun CategorySimilaritySetting(value: Float, onValueChange: (Float) -> Unit, onReset: () -> Unit, resetEnabled: Boolean) {
    var density by remember { mutableStateOf<List<Float>>(emptyList()) }
    LaunchedEffect(Unit) {
        try { density = CategoryExplorationResources.load().density }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* The standard slider remains usable if the optional plot fails. */ }
    }
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant
    val surface = HarmonicTheme.colors.cardBackground
    val state = remember {
        // Whole-point keyboard/accessibility steps, with no ticks in the custom track.
        SliderState(value, steps = 14, trackRange = ArticleCategories.MIN_CUTOFF..ArticleCategories.MAX_CUTOFF)
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
                        moveTo(0f, baseline)
                        density.forEachIndexed { i, height ->
                            lineTo(size.width * i / (density.size - 1), baseline - height * 24.dp.toPx())
                        }
                        lineTo(size.width, baseline); close()
                    }
                    onDrawBehind {
                        scale(if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, 1f) {
                            val cutoff = ((slider.value - ArticleCategories.MIN_CUTOFF) /
                                (ArticleCategories.MAX_CUTOFF - ArticleCategories.MIN_CUTOFF)).coerceIn(0f, 1f) * size.width
                            drawPath(path, primary.copy(alpha = 0.32f))
                            clipRect(right = cutoff) { drawPath(path, surface.copy(alpha = 0.83f)) }
                            drawRoundRect(muted, Offset(0f, baseline - 3.dp.toPx()),
                                Size(size.width, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                            clipRect(left = (cutoff + 8.dp.toPx()).coerceAtMost(size.width)) {
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
    }
}
