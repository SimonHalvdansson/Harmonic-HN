package com.simon.harmonichackernews.ios

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.app.CommonLicenseCatalog
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.debug.DebugCachedPostFixture
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.network.CloudSummaryDefaults
import com.simon.harmonichackernews.network.LinkPreviewUseCase
import com.simon.harmonichackernews.presentation.UserMessageDuration
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.quanta
import com.simon.harmonichackernews.settings.AiSummaryTextSetting
import com.simon.harmonichackernews.settings.ArchiveRedirectDomainCatalog
import com.simon.harmonichackernews.settings.DataSettingsCounts
import com.simon.harmonichackernews.settings.DataSettingsDialogState
import com.simon.harmonichackernews.settings.DataSettingsRuntimeEffect
import com.simon.harmonichackernews.settings.NighttimeSchedule
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.ThemeSelectionPolicy
import com.simon.harmonichackernews.ui.about.AboutScreen
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.licenses.LicensesScreen
import com.simon.harmonichackernews.ui.settings.AddBookmarksToFavoritesDialog
import com.simon.harmonichackernews.ui.settings.AiSummaryBaseUrlDialog
import com.simon.harmonichackernews.ui.settings.AiSummarySettingsDialog
import com.simon.harmonichackernews.ui.settings.AiSummaryTextDialog
import com.simon.harmonichackernews.ui.settings.AppearanceRouteLabels
import com.simon.harmonichackernews.ui.settings.AppearanceSettingsDialog
import com.simon.harmonichackernews.ui.settings.ClearAiModelsConfirmationDialog
import com.simon.harmonichackernews.ui.settings.DataSettingsAction
import com.simon.harmonichackernews.ui.settings.DebugEnvironmentUiState
import com.simon.harmonichackernews.ui.settings.DebugSettingsDialog
import com.simon.harmonichackernews.ui.settings.ImportBookmarksDialog
import com.simon.harmonichackernews.ui.settings.MessageActionDialog
import com.simon.harmonichackernews.ui.settings.SettingsChangelogDialog
import com.simon.harmonichackernews.ui.settings.SettingsPlatformEffect
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.settings.AiModelSelectorRoute
import com.simon.harmonichackernews.ui.settings.AiSummarySettingsRoute
import com.simon.harmonichackernews.ui.settings.ManagedLocalModelPanel
import com.simon.harmonichackernews.ui.settings.AppearanceSettingsRoute
import com.simon.harmonichackernews.ui.settings.CommentsSettingsRoute
import com.simon.harmonichackernews.ui.settings.DataSettingsRoute
import com.simon.harmonichackernews.ui.settings.DebugSettingsRoute
import com.simon.harmonichackernews.ui.settings.FaviconProviderRoute
import com.simon.harmonichackernews.ui.settings.FiltersTagsSettingsRoute
import com.simon.harmonichackernews.ui.settings.FontSelectionRoute
import com.simon.harmonichackernews.ui.settings.NighttimeRangeDialog
import com.simon.harmonichackernews.ui.settings.PaletteTintDialog
import com.simon.harmonichackernews.ui.settings.LinkPreviewsSettingsDialog
import com.simon.harmonichackernews.ui.settings.LinkPreviewsDebugScreen
import com.simon.harmonichackernews.ui.settings.StoriesSettingsRoute
import com.simon.harmonichackernews.ui.settings.ThemeSelectionDialog
import com.simon.harmonichackernews.ui.settings.ThreadDepthIndicatorsDialog
import com.simon.harmonichackernews.ui.settings.WelcomeSettingsDialog
import com.simon.harmonichackernews.ui.settings.StringListEditorDialog
import com.simon.harmonichackernews.ui.settings.ThemePreviewCatalog
import com.simon.harmonichackernews.ui.settings.WebLinksBooleanSetting
import com.simon.harmonichackernews.ui.settings.WebLinksSettingsDialog
import com.simon.harmonichackernews.ui.settings.WebLinksSettingsPresenter
import com.simon.harmonichackernews.ui.settings.WebLinksSettingsScreen
import com.simon.harmonichackernews.ui.settings.faviconProviderPainter
import com.simon.harmonichackernews.ui.theme.CommentDepthPaletteCatalog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import platform.UIKit.UIDevice

