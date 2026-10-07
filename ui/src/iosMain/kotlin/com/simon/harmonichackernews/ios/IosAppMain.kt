package com.simon.harmonichackernews.ios

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import kotlin.time.Clock
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.simon.harmonichackernews.ui.common.LocalIosScreenCorners
import com.simon.harmonichackernews.ui.common.ScreenCorners
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
import com.simon.harmonichackernews.navigation.MainStoryRequest
import com.simon.harmonichackernews.ui.navigation.MainNavigationScene
import com.simon.harmonichackernews.ui.navigation.mainNavigationScenePlan
import com.simon.harmonichackernews.ui.navigation.rememberMainNavigationBackPreview
import com.simon.harmonichackernews.ui.navigation.MainNavigationSurfaceKey
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
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPhone

internal val LocalIosForeground = staticCompositionLocalOf { false }

private data object IosApplicationBackInfo : NavigationEventInfo()

private enum class IosBackVisualTarget { None, Story, Settings, Submissions, Editor }

private class IosBackSettle(
    val visualTarget: IosBackVisualTarget,
    val progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val navigation: MainNavigationSnapshot,
    val commit: Boolean,
)

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
    private var screenCorners by mutableStateOf(ScreenCorners())

    /** UIKit supplies public, container-relative radii in points; non-full-screen hosts send zero. */
    fun updateScreenCorners(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) {
        if (!closed) screenCorners = ScreenCorners(
            topLeft, topRight, bottomRight, bottomLeft,
        )
    }

    fun setForeground(active: Boolean) {
        if (closed) return
        foreground = active
        if (active) refreshAppearance()
    }

    fun refreshAppearance() {
        if (!closed) bootstrap.app.appearance.refreshSelection()
    }

    fun makeViewController(): UIViewController = ComposeUIViewController {
        CompositionLocalProvider(
            LocalIosForeground provides foreground,
            LocalIosScreenCorners provides screenCorners,
        ) {
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
        HarmonicThemeCatalog.scheme(selection.colorScheme, selection.dark, selection.colorStyle)
    }
    SideEffect {
        appearance.setAppearance(selection.dark, palette.colorScheme.surface.toArgb())
    }
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
    var lastBackProgress by remember { mutableStateOf<Float?>(null) }
    var lastBackTarget by remember { mutableStateOf(IosBackVisualTarget.None) }
    var backSettle by remember { mutableStateOf<IosBackSettle?>(null) }
    SideEffect {
        if (activeBack != null && backSettle == null) {
            lastBackProgress = backProgress
            lastBackTarget = visualTarget
            completedBackTarget = IosBackVisualTarget.None
        }
    }
    val performBack by rememberUpdatedState {
        handleIosBack(
            navigation = navigation,
            scene = scene,
            storiesController = storiesController,
            commentsController = commentsController,
            settingsNavigation = settingsNavigation,
            onEditorBackRequested = { editorBackRequestVersion++ },
        )
    }
    fun finishBack(commit: Boolean) {
        if (backSettle != null) return
        val progress = lastBackProgress
        if (progress != null && lastBackTarget != IosBackVisualTarget.None) {
            // Keep the same retained surfaces until the released gesture reaches its endpoint.
            backSettle = IosBackSettle(lastBackTarget, Animatable(progress), navigation, commit)
        } else if (commit) {
            performBack()
        }
        lastBackProgress = null
    }
    NavigationBackHandler(
        state = backEventState,
        isBackEnabled = canNavigateBack && backSettle == null,
        onBackCancelled = { finishBack(commit = false) },
        onBackCompleted = { finishBack(commit = true) },
    )
    LaunchedEffect(backSettle, navigation) {
        val settle = backSettle ?: return@LaunchedEffect
        // A different navigation action supersedes the pending gesture.
        if (navigation == settle.navigation) {
            settle.progress.animateTo(
                targetValue = if (settle.commit) 1f else 0f,
                animationSpec = spring(dampingRatio = 1f, stiffness = 400f, visibilityThreshold = 0.001f),
            )
            if (settle.commit) {
                completedBackTarget = settle.visualTarget
                performBack()
            }
        }
        backSettle = null
    }
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
        HarmonicTheme(palette.colorScheme, palette.dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                IosAppContent(
                    app = bootstrap.app,
                    scene = scene,
                    storiesController = storiesController,
                    commentsController = commentsController,
                    editorBackRequestVersion = editorBackRequestVersion,
                    onStoriesControllerChanged = { storiesController = it },
                    onCommentsControllerChanged = { commentsController = it },
                    onSettingsNavigationChanged = { settingsNavigation = it },
                    backVisualTarget = backSettle?.visualTarget ?: visualTarget,
                    backProgress = backSettle?.progress?.value ?: backProgress,
                    backInProgress = activeBack != null || backSettle != null,
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
    val supportsTwoPane = iosWindowSupportsTwoPane()
    val expandedPhone = supportsTwoPane &&
        UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPhone
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val mainDirective = remember(adaptiveInfo, supportsTwoPane, expandedPhone) {
        val directive = calculatePaneScaffoldDirective(adaptiveInfo)
        directive.copy(
            maxHorizontalPartitions = if (supportsTwoPane) directive.maxHorizontalPartitions else 1,
            horizontalPartitionSpacerSize = if (expandedPhone) 12.dp else 16.dp,
        )
    }
    val isTwoPane = mainDirective.maxHorizontalPartitions > 1
    val incomingModifier = if (backInProgress) Modifier.iosBackIncoming(backProgress) else Modifier
    val outgoingModifier = if (backInProgress) Modifier.iosBackOutgoing(backProgress) else Modifier
    val plan = mainNavigationScenePlan(navigation, isTwoPane)
    val preview = rememberMainNavigationBackPreview(
        plan,
        gesture = true.takeIf {
            backInProgress &&
                backVisualTarget != IosBackVisualTarget.None && !plan.storyUsesTwoPane
        },
        enterModifier = incomingModifier,
        exitModifier = outgoingModifier,
    )
    var lastGestureSource by remember { mutableStateOf<MainNavigationSurfaceKey?>(null) }
    SideEffect { if (preview != null) lastGestureSource = preview.source }
    val renderComments: @Composable (MainStoryRequest, Boolean) -> Unit = { request, fullScreen ->
        IosCommentsContent(
            app = app, scene = scene, request = request,
            isTablet = supportsTwoPane, isTwoPane = !fullScreen, showUpButton = fullScreen,
            onControllerChanged = onCommentsControllerChanged,
        )
    }
    HarmonicAppRoot(
        plan = plan,
        preview = preview,
        completedPredictiveBack = if (completedBackTarget != IosBackVisualTarget.None) {
            setOfNotNull(lastGestureSource)
        } else emptySet(),
        linkPreview = commentsController
            ?.takeIf {
                navigation.currentDestination == MainDestination.STORY &&
                    it.isLinkPreviewOverlayShowing() && !it.searchDialogVisible
            }
            ?.let { controller ->
                { IosCommentLinkPreview(app, scene, controller) }
            },
        stories = { detail, paneComments ->
            val stories: @Composable () -> Unit = {
                IosStoriesContent(
                    app, scene,
                    visible = navigation.currentDestination == MainDestination.STORIES ||
                        (isTwoPane && navigation.currentDestination == MainDestination.STORY),
                    onControllerChanged = onStoriesControllerChanged,
                )
            }
            if (isTwoPane) {
                MainNavigationScene(
                    storyRequest = detail,
                    directive = mainDirective,
                    paneProportion = if (expandedPhone) 0.45f else 0.4f,
                    isFoldable = expandedPhone,
                    snapToCenter = false,
                    onBack = scene.navigation::detailRemovedFromBackStack,
                    stories = stories,
                    emptyDetail = { EmptyCommentsScreen() },
                    comments = paneComments,
                )
            } else {
                stories()
            }
        },
        comments = renderComments,
        settings = { request ->
            IosSettingsShell(
                app = app,
                scene = scene,
                onNavigationChanged = onSettingsNavigationChanged,
                initialSection = SettingsSection.fromRoute(
                    request.initialSectionRoute.orEmpty(),
                ),
            )
        },
        submissions = { request, detail, paneComments ->
            if (isTwoPane) {
                MainNavigationScene(
                    storyRequest = detail, directive = mainDirective,
                    paneProportion = if (expandedPhone) 0.45f else 0.4f,
                    isFoldable = expandedPhone,
                    snapToCenter = false,
                    onBack = scene.navigation::detailRemovedFromBackStack,
                    stories = { IosSubmissionsContent(app, scene, request) },
                    emptyDetail = { EmptyCommentsScreen() },
                    comments = paneComments,
                )
            } else {
                IosSubmissionsContent(app, scene, request)
            }
        },
        editor = { request ->
            IosEditorContent(
                app = app, scene = scene, request = request,
                backRequestVersion = editorBackRequestVersion,
            )
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
    val colors = MaterialTheme.colorScheme
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
            .background(MaterialTheme.colorScheme.surface),
    ) {
        StoriesRoute(
            controller = controller,
            tintStore = app.storyResourceTints,
            filterColors = filterColors,
            onVisibleStoriesChanged = preloadCoordinator::updateVisibleStories,
        )
        IosStatusBarProtection(MaterialTheme.colorScheme.surface)
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
    val supportsTwoPane = iosWindowSupportsTwoPane()
    val expandedPhone = supportsTwoPane &&
        UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPhone
    val directive = remember(adaptiveInfo, supportsTwoPane, expandedPhone) {
        val calculated = calculatePaneScaffoldDirective(adaptiveInfo)
        calculated.copy(
            maxHorizontalPartitions = if (supportsTwoPane) calculated.maxHorizontalPartitions else 1,
            horizontalPartitionSpacerSize = if (expandedPhone) 12.dp else 16.dp,
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
            supportsTwoPane = supportsTwoPane,
            isFoldable = expandedPhone,
            paneProportion = if (expandedPhone) 0.45f else 0.4f,
            snapToCenter = false,
            tabletPaneHorizontalPadding = if (isTwoPane && !expandedPhone) 24.dp else 0.dp,
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

/** Follow the current window across displays; a short landscape phone stays single-pane. */
@Composable
private fun iosWindowSupportsTwoPane(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { minOf(size.width, size.height).toDp() >= 600.dp }
}
