package com.simon.harmonichackernews.desktop

import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.app.StoriesFeatureHost
import com.simon.harmonichackernews.app.createStoriesStore
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.MainNavigationSnapshot
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.PresentationCopy
import com.simon.harmonichackernews.network.CommentThreadSource
import com.simon.harmonichackernews.presentation.CommentsPreloadCoordinator
import com.simon.harmonichackernews.presentation.StoriesPlatformEffect
import com.simon.harmonichackernews.presentation.StoriesRuntimeEffect
import com.simon.harmonichackernews.presentation.WebContentPolicy
import com.simon.harmonichackernews.presentation.WebPreloadEnvironment
import com.simon.harmonichackernews.settings.AppLaunchDialog
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.ProvideHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.harmonicFilterButtonColors
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.comments.EmptyCommentsScreen
import com.simon.harmonichackernews.ui.debug.CoulombGasScreen
import com.simon.harmonichackernews.ui.navigation.HarmonicAppRoot
import com.simon.harmonichackernews.ui.navigation.MainNavigationScene
import com.simon.harmonichackernews.ui.navigation.SinglePaneNavigationScene
import com.simon.harmonichackernews.ui.settings.SettingsListScreen
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.settings.SettingsNavigationShell
import com.simon.harmonichackernews.ui.settings.rememberSettingsNavigationStore
import com.simon.harmonichackernews.ui.stories.StoriesRoute
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.stories.StoriesFeatureListener
import com.simon.harmonichackernews.ui.stories.StoriesPlatformPresentation
import com.simon.harmonichackernews.ui.stories.StoriesScreenStateFactory
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.awt.Taskbar
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlinx.coroutines.delay

internal val desktopAppIconImage: BufferedImage? by lazy(::loadDesktopAppIcon)

fun main() {
    System.setProperty("apple.awt.application.name", "Harmonic")
    val desktopAppIcon = desktopAppIconImage
    installTaskbarIcon(desktopAppIcon)
    val bootstrap = try {
        val metadata = DesktopRuntimeMetadata.load()
        DesktopHarmonicAppBootstrap.production(
            userAgent = "Harmonic-HN-Desktop/${metadata.versionName}",
            metadata = metadata,
        )
    } catch (error: Exception) {
        showDesktopStartupFailure(desktopAppIcon, error)
        return
    }
    try {
        application {
            val windowIcon = remember(desktopAppIcon) { desktopAppIcon?.toPainter() }
            val navigation by bootstrap.scene.navigation.state.collectAsState()
            var storiesController by remember { mutableStateOf<StoriesComposeController?>(null) }
            var commentsController by remember { mutableStateOf<CommentsComposeController?>(null) }
            var editorBackRequestVersion by remember { mutableIntStateOf(0) }
            Window(
                onCloseRequest = ::exitApplication,
                title = "Harmonic",
                icon = windowIcon,
                state = WindowState(width = 1180.dp, height = 840.dp),
                onKeyEvent = { event ->
                    when {
                        event.type != KeyEventType.KeyDown -> false
                        event.key == Key.Escape -> handleDesktopBack(
                            navigation,
                            bootstrap.scene,
                            storiesController,
                            commentsController,
                            onEditorBackRequested = { editorBackRequestVersion++ },
                        )
                        event.key == Key.Comma && (event.isMetaPressed || event.isCtrlPressed) -> {
                            bootstrap.scene.navigation.openSettings(null)
                            true
                        }
                        event.key == Key.W && (event.isMetaPressed || event.isCtrlPressed) -> {
                            exitApplication()
                            true
                        }
                        else -> false
                    }
                },
            ) {
                val selection by bootstrap.app.appearance.selections.collectAsState(
                    initial = bootstrap.app.appearance.selection(),
                )
                val palette = remember(selection) {
                    HarmonicThemeCatalog.resolve(
                        selection.theme,
                        selection.dark,
                        selection.accentPreset,
                    )
                }
                LaunchedEffect(window, selection.dark, palette.colors.background) {
                    DesktopWindowAppearance.apply(
                        window = window,
                        dark = selection.dark,
                        backgroundArgb = palette.colors.background.toArgb(),
                    )
                }
                LaunchedEffect(bootstrap.app.launchState) {
                    when (
                        bootstrap.app.launchState.consumeLaunchDialog(
                            currentVersion = bootstrap.app.metadata.versionCode,
                            showChangelog = bootstrap.app.userSettings.general.showChangelog,
                        )
                    ) {
                        AppLaunchDialog.WELCOME -> bootstrap.scene.navigation.showWelcomeDialog()
                        AppLaunchDialog.CHANGELOG -> bootstrap.scene.navigation.showChangelogDialog()
                        AppLaunchDialog.NONE -> Unit
                    }
                }

                ProvideHarmonicUiDependencies(
                    HarmonicUiDependencies(bootstrap.app, bootstrap.scene),
                ) {
                    HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                        Surface(Modifier.fillMaxSize()) {
                            DesktopAppContent(
                                app = bootstrap.app,
                                scene = bootstrap.scene,
                                storiesController = storiesController,
                                commentsController = commentsController,
                                editorBackRequestVersion = editorBackRequestVersion,
                                onStoriesControllerChanged = { storiesController = it },
                                onCommentsControllerChanged = { commentsController = it },
                            )
                        }
                    }
                }
            }
        }
    } finally {
        bootstrap.close()
    }
}

