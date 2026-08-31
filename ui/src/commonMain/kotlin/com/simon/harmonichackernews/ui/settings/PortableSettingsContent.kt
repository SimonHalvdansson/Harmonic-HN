package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.app.CommonLicenseCatalog
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.debug.DebugCachedPostFixture
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.network.LinkPreviewUseCase
import com.simon.harmonichackernews.settings.ArchiveRedirectDomainCatalog
import com.simon.harmonichackernews.settings.NighttimeSchedule
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.ThemeSelectionPolicy
import com.simon.harmonichackernews.ui.about.AboutScreen
import com.simon.harmonichackernews.ui.common.harmonicFilterButtonColors
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.licenses.LicensesScreen
import com.simon.harmonichackernews.ui.theme.CommentDepthPaletteCatalog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
import kotlinx.coroutines.launch

private const val OpenWithoutCacheStoryId = 49089500

/** Shared settings composition for hosts whose only differences are native facilities. */
@Composable
fun PortableSettingsDetail(
    section: SettingsSection,
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    singlePane: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection, Boolean) -> Unit,
    appIcon: Painter,
    aboutBody: String,
    debugPlatformVersion: String,
    debugNotificationsMessage: String,
    aiSettings: @Composable () -> Unit,
    dataSettings: @Composable () -> Unit,
) {
    when (section) {
        SettingsSection.Appearance -> PortableAppearanceSettings(
            app,
            appIcon,
            singlePane,
            onBack,
        ) { onNavigate(it, singlePane) }
        SettingsSection.Stories -> PortableStoriesSettings(app, singlePane, onBack)
        SettingsSection.Comments -> PortableCommentsSettings(app, singlePane, onBack)
        SettingsSection.WebLinks -> PortableWebLinksSettings(
            app = app,
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
                PortableUserProfileDialog(app, scene, userName, dismiss, onTagChanged)
            },
        )
        SettingsSection.AiSummary -> aiSettings()
        SettingsSection.Data -> dataSettings()
        SettingsSection.Debug -> PortableDebugSettings(
            app = app,
            scene = scene,
            appIcon = appIcon,
            platformVersion = debugPlatformVersion,
            notificationsMessage = debugNotificationsMessage,
            showNavigation = singlePane,
            onBack = onBack,
            onOpenLinkPreviews = { onNavigate(SettingsSection.DebugLinkPreviews, true) },
        )
        SettingsSection.DebugLinkPreviews -> PortableLinkPreviewsDebugScreen(app, scene, onBack)
        SettingsSection.About -> AboutScreen(
            versionLabel = app.metadata.versionLabel,
            appIcon = appIcon,
            onBack = onBack,
            onOpenGithub = { scene.links.open(app.metadata.projectUrl) },
            onOpenChangelog = scene.navigation::showChangelogDialog,
            onOpenLicenses = { onNavigate(SettingsSection.Licenses, true) },
            onOpenPrivacy = { scene.links.open(app.metadata.privacyUrl) },
            showNavigation = singlePane,
            aboutBody = aboutBody,
        )
        SettingsSection.Licenses -> LicensesScreen(
            licenses = CommonLicenseCatalog.entries,
            onBack = onBack,
            onOpenLicense = { scene.links.open(it) },
        )
    }
}

@Composable
private fun PortableWebLinksSettings(
    app: HarmonicAppComposition,
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
private fun PortableAppearanceSettings(
    app: HarmonicAppComposition,
    appIcon: Painter,
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
                    WelcomeSettingsDialog(
                        styleChooser = true,
                        initialExpressive = snapshot.story.cardStyle,
                        onApplyPreset = { expressive ->
                            presenter.applyWelcomePreset(expressive)
                            themeChanged()
                            dismiss()
                        },
                        onDismiss = dismiss,
                        filterButtonColors = harmonicFilterButtonColors(),
                        launcherIcon = {
                            Image(
                                painter = appIcon,
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
private fun PortableStoriesSettings(
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
private fun PortableCommentsSettings(
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
private fun PortableDebugSettings(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    appIcon: Painter,
    platformVersion: String,
    notificationsMessage: String,
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
            platformVersion = platformVersion,
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onOpenHnId = { scene.navigation.openStory(StoryDestination(it)) },
        onOpenWithoutCache = {
            scope.launch {
                app.storyCache.remove(OpenWithoutCacheStoryId)
                app.network.removeCachedStoryResponses(OpenWithoutCacheStoryId)
                scene.navigation.openStory(StoryDestination(OpenWithoutCacheStoryId))
            }
        },
        onCachePost = {
            scope.launch {
                if (DebugCachedPostFixture.seed(app.storyCache::storeStory)) {
                    scene.navigation.openStory(DebugCachedPostFixture.story().toDestination())
                }
            }
        },
        onOpenLink = scene.links::open,
        onOpenLinkPreviews = onOpenLinkPreviews,
        onEasterEggRequested = scene.navigation::openCoulombGas,
        dialogContent = { dialog, dismiss ->
            when (dialog) {
                DebugSettingsDialog.CHANGELOG -> SettingsChangelogDialog(
                    onDismiss = dismiss,
                    onOpenGithub = { scene.links.open(app.metadata.projectUrl) },
                )
                DebugSettingsDialog.WELCOME -> PortableWelcomeDialog(app, appIcon, dismiss)
                DebugSettingsDialog.NOTIFICATIONS -> MessageActionDialog(
                    title = "Notifications",
                    message = notificationsMessage,
                    onDismiss = dismiss,
                )
            }
        },
    )
}

@Composable
private fun PortableLinkPreviewsDebugScreen(
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
fun PortableWelcomeDialog(
    app: HarmonicAppComposition,
    appIcon: Painter,
    onDismiss: () -> Unit,
) {
    WelcomeSettingsDialog(
        styleChooser = false,
        initialExpressive = app.settings.snapshot().story.cardStyle,
        onApplyPreset = { expressive ->
            app.settings.applyWelcomePreset(expressive)
            app.appearance.markWelcomeShown()
            onDismiss()
        },
        onDismiss = onDismiss,
        filterButtonColors = harmonicFilterButtonColors(),
        launcherIcon = {
            Image(
                painter = appIcon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
        },
    )
}
