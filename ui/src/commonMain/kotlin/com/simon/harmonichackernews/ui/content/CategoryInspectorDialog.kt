package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.categorization.ArticleCategoryInputSource
import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.categorization.ArticleCategoryPreferences
import com.simon.harmonichackernews.categorization.ArticleCategoryResult
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_close
import com.simon.harmonichackernews.resources.ic_edit
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.ui.common.DialogPredictiveBackHandler
import com.simon.harmonichackernews.ui.common.PlatformDialogBackgroundDimAmount
import com.simon.harmonichackernews.ui.common.PlatformDialogDimHost
import com.simon.harmonichackernews.ui.common.PlatformDisableDialogWindowAnimations
import com.simon.harmonichackernews.ui.common.TransformOverlay
import com.simon.harmonichackernews.ui.common.platformDialogPredictiveBackSupported
import com.simon.harmonichackernews.ui.common.platformDialogProperties
import com.simon.harmonichackernews.ui.settings.ArticleCategoryEditor
import com.simon.harmonichackernews.ui.settings.settingsDialogMaxWidth
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

internal data class CategoryInspectorSource(
    val inspection: StoryCategoryInspection,
    val categoryId: String,
    val colors: StoryCategoryColors,
    val geometry: CategoryChipGeometry,
)

@Composable
internal fun CategoryInspectorDialog(
    source: CategoryInspectorSource,
    onSourceReadyToCover: () -> Unit,
    onSourceReadyToReveal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    val story = source.inspection
    val currentPreferences by app.articleCategorySettings.updates.collectAsStateWithLifecycle(
        initialValue = app.articleCategorySettings.snapshot())
    val results = remember(app.articleCategories, story.storyId, story.title, story.url) {
        app.articleCategories.observeResult(story.storyId, story.title, story.url)
    }
    val currentResult by results.collectAsStateWithLifecycle(
        initialValue = app.articleCategories.cachedResult(story.storyId, story.title, story.url))
    val preferences = if (story.sample) story.preferences.copy(
        names = currentPreferences.names, categories = currentPreferences.categories) else currentPreferences
    val result = if (story.sample) story.result else currentResult
    val category = preferences.categories.firstOrNull { it.id == source.categoryId }
    val originalCategory = story.preferences.categories.first { it.id == source.categoryId }
    val originalName = story.preferences.name(originalCategory)
    val name = category?.let(preferences::name) ?: originalName
    val hasSourceChip = category != null &&
        ArticleCategories.labelScores(result?.scores.orEmpty(), preferences).any { it.categoryId == source.categoryId }
    var editing by remember(source) { mutableStateOf(false) }
    LaunchedEffect(category?.id) { if (category == null) editing = false }
    var dismissRequest by remember(source) { mutableIntStateOf(0) }
    val backProgress = remember(source) { Animatable(0f) }
    var backEdge by remember(source) { mutableIntStateOf(0) }
    val requestDismiss: () -> Unit = { if (!editing) dismissRequest += 1 }
    val maxWidth = settingsDialogMaxWidth()
    PlatformDialogDimHost {
        Dialog(
            onDismissRequest = requestDismiss,
            properties = platformDialogProperties(
                dismissOnBackPress = !platformDialogPredictiveBackSupported,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                scrimEnabled = false,
                usePlatformInsets = false,
            ),
        ) {
            PlatformDisableDialogWindowAnimations()
            PlatformDialogBackgroundDimAmount(0f)
            DialogPredictiveBackHandler(
                enabled = platformDialogPredictiveBackSupported && !editing,
                onProgress = {
                    backEdge = if (it.swipeDirection < 0f) 1 else 0
                    backProgress.snapTo(it.progress)
                },
                onCancelled = { backProgress.animateTo(0f, tween(180)) },
                onCommitted = { requestDismiss() },
            )
            Box(Modifier.semantics { paneTitle = "$name category inspector" }) {
                TransformOverlay(
                    contentKey = source,
                    sourceBounds = source.geometry.bounds.takeIf { hasSourceChip },
                    // A renamed/removed chip must never replay the old label on return.
                    sourceContentLayer = source.geometry.layer.takeIf { hasSourceChip && name == originalName },
                    dismissRequestVersion = dismissRequest,
                    predictiveBackProgress = backProgress.value,
                    predictiveBackEdge = backEdge,
                    maxWidth = maxWidth,
                    horizontalPadding = 24.dp,
                    verticalPadding = 24.dp,
                    targetCornerRadius = 28.dp,
                    sourceCornerRadius = 5.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    sourceContainerColor = source.colors.container,
                    contentWindowInsets = { WindowInsets.safeDrawing.exclude(WindowInsets.ime) },
                    onSourceReadyToCover = onSourceReadyToCover,
                    onSourceReadyToReveal = onSourceReadyToReveal,
                    onDismissRequest = requestDismiss,
                    onDismissAnimationFinished = onDismiss,
                ) {
                    CategoryInspectorContent(
                        categoryId = source.categoryId,
                        name = name,
                        title = story.title,
                        result = result,
                        preferences = preferences,
                        sample = story.sample,
                        canEdit = category != null,
                        onEditExamples = { editing = true },
                        onDismiss = requestDismiss,
                    )
                }
            }
            // Keep the inspector and feed mounted. The sheet owns back/IME while open and
            // inherits this window so its dim layer also covers the inspector beneath it.
            if (editing && category != null) {
                ArticleCategoryEditor(category, name, onDismiss = { editing = false })
            }
        }
    }
}

