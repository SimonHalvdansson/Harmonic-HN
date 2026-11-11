package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.ColorSchemeStyle

import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.settings.GeneralBooleanPreference
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.ui.navigation.LocalSplitPaneLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import com.simon.harmonichackernews.format.DelimitedListPolicy
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.AppFont
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.AiModelDefaultsUseCase
import com.simon.harmonichackernews.settings.AiSummaryMode
import com.simon.harmonichackernews.settings.AiSummarySettingsRepository
import com.simon.harmonichackernews.settings.CommentSortingPreference
import com.simon.harmonichackernews.settings.CommentVolumeNavigationMode
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.ContentFilterType
import com.simon.harmonichackernews.settings.ArchiveRedirectDomainCatalog
import com.simon.harmonichackernews.settings.UserTagsRepository
import com.simon.harmonichackernews.settings.DebugBooleanPreference
import com.simon.harmonichackernews.settings.DataSettingsCounts
import com.simon.harmonichackernews.settings.DataSettingsPolicy
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
import com.simon.harmonichackernews.ui.content.StoryRowModel
import com.simon.harmonichackernews.ui.content.StoryRowStyleContext
import com.simon.harmonichackernews.ui.content.toStoryRowStyle
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.ui.theme.HarmonicThemePalette
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog

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
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
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
                GeneralBooleanPreference.BOOKMARKS_ENABLED,
                it,
            )
        },
        onShowChangelogChanged = {
            repository.setGeneralBoolean(
                GeneralBooleanPreference.SHOW_CHANGELOG,
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
    localUnavailableReason: String? = null,
    selectedLocalModelId: String? = null,
    geminiNanoAvailable: Boolean = false,
    showNavigation: Boolean,
    contentVersion: Int,
    onBack: () -> Unit,
    onLocalModeUnavailable: () -> Unit,
    onArticleCategoriesRequested: (() -> Unit)? = null,
    localModelsContent: @Composable () -> Unit,
    dialogContent: @Composable (
        dialog: AiSummarySettingsDialog,
        onDismiss: () -> Unit,
    ) -> Unit,
) {
    val persistedSettings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    var dialog by rememberSaveable { mutableStateOf<AiSummarySettingsDialog?>(null) }

    // The page outlives its provider dialog. Changing providers cancels the old lookup,
    // while dismissing the dialog leaves the new provider's model lookup running.
    LaunchedEffect(modelDefaults, persistedSettings.baseUrl) {
        modelDefaults.ensureInitialDefault()
        if (!repository.hasModelSelection()) {
            AiSummaryProviders
                .getProviderForBaseUrl(persistedSettings.baseUrl)
                ?.let { modelDefaults.ensureProviderDefault(it) }
        }
    }
    LaunchedEffect(
        localAvailabilityResolved,
        localSummarizationSupported,
        localModeAvailable,
        geminiNanoAvailable,
        localConfigurationReady,
    ) {
        if (localAvailabilityResolved) {
            repository.preferLocalModeByDefault(
                localAvailable = geminiNanoAvailable && localConfigurationReady,
            )
        }
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
            cloudAiSupported = repository.cloudAiSupported,
            enabled = enabled,
            configurationComplete = configurationComplete,
            localSummarizationSupported = localSummarizationSupported,
            localUnavailableReason = localUnavailableReason,
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
                LocalModelCatalog.MODEL_GEMINI_NANO,
            geminiNanoSummaryMode = persistedSettings.geminiNanoSummaryMode,
            disabledReason = if (
                persistedSettings.mode == AiSummaryMode.CLOUD &&
                persistedSettings.credentialsLoaded && persistedSettings.apiKey.isBlank()
            ) {
                "API key not set"
            } else {
                null
            },
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
        onArticleCategoriesRequested = onArticleCategoriesRequested,
    )
    dialog?.takeIf {
        repository.cloudAiSupported || it == AiSummarySettingsDialog.SystemPrompt
    }?.let { dialogContent(it) { dialog = null } }
}

@Composable
fun DebugSettingsRoute(
    repository: AppSettingsRepository,
    environment: DebugEnvironmentUiState,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onOpenHnId: (Int) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenWithoutCache: () -> Unit,
    onCachePost: () -> Unit,
    onOpenNewCommentsPost: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenLinkPreviews: () -> Unit,
    onOpenGlassSettings: () -> Unit,
    onEasterEggRequested: () -> Unit,
    dialogContent: @Composable (DebugSettingsDialog, onDismiss: () -> Unit) -> Unit,
    onWidgetDebugInfoChanged: () -> Unit = {},
    onWelcomeRequested: () -> Unit,
    debugAccountIdentity: com.simon.harmonichackernews.presentation.DebugAccountIdentity? = null,
    onOpenAccountSettings: () -> Unit = {},
) {
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    var dialog by rememberSaveable { mutableStateOf<DebugSettingsDialog?>(null) }
    DebugSettingsScreen(
        accountContent = debugAccountIdentity?.let { identity -> { DebugAccountControls(identity, onOpenAccountSettings) } },
        showNavigation = showNavigation,
        contentVersion = settings.hashCode(),
        alwaysShowTapToRefresh = settings.debug.alwaysShowTapToRefresh,
        showWidgetDebugInfo = settings.debug.showWidgetDebugInfo,
        mockAiAnswers = settings.debug.mockAiAnswers,
        storyLoadFailurePercent = settings.debug.storyLoadFailurePercent,
        onStoryLoadFailurePercentChanged = repository::setStoryLoadFailurePercent,
        onMockAiAnswersChanged = {
            repository.setDebugBoolean(DebugBooleanPreference.MOCK_AI_ANSWERS, it)
        },
        environment = environment,
        onBack = onBack,
        onAlwaysShowTapToRefreshChanged = {
            repository.setDebugBoolean(DebugBooleanPreference.ALWAYS_SHOW_TAP_TO_REFRESH, it)
        },
        onShowWidgetDebugInfoChanged = {
            repository.setDebugBoolean(DebugBooleanPreference.SHOW_WIDGET_DEBUG_INFO, it)
            onWidgetDebugInfoChanged()
        },
        onGlassSettingsRequested = onOpenGlassSettings,
        onOpenHnId = onOpenHnId,
        onOpenUserProfile = onOpenUserProfile,
        onOpenWithoutCache = onOpenWithoutCache,
        onCachePost = onCachePost,
        onOpenNewCommentsPost = onOpenNewCommentsPost,
        onOpenLink = onOpenLink,
        onLinkPreviewsRequested = onOpenLinkPreviews,
        onWelcomeRequested = onWelcomeRequested,
        onDialogRequested = { dialog = it },
        onEasterEggRequested = onEasterEggRequested,
    )
    dialog?.let { dialogContent(it) { dialog = null } }
}

data class AppearanceRouteLabels(
    val showTransparentStatusBar: Boolean,
    val dynamicColorAvailable: Boolean = true,
    val showExtraSidePadding: Boolean = false,
)

data class ThemeRouteLabels(
    val nighttimeRange: String,
    val activeDark: Boolean,
    val dynamicColorAvailable: Boolean = true,
)

@Composable
fun AppearanceSettingsRoute(
    repository: AppSettingsRepository,
    labels: AppearanceRouteLabels,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection) -> Unit,
    onThemeChanged: () -> Unit,
    onStyleRequested: () -> Unit,
) {
    val presenter = remember(repository) { AppearanceSettingsPresenter(repository) }
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val splitLayout = LocalSplitPaneLayout.current
    AppearanceSettingsScreen(
        state = presenter.state(
            settings = settings,
            themeLabel = themeSettingsSummary(
                settings.appearance,
            ),
            fontLabel = settings.story.fontChoice.label,
            showTransparentStatusBar = labels.showTransparentStatusBar,
        ).copy(
            showExtraSidePadding = splitLayout.supportsTwoPane && labels.showExtraSidePadding,
            extraSidePadding = settings.appearance.extraSidePadding,
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onNavigate = onNavigate,
        onBooleanChanged = { setting, value ->
            if (SettingsPlatformEffect.ThemeChanged in presenter.setBoolean(setting, value)) {
                onThemeChanged()
            }
        },
        onStyleRequested = onStyleRequested,
        onExtraSidePaddingChanged = repository::setExtraSidePadding,
        onSurfaceEffectChanged = repository::setSurfaceEffectMode,
        contentVersion = settings.hashCode(),
    )
}

@Composable
fun PaletteTintSettingsRoute(
    repository: AppSettingsRepository,
    showNavigation: Boolean,
    onBack: () -> Unit,
    previewThemeEffect: @Composable (Boolean) -> Unit = {},
    resolvePreviewScheme: (String, Boolean, ColorSchemeStyle) -> HarmonicThemePalette =
        { scheme, dark, style -> HarmonicThemeCatalog.scheme(scheme, dark, style) },
) {
    val presenter = remember(repository) { AppearanceSettingsPresenter(repository) }
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val config = settings.story.paletteTintConfigKey
    val activeDark = com.simon.harmonichackernews.ui.theme.HarmonicTheme.isDark
    var previewDark by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val dark = previewDark ?: activeDark
    val schemes = settings.appearance.colorSchemes
    val palette = resolvePreviewScheme(schemes.forMode(dark), dark, schemes.styleForMode(dark))
    com.simon.harmonichackernews.ui.theme.HarmonicTheme(palette.colorScheme, dark) {
        previewThemeEffect(dark)
        PaletteTintSettingsScreen(
            initialMode = PaletteTintPreferences.sanitizeMode(config),
            initialStrength = PaletteTintPreferences.strength(config),
            initialColorfulness = PaletteTintPreferences.colorfulness(config),
            initialTone = PaletteTintPreferences.tone(config),
            initialAvoidBackgroundColor = PaletteTintPreferences.avoidBackgroundColor(config),
            previewStyle = StoryDisplaySettings.from(settings.story).toStoryRowStyle(
                StoryRowStyleContext(score = 28, commentCount = 42, isRead = false),
            ).copy(preferredFont = settings.appearance.font),
            showNavigation = showNavigation,
            onBack = onBack,
            onSettingsChanged = { mode, strength, colorfulness, tone, avoidBackgroundColor ->
                presenter.setPaletteTint(mode, strength, colorfulness, tone, avoidBackgroundColor)
            },
            previewDark = dark,
            onTogglePreview = if (settings.appearance.followSystem || settings.general.specialNighttimeTheme) {
                { previewDark = !dark }
            } else null,
            onReset = { presenter.clearPaletteTint() },
        )
    }
}

@Composable
fun ThemeSettingsRoute(
    repository: AppSettingsRepository,
    labels: ThemeRouteLabels,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onThemeChanged: () -> Unit,
    resolvePreviewScheme: (String, Boolean, ColorSchemeStyle) -> HarmonicThemePalette =
        HarmonicThemeCatalog::scheme,
    dialogContent: @Composable (
        dialog: ThemeSettingsDialog,
        presenter: AppearanceSettingsPresenter,
        onDismiss: () -> Unit,
    ) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<ThemeSettingsDialog?>(null) }
    val presenter = remember(repository) { AppearanceSettingsPresenter(repository) }
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())

    fun applyThemeChange(block: () -> Set<SettingsPlatformEffect>) {
        if (SettingsPlatformEffect.ThemeChanged in block()) onThemeChanged()
    }

    ThemeSettingsScreen(
        state = presenter.themeState(
            nighttimeRangeLabel = labels.nighttimeRange,
            activeDark = labels.activeDark,
            dynamicColorAvailable = labels.dynamicColorAvailable,
            settings = settings,
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onFollowSystemChanged = { value -> applyThemeChange { presenter.setFollowSystem(value) } },
        onManualDarkChanged = { value -> applyThemeChange { presenter.setManualDark(value) } },
        onColorSchemeSelected = { value -> applyThemeChange { presenter.setColorScheme(value) } },
        onColorStyleSelected = { value -> applyThemeChange { presenter.setColorStyle(value) } },
        onPureBlackDarkModeChanged = { value -> applyThemeChange { presenter.setPureBlackDarkMode(value) } },
        onNighttimePureBlackDarkModeChanged = { value -> applyThemeChange { presenter.setNighttimePureBlackDarkMode(value) } },
        onNighttimeColorStyleSelected = { value -> applyThemeChange { presenter.setNighttimeColorStyle(value) } },
        onNighttimeColorSchemeSelected = { value -> applyThemeChange { presenter.setNighttimeColorScheme(value) } },
        onSpecialNighttimeChanged = { value ->
            applyThemeChange { presenter.setSpecialNighttime(value) }
        },
        onDialogRequested = { dialog = it },
        resolvePreviewScheme = resolvePreviewScheme,
        previewStyle = StoryDisplaySettings.from(settings.story).toStoryRowStyle(
            StoryRowStyleContext(score = 53, commentCount = 18, isRead = false),
        ).copy(preferredFont = settings.appearance.font),
        commentPreviewStyle = remember(repository) { CommentsSettingsPresenter(repository) }
            .state(settings.copy(pureBlackDarkMode = false)).toPreviewCommentRowStyle().copy(animateChanges = false),
        contentVersion = settings.hashCode(),
    )
    dialog?.let { dialogContent(it, presenter) { dialog = null } }
}

