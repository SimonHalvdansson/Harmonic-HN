package com.simon.harmonichackernews.desktop

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
import com.simon.harmonichackernews.ui.about.SharedAboutScreen
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.licenses.SharedLicensesScreen
import com.simon.harmonichackernews.ui.settings.AddBookmarksToFavoritesDialog
import com.simon.harmonichackernews.ui.settings.AiSummaryBaseUrlDialog
import com.simon.harmonichackernews.ui.settings.AiSummarySettingsDialog
import com.simon.harmonichackernews.ui.settings.AiSummaryTextDialog
import com.simon.harmonichackernews.ui.settings.AppearanceRouteLabels
import com.simon.harmonichackernews.ui.settings.AppearanceSettingsDialog
import com.simon.harmonichackernews.ui.settings.DataSettingsAction
import com.simon.harmonichackernews.ui.settings.DebugEnvironmentUiState
import com.simon.harmonichackernews.ui.settings.DebugSettingsDialog
import com.simon.harmonichackernews.ui.settings.ItemsDialog
import com.simon.harmonichackernews.ui.settings.MessageActionDialog
import com.simon.harmonichackernews.ui.settings.SettingsChangelogDialog
import com.simon.harmonichackernews.ui.settings.SettingsPlatformEffect
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.settings.SharedAiModelSelectorRoute
import com.simon.harmonichackernews.ui.settings.SharedAiSummarySettingsRoute
import com.simon.harmonichackernews.ui.settings.SharedAppearanceSettingsRoute
import com.simon.harmonichackernews.ui.settings.SharedCommentsSettingsRoute
import com.simon.harmonichackernews.ui.settings.SharedDataSettingsRoute
import com.simon.harmonichackernews.ui.settings.SharedDebugSettingsRoute
import com.simon.harmonichackernews.ui.settings.SharedFaviconProviderRoute
import com.simon.harmonichackernews.ui.settings.SharedFiltersTagsSettingsRoute
import com.simon.harmonichackernews.ui.settings.SharedFontSelectionRoute
import com.simon.harmonichackernews.ui.settings.SharedNighttimeRangeDialog
import com.simon.harmonichackernews.ui.settings.SharedPaletteTintDialog
import com.simon.harmonichackernews.ui.settings.SharedLinkPreviewsSettingsDialog
import com.simon.harmonichackernews.ui.settings.SharedLinkPreviewsDebugScreen
import com.simon.harmonichackernews.ui.settings.SharedStoriesSettingsRoute
import com.simon.harmonichackernews.ui.settings.SharedThemeSelectionDialog
import com.simon.harmonichackernews.ui.settings.SharedThreadDepthIndicatorsDialog
import com.simon.harmonichackernews.ui.settings.SharedWelcomeSettingsDialog
import com.simon.harmonichackernews.ui.settings.SharedStringListEditorDialog
import com.simon.harmonichackernews.ui.settings.ThemePreviewCatalog
import com.simon.harmonichackernews.ui.settings.WebLinksBooleanSetting
import com.simon.harmonichackernews.ui.settings.WebLinksSettingsDialog
import com.simon.harmonichackernews.ui.settings.WebLinksSettingsPresenter
import com.simon.harmonichackernews.ui.settings.SharedWebLinksSettingsScreen
import com.simon.harmonichackernews.ui.settings.faviconProviderPainter
import com.simon.harmonichackernews.ui.theme.CommentDepthPaletteCatalog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

private const val DesktopOpenWithoutCacheStoryId = 49089500

