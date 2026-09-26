package com.simon.harmonichackernews.ios

import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import com.simon.harmonichackernews.app.IosHostRuntimeBindings
import com.simon.harmonichackernews.platform.IosAppearanceController
import com.simon.harmonichackernews.platform.IosPlatformBindings
import com.simon.harmonichackernews.platform.accountOrNull
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import kotlin.time.Clock
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.simon.harmonichackernews.app.IosHarmonicAppBootstrap
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.app.StoriesFeatureHost
import com.simon.harmonichackernews.app.createStoriesFeatureStore
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.navigation.MainNavigationSnapshot
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.PresentationCopy
import com.simon.harmonichackernews.network.CommentThreadSource
import com.simon.harmonichackernews.presentation.StoriesPlatformEffect
import com.simon.harmonichackernews.presentation.StoriesFeatureEffect
import com.simon.harmonichackernews.presentation.CommentsPreloadCoordinator
import com.simon.harmonichackernews.presentation.WebContentPolicy
import com.simon.harmonichackernews.presentation.WebPreloadEnvironment
import com.simon.harmonichackernews.settings.AppLaunchDialog
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.ProvideHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.harmonicFilterButtonColors
import com.simon.harmonichackernews.ui.comments.CommentsScreenController
import com.simon.harmonichackernews.ui.comments.EmptyCommentsScreen
import com.simon.harmonichackernews.ui.debug.CoulombGasScreen
import com.simon.harmonichackernews.ui.navigation.HarmonicAppRoot
import com.simon.harmonichackernews.ui.navigation.MainNavigationScene
import com.simon.harmonichackernews.ui.navigation.mainNavigationScenePlan
import com.simon.harmonichackernews.ui.navigation.SinglePaneNavigationScene
import com.simon.harmonichackernews.ui.settings.SettingsListScreen
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.settings.SettingsNavigationShell
import com.simon.harmonichackernews.ui.settings.SettingsNavigationStore
import com.simon.harmonichackernews.ui.settings.handleSettingsBack
import com.simon.harmonichackernews.ui.settings.rememberSettingsNavigationStore
import com.simon.harmonichackernews.ui.stories.StoriesRoute
import com.simon.harmonichackernews.ui.stories.StoriesScreenController
import com.simon.harmonichackernews.ui.stories.StoriesFeatureListener
import com.simon.harmonichackernews.ui.stories.StoriesPlatformPresentation
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import platform.UIKit.UIViewController

internal val LocalIosForeground = staticCompositionLocalOf { false }

private data object IosApplicationBackInfo : NavigationEventInfo()

private enum class IosBackVisualTarget { None, Story, Settings, Submissions, Editor }

/**
 * Swift-facing owner for one Compose iOS scene. The native host retains this object and installs
 * the returned controller; Compose dispatches iOS edge gestures through Navigation Event.
 */
class IosHarmonicApplication(
    bindings: IosPlatformBindings,
    runtime: IosHostRuntimeBindings,
) {
    private val appearance = bindings.appearance
    private val bootstrap = IosHarmonicAppBootstrap(
        userAgent = "Harmonic-HN-iOS/${runtime.metadata.versionName}",
        bindings = bindings,
        runtime = runtime,
    )
    private val scene = bootstrap.createScene()
    private var closed = false
    private var foreground by mutableStateOf(false)

    fun setForeground(active: Boolean) {
        if (closed) return
        foreground = active
        if (active) refreshAppearance()
    }

    fun refreshAppearance() {
        if (!closed) bootstrap.app.appearance.refreshSelection()
    }

    fun makeViewController(): UIViewController = ComposeUIViewController {
        CompositionLocalProvider(LocalIosForeground provides foreground) {
            IosApp(
                bootstrap = bootstrap,
                scene = scene,
                appearance = appearance,
            )
        }
    }

    fun close() {
        if (closed) return
        closed = true
        scene.close()
        bootstrap.close()
    }
}

