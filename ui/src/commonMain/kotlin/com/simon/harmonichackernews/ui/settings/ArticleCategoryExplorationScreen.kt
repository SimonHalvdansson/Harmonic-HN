package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.resources.*
import org.jetbrains.compose.resources.painterResource
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.ui.theme.contrastingColor
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

@Composable
fun ArticleCategoryExplorationScreen(onBack: () -> Unit) {
    var data by remember { mutableStateOf(CategoryExplorationResources.cached()) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var method by rememberSaveable { mutableStateOf("umap") }
    var highlighted by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var explanationStep by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        failed = false
        try { if (data == null) data = CategoryExplorationResources.load() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val colors = categoryMapColors()
    SettingsPage("How categories work", true, onBack,
        contentVersion = (data?.hashCode() ?: 0) + method.hashCode() + (highlighted?.hashCode() ?: 0) + (selected ?: 0) + attempt + failed.hashCode() + explanationStep) {
        item(key = "embedding-walkthrough") {
            CategoryEmbeddingWalkthrough(explanationStep, onStepChanged = { explanationStep = it })
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text("A map of meaning", Modifier.semantics { heading() },
                    fontFamily = ProductSansFontFamily, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Text("Each dot is an example headline. Nearby dots have similar embeddings. Preview text can refine a story’s matches.",
                    Modifier.padding(top = 8.dp), fontFamily = ProductSansFontFamily,
                    fontSize = 16.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "category-map") {
            CategoryExplorationPanel(data, failed, method, colors, highlighted, selected,
                onMethodSelected = { method = it }, onSelect = { selected = it }, onRetry = { attempt++ })
        }
        item(key = "category-map-legend") {
            CategoryExplorationLegend(ArticleCategories.defaults.map { it.id to it.name }, colors, highlighted) { id ->
                highlighted = if (highlighted == id) null else id
                selected = null
            }
        }
    }
}

@Composable
internal fun CategoryExplorationLegend(
    categories: List<Pair<String, String>>,
    colors: List<Color>,
    highlighted: String?,
    onSelected: (String) -> Unit,
) {
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.forEachIndexed { index, (id, name) ->
            FilterChip(
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
                selected = highlighted == id,
                onClick = { onSelected(id) },
                elevation = FilterChipDefaults.filterChipElevation(hoveredElevation = 0.dp),
                label = { Text(name, Modifier.padding(vertical = 6.dp),
                    fontFamily = ProductSansFontFamily, fontSize = 12.sp, lineHeight = 17.sp) },
                leadingIcon = { Icon(painterResource(com.simon.harmonichackernews.ui.content.categorySymbol(id)),
                    null, Modifier.size(18.dp), tint = colors[index]) },
            )
        }
    }
}

/** Reserve the final map and caption dimensions before the bundled data has loaded. */
@Composable
internal fun CategoryExplorationPanel(data: CategoryExplorationData?, failed: Boolean, method: String,
    colors: List<Color>, highlighted: String?, selected: Int?, onMethodSelected: (String) -> Unit,
    onSelect: (Int?) -> Unit, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            SegmentedSetting(options = listOf("umap" to "UMAP", "tsne" to "t-SNE", "pca" to "PCA"),
                selected = method, onSelected = onMethodSelected)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = 600.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    if (data == null) {
                        if (failed) TextButton(onClick = onRetry) { Text("Couldn't load map · Retry", fontFamily = ProductSansFontFamily) }
                        else CircularProgressIndicator()
                    } else CategoryEmbeddingMap(data, data.methods.first { it.id == method }, colors, highlighted, selected,
                        Modifier.fillMaxSize(), onSelect = onSelect)
                }
            }
            // Reserve a two-line headline and three match rows so the controls never move.
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                val point = data?.points?.firstOrNull { it.id == selected }
                if (point != null) {
                    Text(point.title, fontFamily = ProductSansFontFamily, fontSize = 14.sp, lineHeight = 19.sp,
                        minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    CategoryExampleMatches(point, colors, reserveSpace = true)
                } else {
                    Text("Tap a dot to compare its closest categories.",
                        fontFamily = ProductSansFontFamily, fontSize = 14.sp, minLines = 2,
                        lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CategoryExampleMatches(null, colors, reserveSpace = true)
                }
                // Native canvas points have no individual focus targets. These controls make
                // the same comparisons available to keyboard and screen-reader users.
                val examples = data?.points.orEmpty()
                val index = examples.indexOfFirst { it.id == selected }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        TextButton(enabled = examples.isNotEmpty(), onClick = {
                            onSelect(examples[if (index <= 0) examples.lastIndex else index - 1].id)
                        }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(painterResource(Res.drawable.ic_arrow_back), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Previous example", Modifier.weight(1f, fill = false))
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        TextButton(enabled = examples.isNotEmpty(), onClick = {
                            onSelect(examples[if (index < 0 || index == examples.lastIndex) 0 else index + 1].id)
                        }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Next example", Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(8.dp))
                            Icon(painterResource(Res.drawable.ic_arrow_forward), null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryMapColors(): List<Color> {
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    return remember(background) { categoryMapPalette(background) }
}

internal fun categoryMapPalette(background: Color): List<Color> {
    val dark = background.luminance() < 0.5f
    // Distinct hues with theme-dependent lightness, shared by points, labels and legend.
    return List(ArticleCategories.defaults.size) { index ->
        contrastingColor(Color.hsl((index * 137.508f + 205f) % 360f,
            if (dark) 0.65f else 0.72f, if (dark) 0.72f else 0.36f), listOf(background), 4.5f)
    }
}


@Composable
internal fun CategoryExampleMatches(point: CategoryMapPoint?, colors: List<Color>, reserveSpace: Boolean = false) {
    val categories = remember { ArticleCategories.defaults.associateBy { it.id } }
    val indices = remember { ArticleCategories.defaults.mapIndexed { i, category -> category.id to i }.toMap() }
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val matches = point?.matches.orEmpty().filter { it.similarity >= ArticleCategories.MIN_CUTOFF }.take(3)
    if (matches.isEmpty() && !reserveSpace) {
        Text("No matches at ${(ArticleCategories.MIN_CUTOFF * 100).toInt()} or above", Modifier.padding(top = 8.dp), fontFamily = ProductSansFontFamily,
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(if (reserveSpace) 3 else matches.size) { index ->
                val match = matches.getOrNull(index)
                val score = ((match?.similarity ?: 0f) * 1000).roundToInt() / 10f
                val fill = animateFloatAsState((((match?.similarity ?: 0f) - ArticleCategories.MIN_CUTOFF) /
                    (1f - ArticleCategories.MIN_CUTOFF)).coerceIn(0f, 1f), tween(250), label = "example similarity")
                val color = match?.let { colors[indices.getValue(it.category)] } ?: Color.Transparent
                Column(Modifier.fillMaxWidth().graphicsLayer { alpha = if (match == null) 0f else 1f }.clearAndSetSemantics {
                    if (match != null) contentDescription = "${categories.getValue(match.category).name}, similarity $score out of 100"
                }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(match?.let { categories.getValue(it.category).name }.orEmpty(), Modifier.weight(1f),
                            fontFamily = ProductSansFontFamily, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$score", fontFamily = ProductSansFontFamily, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Canvas(Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp)) {
                        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
                        drawRoundRect(color, size = Size(size.width * fill.value, size.height), cornerRadius = CornerRadius(size.height / 2))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().graphicsLayer { alpha = if (matches.isEmpty()) 0f else 1f }
                .then(if (matches.isEmpty()) Modifier.clearAndSetSemantics { } else Modifier),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${(ArticleCategories.MIN_CUTOFF * 100).toInt()}", fontFamily = ProductSansFontFamily, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("100", fontFamily = ProductSansFontFamily, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (point != null && matches.isEmpty()) {
            Text("No matches at ${(ArticleCategories.MIN_CUTOFF * 100).toInt()} or above",
                Modifier.align(Alignment.Center), fontFamily = ProductSansFontFamily,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
