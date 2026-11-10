package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.platform.accountOrNull
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.AiSummaryMode
import com.simon.harmonichackernews.settings.SettingsSearchEntry
import com.simon.harmonichackernews.settings.SettingsSearchIndex
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import com.simon.harmonichackernews.ui.theme.pageBackground
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import com.simon.harmonichackernews.summary.LocalModelCatalog
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun rememberSettingsSearchEntries(capabilities: SettingsSearchCapabilities): List<SettingsSearchEntry> {
    val dependencies = LocalHarmonicUiDependencies.current
    val account by dependencies.platform.accounts.accountState.collectAsStateWithLifecycle()
    val debugAccount by dependencies.debugAccountIdentity.state.collectAsStateWithLifecycle()
    val ai by dependencies.aiSummarySettings.updates.collectAsStateWithLifecycle(
        initialValue = dependencies.aiSummarySettings.snapshot(),
    )
    val settings by dependencies.settings.updates.collectAsStateWithLifecycle(initialValue = dependencies.settings.snapshot())
    val labels = SettingsSection.entries.associateWith { stringResource(it.titleResource) }
    val localModel by dependencies.localModels?.cachedState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    val availability = SettingsSearchAvailability(
        capabilities = capabilities,
        loggedIn = account.accountOrNull != null,
        accountPreview = debugAccount.username != null,
        debug = dependencies.metadata.debugSettingsEnabled,
        cloudAiSupported = dependencies.aiSummarySettings.cloudAiSupported,
        cloudAi = ai.mode == AiSummaryMode.CLOUD || dependencies.localSummaryEngine?.canAttempt() != true,
        geminiNano = ai.mode == AiSummaryMode.LOCAL && dependencies.localSummaryEngine?.canAttempt() == true &&
            localModel?.selectedModelId == LocalModelCatalog.MODEL_GEMINI_NANO,
        nighttimeScheme = settings.general.specialNighttimeTheme,
        localAi = ai.mode == AiSummaryMode.LOCAL && dependencies.localSummaryEngine?.canAttempt() == true,
    )
    return remember(labels, availability) { settingsSearchCatalog(labels, availability) }
}

@Composable
internal fun SettingsSearchButton(onClick: () -> Unit) {
    val motion = LocalSettingsSearchTransition.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(SettingsSearchBarHeight / 2),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().onGloballyPositioned {
            motion?.sourceBounds = it.boundsInWindow(clipBounds = false)
            motion?.sourceVisibleBounds = it.boundsInWindow()
        },
    ) {
        SettingsSearchPillContent()
    }
}

