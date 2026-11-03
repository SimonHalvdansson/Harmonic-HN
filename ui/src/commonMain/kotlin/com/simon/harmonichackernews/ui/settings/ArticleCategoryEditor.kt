@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.categorization.*
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.DomainNamePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ArticleCategoryEditor(category: ArticleCategory, name: String, onDismiss: () -> Unit) {
    val app = LocalHarmonicUiDependencies.current
    val training = app.articleCategoryTraining
    val configuration by training.state.collectAsStateWithLifecycle()
    val manager = app.localModels?.cachedState?.collectAsStateWithLifecycle()?.value
    val canTrain = app.articleCategories.modelReady && manager != null
    val scope = rememberCoroutineScope()
    var examples by remember(category.id) { mutableStateOf<List<ArticleTrainingStory>?>(null) }
    var similarities by remember(category.id) { mutableStateOf<Map<Int, Float>>(emptyMap()) }
    var rename by rememberSaveable(category.id) { mutableStateOf(false) }
    var history by rememberSaveable(category.id) { mutableStateOf(false) }
    var info by rememberSaveable(category.id) { mutableStateOf(false) }
    var reset by rememberSaveable(category.id) { mutableStateOf(false) }
    var delete by rememberSaveable(category.id) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val builtIn = ArticleCategories.defaults.any { it.id == category.id }

    LaunchedEffect(category.id, configuration) {
        try {
            similarities = training.exampleSimilarities(category.id)
            examples = training.examples(category.id)
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Couldn't load examples" }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = com.simon.harmonichackernews.ui.common.DialogScrimColor,
        // The sheet has no text fields. Its child dialogs own the keyboard; inactive windows
        // receive the final IME inset early during predictive back, causing a one-frame jump.
        contentWindowInsets = { WindowInsets.safeDrawing.exclude(WindowInsets.ime).union(WindowInsets.navigationBars) },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontFamily = ProductSansFontFamily, fontSize = 24.sp, lineHeight = 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(categoryTrainingCount(examples?.size ?: 0), modifier = Modifier.animateContentSize(), style = MaterialTheme.typography.bodyMedium,
                        fontFamily = ProductSansFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (builtIn) {
                    IconButton(onClick = { reset = true }, enabled = configuration.excluded[category.id].orEmpty().isNotEmpty() ||
                        configuration.added[category.id].orEmpty().isNotEmpty()) {
                        Icon(painterResource(Res.drawable.ic_refresh), "Restore default examples")
                    }
                } else {
                    IconButton(onClick = { delete = true }) { Icon(painterResource(Res.drawable.ic_delete), "Delete category") }
                }
                IconButton(onClick = { rename = true }) { Icon(painterResource(Res.drawable.ic_edit), "Rename category") }
                IconButton(onClick = { info = true }) { Icon(painterResource(Res.drawable.ic_info), "About category examples") }
            }
            if (builtIn) Text(category.description, Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                fontFamily = ProductSansFontFamily, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { history = true }, enabled = canTrain,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                Icon(painterResource(Res.drawable.ic_add), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add from history", fontFamily = ProductSansFontFamily)
            }
            if (!canTrain) Text("Download embedding model to add stories from history.",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error != null) Text(error.orEmpty(), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error)
            if (examples == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp))
            if (!builtIn && configuration.added[category.id].orEmpty().isNotEmpty()) Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { reset = true }) { Text("Clear examples", fontFamily = ProductSansFontFamily) }
            }
            if (examples?.isEmpty() == true) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(Res.drawable.ic_label), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("No examples yet", fontFamily = ProductSansFontFamily, fontSize = 16.sp)
                    Text("Choose a few matching stories from your history to get started.",
                        modifier = Modifier.padding(top = 8.dp), fontFamily = ProductSansFontFamily, fontSize = 14.sp, lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(examples.orEmpty(), key = { it.id }) { story ->
                    Surface(modifier = Modifier.animateItem(
                        fadeInSpec = tween(220), placementSpec = tween(300), fadeOutSpec = tween(180)),
                        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(story.title, fontFamily = ProductSansFontFamily, fontSize = 16.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(listOfNotNull(DomainNamePolicy.fromUrl(story.url), if (story.source == "default") "Default example" else "From history").joinToString(" · "),
                                        fontFamily = ProductSansFontFamily, fontSize = 13.sp, lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    CategoryCosineScore(similarities[story.id])
                                }
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    try { withContext(Dispatchers.Default) { training.removeExample(category.id, story.id) } }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { error = "Couldn't remove example" }
                                }
                            }) { Icon(painterResource(Res.drawable.ic_close), "Remove ${story.title}", Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
        // Nested dialogs must inherit the sheet window so their dim layer covers it.
        if (info) SettingsAlertDialog(
            onDismissRequest = { info = false },
            title = { SettingsDialogTitle("Category examples") },
            scrollableContent = true,
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsDialogBodyText("Choose stories you’ve opened from your history. Their titles and preview text are used to calculate an average embedding for this category. Add examples that cover the topics you want it to match, and remove examples that don’t fit. The scores show cosine similarity to the category center on the same 0–100 scale as your cutoff.\n\nChanges stay on this device. Renaming a category only changes its label.")
            } },
            confirmButton = { SettingsDialogTextButton(onClick = { info = false }) { Text("Got it") } },
        )
        if (rename) CategoryNameDialog("Category name", name, category.name,
            onSave = { app.articleCategorySettings.rename(category.id, it) }, onDismiss = { rename = false })
        if (history) ArticleCategoryHistoryPicker(category.id, name, examples.orEmpty().map { it.id }.toSet(), onDismiss = { history = false })
        if (reset || delete) MessageActionDialog(
            onDismiss = { reset = false; delete = false },
            title = if (delete) "Delete $name?" else if (builtIn) "Restore default examples?" else "Clear examples?",
            message = if (delete) "This removes the category and its examples. Your browsing history stays unchanged."
                else if (builtIn) "This restores the default examples and removes your changes to this category's example set."
                else "This removes this category's examples. You can add stories from history again.",
            positiveLabel = if (delete) "Delete" else if (builtIn) "Restore" else "Clear",
            negativeLabel = "Cancel",
            onPositive = {
                if (delete) { training.delete(category.id); onDismiss() }
                else training.resetExamples(category.id)
                reset = false; delete = false
            },
            onNegative = { reset = false; delete = false },
        )
    }
}