@Composable
private fun IosApp(
    bootstrap: IosHarmonicAppBootstrap,
    scene: HarmonicSceneComposition,
    appearance: IosAppearanceController,
) {
    val foreground = LocalIosForeground.current
    LaunchedEffect(foreground, bootstrap.app) {
        if (foreground) {
            while (true) {
                bootstrap.app.appearance.refreshSelection()
                delay(60_000L - Clock.System.now().toEpochMilliseconds().mod(60_000))
            }
        }
    }
    val navigation by scene.navigation.state.collectAsState()
    var storiesController by remember { mutableStateOf<StoriesScreenController?>(null) }
    var commentsController by remember { mutableStateOf<CommentsScreenController?>(null) }
    var settingsNavigation by remember { mutableStateOf<SettingsNavigationStore?>(null) }
    var editorBackRequestVersion by remember { mutableIntStateOf(0) }
    var completedBackTarget by remember { mutableStateOf(IosBackVisualTarget.None) }

    val selection by bootstrap.app.appearance.selections.collectAsState(
        initial = bootstrap.app.appearance.selection(),
    )
    val palette = remember(selection) {
        HarmonicThemeCatalog.resolve(selection.theme, selection.dark, selection.accentPreset)
    }
    SideEffect { appearance.setDarkAppearance(selection.dark) }
    LaunchedEffect(bootstrap.app.launchState) {
        when (
            bootstrap.app.launchState.consumeLaunchDialog(
                currentVersion = bootstrap.app.metadata.versionCode,
                showChangelog = bootstrap.app.userSettings.general.showChangelog,
            )
        ) {
            AppLaunchDialog.WELCOME -> scene.navigation.showWelcomeDialog()
            AppLaunchDialog.CHANGELOG -> scene.navigation.showChangelogDialog()
            AppLaunchDialog.NONE -> Unit
        }
    }
    val canNavigateBack = canHandleIosBack(
        navigation = navigation,
        storiesController = storiesController,
        commentsController = commentsController,
    )
    val backEventState = rememberNavigationEventState(
        currentInfo = IosApplicationBackInfo,
        backInfo = if (canNavigateBack) listOf(IosApplicationBackInfo) else emptyList(),
    )
    val activeBack = backEventState.transitionState as? NavigationEventTransitionState.InProgress
    val backProgress = activeBack?.latestEvent?.progress?.coerceIn(0f, 1f) ?: 0f
    val settingsCanNavigateBack = settingsNavigation?.state?.collectAsState()
        ?.value?.canNavigateBackWithinSettings == true
    val visualTarget = iosBackVisualTarget(
        navigation, storiesController, commentsController, settingsCanNavigateBack,
    )
    NavigationBackHandler(
        state = backEventState,
        isBackEnabled = canNavigateBack,
        onBackCancelled = { completedBackTarget = IosBackVisualTarget.None },
        onBackCompleted = {
            completedBackTarget = visualTarget
            handleIosBack(
                navigation = navigation,
                scene = scene,
                storiesController = storiesController,
                commentsController = commentsController,
                settingsNavigation = settingsNavigation,
                onEditorBackRequested = { editorBackRequestVersion++ },
            )
        },
    )
    LaunchedEffect(navigation.storyRequest?.serial) {
        if (navigation.storyRequest != null) completedBackTarget = IosBackVisualTarget.None
    }
    LaunchedEffect(navigation.settingsRequest?.serial) {
        if (navigation.settingsRequest != null) completedBackTarget = IosBackVisualTarget.None
    }
    LaunchedEffect(navigation.submissionsRequest?.serial) {
        if (navigation.submissionsRequest != null) completedBackTarget = IosBackVisualTarget.None
    }
    LaunchedEffect(navigation.editorRequest?.serial) {
        if (navigation.editorRequest != null) completedBackTarget = IosBackVisualTarget.None
    }

    ProvideHarmonicUiDependencies(
        HarmonicUiDependencies(bootstrap.app, scene),
    ) {
        HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
            Surface(Modifier.fillMaxSize()) {
                IosAppContent(
                    app = bootstrap.app,
                    scene = scene,
                    storiesController = storiesController,
                    commentsController = commentsController,
                    editorBackRequestVersion = editorBackRequestVersion,
                    onStoriesControllerChanged = { storiesController = it },
                    onCommentsControllerChanged = { commentsController = it },
                    onSettingsNavigationChanged = { settingsNavigation = it },
                    backVisualTarget = visualTarget,
                    backProgress = backProgress,
                    backInProgress = activeBack != null,
                    completedBackTarget = completedBackTarget,
                )
            }
        }
    }
}