@Composable
private fun SettingsSearchPillContent(modifier: Modifier = Modifier, showIcon: Boolean = true) {
    Row(
        modifier.fillMaxWidth().height(SettingsSearchBarHeight).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) Icon(painterResource(Res.drawable.ic_search), null, Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else Spacer(Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(stringResource(Res.string.settings_search_title), fontFamily = AppUiFontFamily, fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SettingsSearchDetailPlaceholder() {
    Box(Modifier.fillMaxSize()
        .consumeSettingsPaneBounds()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .padding(top = SettingsSearchBarHeight)
        .imePadding()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        .padding(32.dp).testTag("settings-search-detail-empty"), contentAlignment = Alignment.Center) {
        Text(
            stringResource(Res.string.settings_search_choose_result),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = AppUiFontFamily,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Search has its own retained entry. A result pushes a normal Settings page above it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSearchScreen(
    navigation: SettingsNavigationStore,
    entries: List<SettingsSearchEntry>,
    onBack: () -> Unit,
) {
    val state by navigation.state.collectAsStateWithLifecycle()
    val index = remember(entries) { SettingsSearchIndex(entries) }
    val results = remember(index, state.searchQuery) { index.search(state.searchQuery) }
    val recent = remember(entries, state.recentSearchResultIds) {
        state.recentSearchResultIds.mapNotNull { id -> entries.firstOrNull { it.id == id } }
    }
    val queryEmpty = state.searchQuery.isBlank()
    val displayed = if (queryEmpty) recent else results
    val recentTitle = stringResource(Res.string.settings_search_recent)
    val groups = if (queryEmpty) mapOf(recentTitle to displayed) else displayed.groupBy { it.path.first() }
    val listState = rememberSettingsListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val textFieldState = rememberTextFieldState(initialText = state.searchQuery)
    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    val searchLabel = stringResource(Res.string.settings_search_title)
    val motion = navigation.searchTransition
    val colorScheme = MaterialTheme.colorScheme
    val backLabel = stringResource(Res.string.settings_search_back)
    val topInset = WindowInsets.safeDrawing.getTop(LocalDensity.current)
    val resultCount = stringResource(
        if (results.size == 1) Res.string.settings_search_one_result else Res.string.settings_search_results,
        results.size,
    )
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.distinctUntilChanged().collect { query ->
            if (query != navigation.state.value.searchQuery) {
                navigation.updateSearchQuery(query)
                listState.scrollToItem(0)
            }
        }
    }
    var requestedFocus by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Returning from a result must not reopen the keyboard or reset the results' scroll.
        if (!requestedFocus) {
            requestedFocus = true
            // Let the morph settle before the IME changes the available result area.
            snapshotFlow { motion.expansion() }.first { it >= 1f }
            focusRequester.requestFocus()
        }
    }

    fun changeQuery(query: String) {
        textFieldState.edit { replace(0, length, query) }
        navigation.updateSearchQuery(query)
        scope.launch { listState.scrollToItem(0) }
    }
    fun hideKeyboard() {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    Column(
        Modifier.fillMaxSize().drawBehind {
            drawRect(lerp(colorScheme.surfaceContainerHighest, colorScheme.pageBackground, motion.expansion()))
        }
            .consumeSettingsPaneBounds()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .imePadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        Box(Modifier.fillMaxWidth().height(SettingsSearchBarHeight).settingsSearchHeaderMotion(motion, topInset)) {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = { hideKeyboard() },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = AppUiFontFamily),
                colors = SearchBarDefaults.inputFieldColors().copy(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                placeholder = { Text(stringResource(Res.string.settings_search_title)) },
                // Keep the field's layout, but draw one persistent icon above both text layers.
                leadingIcon = { Spacer(Modifier.size(48.dp)) },
                trailingIcon = if (state.searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { changeQuery(""); focusRequester.requestFocus() }) {
                            Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.settings_search_clear))
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth().height(SettingsSearchBarHeight)
                    .graphicsLayer { alpha = (motion.expansion() / 0.4f).coerceIn(0f, 1f) }
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = searchLabel },
            )
            SettingsSearchPillContent(Modifier.graphicsLayer {
                alpha = 1f - (motion.expansion() / 0.4f).coerceIn(0f, 1f)
            }.clearAndSetSemantics { }, showIcon = false)
            Box(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                    .graphicsLayer {
                        // Pill icon center: 32dp. Expanded field icon center: 28dp.
                        translationX = 4.dp.toPx() * (1f - motion.expansion())
                    }
                    .size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Fade press feedback with the expanded control, while the icon keeps
                // morphing into the source pill without a ripple disappearing at handoff.
                IconButton(
                    onClick = { hideKeyboard(); onBack() },
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { alpha = motion.expansion() }
                        .semantics { contentDescription = backLabel },
                ) { }
                SettingsSearchNavigationIcon(
                    progress = motion.expansion,
                    tint = { colorScheme.onSurfaceVariant },
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().graphicsLayer {
            alpha = ((motion.expansion() - 0.2f) / 0.8f).coerceIn(0f, 1f)
            translationY = (((motion.sourceInViewport()?.top ?: topInset.toFloat()) - topInset) +
                12.dp.toPx()) * (1f - motion.expansion())
        }) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            if (displayed.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp).testTag("settings-search-list-empty"),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_search), null, Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(if (queryEmpty) Res.string.settings_search_hint else Res.string.settings_search_empty),
                        fontFamily = AppUiFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Text(
                        stringResource(if (queryEmpty) Res.string.settings_search_hint_detail else Res.string.settings_search_empty_detail),
                        Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = AppUiFontFamily, fontSize = 14.sp, lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(), state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (!queryEmpty) item(key = "search-result-count") {
                        Text(
                            resultCount,
                            Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = AppUiFontFamily, fontSize = 13.sp,
                        )
                    }
                    groups.forEach { (heading, group) ->
                        settingsCategory(heading) {
                            group.forEach { entry ->
                                row(key = entry.id) {
                                    SettingRow(
                                        title = entry.title,
                                        summary = entry.path.joinToString(" › "),
                                        icon = null,
                                        onClick = {
                                            hideKeyboard()
                                            navigation.openSearchResult(entry)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