private const val IosOpenWithoutCacheStoryId = 49089500

@Composable
internal fun IosSettingsDetail(
    section: SettingsSection,
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    singlePane: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection, Boolean) -> Unit,
) {
    when (section) {
        SettingsSection.Appearance -> IosAppearanceSettings(app, singlePane, onBack) {
            onNavigate(it, singlePane)
        }
        SettingsSection.Stories -> IosStoriesSettings(app, singlePane, onBack)
        SettingsSection.Comments -> IosCommentsSettings(app, singlePane, onBack)
        SettingsSection.WebLinks -> IosWebLinksSettings(
            app = app,
            scene = scene,
            showNavigation = singlePane,
            onBack = onBack,
        )
        SettingsSection.FiltersTags -> FiltersTagsSettingsRoute(
            settings = app.settings,
            filters = app.contentFilters,
            userTags = app.userTags,
            showNavigation = singlePane,
            onBack = onBack,
            profileDialog = { userName, dismiss, onTagChanged ->
                IosUserProfileDialog(app, scene, userName, dismiss, onTagChanged)
            },
        )
        SettingsSection.AiSummary -> IosAiSettings(app, scene, singlePane, onBack)
        SettingsSection.Data -> IosDataSettings(app, scene, singlePane, onBack)
        SettingsSection.Debug -> IosDebugSettings(
            app,
            scene,
            singlePane,
            onBack,
        ) { onNavigate(SettingsSection.DebugLinkPreviews, true) }
        SettingsSection.DebugLinkPreviews -> IosLinkPreviewsDebugScreen(
            app,
            scene,
            onBack,
        )
        SettingsSection.About -> AboutScreen(
            versionLabel = app.metadata.versionLabel,
            appIcon = painterResource(Res.drawable.quanta),
            onBack = onBack,
            onOpenGithub = { scene.links.open(app.metadata.projectUrl) },
            onOpenChangelog = scene.navigation::showChangelogDialog,
            onOpenLicenses = { onNavigate(SettingsSection.Licenses, true) },
            onOpenPrivacy = { scene.links.open(app.metadata.privacyUrl) },
            showNavigation = singlePane,
            aboutBody = "Harmonic is an open-source Hacker News client. " +
                "This iOS host uses the same Kotlin Multiplatform application logic and " +
                "Compose screens as the Android app, with iOS-native storage, links, and " +
                "keyboard/window behavior.",
        )
        SettingsSection.Licenses -> LicensesScreen(
            licenses = CommonLicenseCatalog.entries,
            onBack = onBack,
            onOpenLicense = { scene.links.open(it) },
        )
    }
}