@Composable
internal fun DesktopSettingsDetail(
    section: SettingsSection,
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    singlePane: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection, Boolean) -> Unit,
) {
    when (section) {
        SettingsSection.Appearance -> DesktopAppearanceSettings(app, singlePane, onBack) {
            onNavigate(it, singlePane)
        }
        SettingsSection.Stories -> DesktopStoriesSettings(app, singlePane, onBack)
        SettingsSection.Comments -> DesktopCommentsSettings(app, singlePane, onBack)
        SettingsSection.WebLinks -> DesktopWebLinksSettings(
            app = app,
            scene = scene,
            showNavigation = singlePane,
            onBack = onBack,
        )
        SettingsSection.FiltersTags -> SharedFiltersTagsSettingsRoute(
            settings = app.settings,
            filters = app.contentFilters,
            userTags = app.userTags,
            showNavigation = singlePane,
            onBack = onBack,
            profileDialog = { userName, dismiss, onTagChanged ->
                DesktopUserProfileDialog(app, scene, userName, dismiss, onTagChanged)
            },
        )
        SettingsSection.AiSummary -> DesktopAiSettings(app, scene, singlePane, onBack)
        SettingsSection.Data -> DesktopDataSettings(app, scene, singlePane, onBack)
        SettingsSection.Debug -> DesktopDebugSettings(
            app,
            scene,
            singlePane,
            onBack,
        ) { onNavigate(SettingsSection.DebugLinkPreviews, true) }
        SettingsSection.DebugLinkPreviews -> DesktopLinkPreviewsDebugScreen(
            app,
            scene,
            onBack,
        )
        SettingsSection.About -> SharedAboutScreen(
            versionLabel = app.metadata.versionLabel,
            appIcon = painterResource(Res.drawable.quanta),
            onBack = onBack,
            onOpenGithub = { scene.links.open(app.metadata.projectUrl) },
            onOpenChangelog = scene.navigation::showChangelogDialog,
            onOpenLicenses = { onNavigate(SettingsSection.Licenses, true) },
            onOpenPrivacy = { scene.links.open(app.metadata.privacyUrl) },
            showNavigation = singlePane,
            aboutBody = "Harmonic is an open-source Hacker News client. " +
                "This desktop host uses the same Kotlin Multiplatform application logic and " +
                "Compose screens as the Android app, with desktop-native storage, links, and " +
                "keyboard/window behavior.",
        )
        SettingsSection.Licenses -> SharedLicensesScreen(
            licenses = CommonLicenseCatalog.entries,
            onBack = onBack,
            onOpenLicense = { scene.links.open(it) },
        )
    }
}

