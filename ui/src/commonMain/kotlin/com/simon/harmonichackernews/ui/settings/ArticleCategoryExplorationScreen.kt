package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.categorization.ArticleCategories
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
    LaunchedEffect(attempt) {
        failed = false
        try { if (data == null) data = CategoryExplorationResources.load() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val colors = categoryMapColors()
    SettingsPage("How categories work", true, onBack,
        contentVersion = (data?.hashCode() ?: 0) + method.hashCode() + (highlighted?.hashCode() ?: 0) + (selected ?: 0) + attempt + failed.hashCode()) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text("A map of meaning", fontFamily = ProductSansFontFamily, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Text("Each dot is one example. Nearby dots have similar embeddings.",
                    Modifier.padding(top = 8.dp), fontFamily = ProductSansFontFamily,
                    fontSize = 16.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "category-map") {
            CategoryExplorationPanel(data, failed, method, colors, highlighted, selected,
                onMethodSelected = { method = it }, onSelect = { selected = it }, onRetry = { attempt++ })
        }
        item(key = "category-map-legend") {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ArticleCategories.defaults.forEachIndexed { index, category ->
                    FilterChip(modifier = Modifier.height(32.dp), selected = highlighted == category.id, onClick = {
                        highlighted = if (highlighted == category.id) null else category.id
                        selected = null
                    }, elevation = FilterChipDefaults.filterChipElevation(hoveredElevation = 0.dp),
                        label = { Text(category.name, fontFamily = ProductSansFontFamily, fontSize = 12.sp) },
                        leadingIcon = { Box(Modifier.size(8.dp).background(colors[index], CircleShape)) })
                }
            }
        }
        item {
            SettingsCategory("From text to labels") {
                ExplanationStep("1", "Turn story into vector", "EmbeddingGemma encodes the title and available preview text as a normalized, 768-dimensional vector. Inference runs on your device.")
                SettingsDivider()
                ExplanationStep("2", "Compare against references", "Reference examples define each category’s center: the normalized mean of their embedding vectors. Cosine similarity compares the story vector with each center.")
                SettingsDivider()
                ExplanationStep("3", "Apply similarity cutoff", "Cosine similarity is displayed on a 0–100 scale. Selected categories at or above your cutoff are ranked by similarity, then limited to your maximum label count.")
            }
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
            // Only an explicit example selection changes this height. Loading data does not move the legend.
            Column(Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                val point = data?.points?.firstOrNull { it.id == selected }
                if (point != null) {
                    Text(point.title, fontFamily = ProductSansFontFamily, fontSize = 14.sp, lineHeight = 19.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    CategoryExampleMatches(point, colors)
                } else Text("Tap a dot to compare its closest categories.",
                    fontFamily = ProductSansFontFamily, fontSize = 12.sp,
                    lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExplanationStep(number: String, title: String, body: String) {
    Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                Text(number, fontFamily = ProductSansFontFamily, fontSize = 14.sp)
            }
        }
        Column {
            Text(title, fontFamily = ProductSansFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(body, Modifier.padding(top = 4.dp), fontFamily = ProductSansFontFamily, fontSize = 14.sp, lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun CategoryExampleMatches(point: CategoryMapPoint, colors: List<Color>) {
    val categories = remember { ArticleCategories.defaults.associateBy { it.id } }
    val indices = remember { ArticleCategories.defaults.mapIndexed { i, category -> category.id to i }.toMap() }
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val matches = point.matches.filter { it.similarity >= 0.75f }.take(3)
    if (matches.isEmpty()) {
        Text("No matches at 75 or above", Modifier.padding(top = 8.dp), fontFamily = ProductSansFontFamily,
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        matches.forEach { match ->
            val score = (match.similarity * 1000).roundToInt() / 10f
            val fill = animateFloatAsState(((match.similarity - 0.75f) / 0.25f).coerceIn(0f, 1f), tween(250), label = "example similarity")
            val color = colors[indices.getValue(match.category)]
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = "${categories.getValue(match.category).name}, similarity $score out of 100"
            }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(categories.getValue(match.category).name, fontFamily = ProductSansFontFamily, fontSize = 12.sp)
                    Text("$score", fontFamily = ProductSansFontFamily, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Canvas(Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp)) {
                    drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
                    drawRoundRect(color, size = Size(size.width * fill.value, size.height), cornerRadius = CornerRadius(size.height / 2))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("75", fontFamily = ProductSansFontFamily, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("100", fontFamily = ProductSansFontFamily, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
