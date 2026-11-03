package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.categorization.*
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelTransferState
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.themeBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@Composable
fun ArticleCategorySettingsScreen(onBack: () -> Unit, onHowItWorks: () -> Unit) {
    val app = LocalHarmonicUiDependencies.current
    val repository = app.articleCategorySettings
    val preferences by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val configuration by app.articleCategoryTraining.state.collectAsStateWithLifecycle()
    val runtime by app.articleCategories.state.collectAsStateWithLifecycle()
    val models = app.localModels
    val manager = models?.cachedState?.collectAsStateWithLifecycle()?.value
    val model = LocalModelCatalog.embeddingModel
    val supported = app.articleCategories.supported && models?.isSupported(model) == true
    val transfer = manager?.statuses?.get(model.id)
    val delivery = manager?.runtimeStatuses?.get(model.runtime)
    val installingRuntime = delivery?.active == true && delivery.pendingModelId == model.id
    val downloaded = transfer?.state == LocalModelTransferState.DOWNLOADED
    val transferring = transfer?.state in setOf(LocalModelTransferState.DOWNLOADING, LocalModelTransferState.WAITING) || installingRuntime
    val received = if (installingRuntime) delivery?.downloadedBytes ?: 0L else transfer?.receivedBytes ?: 0L
    val total = if (installingRuntime) delivery?.totalBytes ?: 0L else model.sizeBytes
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var confirmRemoval by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var requestingDownload by remember { mutableStateOf(false) }
    var cancellingDownload by remember { mutableStateOf(false) }
    var resettingCutoff by remember { mutableStateOf(false) }
    val displayedCutoff by animateFloatAsState(preferences.cutoff,
        animationSpec = if (resettingCutoff) tween(350, easing = FastOutSlowInEasing) else snap(),
        finishedListener = { resettingCutoff = false }, label = "similarity reset")
    val downloadActive = transferring || requestingDownload
    LaunchedEffect(transferring, downloaded, transfer?.state, delivery?.state) {
        if (transferring || downloaded || transfer?.state == LocalModelTransferState.FAILED) requestingDownload = false
        if (!transferring) cancellingDownload = false
    }

    LaunchedEffect(configuration) {
        try {
            counts = preferences.categories.associate { it.id to app.articleCategoryTraining.examples(it.id).size }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "Couldn't load examples" }
    }

    SettingsPage(title = "Article categories", showNavigation = true, onBack = onBack,
        contentVersion = preferences.hashCode() + configuration.hashCode() + (transfer?.hashCode() ?: 0)) {
        item {
            SettingsMainToggle(
                title = "Categorize stories",
                checked = preferences.enabled && downloaded,
                enabled = supported && downloaded && !removing,
                summary = when {
                    !supported -> "Unavailable on this device or in this build"
                    !downloaded -> "Download embedding model"
                    else -> null
                },
                onCheckedChange = { if (downloaded) repository.setEnabled(it) },
            )
        }
        item(key = "category-introduction") { ArticleCategoryIntroduction(onHowItWorks) }
        item(key = "embedding-model") {
            SettingsCategory("Model", titleTopPadding = 8.dp) {
              Column(Modifier.fillMaxWidth().themeBackground { cardBackground }) {
                SettingRow(
                    title = "EmbeddingGemma 2",
                    summary = when {
                        removing -> "Removing…"
                        installingRuntime -> "Installing runtime…"
                        downloadActive -> "${received / 1_000_000} / ${(model.sizeBytes + 999_999) / 1_000_000} MB"
                        else -> "165 MB"
                    },
                    icon = Res.drawable.model_logo_google,
                    onClick = null,
                    trailing = {
                        AnimatedContent(
                            targetState = when { downloaded -> "downloaded"; downloadActive -> "downloading"; else -> "available" },
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            contentAlignment = Alignment.CenterEnd,
                            modifier = Modifier.size(48.dp), label = "embedding model action",
                        ) { action -> when (action) {
                            "downloading" -> IconButton(enabled = !cancellingDownload, onClick = {
                                cancellingDownload = true
                                scope.launch {
                                    try { models?.cancel(model.id); requestingDownload = false }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { message = "Couldn't cancel download. Try again." }
                                    finally { cancellingDownload = false }
                                }
                            }) { Icon(painterResource(Res.drawable.ic_close), "Cancel model download") }
                            "downloaded" -> IconButton(onClick = { confirmRemoval = true }, enabled = !removing) {
                                Icon(painterResource(Res.drawable.ic_delete), "Remove embedding model")
                            }
                            else -> IconButton(enabled = supported, onClick = {
                                requestingDownload = true
                                scope.launch {
                                    try {
                                        message = models?.requestRuntimeAndModelDownload(model.id)
                                        if (message != null) requestingDownload = false
                                    } catch (cancelled: CancellationException) {
                                        requestingDownload = false
                                        throw cancelled
                                    } catch (_: Exception) {
                                        requestingDownload = false
                                        message = "Couldn't start download. Try again."
                                    }
                                }
                            }) { Icon(painterResource(Res.drawable.ic_file_download), if (transfer?.state == LocalModelTransferState.FAILED) "Retry model download" else "Download embedding model") }
                        } }
                    },
                )
                FlowRow(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LocalModelTag("Persistent background", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    LocalModelTag("191 MB RAM", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                }
                EmbeddingModelProgress(downloadActive, received, total)
                if (runtime.status == ArticleCategoryStatus.FAILED) SettingRow(
                    title = "Couldn't load model", summary = "Tap to retry", icon = Res.drawable.ic_info,
                    onClick = app.articleCategories::retry,
                )
                if (runtime.status == ArticleCategoryStatus.PAUSED) SettingRow(
                    title = "Categorization paused", summary = "Tap to resume", icon = Res.drawable.ic_info,
                    onClick = app.articleCategories::retry,
                )
                val error = message ?: transfer?.error?.takeIf(String::isNotBlank) ?: delivery?.error?.takeIf(String::isNotBlank)
                if (error != null) CategorySettingsInfo(error, error = true)
              }
            }
        }
        settingsCategory("Categories") {
            preferences.categories.forEach { category ->
                row(category.id) {
                    NavigationSwitchSettingRow(
                        title = preferences.name(category),
                        summary = counts[category.id]?.let(::categoryTrainingCount),
                        icon = com.simon.harmonichackernews.ui.content.categorySymbol(category.id),
                        onClick = { editing = category.id },
                        checked = category.id in preferences.selected,
                        onCheckedChange = { repository.setSelected(category.id, it) },
                    )
                }
            }
            row("create-category") { SettingRow(title = "New category", icon = Res.drawable.ic_add,
                enabled = app.articleCategories.modelReady && !removing, onClick = { creating = true }) }
        }
        item {
            SettingsCategory("Configuration") {
                CategorySimilaritySetting(value = displayedCutoff,
                    onValueChange = { resettingCutoff = false; repository.setCutoff((it * 100).roundToInt() / 100f) },
                    onReset = { resettingCutoff = true; repository.setCutoff(ArticleCategories.DEFAULT_CUTOFF) },
                    resetEnabled = preferences.cutoff != ArticleCategories.DEFAULT_CUTOFF)
                SettingsDivider()
                SegmentedSetting(title = "Maximum labels per story", options = listOf(1 to "1", 2 to "2", 3 to "3"),
                    selected = preferences.maxLabels, onSelected = repository::setMaxLabels)
            }
        }
    }
    preferences.categories.firstOrNull { it.id == editing }?.let { category ->
        ArticleCategoryEditor(category, preferences.name(category), onDismiss = { editing = null })
    }
    if (creating) CategoryNameDialog("New category", "", "", onSave = { name ->
        val id = app.articleCategoryTraining.create(name)
        repository.setSelected(id, true)
        creating = false
        editing = id
    }, onDismiss = { creating = false })
    if (confirmRemoval) MessageActionDialog(
        onDismiss = { confirmRemoval = false },
        title = "Remove embedding model?",
        message = "This frees 165 MB and turns categorization off. Your categories and examples stay on this device.",
        positiveLabel = "Remove",
        negativeLabel = "Cancel",
        onPositive = {
            confirmRemoval = false
            removing = true
            scope.launch {
                try { app.articleCategories.removeModel() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "Couldn't remove model. Try again." }
                finally { removing = false }
            }
        },
        onNegative = { confirmRemoval = false },
    )
}

@Composable
internal fun EmbeddingModelProgress(visible: Boolean, received: Long, total: Long) {
    val progress by animateFloatAsState(
        targetValue = if (visible && total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(250, easing = LinearEasing), label = "embedding download progress")
    // Animate the measured child height so the enclosing settings card and following rows move too.
    AnimatedVisibility(visible,
        enter = expandVertically(tween(300, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(300, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(150))) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
            if (total > 0 && received > 0) LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun CategorySettingsInfo(text: String, error: Boolean = false) {
    Text(text, modifier = Modifier.fillMaxWidth().themeBackground { cardBackground }.padding(horizontal = 24.dp, vertical = 12.dp),
        fontFamily = com.simon.harmonichackernews.ui.theme.ProductSansFontFamily, fontSize = 14.sp, lineHeight = 20.sp,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun categoryTrainingCount(count: Int): String = "$count ${if (count == 1) "example" else "examples"}"

@Composable
internal fun CategoryNameDialog(title: String, initial: String, default: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    AiSummaryTextDialog(title, "Name", initial, default,
        minLines = 1, maxLines = 1, textSizeSp = 16, trimValue = true, allowEmpty = false,
        showReset = default.isNotEmpty(), asciiInput = false,
        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
        maxLength = 40,
        onSave = { name -> if (name.length > 40) "Use 40 characters or fewer" else {
            try { onSave(name); null } catch (error: IllegalArgumentException) { error.message ?: "Couldn't save category" }
        } }, onDismiss = onDismiss)
}
