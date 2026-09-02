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
import com.simon.harmonichackernews.settings.ArchiveRedirectDomainCatalog
import com.simon.harmonichackernews.settings.UserTagsRepository
import com.simon.harmonichackernews.settings.ThemePreferences
import com.simon.harmonichackernews.settings.DebugBooleanPreference
import com.simon.harmonichackernews.settings.DataSettingsCounts
import com.simon.harmonichackernews.settings.DataSettingsPolicy
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
import com.simon.harmonichackernews.ui.content.StoryItemUiModel

/**
 * Portable Data settings route. Hosts provide the counts and perform the genuinely platform
 * specific actions (document pickers, OS settings and cache deletion).
 */
@Composable
fun DataSettingsRoute(
    repository: AppSettingsRepository,
    counts: DataSettingsCounts,
    loggedIn: Boolean,
    showNavigation: Boolean,
    showAppLinkSettings: Boolean = true,
    onBack: () -> Unit,
    onAction: (DataSettingsAction) -> Unit,
    contentVersion: Int = 0,
) {
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    DataSettingsScreen(
        state = DataSettingsPolicy.snapshot(
            settings = settings,
            counts = counts,
            loggedIn = loggedIn,
        ),
        showNavigation = showNavigation,
        showAppLinkSettings = showAppLinkSettings,
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
fun AiSummarySettingsRoute(
    repository: AiSummarySettingsRepository,
    modelDefaults: AiModelDefaultsUseCase,
    localSummarizationSupported: Boolean,
    localAvailabilityResolved: Boolean,
    localConfigurationReady: Boolean,
    localModeAvailable: Boolean,
    selectedLocalModelId: String? = null,
    geminiNanoAvailable: Boolean = false,
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

    LaunchedEffect(modelDefaults) {
        modelDefaults.ensureInitialDefault()
    }
    LaunchedEffect(
        localAvailabilityResolved,
        localSummarizationSupported,
        localModeAvailable,
    ) {
        if (
            localAvailabilityResolved &&
            (!localSummarizationSupported || !localModeAvailable)
        ) {
            repository.forceCloudMode()
        }
    }

    val configurationComplete =
        persistedSettings.configurationComplete(localConfigurationReady)
    val configurationResolved = when (persistedSettings.mode) {
        AiSummaryMode.LOCAL -> localAvailabilityResolved
        AiSummaryMode.CLOUD -> persistedSettings.credentialsLoaded
    }
    // Preserve the explicitly selected state while secure credentials or local capabilities load.
    // An unresolved dependency must never look like a persisted user choice to turn summaries off.
    val enabled = if (configurationResolved) {
        persistedSettings.enabled(localConfigurationReady)
    } else {
        persistedSettings.explicitlyEnabled == true
    }
    LaunchedEffect(
        configurationComplete,
        configurationResolved,
        enabled,
        localConfigurationReady,
        persistedSettings.credentialsLoaded,
    ) {
        if (configurationResolved) {
            repository.disableIfConfigurationIncomplete(
                localConfigurationReady = localConfigurationReady,
                configurationResolved = true,
            )
        }
    }

    AiSummarySettingsScreen(
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
            autoSummarizeArticles = persistedSettings.autoSummarizeArticles,
            enableBoldFormatting = persistedSettings.enableBoldFormatting,
            showAdditionalInfo = persistedSettings.showAdditionalInfo,
            geminiNanoSelected = geminiNanoAvailable &&
                selectedLocalModelId ==
                com.simon.harmonichackernews.summary.LocalModelCatalog.MODEL_GEMINI_NANO,
            geminiNanoSummaryMode = persistedSettings.geminiNanoSummaryMode,
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
        onGeminiNanoSummaryModeSelected = repository::setGeminiNanoSummaryMode,
        onStreamChanged = repository::setStreamResponses,
        onAutoSummarizeChanged = repository::setAutoSummarizeArticles,
        onEnableBoldFormattingChanged = repository::setEnableBoldFormatting,
        onShowAdditionalInfoChanged = repository::setShowAdditionalInfo,
        onDialogRequested = { dialog = it },
        localModelsContent = localModelsContent,
    )
    dialog?.let { dialogContent(it) { dialog = null } }
}

@Composable
fun DebugSettingsRoute(
    repository: AppSettingsRepository,
    environment: DebugEnvironmentUiState,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onOpenHnId: (Int) -> Unit,
    onOpenWithoutCache: () -> Unit,
    onCachePost: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenLinkPreviews: () -> Unit,
    onEasterEggRequested: () -> Unit,
    dialogContent: @Composable (DebugSettingsDialog, onDismiss: () -> Unit) -> Unit,
) {
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    var dialog by rememberSaveable { mutableStateOf<DebugSettingsDialog?>(null) }
    DebugSettingsScreen(
        showNavigation = showNavigation,
        contentVersion = settings.hashCode(),
        alwaysShowTapToRefresh = settings.debug.alwaysShowTapToRefresh,
        environment = environment,
        onBack = onBack,
        onAlwaysShowTapToRefreshChanged = {
            repository.setDebugBoolean(DebugBooleanPreference.ALWAYS_SHOW_TAP_TO_REFRESH, it)
        },
        onOpenHnId = onOpenHnId,
        onOpenWithoutCache = onOpenWithoutCache,
        onCachePost = onCachePost,
        onOpenLink = onOpenLink,
        onLinkPreviewsRequested = onOpenLinkPreviews,
        onDialogRequested = { dialog = it },
        onEasterEggRequested = onEasterEggRequested,
    )
    dialog?.let { dialogContent(it) { dialog = null } }
}

data class AppearanceRouteLabels(
    val showTransparentStatusBar: Boolean,
    val materialYouAvailable: Boolean = true,
)

data class ThemeRouteLabels(
    val nighttimeRange: String,
    val activeTheme: String,
    val materialYouAvailable: Boolean = true,
)

@Composable
fun AppearanceSettingsRoute(
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
    AppearanceSettingsScreen(
        state = presenter.state(
            settings = settings,
            themeLabel = themeSettingsSummary(
                settings.appearance,
                labels.materialYouAvailable,
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
fun ThemeSettingsRoute(
    repository: AppSettingsRepository,
    labels: ThemeRouteLabels,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onThemeChanged: () -> Unit,
    dialogContent: @Composable (
        dialog: ThemeSettingsDialog,
        presenter: AppearanceSettingsPresenter,
        onDismiss: () -> Unit,
    ) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<ThemeSettingsDialog?>(null) }
    val presenter = remember(repository) { AppearanceSettingsPresenter(repository) }
    val settings by repository.updates.collectAsState(initial = repository.snapshot())

    fun applyThemeChange(block: () -> Set<SettingsPlatformEffect>) {
        if (SettingsPlatformEffect.ThemeChanged in block()) onThemeChanged()
    }

    ThemeSettingsScreen(
        state = presenter.themeState(
            nighttimeRangeLabel = labels.nighttimeRange,
            activeTheme = labels.activeTheme,
            materialYouAvailable = labels.materialYouAvailable,
            settings = settings,
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onFollowSystemChanged = { value -> applyThemeChange { presenter.setFollowSystem(value) } },
        onManualDarkChanged = { value -> applyThemeChange { presenter.setManualDark(value) } },
        onPairSelected = { pair -> applyThemeChange { presenter.setPair(pair) } },
        onAccentSelected = { value -> applyThemeChange { presenter.setAccent(value) } },
        onSpecialNighttimeChanged = { value ->
            applyThemeChange { presenter.setSpecialNighttime(value) }
        },
        onDialogRequested = { dialog = it },
        previewPalette = ThemePreviewCatalog::preview,
        contentVersion = settings.hashCode(),
    )
    dialog?.let { dialogContent(it, presenter) { dialog = null } }
}

@Composable
fun StoriesSettingsRoute(
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
    StoriesSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onBooleanChanged = { setting, value ->
            presenter.setBoolean(setting, value).forEach(onPlatformEffect)
        },
        onPreviewImageModeChanged = presenter::setPreviewImageMode,
        onStringChanged = presenter::setString,
        onTextSizeOffsetChanged = presenter::setTextSizeOffset,
        onDialogRequested = { dialog = it },
        contentVersion = settings.hashCode() + refresh,
    )
    when (dialog) {
        StoriesSettingsDialog.Hotness -> SettingsChoiceDialog(
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
        StoriesSettingsDialog.StartingPage -> SettingsChoiceDialog(
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
            description = "Choose which optional Hacker News frontpages appear in the story-list picker. You can set the default under Starting page.",
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
fun CommentsSettingsRoute(
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
    CommentsSettingsScreen(
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
        CommentsSettingsDialog.Sorting -> SettingsChoiceDialog(
            "Comment sorting",
            CommentSortingPreference.entries.map { it.storedValue to it.label },
            state.sorting.storedValue,
            { dialog = null },
            presenter::setSorting,
        )
        CommentsSettingsDialog.Provider -> CommentsProviderDialog(
            selected = state.provider,
            onProviderSelected = { presenter.setProvider(it.storedValue) },
            onDismiss = { dialog = null },
        )
        CommentsSettingsDialog.VolumeNavigation -> SettingsChoiceDialog(
            "Volume buttons for navigation",
            CommentVolumeNavigationMode.entries.map { it.storedValue to it.label },
            state.volumeNavigation.storedValue,
            { dialog = null },
            presenter::setVolumeNavigation,
        )
        CommentsSettingsDialog.ThreadDepth -> threadDepthDialog(presenter) { dialog = null }
        CommentsSettingsDialog.Preload -> PreloadCommentsDialog(
            initialMode = settings.comments.commentsPreloadMode,
            initialBattery = settings.comments.preloadCommentsMinimumBattery,
            onSave = presenter::setPreload,
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
fun WebLinksSettingsRoute(
    repository: AppSettingsRepository,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<WebLinksSettingsDialog?>(null) }
    val presenter = remember(repository) { WebLinksSettingsPresenter(repository) }
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    val reading = settings.reading
    WebLinksSettingsScreen(
        state = presenter.state(reading.readerFont.label, settings),
        showNavigation = showNavigation,
        onBack = onBack,
        onBooleanChanged = presenter::setBoolean,
        onReaderFontSizeChanged = presenter::setReaderFontSize,
        onDialogRequested = { dialog = it },
        contentVersion = settings.hashCode(),
    )
    when (dialog) {
        WebLinksSettingsDialog.Preload -> PreloadWebViewDialog(
            initialMode = reading.preloadMode,
            initialBattery = reading.preloadWebViewMinimumBattery,
            onSave = presenter::setPreload,
            onDismiss = { dialog = null },
        )
        WebLinksSettingsDialog.ReaderFont -> FontSelectionDialog(
            readerMode = true,
            selected = reading.readerFont,
            options = AppFont.entries.map { it.label to it },
            onSelected = presenter::setReaderFont,
            onDismiss = { dialog = null },
        )
        WebLinksSettingsDialog.ArchiveDomains -> StringListEditorDialog(
            title = "Redirect to archive version",
            subtitle = "Choose domains where Harmonic should automatically redirect the " +
                "embedded browser to the archive.is version.",
            inputLabel = "Domain",
            initialItems = reading.archiveRedirectDomains,
            emptyMessage = "No archive redirect domains",
            suggestedItems = ArchiveRedirectDomainCatalog.suggested,
            suggestionsLabel = "Suggested domains",
            parseInput = ArchiveRedirectPolicy::parseDomains,
            emptyInputError = "Enter a domain",
            onItemsChanged = presenter::setArchiveDomains,
            onDismiss = { dialog = null },
        )
        WebLinksSettingsDialog.LinkPreviews -> LinkPreviewsSettingsDialog(
            enabledTypes = reading.enabledLinkPreviews,
            onEnabledChanged = presenter::setLinkPreview,
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
fun FiltersTagsSettingsRoute(
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
    FiltersTagsSettingsScreen(
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
        StringListEditorDialog(
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
        UserTagDialog(
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
private fun SettingsChoiceDialog(
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