private fun canHandleIosBack(
    navigation: MainNavigationSnapshot,
    storiesController: StoriesScreenController?,
    commentsController: CommentsScreenController?,
): Boolean = navigation.failureRequest != null ||
    navigation.userRequest != null || navigation.captchaRequest != null ||
    navigation.loginDialogVisible || navigation.cacheStoriesDialogVisible ||
    navigation.changelogDialogVisible || navigation.welcomeDialogVisible ||
    navigation.coulombGasVisible || navigation.editorRequest != null ||
    navigation.submissionsRequest != null || navigation.settingsRequest != null ||
    commentsController?.isLinkPreviewOverlayShowing() == true ||
    commentsController?.isCommentActionOverlayShowing() == true ||
    commentsController?.searchDialogVisible == true ||
    commentsController?.isWebsiteVisible() == true ||
    storiesController?.isStoryPreviewShowing() == true ||
    navigation.storyRequest != null || storiesController?.searching == true

private fun iosBackVisualTarget(
    navigation: MainNavigationSnapshot,
    storiesController: StoriesScreenController?,
    commentsController: CommentsScreenController?,
    settingsCanNavigateBack: Boolean,
): IosBackVisualTarget = when {
    navigation.failureRequest != null || navigation.userRequest != null ||
        navigation.captchaRequest != null || navigation.loginDialogVisible ||
        navigation.cacheStoriesDialogVisible || navigation.changelogDialogVisible ||
        navigation.welcomeDialogVisible || navigation.coulombGasVisible ||
        commentsController?.isLinkPreviewOverlayShowing() == true ||
        commentsController?.isCommentActionOverlayShowing() == true ||
        commentsController?.searchDialogVisible == true ||
        commentsController?.isWebsiteVisible() == true -> IosBackVisualTarget.None
    navigation.editorRequest != null -> IosBackVisualTarget.Editor
    navigation.submissionsRequest != null -> IosBackVisualTarget.Submissions
    navigation.settingsRequest != null ->
        if (settingsCanNavigateBack) IosBackVisualTarget.None else IosBackVisualTarget.Settings
    navigation.storyRequest != null -> IosBackVisualTarget.Story
    storiesController?.isStoryPreviewShowing() == true || storiesController?.searching == true ->
        IosBackVisualTarget.None
    else -> IosBackVisualTarget.None
}

