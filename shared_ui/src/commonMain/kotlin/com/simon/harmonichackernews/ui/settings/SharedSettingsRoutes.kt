package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import com.simon.harmonichackernews.StoryTypeSettingsPolicy
import com.simon.harmonichackernews.format.DelimitedListPolicy
import com.simon.harmonichackernews.settings.AppFont
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.AiModelDefaultsUseCase
import com.simon.harmonichackernews.settings.AiSummaryMode
import com.simon.harmonichackernews.settings.AiSummarySettingsRepository
import com.simon.harmonichackernews.settings.CommentSortingPreference
import com.simon.harmonichackernews.settings.CommentVolumeNavigationMode
import com.simon.harmonichackernews.settings.CommentsProvider
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.ContentFilterType
import com.simon.harmonichackernews.settings.UserTagsRepository
import com.simon.harmonichackernews.settings.ThemePreferences
import com.simon.harmonichackernews.settings.DebugBooleanPreference
import com.simon.harmonichackernews.settings.DataSettingsCounts
import com.simon.harmonichackernews.settings.DataSettingsPolicy
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
import com.simon.harmonichackernews.ui.content.StoryItemUiModel

private val SuggestedArchiveDomains = listOf(
    "ft.com", "wsj.com", "bloomberg.com", "economist.com", "foreignpolicy.com",
    "nytimes.com", "washingtonpost.com", "theatlantic.com", "newyorker.com",
    "technologyreview.com",
)

/**
 * Portable Data settings route. Hosts provide the counts and perform the genuinely platform
 * specific actions (document pickers, OS settings and cache deletion).
 */