@Composable
fun StoriesSettingsRoute(
    repository: AppSettingsRepository,
    categorySettings: com.simon.harmonichackernews.categorization.ArticleCategorySettings? = null,
    previewModel: StoryRowModel,
    faviconIcon: Painter,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onSpacingRequested: () -> Unit,
    onPlatformEffect: (SettingsPlatformEffect) -> Unit,
    onManageFrontpages: () -> Unit,
    onPaletteTintRequested: () -> Unit,
    onArticleCategoriesRequested: () -> Unit = {},
    categoryAvailability: StoryCategoryAvailability = StoryCategoryAvailability(),
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
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val story = settings.story
    val categories = categorySettings?.updates?.collectAsStateWithLifecycle(initialValue = categorySettings.snapshot())?.value
    val mockCategories = DebugStoryCategoryPreview.enabled
    val enabled = if (mockCategories) DebugStoryCategoryPreview.showTags else categories?.enabled == true
    val previewCategories = if (categories?.enabled == true) categories.categories.filter { it.id in categories.selected }.take(2) else emptyList()
    val state = presenter.state(settings, previewModel.copy(
        categoryLabels = if (mockCategories) { if (enabled) listOf("AI", "Programming") else emptyList() } else previewCategories.map { categories!!.name(it) },
        categoryIds = if (mockCategories) listOf("ai", "software") else previewCategories.map { it.id }), faviconIcon)
        .copy(categorizationEnabled = enabled,
            canEnableCategorization = mockCategories || categoryAvailability.canEnable,
            categorizationUnavailableReason = if (mockCategories) null else categoryAvailability.reason)
    StoriesSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onSpacingRequested = onSpacingRequested,
        onBooleanChanged = { setting, value ->
            presenter.setBoolean(setting, value).forEach(onPlatformEffect)
        },
        onPreviewImageModeChanged = presenter::setPreviewImageMode,
        onManageFrontpages = onManageFrontpages,
        onPaletteTintRequested = onPaletteTintRequested,
        onArticleCategoriesRequested = onArticleCategoriesRequested,
        onCategorizationEnabledChanged = { value ->
            if (mockCategories) DebugStoryCategoryPreview.showTags = value
            else if (!value || categoryAvailability.canEnable) categorySettings?.setEnabled(value)
        },
        onStringChanged = presenter::setString,
        onTextSizeOffsetChanged = presenter::setTextSizeOffset,
        onResetLayout = {
            presenter.resetLayout().forEach(onPlatformEffect)
        },
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
            story.hotnessThreshold.toString(),
            { dialog = null },
            presenter::setHotness,
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
    onSpacingRequested: () -> Unit,
    onThreadDepthRequested: () -> Unit,
    onQuoteHighlightingRequested: () -> Unit,
    onUserAvatarsRequested: () -> Unit,
    volumeNavigationSupported: Boolean = true,
    swipeBackSupported: Boolean = false,
    twoPane: Boolean = false,
) {
    var dialog by rememberSaveable { mutableStateOf<CommentsSettingsDialog?>(null) }
    val presenter = remember(repository) { CommentsSettingsPresenter(repository) }
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val state = presenter.state(settings)
    CommentsSettingsScreen(
        state = state,
        volumeNavigationSupported = volumeNavigationSupported,
        swipeBackSupported = swipeBackSupported,
        twoPane = twoPane,
        showNavigation = showNavigation,
        onBack = onBack,
        onSpacingRequested = onSpacingRequested,
        onDisplayStyleChanged = presenter::setDisplayStyle,
        onCollectedLinksModeChanged = presenter::setCollectedLinksMode,
        onTextSizeOffsetChanged = presenter::setTextSizeOffset,
        onBooleanChanged = presenter::setBoolean,
        onDialogRequested = { dialog = it },
        onThreadDepthRequested = onThreadDepthRequested,
        onThreadDepthEnabledChanged = repository::setCommentDepthIndicatorsEnabled,
        onQuoteHighlightingRequested = onQuoteHighlightingRequested,
        onUserAvatarsRequested = onUserAvatarsRequested,
        onUserAvatarsEnabledChanged = repository::setUserAvatarsEnabled,
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
        CommentsSettingsDialog.HiddenComments -> HiddenCommentsDialog(
            hideFlaggedComments = state.hideFlaggedComments,
            hideDeadComments = state.hideDeadComments,
            hideDelayedComments = state.hideDelayedComments,
            onBooleanChanged = presenter::setBoolean,
            onDismiss = { dialog = null },
        )
        CommentsSettingsDialog.Preload -> PreloadCommentsDialog(
            initialMode = settings.comments.preloadCommentsMode,
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
    onReaderModeRequested: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<WebLinksSettingsDialog?>(null) }
    val presenter = remember(repository) { WebLinksSettingsPresenter(repository) }
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val reading = settings.reading
    WebLinksSettingsScreen(
        state = presenter.state(reading.readerModeFont.label, settings),
        showNavigation = showNavigation,
        onBack = onBack,
        onBooleanChanged = presenter::setBoolean,
        onReaderModeRequested = onReaderModeRequested,
        onDialogRequested = { dialog = it },
        contentVersion = settings.hashCode(),
    )
    when (dialog) {
        WebLinksSettingsDialog.Preload -> PreloadWebViewDialog(
            initialMode = reading.preloadWebViewMode,
            initialBattery = reading.preloadWebViewMinimumBattery,
            onSave = presenter::setPreload,
            onDismiss = { dialog = null },
        )
        WebLinksSettingsDialog.ReaderFont -> FontSelectionDialog(
            readerMode = true,
            selected = reading.readerModeFont,
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
        WebLinksSettingsDialog.NitterInstance -> NitterInstanceDialog(
            initialValue = reading.nitterInstanceUrl,
            onSave = presenter::setNitterInstanceUrl,
            onDismiss = { dialog = null },
        )
        WebLinksSettingsDialog.LinkPreviews -> LinkPreviewsSettingsDialog(
            enabledTypes = reading.enabledLinkPreviews,
            previewsEnabled = reading.linkPreviewsEnabled,
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
    onManageFrontpages: () -> Unit,
    onProfileAccepted: (String) -> Unit = {},
    profileDialog: @Composable (
        userName: String,
        onDismiss: () -> Unit,
        onTagChanged: () -> Unit,
    ) -> Unit,
) {
    val presenter = remember(settings, filters, userTags) {
        FiltersTagsSettingsPresenter(settings, filters, userTags)
    }
    val snapshot by settings.updates.collectAsStateWithLifecycle(initialValue = settings.snapshot())
    var refresh by remember { mutableIntStateOf(0) }
    var filterDialog by rememberSaveable { mutableStateOf<ContentFilterDialog?>(null) }
    var tagDialogUser by rememberSaveable { mutableStateOf<String?>(null) }
    var legacyTagUser by rememberSaveable { mutableStateOf<String?>(null) }
    var legacyBlockUser by rememberSaveable { mutableStateOf<String?>(null) }
    var profileUser by rememberSaveable { mutableStateOf<String?>(null) }
    FiltersTagsSettingsScreen(
        state = presenter.state(snapshot),
        showNavigation = showNavigation,
        onBack = onBack,
        onHideJobsChanged = presenter::setHideJobs,
        onAdditionalFrontpagesRequested = onManageFrontpages,
        onFilterRequested = { filterDialog = it },
        onProfileRequested = { onProfileAccepted(it); profileUser = it },
        onTagEditRequested = { tagDialogUser = it },
        onTagDeleteRequested = {
            presenter.setTag(it, "")
            refresh++
        },
        onLegacyTagReviewRequested = { legacyTagUser = it },
        onLegacyTagDeleteRequested = { userTags.discardLegacyTag(it); refresh++ },
        contentVersion = snapshot.hashCode() + refresh,
    )
    when (filterDialog) {
        ContentFilterDialog.StoryTitle -> TitleFilterRulesDialog(
            initialRules = filters.titleRules(),
            editable = filters.canEditTitleRules(),
            onRulesChanged = { filters.setTitleRules(it); refresh++ },
            onDismiss = { filterDialog = null },
        )
        ContentFilterDialog.Domain -> DomainFilterRulesDialog(
            initialRules = filters.domainRules(),
            editable = filters.canEditDomainRules(),
            onRulesChanged = { filters.setDomainRules(it); refresh++ },
            onDismiss = { filterDialog = null },
        )
        ContentFilterDialog.User -> {
            BlockedUsersDialog(
                users = filters.items(ContentFilterType.USER),
                legacyUsers = filters.legacyUsers(),
                onUsersChanged = { filters.setItems(ContentFilterType.USER, it); refresh++ },
                onProfileRequested = { userName -> onProfileAccepted(userName); profileUser = userName },
                onLegacyReviewRequested = { legacyBlockUser = it },
                onLegacyDiscardRequested = { filters.discardLegacyUser(it); refresh++ },
                nestedDialog = {
                    profileUser?.let { userName ->
                        profileDialog(userName, { profileUser = null }) { refresh++ }
                    }
                    legacyBlockUser?.let { legacy ->
                        LegacyUsernameReviewDialog(
                            title = "Confirm blocked user",
                            savedUsername = legacy,
                            onResolve = { exact ->
                                if (filters.resolveLegacyUser(legacy, exact)) {
                                    refresh++
                                    null
                                } else "Enter one exact username for this saved block."
                            },
                            onDismiss = { legacyBlockUser = null },
                        )
                    }
                },
                onDismiss = { filterDialog = null },
            )
        }
        null -> Unit
    }
    tagDialogUser?.let { userName ->
        UserTagDialog(
            currentTag = presenter.tagFor(userName),
            currentColor = userTags.colorFor(userName),
            userName = userName,
            onSave = { tag, color ->
                presenter.setTag(userName, tag, color)
                refresh++
                tagDialogUser = null
            },
            onDismiss = { tagDialogUser = null },
        )
    }
    legacyTagUser?.let { legacyUsername ->
        userTags.legacyTags().firstOrNull { it.username == legacyUsername }?.let { legacy ->
            LegacyUsernameReviewDialog(
                title = "Confirm tagged user",
                savedUsername = legacy.username,
                savedTag = legacy.tag,
                onResolve = { exact ->
                    when (userTags.resolveLegacyTag(legacy.username, exact)) {
                        com.simon.harmonichackernews.settings.LegacyTagResolution.RESOLVED -> {
                            refresh++
                            null
                        }
                        com.simon.harmonichackernews.settings.LegacyTagResolution.TARGET_EXISTS ->
                            "That exact user already has a tag. Both saved tags have been kept."
                        com.simon.harmonichackernews.settings.LegacyTagResolution.INVALID_USERNAME ->
                            "Enter the exact username."
                        com.simon.harmonichackernews.settings.LegacyTagResolution.INVALID_DATA ->
                            "The saved tag data could not be read. It has been kept unchanged."
                        com.simon.harmonichackernews.settings.LegacyTagResolution.NOT_FOUND ->
                            "This saved tag is no longer awaiting review."
                    }
                },
                onDismiss = { legacyTagUser = null },
            )
        }
    }
    if (filterDialog == null) {
        profileUser?.let { userName ->
            profileDialog(userName, { profileUser = null }) { refresh++ }
        }
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