@Composable
internal fun CategoryInspectorContent(
    categoryId: String,
    name: String,
    title: String,
    result: ArticleCategoryResult?,
    preferences: ArticleCategoryPreferences,
    sample: Boolean,
    canEdit: Boolean,
    onEditExamples: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val score = result?.scores?.firstOrNull { it.categoryId == categoryId }?.similarity
    val nearby = remember(result, preferences.categories, categoryId) {
        result?.scores.orEmpty().filter { candidate ->
            candidate.categoryId != categoryId && candidate.similarity.isFinite() &&
                preferences.categories.any { it.id == candidate.categoryId }
        }.sortedByDescending { it.similarity }.take(3)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("category-inspector-content")) {
        val compactHeight = maxHeight < 360.dp
        Column(
            Modifier.fillMaxWidth()
                .padding(if (compactHeight) 12.dp else 24.dp)
                .animateContentSize(contentTween()),
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 8.dp else 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(if (compactHeight) { if (sample) "Sample" else "Category" }
                    else if (sample) "Sample category inspector" else "Category match",
                    Modifier.weight(1f).padding(top = if (compactHeight) 8.dp else 12.dp),
                    color = colors.onSurfaceVariant, fontFamily = ProductSansFontFamily,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = if (compactHeight) 12.sp else MaterialTheme.typography.titleSmall.fontSize,
                    lineHeight = if (compactHeight) 16.sp else MaterialTheme.typography.titleSmall.lineHeight,
                    maxLines = if (compactHeight) 1 else 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(Res.drawable.ic_close), "Close category inspector")
                }
            }
            Column(Modifier.weight(1f, fill = false).testTag("category-inspector-scroll-content")
                .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(name, Modifier.semantics { heading() },
                    color = colors.onSurface, fontFamily = ProductSansFontFamily,
                    style = MaterialTheme.typography.headlineSmall)
                Text(title, color = colors.onSurfaceVariant, fontFamily = ProductSansFontFamily,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryScoreMetric("Similarity", score?.takeIf(Float::isFinite)?.let(::similarityScore))
                    CategoryScoreMetric(if (sample) "Sample cutoff" else "Your cutoff", similarityScore(preferences.cutoff))
                }
                if (!canEdit) {
                    Text("This category was removed.", color = colors.onSurfaceVariant,
                        fontFamily = ProductSansFontFamily, style = MaterialTheme.typography.bodySmall)
                } else if (result == null) {
                    Text("This match needs to be recalculated.", color = colors.onSurfaceVariant,
                        fontFamily = ProductSansFontFamily, style = MaterialTheme.typography.bodySmall)
                } else if (score?.isFinite() != true) {
                    Text("This category has no current similarity score. Edit its examples to update the match.",
                        color = colors.onSurfaceVariant, fontFamily = ProductSansFontFamily,
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        when (result.inputSource) {
                            ArticleCategoryInputSource.TITLE_ONLY -> "Based on the title only"
                            ArticleCategoryInputSource.PREVIEW_TEXT -> "Based on the title and preview text"
                        },
                        modifier = Modifier.testTag("category-inspector-provenance"),
                        color = colors.onSurfaceVariant, fontFamily = ProductSansFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (nearby.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Nearby categories", Modifier.semantics { heading() }, color = colors.onSurface,
                        fontFamily = ProductSansFontFamily, style = MaterialTheme.typography.titleSmall)
                    nearby.forEach { match ->
                        val category = preferences.categories.first { it.id == match.categoryId }
                        val categoryName = preferences.name(category)
                        val value = similarityScore(match.similarity)
                        Column(Modifier.fillMaxWidth().clearAndSetSemantics {
                            contentDescription = "$categoryName, similarity $value out of 100"
                        }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(categoryName, Modifier.weight(1f), color = colors.onSurface,
                                    fontFamily = ProductSansFontFamily, style = MaterialTheme.typography.bodyMedium)
                                Text(value, color = colors.onSurfaceVariant, fontFamily = ProductSansFontFamily,
                                    style = MaterialTheme.typography.labelLarge)
                            }
                            LinearProgressIndicator(progress = { match.similarity.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = colors.secondary, trackColor = colors.surfaceContainerHighest,
                                gapSize = 0.dp, drawStopIndicator = {})
                        }
                    }
                }
                Text(if (sample) "These sample scores demonstrate the inspector. They are not a result for a live story."
                    else "Labels need at least 75% of the strongest match.",
                    color = colors.onSurfaceVariant, fontFamily = ProductSansFontFamily,
                    fontSize = 12.sp, lineHeight = 17.sp)
            }
            Button(onClick = onEditExamples, enabled = canEdit,
                contentPadding = PaddingValues(horizontal = if (compactHeight) 12.dp else 24.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("category-inspector-edit-examples")
                    .semantics { contentDescription = "Edit examples for $name" }) {
                Icon(painterResource(Res.drawable.ic_edit), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (compactHeight) "Examples" else "Edit examples", fontFamily = ProductSansFontFamily,
                    modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}

@Composable
private fun CategoryScoreMetric(label: String, value: String?) {
    Column(Modifier.clearAndSetSemantics {
        contentDescription = if (value == null) "$label, not available" else "$label, $value out of 100"
    }) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = ProductSansFontFamily, style = MaterialTheme.typography.labelMedium)
        Text(if (value == null) "—" else "$value / 100", color = MaterialTheme.colorScheme.primary,
            fontFamily = ProductSansFontFamily, style = MaterialTheme.typography.headlineSmall)
    }
}

private fun similarityScore(value: Float) = (value * 100).roundToInt().toString()