private fun handleIosBack(
    navigation: MainNavigationSnapshot,
    scene: HarmonicSceneComposition,
    storiesController: StoriesScreenController?,
    commentsController: CommentsScreenController?,
    settingsNavigation: SettingsNavigationStore?,
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
        navigation.settingsRequest != null -> {
            handleSettingsBack(settingsNavigation, scene.navigation::closeSettings)
        }
        commentsController?.isLinkPreviewOverlayShowing() == true ->
            commentsController.requestDismissLinkPreview()
        commentsController?.isCommentActionOverlayShowing() == true ->
            commentsController.requestDismissCommentActions()
        commentsController?.searchDialogVisible == true -> commentsController.dismissCommentSearch()
        commentsController?.isWebsiteVisible() == true -> commentsController.requestExpandSheet()
        navigation.storyRequest != null -> scene.navigation.detailRemovedFromBackStack()
        storiesController?.isStoryPreviewShowing() == true ->
            storiesController.requestDismissStoryPreview()
        storiesController?.searching == true -> storiesController.finishSearchBack()
        else -> return false
    }
    return true
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun IosAppContent(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    storiesController: StoriesScreenController?,
    commentsController: CommentsScreenController?,
    editorBackRequestVersion: Int,
    onStoriesControllerChanged: (StoriesScreenController?) -> Unit,
    onCommentsControllerChanged: (CommentsScreenController?) -> Unit,
    onSettingsNavigationChanged: (SettingsNavigationStore?) -> Unit,
    backVisualTarget: IosBackVisualTarget,
    backProgress: Float,
    backInProgress: Boolean,
    completedBackTarget: IosBackVisualTarget,
) {
    val navigation by scene.navigation.state.collectAsState()
    val density = LocalDensity.current
    val transitionOffsetPx = with(density) { 96.dp.roundToPx() }
    val isTabletDevice = isIosTabletWindow()
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val mainDirective = remember(adaptiveInfo, isTabletDevice) {
        val directive = calculatePaneScaffoldDirective(adaptiveInfo)
        directive.copy(
            maxHorizontalPartitions = if (isTabletDevice) directive.maxHorizontalPartitions else 1,
            horizontalPartitionSpacerSize = 16.dp,
        )
    }
    val isTwoPane = mainDirective.maxHorizontalPartitions > 1
    val incomingModifier = if (backInProgress) Modifier.iosBackIncoming(backProgress) else Modifier
    val outgoingModifier = if (backInProgress) Modifier.iosBackOutgoing(backProgress) else Modifier
    HarmonicAppRoot(
        navigation = navigation,
        transitionOffsetPx = transitionOffsetPx,
        completedSettingsPredictiveBack = completedBackTarget == IosBackVisualTarget.Settings,
        completedSubmissionsPredictiveBack = completedBackTarget == IosBackVisualTarget.Submissions,
        completedEditorPredictiveBack = completedBackTarget == IosBackVisualTarget.Editor,
        basePredictiveModifier = if (
            backVisualTarget == IosBackVisualTarget.Settings ||
            backVisualTarget == IosBackVisualTarget.Submissions
        ) incomingModifier else Modifier,
        settingsPredictiveModifier = if (backVisualTarget == IosBackVisualTarget.Settings) {
            outgoingModifier
        } else {
            Modifier
        },
        submissionsPredictiveModifier = if (backVisualTarget == IosBackVisualTarget.Submissions) {
            outgoingModifier
        } else {
            Modifier
        },
        editorPredictiveModifier = if (backVisualTarget == IosBackVisualTarget.Editor) {
            outgoingModifier
        } else {
            Modifier
        },
        linkPreview = commentsController
            ?.takeIf {
                it.isLinkPreviewOverlayShowing() && !it.searchDialogVisible
            }
            ?.let { controller ->
                { IosCommentLinkPreview(app, scene, controller) }
            },
        base = {
            if (isTwoPane) {
                MainNavigationScene(
                    storyRequest = navigation.storyRequest,
                    directive = mainDirective,
                    paneProportion = 0.4f,
                    onBack = scene.navigation::detailRemovedFromBackStack,
                    stories = {
                        IosStoriesContent(
                            app, scene,
                            visible = navigation.currentDestination == MainDestination.STORIES ||
                                (isTwoPane && navigation.currentDestination == MainDestination.STORY),
                            onControllerChanged = onStoriesControllerChanged,
                        )
                    },
                    emptyDetail = { EmptyCommentsScreen() },
                    comments = { request ->
                        IosCommentsContent(
                            app = app,
                            scene = scene,
                            request = request,
                            isTablet = isTabletDevice,
                            isTwoPane = isTwoPane,
                            showUpButton = false,
                            onControllerChanged = onCommentsControllerChanged,
                        )
                    },
                )
            } else {
                SinglePaneNavigationScene(
                    scene = mainNavigationScenePlan(navigation, isTwoPane = false),
                    completedPredictivePop = completedBackTarget == IosBackVisualTarget.Story,
                    predictiveBackActive = backInProgress &&
                        backVisualTarget == IosBackVisualTarget.Story,
                    storiesPredictiveModifier = if (
                        backVisualTarget == IosBackVisualTarget.Story
                    ) incomingModifier else Modifier,
                    commentsPredictiveModifier = if (
                        backVisualTarget == IosBackVisualTarget.Story
                    ) outgoingModifier else Modifier,
                    stories = {
                        IosStoriesContent(
                            app, scene,
                            visible = navigation.currentDestination == MainDestination.STORIES ||
                                (isTwoPane && navigation.currentDestination == MainDestination.STORY),
                            onControllerChanged = onStoriesControllerChanged,
                        )
                    },
                    comments = { request ->
                        IosCommentsContent(
                            app = app,
                            scene = scene,
                            request = request,
                            isTablet = isTabletDevice,
                            isTwoPane = isTwoPane,
                            showUpButton = true,
                            onControllerChanged = onCommentsControllerChanged,
                        )
                    },
                )
            }
        },
        settings = {
            IosSettingsShell(
                app = app,
                scene = scene,
                onNavigationChanged = onSettingsNavigationChanged,
                initialSection = SettingsSection.fromRoute(
                    navigation.currentSettingsSectionRoute.orEmpty(),
                ),
            )
        },
        submissions = {
            navigation.lastSubmissionsRequest?.let {
                IosSubmissionsContent(app, scene, it)
            }
        },
        editor = {
            navigation.lastEditorRequest?.let {
                IosEditorContent(
                    app = app,
                    scene = scene,
                    request = it,
                    backRequestVersion = editorBackRequestVersion,
                )
            }
        },
        immersive = { CoulombGasScreen() },
        foreground = {
            IosAppForeground(app, scene, navigation, storiesController)
        },
    )
}