@Composable
private fun IosWebLinksSettings(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<WebLinksSettingsDialog?>(null) }
    val presenter = remember(app.settings) { WebLinksSettingsPresenter(app.settings) }
    val settings by app.settings.updates.collectAsState(initial = app.settings.snapshot())
    val reading = settings.reading

    WebLinksSettingsScreen(
        state = presenter.state(reading.readerFont.label, settings),
        showNavigation = showNavigation,
        onBack = onBack,
        onBooleanChanged = { setting, value ->
            presenter.setBoolean(setting, value)
        },
        onReaderFontSizeChanged = presenter::setReaderFontSize,
        onDialogRequested = { requested ->
            if (
                requested == WebLinksSettingsDialog.ArchiveDomains ||
                requested == WebLinksSettingsDialog.LinkPreviews
            ) {
                dialog = requested
            }
        },
        contentVersion = settings.hashCode(),
    )

    if (dialog == WebLinksSettingsDialog.ArchiveDomains) {
        StringListEditorDialog(
            title = "Redirect to archive version",
            subtitle = "Choose domains where Harmonic should redirect links to archive.is.",
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
    }
    if (dialog == WebLinksSettingsDialog.LinkPreviews) {
        LinkPreviewsSettingsDialog(
            enabledTypes = reading.enabledLinkPreviews,
            onEnabledChanged = presenter::setLinkPreview,
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun IosAppearanceSettings(
    app: HarmonicAppComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection) -> Unit,
) {
    val snapshot = app.settings.snapshot()
    val schedule = app.appearance.schedule
    fun themeChanged() = app.appearance.refreshSelection()

    AppearanceSettingsRoute(
        repository = app.settings,
        labels = AppearanceRouteLabels(
            nighttimeRange = ThemeSelectionPolicy.formatSchedule(
                schedule,
                app.platform.timeFormatting.uses24HourClock(),
            ),
            showTransparentStatusBar = false,
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onNavigate = onNavigate,
        onThemeChanged = ::themeChanged,
        dialogContent = { dialog, presenter, dismiss ->
            when (dialog) {
                AppearanceSettingsDialog.Theme,
                AppearanceSettingsDialog.NighttimeTheme -> {
                    val nighttime = dialog == AppearanceSettingsDialog.NighttimeTheme
                    ThemeSelectionDialog(
                        nighttime = nighttime,
                        selected = if (nighttime) {
                            presenter.snapshot.appearance.nighttimeTheme
                        } else {
                            presenter.snapshot.appearance.theme
                        },
                        onThemeSelected = { value ->
                            presenter.setTheme(value, nighttime)
                            themeChanged()
                            dismiss()
                        },
                        onDismiss = dismiss,
                        previewPalettes = { ThemePreviewCatalog.palettes(it) },
                    )
                }
                AppearanceSettingsDialog.NighttimeRange -> NighttimeRangeDialog(
                    initialHours = app.appearance.schedule.toIntArray(),
                    is24Hour = app.platform.timeFormatting.uses24HourClock(),
                    onRangeSelected = { fromHour, fromMinute, toHour, toMinute ->
                        app.appearance.saveSchedule(
                            NighttimeSchedule(fromHour, fromMinute, toHour, toMinute),
                        )
                        themeChanged()
                    },
                    onDismiss = dismiss,
                )
                AppearanceSettingsDialog.Font -> FontSelectionRoute(
                    readerMode = false,
                    onDismiss = dismiss,
                )
                AppearanceSettingsDialog.Style -> {
                    val colors = HarmonicTheme.colors
                    WelcomeSettingsDialog(
                        styleChooser = true,
                        initialExpressive = snapshot.story.cardStyle,
                        onApplyPreset = { expressive ->
                            presenter.applyWelcomePreset(expressive)
                            themeChanged()
                            dismiss()
                        },
                        onDismiss = dismiss,
                        filterButtonColors = HarmonicFilterButtonColors(
                            checkedBackground = colors.storyNormal,
                            checkedText = colors.background,
                            checkedStroke = colors.storyNormal,
                            uncheckedText = colors.storyNormal,
                            uncheckedStroke = colors.outlineVariant,
                        ),
                        launcherIcon = {
                            Image(
                                painter = painterResource(Res.drawable.quanta),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                            )
                        },
                    )
                }
                AppearanceSettingsDialog.PaletteTint -> {
                    val config = presenter.snapshot.story.paletteTintConfigKey
                    PaletteTintDialog(
                        initialMode = PaletteTintPreferences.sanitizeMode(config),
                        initialStrength = PaletteTintPreferences.strength(config),
                        initialColorfulness = PaletteTintPreferences.colorfulness(config),
                        initialTone = PaletteTintPreferences.tone(config),
                        onSettingsChanged = { mode, strength, colorfulness, tone ->
                            presenter.setPaletteTint(mode, strength, colorfulness, tone)
                        },
                        onReset = { presenter.clearPaletteTint() },
                        onDismiss = dismiss,
                    )
                }
            }
        },
    )
}

@Composable
private fun IosStoriesSettings(
    app: HarmonicAppComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val story = app.settings.snapshot().story
    StoriesSettingsRoute(
        repository = app.settings,
        previewModel = SettingsStoryPreviewModel.copy(
            tintFallbackArgb = HarmonicTheme.colors.surfaceContainerHigh.toArgb(),
        ),
        faviconIcon = faviconProviderPainter(story.faviconProvider),
        showNavigation = showNavigation,
        onBack = onBack,
        onPlatformEffect = { effect ->
            when (effect) {
                SettingsPlatformEffect.RefreshStoryWidgets -> Unit
                SettingsPlatformEffect.ThemeChanged -> app.appearance.refreshSelection()
            }
        },
        faviconDialog = { selected, _, onSelected, dismiss ->
            FaviconProviderRoute(selected, onSelected, dismiss)
        },
    )
}

@Composable
private fun IosCommentsSettings(
    app: HarmonicAppComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    CommentsSettingsRoute(
        repository = app.settings,
        showNavigation = showNavigation,
        onBack = onBack,
        threadDepthDialog = { presenter, dismiss ->
            val selection = app.appearance.selection()
            val mode = presenter.state().depthMode
            ThreadDepthIndicatorsDialog(
                mode = mode,
                indicatorColors = CommentDepthPaletteCatalog.colors(
                    mode,
                    selection.theme,
                    selection.dark,
                ),
                onModeSelected = {
                    presenter.setDepthIndicatorMode(it)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        },
    )
}

@Composable
private fun IosAiSettings(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val settingsRuntime = remember(app, scope) {
        app.createLocalSummarySettingsRuntime(scope)
    }
    val availabilityState by settingsRuntime.state.collectAsState()

    LaunchedEffect(settingsRuntime, refresh) {
        settingsRuntime.resolve()
    }
    DisposableEffect(settingsRuntime) {
        onDispose(settingsRuntime::dispose)
    }

    AiSummarySettingsRoute(
        repository = app.aiSummarySettings,
        modelDefaults = app.aiModelDefaults,
        localSummarizationSupported = availabilityState.supported,
        localAvailabilityResolved = availabilityState.availabilityResolved,
        localConfigurationReady = availabilityState.configurationReady,
        localModeAvailable = availabilityState.available,
        showNavigation = showNavigation,
        contentVersion = refresh,
        onBack = onBack,
        onLocalModeUnavailable = {
            scene.userMessages.show(
                availabilityState.failure
                    ?: "Apple Intelligence summarization is unavailable on this device",
                UserMessageDuration.LONG,
            )
        },
        localModelsContent = {
            ManagedLocalModelPanel(
                title = "Apple Intelligence",
                status = when {
                    !availabilityState.availabilityResolved -> "Checking availability…"
                    availabilityState.available -> "Available · system managed"
                    else -> availabilityState.failure ?: "Not available"
                },
                available = availabilityState.available,
            )
        },
        dialogContent = { dialog, dismiss ->
            when (dialog) {
                AiSummarySettingsDialog.BaseUrl -> AiSummaryBaseUrlDialog(dismiss)
                AiSummarySettingsDialog.ApiKey -> AiSummaryTextDialog(
                    setting = AiSummaryTextSetting.API_KEY,
                    title = "API Key",
                    hint = "API Key",
                    defaultValue = "",
                    minLines = 1,
                    maxLines = 1,
                    textSizeSp = 16,
                    trimValue = true,
                    allowEmpty = true,
                    showReset = false,
                    onDismiss = dismiss,
                    onSaved = { refresh++ },
                )
                AiSummarySettingsDialog.Model -> AiModelSelectorRoute(onDismiss = dismiss)
                AiSummarySettingsDialog.SystemPrompt -> AiSummaryTextDialog(
                    setting = AiSummaryTextSetting.SYSTEM_PROMPT,
                    title = "System prompt",
                    hint = "System prompt",
                    defaultValue = CloudSummaryDefaults.SYSTEM_PROMPT,
                    minLines = 5,
                    maxLines = 10,
                    textSizeSp = 15,
                    trimValue = false,
                    allowEmpty = true,
                    showReset = true,
                    onDismiss = dismiss,
                    onSaved = { refresh++ },
                )
            }
        },
    )
}

@Composable
private fun IosDataSettings(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val runtime = remember(app, scope) {
        app.createDataSettingsRuntime(scope) {
            app.platform.timeFormatting.localDate(app.nowMillis())
        }
    }
    val state by runtime.state.collectAsState()

    LaunchedEffect(runtime) {
        runtime.effects.collect { effect ->
            when (effect) {
                is DataSettingsRuntimeEffect.CreateExportDocument -> {
                    app.platform.sharing.share(effect.content, effect.filename)
                    scene.userMessages.show("Choose Save to Files to export bookmarks")
                }
                DataSettingsRuntimeEffect.OpenImportDocument -> {
                    scene.userMessages.show(
                        "Bookmark import needs a Files picker and is not available in this build",
                        UserMessageDuration.LONG,
                    )
                }
                DataSettingsRuntimeEffect.OpenAppLinkSettings ->
                    scene.userMessages.show("iOS link handling is controlled by your browser and OS")
                DataSettingsRuntimeEffect.SettingsReset -> app.appearance.refreshSelection()
                is DataSettingsRuntimeEffect.Message -> scene.userMessages.show(effect.text)
            }
        }
    }

    DataSettingsRoute(
        repository = app.settings,
        counts = DataSettingsCounts(
            state.snapshot.bookmarkCount,
            state.snapshot.historyCount,
            state.snapshot.postCacheCount,
            state.snapshot.tintCacheCount,
            state.snapshot.aiModelBytes,
        ),
        loggedIn = state.snapshot.loggedIn,
        showNavigation = showNavigation,
        showAppLinkSettings = false,
        onBack = onBack,
        contentVersion = state.revision,
        onAction = { action ->
            when (action) {
                DataSettingsAction.AddBookmarksToFavorites -> runtime.addBookmarksToFavorites()
                DataSettingsAction.ExportBookmarks -> runtime.exportBookmarks()
                DataSettingsAction.ImportBookmarks -> runtime.showDialog(DataSettingsDialogState.IMPORT)
                DataSettingsAction.ClearHistory -> runtime.clearHistory()
                DataSettingsAction.ClearPostCache -> runtime.clearPostCache()
                DataSettingsAction.ClearTintCache -> runtime.clearTintCache()
                DataSettingsAction.ClearAiModels ->
                    runtime.showDialog(DataSettingsDialogState.AI_MODELS)
                DataSettingsAction.OpenLinksSettings -> runtime.showDialog(DataSettingsDialogState.LINKS)
                DataSettingsAction.ResetSettings -> runtime.showDialog(DataSettingsDialogState.RESET)
            }
        },
    )

    when (state.dialog) {
        DataSettingsDialogState.IMPORT -> ImportBookmarksDialog(
            onDismiss = { runtime.showDialog(null) },
            onImport = runtime::requestImport,
        )
        DataSettingsDialogState.RESET -> MessageActionDialog(
            title = "Reset all settings?",
            message = "Bookmarks, history, account details, user tags and cached posts are preserved.",
            positiveLabel = "Reset",
            negativeLabel = "Cancel",
            onPositive = runtime::resetSettings,
            onNegative = { runtime.showDialog(null) },
            onDismiss = { runtime.showDialog(null) },
        )
        DataSettingsDialogState.LINKS -> MessageActionDialog(
            message = "iOS link handling is configured in your operating system and browser.",
            onDismiss = { runtime.showDialog(null) },
        )
        DataSettingsDialogState.AI_MODELS -> ClearAiModelsConfirmationDialog(
            modelNames = state.snapshot.aiModelNames,
            onClear = runtime::clearAiModels,
            onDismiss = { runtime.showDialog(null) },
        )
        null -> Unit
    }
    state.favoriteIds?.let { ids ->
        AddBookmarksToFavoritesDialog(ids.toIntArray(), runtime::dismissFavorites)
    }
}

@Composable
private fun IosDebugSettings(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onOpenLinkPreviews: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    DebugSettingsRoute(
        repository = app.settings,
        environment = DebugEnvironmentUiState(
            appVersion = app.metadata.versionName,
            appBuild = app.metadata.buildNumber,
            buildVersion = app.metadata.buildType,
            platformVersion = UIDevice.currentDevice.systemName + " " +
                UIDevice.currentDevice.systemVersion,
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onOpenHnId = { scene.navigation.openStory(StoryDestination(it)) },
        onOpenWithoutCache = {
            scope.launch {
                app.storyCache.remove(IosOpenWithoutCacheStoryId)
                app.network.removeCachedStoryResponses(IosOpenWithoutCacheStoryId)
                scene.navigation.openStory(StoryDestination(IosOpenWithoutCacheStoryId))
            }
        },
        onCachePost = {
            scope.launch {
                if (DebugCachedPostFixture.seed(app.storyCache::storeStory)) {
                    scene.navigation.openStory(DebugCachedPostFixture.story().toDestination())
                }
            }
        },
        onOpenLink = { scene.links.open(it) },
        onOpenLinkPreviews = onOpenLinkPreviews,
        onEasterEggRequested = scene.navigation::openCoulombGas,
        dialogContent = { dialog, dismiss ->
            when (dialog) {
                DebugSettingsDialog.CHANGELOG -> SettingsChangelogDialog(
                    onDismiss = dismiss,
                    onOpenGithub = { scene.links.open(app.metadata.projectUrl) },
                )
                DebugSettingsDialog.WELCOME -> IosWelcomeDialog(app, dismiss)
                DebugSettingsDialog.NOTIFICATIONS -> MessageActionDialog(
                    title = "Notifications",
                    message = "Reply notifications and Android notification fixtures do not apply to iOS.",
                    onDismiss = dismiss,
                )
            }
        },
    )
}

@Composable
private fun IosLinkPreviewsDebugScreen(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    onBack: () -> Unit,
) {
    val useCase = remember(app.network.linkPreviewRepository) {
        LinkPreviewUseCase(app.network.linkPreviewRepository)
    }
    LinkPreviewsDebugScreen(
        comments = app.userSettings.comments,
        loadPreview = useCase::load,
        onOpenLink = scene.links::open,
        onBack = onBack,
    )
}

@Composable
internal fun IosWelcomeDialog(app: HarmonicAppComposition, onDismiss: () -> Unit) {
    val colors = HarmonicTheme.colors
    WelcomeSettingsDialog(
        styleChooser = false,
        initialExpressive = app.settings.snapshot().story.cardStyle,
        onApplyPreset = { expressive ->
            app.settings.applyWelcomePreset(expressive)
            app.appearance.markWelcomeShown()
            onDismiss()
        },
        onDismiss = onDismiss,
        filterButtonColors = HarmonicFilterButtonColors(
            checkedBackground = colors.storyNormal,
            checkedText = colors.background,
            checkedStroke = colors.storyNormal,
            uncheckedText = colors.storyNormal,
            uncheckedStroke = colors.outlineVariant,
        ),
        launcherIcon = {
            Image(
                painterResource(Res.drawable.quanta),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
        },
    )
}
