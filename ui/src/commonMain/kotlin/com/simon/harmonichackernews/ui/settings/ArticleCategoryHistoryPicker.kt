package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.simon.harmonichackernews.categorization.ArticleTrainingStory
import com.simon.harmonichackernews.categorization.ArticleTrainingConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.DialogSearchField
import com.simon.harmonichackernews.ui.common.relativeTimeOnResume
import com.simon.harmonichackernews.ui.common.PlatformDisableDialogWindowAnimations
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.DomainNamePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ArticleCategoryHistoryPicker(categoryId: String, name: String, existing: Set<Int>, onDismiss: () -> Unit) {
    val app = LocalHarmonicUiDependencies.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val compactHeight = windowHeight < 500.dp
    val listMaxHeight = windowHeight * 0.62f
    var stories by remember { mutableStateOf<List<ArticleTrainingStory>>(emptyList()) }
    var selected by rememberSaveable(categoryId, stateSaver = CategoryHistorySelectionSaver) {
        mutableStateOf<Set<Int>>(emptySet())
    }
    var query by rememberSaveable(categoryId, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    // Retain only a paging/scroll checkpoint, not potentially megabytes of story text in a Bundle.
    var loadedEnd by rememberSaveable(categoryId) { mutableIntStateOf(0) }
    val restoreEnd = remember(categoryId) { loadedEnd }
    val restoreIndex = remember(categoryId) { listState.firstVisibleItemIndex }
    val restoreScrollOffset = remember(categoryId) { listState.firstVisibleItemScrollOffset }
    var restoring by remember(categoryId) { mutableStateOf(restoreEnd > 0) }
    var offset by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var adding by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryOffset by remember { mutableStateOf<Int?>(null) }
    var addJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val configuration by app.articleCategoryTraining.state.collectAsStateWithLifecycle()
    var scoreConfiguration by remember { mutableStateOf<ArticleTrainingConfiguration?>(null) }
    var similarities by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }
    var hasCenter by remember { mutableStateOf(true) }
    var visibleIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var similarityError by remember { mutableStateOf(false) }
    var scoreRetry by remember { mutableIntStateOf(0) }
    val keyboardAction = remember { SettingsDialogDismissState() }

    fun dismissPicker() {
        addJob?.cancel()
        keyboardAction.request(onDismiss)
    }

    fun addSelected() {
        if (adding || keyboardAction.isClosing || selected.isEmpty()) return
        val chosen = stories.filter { it.id in selected }
        keyboardAction.request {
            adding = true
            error = null
            addJob = scope.launch {
                try {
                    app.articleCategories.train(categoryId, chosen) { done, _ -> progress = done }
                    keyboardAction.request(onDismiss)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    error = if (failure is IllegalArgumentException) failure.message else "Couldn't add examples. Try again."
                } finally { adding = false }
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Int }.toSet() }
            .distinctUntilChanged().collect { visibleIds = it }
    }
    LaunchedEffect(visibleIds, stories, query.text, configuration, adding, scoreRetry) {
        if (adding) return@LaunchedEffect
        try {
            if (scoreConfiguration !== configuration) {
                similarities = app.articleCategoryTraining.exampleSimilarities(categoryId)
                hasCenter = categoryId in app.articleCategoryTraining.centers()
                scoreConfiguration = configuration
            }
            if (!hasCenter) return@LaunchedEffect
            val filtered = stories.filter {
                it.title.contains(query.text, true) || DomainNamePolicy.fromUrl(it.url).orEmpty().contains(query.text, true)
            }
            val first = filtered.indexOfFirst { it.id in visibleIds }.coerceAtLeast(0)
            val last = filtered.indexOfLast { it.id in visibleIds }.coerceAtLeast(first)
            // Start before the first layout, then keep a bounded window ahead of scrolling.
            val pending = filtered.subList((first - 4).coerceAtLeast(0), (last + 13).coerceAtMost(filtered.size))
                .filter { it.id !in similarities }.sortedBy { if (it.id in visibleIds) 0 else 1 }
            if (pending.isEmpty()) return@LaunchedEffect
            similarityError = false
            app.articleCategories.previewSimilarities(categoryId, pending) { id, score -> similarities = similarities + (id to score) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { similarityError = true }
    }

    suspend fun loadMore(retry: Boolean = false) {
        loading = true
        try {
            val requestedOffset = if (retry) retryOffset ?: offset else offset
            val page = app.articleCategoryHistory.page(requestedOffset)
            val firstStories = stories.isEmpty()
            stories = (stories + page.stories).distinctBy { it.id }
            // The loading footer must not remain the anchor when rows arrive above it.
            if (firstStories && !restoring) listState.requestScrollToItem(0)
            offset = maxOf(offset, page.nextOffset)
            loadedEnd = maxOf(loadedEnd, offset)
            if (page.nextOffset >= offset) hasMore = page.hasMore
            retryOffset = requestedOffset.takeIf { page.failures > 0 }
            error = if (page.failures > 0) "Some stories couldn't be loaded. Try again." else null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Couldn't load history. Try again." }
        finally { loading = false }
    }

    LaunchedEffect(categoryId) {
        do {
            val before = offset
            loadMore()
            if (offset <= before || error != null) break
        } while (hasMore && offset < restoreEnd)
        if (restoring) listState.requestScrollToItem(restoreIndex, restoreScrollOffset)
        restoring = false
    }
    val visibleStories = stories.filter {
        it.title.contains(query.text, true) || DomainNamePolicy.fromUrl(it.url).orEmpty().contains(query.text, true)
    }
    SettingsAlertDialog(
        onDismissRequest = { if (!adding) dismissPicker() },
        modifier = Modifier.testTag("category-history-dialog"),
        properties = DialogProperties(
            dismissOnBackPress = !adding && !keyboardAction.isClosing,
            dismissOnClickOutside = !adding && !keyboardAction.isClosing,
        ),
        title = { SettingsDialogTitle("Add to $name") },
        edgeToEdgeContent = true,
        scrollableContent = true,
        text = {
            PlatformDisableDialogWindowAnimations()
            CompleteSettingsDialogDismissal(keyboardAction)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .animateContentSize(tween(300, easing = FastOutSlowInEasing), Alignment.TopCenter)) {
                if (!compactHeight) Text(if (hasCenter) "Choose examples from your history." else "Add an example to start comparing similarities.", Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    fontFamily = ProductSansFontFamily, fontSize = 14.sp, lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Spacer(Modifier.height(12.dp))
                DialogSearchField(query, {
                    if (!keyboardAction.isClosing) { query = it; listState.requestScrollToItem(0) }
                }, "Search loaded history",
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 24.dp)
                        .testTag("category-history-query"), enabled = !adding)
                AnimatedVisibility(visible = adding) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Text("Adding ${(progress + 1).coerceAtMost(selected.size)} of ${selected.size}…",
                            fontFamily = ProductSansFontFamily, fontSize = 14.sp)
                        LinearProgressIndicator(progress = { progress.toFloat() / selected.size.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
                if (error != null) Text(error.orEmpty(), Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    fontFamily = ProductSansFontFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                if (similarityError) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Similarities unavailable", Modifier.weight(1f), fontFamily = ProductSansFontFamily, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { app.articleCategories.retry(); scoreRetry++ }) { Text("Retry", fontFamily = ProductSansFontFamily) }
                }
                if (stories.isEmpty() && !loading && error == null) CategoryHistoryEmptyState(
                    "No stories yet", "Open stories in Harmonic to choose them as examples here.", compactHeight)
                if (stories.isNotEmpty() && visibleStories.isEmpty() && !loading) CategoryHistoryEmptyState(
                    "No matching stories", "Try another title or domain.", compactHeight)
                CategoryHistoryRows(visibleStories, selected, existing, !adding && !keyboardAction.isClosing,
                    Modifier, listState = listState, maxHeight = listMaxHeight, similarities = similarities, footer = {
                        AnimatedVisibility(visible = hasMore || loading || error != null) {
                            FilledTonalButton(
                                enabled = !loading && !adding && !keyboardAction.isClosing,
                                onClick = { scope.launch { loadMore(retry = error != null) } },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                            ) {
                                AnimatedContent(targetState = loading, label = "history page loading") { busy ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        else Icon(painterResource(Res.drawable.ic_history), null, Modifier.size(18.dp))
                                        Text(if (busy) "Loading history…" else if (error != null) "Try again" else "Load older stories",
                                            fontFamily = ProductSansFontFamily)
                                    }
                                }
                            }
                        }
                        if (selected.size >= 20) Text("Add up to 20 at a time.", Modifier.padding(horizontal = 24.dp),
                            fontFamily = ProductSansFontFamily, fontSize = 13.sp)
                    }, onSelection = { id, checked ->
                        if (!keyboardAction.isClosing) {
                            selected = if (checked && selected.size < 20) selected + id else selected - id
                        }
                    })
            }
        },
        confirmButton = { SettingsDialogTextButton(enabled = selected.isNotEmpty() && !adding && !keyboardAction.isClosing,
            modifier = Modifier.animateContentSize(), onClick = ::addSelected) {
            Text(if (selected.isEmpty()) "Add" else "Add (${selected.size})", fontFamily = ProductSansFontFamily, fontSize = 16.sp,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "'liga' 0, 'clig' 0, 'calt' 0, 'dlig' 0")) } },
        dismissButton = { SettingsDialogTextButton(enabled = !keyboardAction.isClosing, onClick = ::dismissPicker) {
            Text("Cancel", fontFamily = ProductSansFontFamily, fontSize = 16.sp)
        } },
    )
}

private val CategoryHistorySelectionSaver = listSaver<Set<Int>, Int>(
    save = { it.toList() },
    restore = { it.take(20).toSet() },
)

@Composable
internal fun CategoryHistoryRows(stories: List<ArticleTrainingStory>, selected: Set<Int>, existing: Set<Int>,
    enabled: Boolean, modifier: Modifier = Modifier, listState: LazyListState = rememberLazyListState(),
    maxHeight: androidx.compose.ui.unit.Dp = 600.dp,
    similarities: Map<Int, Float> = emptyMap(),
    footer: (@Composable () -> Unit)? = null, onSelection: (Int, Boolean) -> Unit) {
    LazyColumn(modifier.fillMaxWidth().heightIn(max = maxHeight), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
        items(stories, key = { it.id }) { story ->
            val alreadyAdded = story.id in existing
            Row(Modifier.fillMaxWidth().animateItem(placementSpec = tween(300))
                .toggleable(value = alreadyAdded || story.id in selected, role = Role.Checkbox,
                    enabled = enabled && !alreadyAdded, onValueChange = { onSelection(story.id, it) })
                .padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = alreadyAdded || story.id in selected, enabled = enabled && !alreadyAdded, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(story.title, fontFamily = ProductSansFontFamily, fontSize = 16.sp, lineHeight = 21.sp,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val viewed = story.viewedAtEpochSeconds?.let { time -> relativeTimeOnResume(time.toInt()).let {
                        if (it == "just now" || it == "?") it else "$it ago"
                    } }
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(listOfNotNull(DomainNamePolicy.fromUrl(story.url)?.takeIf(String::isNotBlank), viewed).joinToString(" · "),
                            fontFamily = ProductSansFontFamily, fontSize = 13.sp, lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        CategoryCosineScore(similarities[story.id])
                    }
                }
            }
        }
        if (footer != null) item(key = "history-footer") {
            Column(Modifier.fillMaxWidth().animateItem(placementSpec = tween(300))) { footer() }
        }
    }
}

@Composable
internal fun CategoryHistoryEmptyState(title: String, subtitle: String, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth().heightIn(min = if (compact) 100.dp else 220.dp).padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (!compact) Icon(painterResource(Res.drawable.ic_history), null, Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, Modifier.padding(top = if (compact) 0.dp else 16.dp), fontFamily = ProductSansFontFamily,
            fontSize = 16.sp, textAlign = TextAlign.Center)
        Text(subtitle, Modifier.padding(top = 8.dp), fontFamily = ProductSansFontFamily,
            fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