private fun showDesktopStartupFailure(icon: BufferedImage?, error: Exception) {
    System.err.println("Harmonic could not initialize its desktop storage.")
    error.printStackTrace(System.err)
    application {
        val windowIcon = remember(icon) { icon?.toPainter() }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Harmonic – Startup error",
            icon = windowIcon,
            state = WindowState(width = 620.dp, height = 260.dp),
        ) {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(24.dp)) {
                        Text(
                            "Harmonic could not open its settings or data folder. " +
                                "Check that the folder is accessible, then restart the app.\n\n" +
                                (error.message ?: error::class.simpleName.orEmpty()),
                        )
                    }
                }
            }
        }
    }
}

private fun loadDesktopAppIcon(): BufferedImage? = runCatching {
    val osName = System.getProperty("os.name")
    val resourceName = when {
        osName.startsWith("Mac", ignoreCase = true) -> "harmonic-app-icon-macos.png"
        osName.startsWith("Windows", ignoreCase = true) -> "harmonic-app-icon-windows.png"
        else -> "harmonic-app-icon.png"
    }
    Thread.currentThread().contextClassLoader
        .getResourceAsStream(resourceName)
        ?.use(ImageIO::read)
}.getOrNull()

private fun installTaskbarIcon(icon: BufferedImage?) {
    if (icon == null || !Taskbar.isTaskbarSupported()) return
    runCatching {
        Taskbar.getTaskbar().takeIf { it.isSupported(Taskbar.Feature.ICON_IMAGE) }?.iconImage = icon
    }
}