@Composable
fun SharedDataSettingsRoute(
    repository: AppSettingsRepository,
    counts: DataSettingsCounts,
    loggedIn: Boolean,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onAction: (DataSettingsAction) -> Unit,
    contentVersion: Int = 0,
) {
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    SharedDataSettingsScreen(
        state = DataSettingsPolicy.snapshot(
            settings = settings,
            counts = counts,
            loggedIn = loggedIn,
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onBookmarksEnabledChanged = {
            repository.setGeneralBoolean(
                com.simon.harmonichackernews.settings.GeneralBooleanPreference.BOOKMARKS_ENABLED,
                it,
            )
        },
        onShowChangelogChanged = {
            repository.setGeneralBoolean(
                com.simon.harmonichackernews.settings.GeneralBooleanPreference.SHOW_CHANGELOG,
                it,
            )
        },
        onAction = onAction,
        contentVersion = settings.hashCode() + contentVersion,
    )
}

/**
 * Portable AI settings route. Device capability probing and the local-model panel remain host
 * services; configuration completeness, default selection and mode enforcement are shared.
 */
@Composable
fun SharedAiSummarySettingsRoute(
    repository: AiSummarySettingsRepository,
    modelDefaults: AiModelDefaultsUseCase,
    localSummarizationSupported: Boolean,
    localConfigurationReady: Boolean,
    localModeAvailable: Boolean,
    showNavigation: Boolean,
    contentVersion: Int,
    onBack: () -> Unit,
    onLocalModeUnavailable: () -> Unit,
    localModelsContent: @Composable () -> Unit,
    dialogContent: @Composable (
        dialog: AiSummarySettingsDialog,
        onDismiss: () -> Unit,
    ) -> Unit,
) {
    val persistedSettings by repository.updates.collectAsState(initial = repository.snapshot())
    var dialog by rememberSaveable { mutableStateOf<AiSummarySettingsDialog?>(null) }

    LaunchedEffect(modelDefaults, localSummarizationSupported, localModeAvailable) {
        modelDefaults.ensureInitialDefault()
        if (!localSummarizationSupported || !localModeAvailable) repository.forceCloudMode()
    }

    val configurationComplete =
        persistedSettings.configurationComplete(localConfigurationReady)
    val enabled = persistedSettings.enabled(localConfigurationReady)
    LaunchedEffect(configurationComplete, enabled, localConfigurationReady) {
        repository.disableIfConfigurationIncomplete(localConfigurationReady)
    }

    SharedAiSummarySettingsScreen(
        state = AiSummarySettingsUiState(
            enabled = enabled,
            configurationComplete = configurationComplete,
            localSummarizationSupported = localSummarizationSupported,
            mode = persistedSettings.mode,
            baseUrl = persistedSettings.baseUrl,
            apiKeyPreview = persistedSettings.apiKeyPreview,
            model = persistedSettings.model,
            systemPrompt = persistedSettings.systemPrompt,
            streamResponses = persistedSettings.streamResponses,
        ),
        showNavigation = showNavigation,
        contentVersion = persistedSettings.hashCode() + contentVersion,
        onBack = onBack,
        onEnabledChanged = repository::setEnabled,
        onModeSelected = { selectedMode ->
            if (selectedMode == AiSummaryMode.LOCAL && !localModeAvailable) {
                onLocalModeUnavailable()
            } else {
                repository.setMode(selectedMode)
            }
        },
        onStreamChanged = repository::setStreamResponses,
        onDialogRequested = { dialog = it },
        localModelsContent = localModelsContent,
    )
    dialog?.let { dialogContent(it) { dialog = null } }
}

@Composable
fun SharedDebugSettingsRoute(
    repository: AppSettingsRepository,
    environment: DebugEnvironmentUiState,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onOpenHnId: (Int) -> Unit,
    onOpenWithoutCache: () -> Unit,
    onOpenLink: (String) -> Unit,
    onEasterEggRequested: () -> Unit,
    dialogContent: @Composable (DebugSettingsDialog, onDismiss: () -> Unit) -> Unit,
) {
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    var dialog by rememberSaveable { mutableStateOf<DebugSettingsDialog?>(null) }
    SharedDebugSettingsScreen(
        showNavigation = showNavigation,
        contentVersion = settings.hashCode(),
        alwaysShowTapToRefresh = settings.debug.alwaysShowTapToRefresh,
        showAiSummaryDebugInfo = settings.debug.showAiSummaryDebugInfo,
        environment = environment,
        onBack = onBack,
        onAlwaysShowTapToRefreshChanged = {
            repository.setDebugBoolean(DebugBooleanPreference.ALWAYS_SHOW_TAP_TO_REFRESH, it)
        },
        onShowAiSummaryDebugInfoChanged = {
            repository.setDebugBoolean(DebugBooleanPreference.SHOW_AI_SUMMARY_INFO, it)
        },
        onOpenHnId = onOpenHnId,
        onOpenWithoutCache = onOpenWithoutCache,
        onOpenLink = onOpenLink,
        onDialogRequested = { dialog = it },
        onEasterEggRequested = onEasterEggRequested,
    )
    dialog?.let { dialogContent(it) { dialog = null } }
}

data class AppearanceRouteLabels(
    val nighttimeRange: String,
    val showTransparentStatusBar: Boolean,
)

@Composable
fun SharedAppearanceSettingsRoute(
    repository: AppSettingsRepository,
    labels: AppearanceRouteLabels,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection) -> Unit,
    onThemeChanged: () -> Unit,
    dialogContent: @Composable (
        dialog: AppearanceSettingsDialog,
        presenter: AppearanceSettingsPresenter,
        onDismiss: () -> Unit,
    ) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<AppearanceSettingsDialog?>(null) }
    val presenter = remember(repository) { AppearanceSettingsPresenter(repository) }
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    SharedAppearanceSettingsScreen(
        state = presenter.state(
            settings = settings,
            themeLabel = harmonicThemeLabel(
                settings.appearance.theme,
                ThemePreferences.DEFAULT,
            ),
            nighttimeRangeLabel = labels.nighttimeRange,
            nighttimeThemeLabel = harmonicThemeLabel(
                settings.appearance.nighttimeTheme,
                ThemePreferences.DEFAULT_NIGHTTIME,
            ),
            fontLabel = settings.story.fontChoice.label,
            showTransparentStatusBar = labels.showTransparentStatusBar,
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onNavigate = onNavigate,
        onBooleanChanged = { setting, value ->
            if (SettingsPlatformEffect.ThemeChanged in presenter.setBoolean(setting, value)) {
                onThemeChanged()
            }
        },
        onDialogRequested = { dialog = it },
        contentVersion = settings.hashCode(),
    )
    dialog?.let { dialogContent(it, presenter) { dialog = null } }
}