private fun Modifier.iosBackOutgoing(progress: Float): Modifier = graphicsLayer {
    translationX = size.width * progress
    shadowElevation = if (progress in 0f..0.999f) 12f else 0f
}

private fun Modifier.iosBackIncoming(progress: Float): Modifier =
    drawWithContent {
        drawContent()
        drawRect(Color.Black.copy(alpha = 0.14f * (1f - progress)))
    }.graphicsLayer {
        translationX = -size.width * 0.24f * (1f - progress)
    }

@Composable
private fun IosStoriesContent(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    visible: Boolean,
    onControllerChanged: (StoriesScreenController?) -> Unit,
) {
    val foreground = LocalIosForeground.current
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
    val defaultStoryHeightPx = with(LocalDensity.current) { 96.dp.roundToPx() }
    val store = remember(app, scene, scope) {
        app.createStoriesFeatureStore(
            StoriesFeatureHost(
                scope = scope,
                sessionState = scene.sessions.stories,
                platform = app.storiesPlatformDependencies(),
                userSettings = app.userSettings,
            ),
        )
    }
    val controller = remember(store, defaultStoryHeightPx) {
        lateinit var created: StoriesScreenController
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
            override fun isSplitLayout(): Boolean = false
        }
        created = StoriesScreenController.create(
            defaultStoryHeightPx = defaultStoryHeightPx,
            savedItemState = store.savedItemState,
            listener = StoriesFeatureListener(store, callbacks),
        )
        created
    }
    val state by store.state.collectAsState()
    val colors = HarmonicTheme.colors
    val filterColors = harmonicFilterButtonColors()

    SideEffect { onControllerChanged(controller) }
    DisposableEffect(store) {
        store.start()
        onDispose {
            onControllerChanged(null)
            store.onStop()
            store.close()
            preloadCoordinator.dispose()
        }
    }
    DisposableEffect(store, foreground, visible) {
        if (foreground && visible) {
            store.onStart()
            store.onResume()
        }
        onDispose { store.onStop() }
    }
    LaunchedEffect(
        foreground, visible,
        preloadCoordinator,
        appSettings.comments.preloadCommentsMode,
        appSettings.comments.preloadCommentsMinimumBattery,
        appSettings.reading.useAlgoliaApi,
    ) {
        preloadCoordinator.setEnabled(
            foreground && visible && appSettings.comments.preloadCommentsFromStories,
        )
    }
    LaunchedEffect(state, controller) {
        val lastUpdatedText = state.lastUpdatedMillis?.let { millis ->
            PresentationCopy.lastUpdated(app.platform.timeFormatting.time(millis))
        }
        controller.updateContent(
            state,
            StoriesPlatformPresentation(lastUpdatedText, contentInsetStartPx = 0),
        )
    }
    LaunchedEffect(store, controller, scene) {
        store.effects.collect { effect ->
            when (effect) {
                is StoriesFeatureEffect.OpenStory -> scene.navigation.openStory(effect.destination)
                is StoriesFeatureEffect.OpenExternalLink ->
                    scene.links.openExternal(ExternalLinkRequest(effect.url))
                is StoriesFeatureEffect.Platform -> when (val platform = effect.effect) {
                    StoriesPlatformEffect.OpenSettings -> scene.navigation.openSettings(null)
                    StoriesPlatformEffect.RequestLogin -> scene.navigation.showLoginDialog()
                    is StoriesPlatformEffect.OpenProfile ->
                        scene.navigation.showUserDialog(platform.userName)
                    StoriesPlatformEffect.ShowCacheDialog ->
                        scene.navigation.showCacheStoriesDialog()
                    StoriesPlatformEffect.OpenSubmitEditor ->
                        scene.navigation.openEditor(EditorDestination(EditorType.POST))
                }
                is StoriesFeatureEffect.StoryChanged ->
                    effect.storyId?.let(controller::invalidateStory)
                StoriesFeatureEffect.LoginRequired -> scene.navigation.showLoginDialog()
                is StoriesFeatureEffect.UserMessage -> scene.userMessages.show(effect.message)
                is StoriesFeatureEffect.SavedActionFailed -> {
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

    Box(
        Modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background),
    ) {
        StoriesRoute(
            controller = controller,
            tintStore = app.storyResourceTints,
            filterColors = filterColors,
            onVisibleStoriesChanged = preloadCoordinator::updateVisibleStories,
        )
        IosStatusBarProtection(HarmonicTheme.colors.background)
        if (controller.isStoryPreviewShowing()) {
            IosStoryPreviewOverlay(app, controller)
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun IosSettingsShell(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    initialSection: SettingsSection?,
    onNavigationChanged: (SettingsNavigationStore?) -> Unit,
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val settingsAccountState by app.platform.accounts.accountState.collectAsState()
    val isTabletDevice = isIosTabletWindow()
    val directive = remember(adaptiveInfo, isTabletDevice) {
        val calculated = calculatePaneScaffoldDirective(adaptiveInfo)
        calculated.copy(
            maxHorizontalPartitions = if (isTabletDevice) calculated.maxHorizontalPartitions else 1,
        )
    }
    val isTwoPane = directive.maxHorizontalPartitions > 1
    val navigation = rememberSettingsNavigationStore(
        initialSection = initialSection,
        twoPane = isTwoPane,
    )
    val currentOnNavigationChanged by rememberUpdatedState(onNavigationChanged)
    DisposableEffect(navigation) {
        currentOnNavigationChanged(navigation)
        onDispose { currentOnNavigationChanged(null) }
    }

    LaunchedEffect(initialSection) { initialSection?.let(navigation::navigateTo) }

    // Compose iOS can leave LookaheadDelegate alignment-line ownership stale when a
    // Navigation 3 list/detail scene changes its pane count during a live resize. Recreate only
    // the scene machinery at the breakpoint; the navigation store stays outside this key so the
    // selected section and detail stack survive both directions of the layout change.
    key(isTwoPane) {
        SettingsNavigationShell(
            navigation = navigation,
            directive = directive,
            supportsTwoPane = isTabletDevice,
            isFoldable = false,
            tabletPaneHorizontalPadding = if (isTwoPane) 24.dp else 0.dp,
            onBackFromSettings = scene.navigation::closeSettings,
            onSectionChanged = { scene.navigation.updateSettingsSection(it.route) },
            animateDetailChanges = false,
            renderList = { selectedSection, showSelection, onBack, onSectionSelected ->
                SettingsListScreen(
                    selectedSection = selectedSection,
                    showSelection = showSelection,
                    showDebugSettings = app.metadata.debugSettingsEnabled,
                    loggedIn = settingsAccountState.accountOrNull != null,
                    onBack = onBack,
                    onSectionSelected = onSectionSelected,
                )
            },
            renderDetail = { section, singlePane, onBack, onNavigate ->
                IosSettingsDetail(
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

/** A wide phone in landscape still needs a single reading/settings pane. */
@Composable
private fun isIosTabletWindow(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { minOf(size.width, size.height).toDp() >= 600.dp }
}