private fun handleDesktopBack(
    navigation: MainNavigationSnapshot,
    scene: HarmonicSceneComposition,
    storiesController: StoriesComposeController?,
    commentsController: CommentsComposeController?,
    onEditorBackRequested: () -> Unit,
): Boolean {
    when {
        navigation.failureRequest != null -> scene.navigation.dismissFailureDetailDialog()
        navigation.userRequest != null -> scene.navigation.dismissUserDialog()
        navigation.captchaRequest != null -> scene.navigation.dismissCaptchaDialog()
        navigation.loginDialogVisible -> scene.navigation.dismissLoginDialog()
        navigation.cacheStoriesDialogVisible -> scene.navigation.dismissCacheStoriesDialog()
        navigation.changelogDialogVisible -> scene.navigation.dismissChangelogDialog()
        navigation.welcomeDialogVisible -> return true
        navigation.coulombGasVisible -> scene.navigation.closeCoulombGas()
        navigation.editorRequest != null -> onEditorBackRequested()
        navigation.submissionsRequest != null -> scene.navigation.closeSubmissions()
        navigation.settingsRequest != null -> scene.navigation.closeSettings()
        commentsController?.isHeaderMenuShowing() == true ->
            commentsController.requestDismissHeaderMenu()
        commentsController?.isLinkPreviewOverlayShowing() == true ->
            commentsController.requestDismissLinkPreview()
        commentsController?.isCommentActionOverlayShowing() == true ->
            commentsController.requestDismissCommentActions()
        commentsController?.searchDialogVisible == true -> commentsController.dismissCommentSearch()
        storiesController?.isHeaderMenuShowing() == true ->
            storiesController.requestDismissHeaderMenu()
        commentsController?.isWebsiteVisible() == true -> commentsController.requestExpandSheet()
        storiesController?.isStoryPreviewShowing() == true ->
            storiesController.requestDismissStoryPreview()
        navigation.storyRequest != null -> scene.navigation.detailRemovedFromBackStack()
        storiesController?.searching == true -> storiesController.finishSearchBack()
        else -> return false
    }
    return true
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun DesktopAppContent(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    storiesController: StoriesComposeController?,
    commentsController: CommentsComposeController?,
    editorBackRequestVersion: Int,
    onStoriesControllerChanged: (StoriesComposeController?) -> Unit,
    onCommentsControllerChanged: (CommentsComposeController?) -> Unit,
) {
    val navigation by scene.navigation.state.collectAsState()
    val storyDestinationForeground = navigation.currentDestination == MainDestination.STORY
    var destinationTransitionSettled by remember {
        mutableStateOf(storyDestinationForeground)
    }
    LaunchedEffect(storyDestinationForeground) {
        if (!storyDestinationForeground) {
            destinationTransitionSettled = false
        } else if (!destinationTransitionSettled) {
            delay(DesktopDestinationTransitionDurationMillis)
            destinationTransitionSettled = true
        }
    }
    val composeOverlayVisible = navigation.welcomeDialogVisible ||
        navigation.changelogDialogVisible ||
        navigation.cacheStoriesDialogVisible ||
        navigation.loginDialogVisible ||
        navigation.captchaRequest != null ||
        navigation.userRequest != null ||
        navigation.failureRequest != null ||
        storiesController?.isHeaderMenuShowing() == true ||
        storiesController?.frontDatePickerRequest != null ||
        storiesController?.isStoryPreviewShowing() == true ||
        commentsController?.isHeaderMenuShowing() == true ||
        commentsController?.isLinkPreviewOverlayShowing() == true ||
        commentsController?.isCommentActionOverlayShowing() == true ||
        commentsController?.searchDialogVisible == true
    var overlayTransitionSettled by remember { mutableStateOf(!composeOverlayVisible) }
    LaunchedEffect(composeOverlayVisible) {
        if (composeOverlayVisible) {
            overlayTransitionSettled = false
        } else if (!overlayTransitionSettled) {
            delay(DesktopOverlayExitDurationMillis)
            overlayTransitionSettled = true
        }
    }
    val webViewForegroundAllowed = storyDestinationForeground &&
        destinationTransitionSettled &&
        !composeOverlayVisible &&
        overlayTransitionSettled
    val transitionOffsetPx = with(LocalDensity.current) { 96.dp.roundToPx() }
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val mainDirective = remember(adaptiveInfo) {
        calculatePaneScaffoldDirective(adaptiveInfo).copy(
            horizontalPartitionSpacerSize = 16.dp,
        )
    }
    val isTwoPane = mainDirective.maxHorizontalPartitions > 1
    HarmonicAppRoot(
        navigation = navigation,
        transitionOffsetPx = transitionOffsetPx,
        completedSettingsPredictiveBack = false,
        completedSubmissionsPredictiveBack = false,
        completedEditorPredictiveBack = false,
        linkPreview = commentsController
            ?.takeIf {
                it.isLinkPreviewOverlayShowing() && !it.searchDialogVisible
            }
            ?.let { controller ->
                { DesktopCommentLinkPreview(app, scene, controller) }
            },
        base = {
            if (isTwoPane) {
                MainNavigationScene(
                    storyRequest = navigation.storyRequest,
                    directive = mainDirective,
                    paneProportion = 0.4f,
                    onBack = scene.navigation::detailRemovedFromBackStack,
                    stories = {
                        DesktopStoriesContent(
                            app,
                            scene,
                            isSplitLayout = true,
                            onControllerChanged = onStoriesControllerChanged,
                        )
                    },
                    emptyDetail = { EmptyCommentsScreen() },
                    animateDetailVisibilityChanges = true,
                    comments = { request ->
                        DesktopCommentsContent(
                            app = app,
                            scene = scene,
                            request = request,
                            showNavigation = false,
                            webViewForegroundAllowed = webViewForegroundAllowed,
                            onClose = scene.navigation::detailRemovedFromBackStack,
                            onControllerChanged = onCommentsControllerChanged,
                        )
                    },
                )
            } else {
                SinglePaneNavigationScene(
                    storyRequests = navigation.storyBackStack,
                    completedPredictivePop = false,
                    predictiveBackActive = false,
                    showStoriesRoot = navigation.storyBackStack.isEmpty() ||
                        navigation.storyStackParentDestination == MainDestination.STORIES,
                    stories = {
                        DesktopStoriesContent(
                            app,
                            scene,
                            isSplitLayout = false,
                            onControllerChanged = onStoriesControllerChanged,
                        )
                    },
                    comments = { request ->
                        DesktopCommentsContent(
                            app = app,
                            scene = scene,
                            request = request,
                            showNavigation = true,
                            webViewForegroundAllowed = webViewForegroundAllowed,
                            onClose = scene.navigation::detailRemovedFromBackStack,
                            onControllerChanged = onCommentsControllerChanged,
                        )
                    },
                )
            }
        },
        settings = {
            DesktopSettingsShell(
                app = app,
                scene = scene,
                initialSection = SettingsSection.fromRoute(
                    navigation.currentSettingsSectionRoute.orEmpty(),
                ),
            )
        },
        submissions = {
            navigation.lastSubmissionsRequest?.let {
                DesktopSubmissionsContent(app, scene, it)
            }
        },
        editor = {
            navigation.lastEditorRequest?.let {
                DesktopEditorContent(
                    app = app,
                    scene = scene,
                    request = it,
                    backRequestVersion = editorBackRequestVersion,
                )
            }
        },
        immersive = { CoulombGasScreen() },
        foreground = {
            DesktopAppForeground(app, scene, navigation, storiesController)
        },
    )
}

private const val DesktopDestinationTransitionDurationMillis = 450L
private const val DesktopOverlayExitDurationMillis = 160L

@Composable
private fun DesktopStoriesContent(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    isSplitLayout: Boolean,
    onControllerChanged: (StoriesComposeController?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val appSettings by app.settings.updates.collectAsState(app.settings.snapshot())
    val latestAppSettings by rememberUpdatedState(appSettings)
    val preloadCoordinator = remember(app, scope) {
        CommentsPreloadCoordinator(
            preloads = app.commentsPreloads,
            loadFilteredUsers = { app.contentFilters.load().users },
            preloadAllowed = {
                val comments = latestAppSettings.comments
                WebContentPolicy.shouldPreload(
                    comments.preloadCommentsMode,
                    comments.preloadCommentsMinimumBattery,
                    WebPreloadEnvironment(
                        unmeteredConnection = app.platform.connectivity.isUnmetered(),
                        batteryPercent = app.platform.battery.batteryPercent(),
                    ),
                )
            },
            preloadSource = {
                if (latestAppSettings.reading.useAlgoliaApi) {
                    CommentThreadSource.ALGOLIA
                } else {
                    CommentThreadSource.OFFICIAL
                }
            },
            scope = scope,
        )
    }
    val currentSplitLayout by rememberUpdatedState(isSplitLayout)
    val density = LocalDensity.current
    val defaultStoryHeightPx = with(density) { 96.dp.roundToPx() }
    val contentInsetStartPx = with(density) {
        if (isSplitLayout) DesktopWidePaneHorizontalPadding.roundToPx() else 0
    }
    val store = remember(app, scene, scope) {
        app.createStoriesStore(
            StoriesFeatureHost(
                scope = scope,
                sessionState = scene.sessions.stories,
                platform = app.storiesPlatformDependencies(),
                userSettings = app.userSettings,
            ),
        )
    }
    val controller = remember(store, defaultStoryHeightPx) {
        lateinit var created: StoriesComposeController
        val callbacks = object : StoriesFeatureListener.PlatformCallbacks {
            override fun onSearchStateChanged(searching: Boolean) {
                created.endPredictiveBack()
            }

            override fun showFrontDatePicker() {
                val state = store.state.value
                created.showFrontDatePicker(
                    state.frontDateSelectedMillis,
                    state.frontDateEarliestMillis,
                    state.frontDateLatestMillis,
                )
            }

            override fun onStoryPreviewVisibilityChanged(showing: Boolean) = Unit
            override fun isSplitLayout(): Boolean = currentSplitLayout
        }
        created = StoriesComposeController.create(
            defaultStoryHeightPx = defaultStoryHeightPx,
            savedItemState = store.savedItemState,
            listener = StoriesFeatureListener(store, callbacks),
        )
        created
    }
    val state by store.state.collectAsState()
    val filterColors = harmonicFilterButtonColors()

    SideEffect { onControllerChanged(controller) }
    DisposableEffect(store) {
        store.start()
        store.onStart()
        onDispose {
            onControllerChanged(null)
            store.onStop()
            store.close()
            preloadCoordinator.dispose()
        }
    }
    LaunchedEffect(
        preloadCoordinator,
        appSettings.comments.preloadCommentsMode,
        appSettings.comments.preloadCommentsMinimumBattery,
        appSettings.reading.useAlgoliaApi,
    ) {
        preloadCoordinator.setEnabled(
            appSettings.comments.preloadCommentsFromStories,
        )
    }
    LaunchedEffect(state, controller, contentInsetStartPx) {
        val lastUpdatedText = state.lastUpdatedMillis?.let { millis ->
            PresentationCopy.lastUpdated(app.platform.timeFormatting.time(millis))
        }
        controller.updateContent(
            StoriesScreenStateFactory.create(
                state,
                StoriesPlatformPresentation(
                    lastUpdatedText,
                    contentInsetStartPx = contentInsetStartPx,
                ),
            ),
        )
    }
    LaunchedEffect(store, controller, scene) {
        store.effects.collect { effect ->
            when (effect) {
                is StoriesRuntimeEffect.OpenStory -> scene.navigation.openStory(effect.destination)
                is StoriesRuntimeEffect.OpenExternalLink ->
                    scene.links.openExternal(ExternalLinkRequest(effect.url))
                is StoriesRuntimeEffect.Platform -> when (val platform = effect.effect) {
                    StoriesPlatformEffect.OpenSettings -> scene.navigation.openSettings(null)
                    StoriesPlatformEffect.RequestLogin -> scene.navigation.showLoginDialog()
                    is StoriesPlatformEffect.OpenProfile ->
                        scene.navigation.showUserDialog(platform.userName)
                    StoriesPlatformEffect.ShowCacheDialog ->
                        scene.navigation.showCacheStoriesDialog()
                    StoriesPlatformEffect.OpenSubmitEditor ->
                        scene.navigation.openEditor(EditorDestination(EditorType.POST))
                }
                is StoriesRuntimeEffect.StoryChanged ->
                    effect.storyId?.let(controller::invalidateStory)
                StoriesRuntimeEffect.LoginRequired -> scene.navigation.showLoginDialog()
                is StoriesRuntimeEffect.UserMessage -> scene.userMessages.show(effect.message)
                is StoriesRuntimeEffect.SavedActionFailed -> {
                    if (effect.presentation.showDetails) {
                        scene.navigation.showFailureDetailDialog(
                            effect.presentation.failureSummary,
                            effect.presentation.failureDetail,
                            null,
                        )
                    } else {
                        scene.userMessages.show(effect.presentation.message)
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        StoriesRoute(
            controller = controller,
            tintStore = app.storyResourceTints,
            commentText = { AnnotatedString(it) },
            filterColors = filterColors,
            extraCompactSelectedText = false,
            compactSelectedText = false,
            pullToRefreshEnabled = false,
            showRefreshMenuItem = true,
            onVisibleStoriesChanged = preloadCoordinator::updateVisibleStories,
        )
        if (controller.isStoryPreviewShowing()) {
            DesktopStoryPreviewOverlay(app, controller)
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun DesktopSettingsShell(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    initialSection: SettingsSection?,
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(adaptiveInfo) { calculatePaneScaffoldDirective(adaptiveInfo) }
    val isTwoPane = directive.maxHorizontalPartitions > 1
    val navigation = rememberSettingsNavigationStore(
        initialSection = initialSection,
        twoPane = isTwoPane,
    )

    LaunchedEffect(initialSection) { initialSection?.let(navigation::navigateTo) }

    // Compose Desktop can leave LookaheadDelegate alignment-line ownership stale when a
    // Navigation 3 list/detail scene changes its pane count during a live resize. Recreate only
    // the scene machinery at the breakpoint; the navigation store stays outside this key so the
    // selected section and detail stack survive both directions of the layout change.
    key(isTwoPane) {
        SettingsNavigationShell(
            navigation = navigation,
            directive = directive,
            isFoldable = false,
            tabletPaneHorizontalPadding = if (isTwoPane) {
                DesktopWidePaneHorizontalPadding
            } else {
                0.dp
            },
            onBackFromSettings = scene.navigation::closeSettings,
            onSectionChanged = { scene.navigation.updateSettingsSection(it.route) },
            renderList = { selectedSection, showSelection, onBack, onSectionSelected ->
                SettingsListScreen(
                    selectedSection = selectedSection,
                    showSelection = showSelection,
                    showDebugSettings = app.metadata.debugSettingsEnabled,
                    onBack = onBack,
                    onSectionSelected = onSectionSelected,
                )
            },
            renderDetail = { section, singlePane, onBack, onNavigate ->
                DesktopSettingsDetail(
                    section,
                    app,
                    scene,
                    singlePane,
                    onBack,
                    onNavigate,
                )
            },
        )
    }
}

internal val DesktopWidePaneHorizontalPadding = 24.dp