@Composable
fun SharedStoriesSettingsRoute(
    repository: AppSettingsRepository,
    previewModel: StoryItemUiModel,
    faviconIcon: Painter,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onPlatformEffect: (SettingsPlatformEffect) -> Unit,
    faviconDialog: @Composable (
        selected: String,
        presenter: StoriesSettingsPresenter,
        onSelected: (String) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf<StoriesSettingsDialog?>(null) }
    val presenter = remember(repository) { StoriesSettingsPresenter(repository) }
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    val story = settings.story
    val state = presenter.state(settings, previewModel, faviconIcon)
    SharedStoriesSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onBooleanChanged = { setting, value ->
            presenter.setBoolean(setting, value).forEach(onPlatformEffect)
        },
        onStringChanged = presenter::setString,
        onTextSizeOffsetChanged = presenter::setTextSizeOffset,
        onDialogRequested = { dialog = it },
        contentVersion = settings.hashCode() + refresh,
    )
    when (dialog) {
        StoriesSettingsDialog.Hotness -> SharedSettingsChoiceDialog(
            "Highlight hot stories",
            listOf(
                "-1" to "Never",
                "100" to "Points + comments > 100",
                "200" to "Points + comments > 200",
                "300" to "Points + comments > 300",
                "400" to "Points + comments > 400",
            ),
            story.hotness.toString(),
            { dialog = null },
            presenter::setHotness,
        )
        StoriesSettingsDialog.StartingPage -> SharedSettingsChoiceDialog(
            "Starting page",
            StoryTypeSettingsPolicy.startingPageLabels(story.additionalFrontpages)
                .map { it to it },
            state.startingPage,
            { dialog = null },
        ) {
            presenter.setStartingPage(it).forEach(onPlatformEffect)
        }
        StoriesSettingsDialog.AdditionalFrontpages -> MultiChoiceDialog(
            title = "Additional frontpages",
            options = StoryTypeSettingsPolicy.additionalFrontpageLabels,
            selected = story.additionalFrontpages,
            onDismiss = { dialog = null },
            onSelectionChanged = {
                presenter.setAdditionalFrontpages(it).forEach(onPlatformEffect)
                dialog = null
            },
        )
        StoriesSettingsDialog.FaviconProvider -> faviconDialog(
            story.faviconProvider,
            presenter,
            {
                presenter.setFaviconProvider(it)
                refresh++
            },
            { dialog = null },
        )
        null -> Unit
    }
}

@Composable
fun SharedCommentsSettingsRoute(
    repository: AppSettingsRepository,
    showNavigation: Boolean,
    onBack: () -> Unit,
    threadDepthDialog: @Composable (
        presenter: CommentsSettingsPresenter,
        onDismiss: () -> Unit,
    ) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<CommentsSettingsDialog?>(null) }
    val presenter = remember(repository) { CommentsSettingsPresenter(repository) }
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    val state = presenter.state(settings)
    SharedCommentsSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onDisplayStyleChanged = presenter::setDisplayStyle,
        onTextSizeOffsetChanged = presenter::setTextSizeOffset,
        onBooleanChanged = presenter::setBoolean,
        onDialogRequested = { dialog = it },
        contentVersion = settings.hashCode(),
    )
    when (dialog) {
        CommentsSettingsDialog.Sorting -> SharedSettingsChoiceDialog(
            "Comment sorting",
            CommentSortingPreference.entries.map { it.storedValue to it.label },
            state.sorting.storedValue,
            { dialog = null },
            presenter::setSorting,
        )
        CommentsSettingsDialog.Provider -> SharedSettingsChoiceDialog(
            "Comments provider",
            CommentsProvider.entries.map { it.storedValue to it.label },
            state.provider.storedValue,
            { dialog = null },
            presenter::setProvider,
        )
        CommentsSettingsDialog.VolumeNavigation -> SharedSettingsChoiceDialog(
            "Volume buttons for navigation",
            CommentVolumeNavigationMode.entries.map { it.storedValue to it.label },
            state.volumeNavigation.storedValue,
            { dialog = null },
            presenter::setVolumeNavigation,
        )
        CommentsSettingsDialog.ThreadDepth -> threadDepthDialog(presenter) { dialog = null }
        null -> Unit
    }
}