@Composable
private fun DesktopWebLinksSettings(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<WebLinksSettingsDialog?>(null) }
    val presenter = remember(app.settings) { WebLinksSettingsPresenter(app.settings) }
    val settings by app.settings.updates.collectAsState(initial = app.settings.snapshot())
    val reading = settings.reading

    SharedWebLinksSettingsScreen(
        state = presenter.state(reading.readerFont.label, settings),
        showNavigation = showNavigation,
        onBack = onBack,
        onBooleanChanged = presenter::setBoolean,
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
        SharedStringListEditorDialog(
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
        SharedLinkPreviewsSettingsDialog(
            enabledTypes = reading.enabledLinkPreviews,
            onEnabledChanged = presenter::setLinkPreview,
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun DesktopAppearanceSettings(
    app: HarmonicAppComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection) -> Unit,
) {
    val snapshot = app.settings.snapshot()
    val schedule = app.appearance.schedule
    fun themeChanged() = app.appearance.refreshSelection()

    SharedAppearanceSettingsRoute(
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
                    SharedThemeSelectionDialog(
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
                AppearanceSettingsDialog.NighttimeRange -> SharedNighttimeRangeDialog(
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
                AppearanceSettingsDialog.Font -> SharedFontSelectionRoute(
                    readerMode = false,
                    onDismiss = dismiss,
                )
                AppearanceSettingsDialog.Style -> {
                    val colors = HarmonicTheme.colors
                    SharedWelcomeSettingsDialog(
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
                    SharedPaletteTintDialog(
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
private fun DesktopStoriesSettings(
    app: HarmonicAppComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val story = app.settings.snapshot().story
    SharedStoriesSettingsRoute(
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
            SharedFaviconProviderRoute(selected, onSelected, dismiss)
        },
    )
}

@Composable
private fun DesktopCommentsSettings(
    app: HarmonicAppComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    SharedCommentsSettingsRoute(
        repository = app.settings,
        showNavigation = showNavigation,
        onBack = onBack,
        threadDepthDialog = { presenter, dismiss ->
            val selection = app.appearance.selection()
            val mode = presenter.state().depthMode
            SharedThreadDepthIndicatorsDialog(
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
private fun DesktopAiSettings(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    SharedAiSummarySettingsRoute(
        repository = app.aiSummarySettings,
        modelDefaults = app.aiModelDefaults,
        localSummarizationSupported = false,
        localConfigurationReady = false,
        localModeAvailable = false,
        showNavigation = showNavigation,
        contentVersion = refresh,
        onBack = onBack,
        onLocalModeUnavailable = {
            scene.userMessages.show(
                "Local summarization is not bundled in the desktop app",
                UserMessageDuration.LONG,
            )
        },
        localModelsContent = {
            Text("Local model downloads are currently Android-only. Cloud summaries work on desktop.")
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
                AiSummarySettingsDialog.Model -> SharedAiModelSelectorRoute(onDismiss = dismiss)
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
private fun DesktopDataSettings(
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
                    val file = chooseTextFile(save = true, suggestedName = effect.filename)
                    if (file == null) return@collect
                    runCatching { withContext(Dispatchers.IO) { file.writeText(effect.content) } }
                        .onSuccess { scene.userMessages.show("Bookmarks exported") }
                        .onFailure { scene.userMessages.show("Write error") }
                }
                DataSettingsRuntimeEffect.OpenImportDocument -> {
                    val file = chooseTextFile(save = false)
                    if (file == null) return@collect
                    runCatching { withContext(Dispatchers.IO) { file.readText() } }
                        .onSuccess(runtime::importBookmarks)
                        .onFailure { scene.userMessages.show("Read error") }
                }
                DataSettingsRuntimeEffect.OpenAppLinkSettings ->
                    scene.userMessages.show("Desktop link handling is controlled by your browser and OS")
                DataSettingsRuntimeEffect.SettingsReset -> app.appearance.refreshSelection()
                is DataSettingsRuntimeEffect.Message -> scene.userMessages.show(effect.text)
            }
        }
    }

    SharedDataSettingsRoute(
        repository = app.settings,
        counts = DataSettingsCounts(
            state.snapshot.bookmarkCount,
            state.snapshot.historyCount,
            state.snapshot.postCacheCount,
            state.snapshot.tintCacheCount,
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
                DataSettingsAction.OpenLinksSettings -> runtime.showDialog(DataSettingsDialogState.LINKS)
                DataSettingsAction.ResetSettings -> runtime.showDialog(DataSettingsDialogState.RESET)
            }
        },
    )

    when (state.dialog) {
        DataSettingsDialogState.IMPORT -> ItemsDialog(
            title = "Import bookmarks",
            options = listOf("Overwrite current bookmarks", "Add to current bookmarks"),
            onDismiss = { runtime.showDialog(null) },
            onSelected = { runtime.requestImport(overwrite = it == 0) },
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
            message = "Desktop link handling is configured in your operating system and browser.",
            onDismiss = { runtime.showDialog(null) },
        )
        null -> Unit
    }
    state.favoriteIds?.let { ids ->
        AddBookmarksToFavoritesDialog(ids.toIntArray(), runtime::dismissFavorites)
    }
}

@Composable
private fun DesktopDebugSettings(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onOpenLinkPreviews: () -> Unit,
) {
    SharedDebugSettingsRoute(
        repository = app.settings,
        environment = DebugEnvironmentUiState(
            appVersion = app.metadata.versionName,
            appBuild = app.metadata.buildNumber,
            buildVersion = app.metadata.buildType,
            platformVersion = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onOpenHnId = { scene.navigation.openStory(StoryDestination(it)) },
        onOpenWithoutCache = {
            app.storyCache.remove(DesktopOpenWithoutCacheStoryId)
            app.network.removeCachedStoryResponses(DesktopOpenWithoutCacheStoryId)
            scene.navigation.openStory(StoryDestination(DesktopOpenWithoutCacheStoryId))
        },
        onCachePost = {
            if (DebugCachedPostFixture.seed(app.storyCache.repository, app.nowMillis())) {
                scene.navigation.openStory(DebugCachedPostFixture.story().toDestination())
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
                DebugSettingsDialog.WELCOME -> DesktopWelcomeDialog(app, dismiss)
                DebugSettingsDialog.NOTIFICATIONS -> MessageActionDialog(
                    title = "Notifications",
                    message = "Reply notifications and Android notification fixtures do not apply to desktop.",
                    onDismiss = dismiss,
                )
            }
        },
    )
}

@Composable
private fun DesktopLinkPreviewsDebugScreen(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    onBack: () -> Unit,
) {
    val useCase = remember(app.network.linkPreviewRepository) {
        LinkPreviewUseCase(app.network.linkPreviewRepository)
    }
    SharedLinkPreviewsDebugScreen(
        comments = app.userSettings.comments,
        loadPreview = useCase::load,
        onOpenLink = scene.links::open,
        onBack = onBack,
    )
}

@Composable
internal fun DesktopWelcomeDialog(app: HarmonicAppComposition, onDismiss: () -> Unit) {
    val colors = HarmonicTheme.colors
    SharedWelcomeSettingsDialog(
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

private fun chooseTextFile(save: Boolean, suggestedName: String? = null): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = if (save) "Export Harmonic bookmarks" else "Import Harmonic bookmarks"
        fileFilter = FileNameExtensionFilter("Text files", "txt")
        suggestedName?.let { selectedFile = File(it) }
    }
    val result = if (save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
    return chooser.selectedFile.takeIf { result == JFileChooser.APPROVE_OPTION }
}