@Composable
fun SharedWebLinksSettingsRoute(
    repository: AppSettingsRepository,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<WebLinksSettingsDialog?>(null) }
    val presenter = remember(repository) { WebLinksSettingsPresenter(repository) }
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    val reading = settings.reading
    SharedWebLinksSettingsScreen(
        state = presenter.state(reading.readerFont.label, settings),
        showNavigation = showNavigation,
        onBack = onBack,
        onBooleanChanged = presenter::setBoolean,
        onReaderFontSizeChanged = presenter::setReaderFontSize,
        onDialogRequested = { dialog = it },
        contentVersion = settings.hashCode(),
    )
    when (dialog) {
        WebLinksSettingsDialog.Preload -> SharedPreloadWebViewDialog(
            initialMode = reading.preloadMode,
            initialBattery = reading.preloadWebViewMinimumBattery,
            onSave = presenter::setPreload,
            onDismiss = { dialog = null },
        )
        WebLinksSettingsDialog.ReaderFont -> SharedFontSelectionDialog(
            readerMode = true,
            selected = reading.readerFont,
            options = AppFont.entries.map { it.label to it },
            onSelected = presenter::setReaderFont,
            onDismiss = { dialog = null },
        )
        WebLinksSettingsDialog.ArchiveDomains -> SharedStringListEditorDialog(
            title = "Redirect to archive version",
            subtitle = "Choose domains where Harmonic should automatically redirect the " +
                "embedded browser to the archive.is version.",
            inputLabel = "Domain",
            initialItems = reading.archiveRedirectDomains,
            emptyMessage = "No archive redirect domains",
            suggestedItems = SuggestedArchiveDomains,
            suggestionsLabel = "Suggested domains",
            parseInput = ArchiveRedirectPolicy::parseDomains,
            emptyInputError = "Enter a domain",
            onItemsChanged = presenter::setArchiveDomains,
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
fun SharedFiltersTagsSettingsRoute(
    settings: AppSettingsRepository,
    filters: ContentFilterRepository,
    userTags: UserTagsRepository,
    showNavigation: Boolean,
    onBack: () -> Unit,
    profileDialog: @Composable (
        userName: String,
        onDismiss: () -> Unit,
        onTagChanged: () -> Unit,
    ) -> Unit,
) {
    val presenter = remember(settings, filters, userTags) {
        FiltersTagsSettingsPresenter(settings, filters, userTags)
    }
    val snapshot by settings.updates.collectAsState(initial = settings.snapshot())
    var refresh by remember { mutableIntStateOf(0) }
    var filterDialog by rememberSaveable { mutableStateOf<ContentFilterDialog?>(null) }
    var tagDialogUser by rememberSaveable { mutableStateOf<String?>(null) }
    var profileUser by rememberSaveable { mutableStateOf<String?>(null) }
    SharedFiltersTagsSettingsScreen(
        state = presenter.state(snapshot),
        showNavigation = showNavigation,
        onBack = onBack,
        onHideJobsChanged = presenter::setHideJobs,
        onFilterRequested = { filterDialog = it },
        onProfileRequested = { profileUser = it },
        onTagEditRequested = { tagDialogUser = it },
        onTagDeleteRequested = {
            presenter.setTag(it, "")
            refresh++
        },
        contentVersion = snapshot.hashCode() + refresh,
    )
    filterDialog?.let { type ->
        val content = type.content
        SharedStringListEditorDialog(
            title = content.title,
            subtitle = content.subtitle,
            inputLabel = content.inputLabel,
            initialItems = presenter.filterItems(content.type),
            emptyMessage = content.emptyMessage,
            parseInput = DelimitedListPolicy::parseCommaSeparated,
            emptyInputError = "Enter a value",
            disableSuggestions = content.type == ContentFilterType.USER,
            onItemsChanged = { presenter.setFilterItems(content.type, it) },
            onDismiss = { filterDialog = null },
        )
    }
    tagDialogUser?.let { userName ->
        SharedUserTagDialog(
            currentTag = presenter.tagFor(userName),
            onSave = {
                presenter.setTag(userName, it)
                refresh++
                tagDialogUser = null
            },
            onDismiss = { tagDialogUser = null },
        )
    }
    profileUser?.let { userName ->
        profileDialog(userName, { profileUser = null }) { refresh++ }
    }
}

@Composable
private fun SharedSettingsChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) = SingleChoiceDialog(
    title = title,
    options = options,
    selected = selected,
    onDismiss = onDismiss,
    onSelected = {
        onSelected(it)
        onDismiss()
    },
)
