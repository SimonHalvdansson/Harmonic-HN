package com.simon.harmonichackernews.ios

import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
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
import com.simon.harmonichackernews.app.createStoriesStore
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.navigation.MainNavigationSnapshot
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.PresentationCopy
import com.simon.harmonichackernews.presentation.StoriesPlatformEffect
import com.simon.harmonichackernews.presentation.StoriesRuntimeEffect
import com.simon.harmonichackernews.settings.AppLaunchDialog
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.ProvideHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.comments.EmptyCommentsScreen
import com.simon.harmonichackernews.ui.debug.CoulombGasScreen
import com.simon.harmonichackernews.ui.navigation.SharedHarmonicAppRoot
import com.simon.harmonichackernews.ui.navigation.SharedMainNavigationScene
import com.simon.harmonichackernews.ui.navigation.SharedSinglePaneNavigationScene
import com.simon.harmonichackernews.ui.settings.SettingsListScreen
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.settings.SharedSettingsNavigationShell
import com.simon.harmonichackernews.ui.settings.rememberSettingsNavigationStore
import com.simon.harmonichackernews.ui.stories.SharedStoriesRoute
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.stories.StoriesFeatureListener
import com.simon.harmonichackernews.ui.stories.StoriesPlatformPresentation
import com.simon.harmonichackernews.ui.stories.StoriesScreenStateFactory
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import platform.UIKit.UIViewController

private data object IosApplicationBackInfo : NavigationEventInfo()

private enum class IosBackVisualTarget { None, Story, Settings, Submissions, Editor }

/**
 * Swift-facing owner for one Compose iOS scene. The native host retains this object and installs
 * the returned controller; Compose dispatches iOS edge gestures through Navigation Event.
 */
class IosHarmonicApplication(
    bindings: com.simon.harmonichackernews.platform.IosPlatformBindings,
    runtime: com.simon.harmonichackernews.app.IosHostRuntimeBindings,
) {
    private val appearance = bindings.appearance
    private val bootstrap = IosHarmonicAppBootstrap(
        userAgent = "Harmonic-HN-iOS/${runtime.metadata.versionName}",
        bindings = bindings,
        runtime = runtime,
    )
    private val scene = bootstrap.createScene()
    private var backHandler: () -> Boolean = { false }
    private var closed = false

    fun makeViewController(): UIViewController = ComposeUIViewController {
        IosApp(
            bootstrap = bootstrap,
            scene = scene,
            appearance = appearance,
            installBackHandler = { backHandler = it },
        )
    }

    /** Returns true when the shared scene consumed the native back request. */
    fun requestBack(): Boolean = backHandler()

    fun close() {
        if (closed) return
        closed = true
        backHandler = { false }
        scene.close()
        bootstrap.close()
    }
}

@Composable
private fun IosApp(
    bootstrap: IosHarmonicAppBootstrap,
    scene: HarmonicSceneComposition,
    appearance: com.simon.harmonichackernews.platform.IosAppearanceController,
    installBackHandler: (() -> Boolean) -> Unit,
) {
    val navigation by scene.navigation.state.collectAsState()
    var storiesController by remember { mutableStateOf<StoriesComposeController?>(null) }
    var commentsController by remember { mutableStateOf<CommentsComposeController?>(null) }
    var editorBackRequestVersion by remember { mutableIntStateOf(0) }
    var completedBackTarget by remember { mutableStateOf(IosBackVisualTarget.None) }

    val selection by bootstrap.app.appearance.selections.collectAsState(
        initial = bootstrap.app.appearance.selection(),
    )
    val palette = remember(selection) {
        HarmonicThemeCatalog.resolve(selection.theme, selection.dark)
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
    SideEffect {
        installBackHandler {
            handleIosBack(
                navigation = navigation,
                scene = scene,
                storiesController = storiesController,
                commentsController = commentsController,
                onEditorBackRequested = { editorBackRequestVersion++ },
            )
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
    val visualTarget = iosBackVisualTarget(navigation, storiesController, commentsController)
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
        HarmonicTheme(palette.colors, palette.colorScheme) {
            Surface(Modifier.fillMaxSize()) {
                IosAppContent(
                    app = bootstrap.app,
                    scene = scene,
                    storiesController = storiesController,
                    commentsController = commentsController,
                    editorBackRequestVersion = editorBackRequestVersion,
                    onStoriesControllerChanged = { storiesController = it },
                    onCommentsControllerChanged = { commentsController = it },
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
    storiesController: StoriesComposeController?,
    commentsController: CommentsComposeController?,
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
    storiesController: StoriesComposeController?,
    commentsController: CommentsComposeController?,
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
    navigation.settingsRequest != null -> IosBackVisualTarget.Settings
    navigation.storyRequest != null -> IosBackVisualTarget.Story
    storiesController?.isStoryPreviewShowing() == true || storiesController?.searching == true ->
        IosBackVisualTarget.None
    else -> IosBackVisualTarget.None
}

private fun handleIosBack(
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
    storiesController: StoriesComposeController?,
    commentsController: CommentsComposeController?,
    editorBackRequestVersion: Int,
    onStoriesControllerChanged: (StoriesComposeController?) -> Unit,
    onCommentsControllerChanged: (CommentsComposeController?) -> Unit,
    backVisualTarget: IosBackVisualTarget,
    backProgress: Float,
    backInProgress: Boolean,
    completedBackTarget: IosBackVisualTarget,
) {
    val navigation by scene.navigation.state.collectAsState()
    val density = LocalDensity.current
    val transitionOffsetPx = with(density) { 96.dp.roundToPx() }
    val isTabletDevice = with(density) {
        LocalWindowInfo.current.containerSize.width.toDp() >= 600.dp
    }
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val mainDirective = remember(adaptiveInfo) {
        calculatePaneScaffoldDirective(adaptiveInfo).copy(
            horizontalPartitionSpacerSize = 16.dp,
        )
    }
    val isTwoPane = mainDirective.maxHorizontalPartitions > 1
    val incomingModifier = if (backInProgress) Modifier.iosBackIncoming(backProgress) else Modifier
    val outgoingModifier = if (backInProgress) Modifier.iosBackOutgoing(backProgress) else Modifier
    SharedHarmonicAppRoot(
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
            ?.takeIf(CommentsComposeController::isLinkPreviewOverlayShowing)
            ?.let { controller ->
                { IosCommentLinkPreview(app, scene, controller) }
            },
        base = {
            if (isTwoPane) {
                SharedMainNavigationScene(
                    storyRequest = navigation.storyRequest,
                    directive = mainDirective,
                    paneProportion = 0.4f,
                    onBack = scene.navigation::detailRemovedFromBackStack,
                    stories = {
                        IosStoriesContent(app, scene, onStoriesControllerChanged)
                    },
                    emptyDetail = { EmptyCommentsScreen() },
                    comments = { request ->
                        IosCommentsContent(
                            app = app,
                            scene = scene,
                            request = request,
                            isTablet = isTabletDevice,
                            showUpButton = false,
                            onControllerChanged = onCommentsControllerChanged,
                        )
                    },
                )
            } else {
                SharedSinglePaneNavigationScene(
                    storyRequest = navigation.storyRequest,
                    lastStoryRequest = navigation.lastStoryRequest,
                    completedPredictivePop = completedBackTarget == IosBackVisualTarget.Story,
                    predictiveBackActive = backInProgress &&
                        backVisualTarget == IosBackVisualTarget.Story,
                    showStoriesPane = true,
                    storiesPredictiveModifier = if (
                        backVisualTarget == IosBackVisualTarget.Story
                    ) incomingModifier else Modifier,
                    commentsPredictiveModifier = if (
                        backVisualTarget == IosBackVisualTarget.Story
                    ) outgoingModifier else Modifier,
                    stories = {
                        IosStoriesContent(app, scene, onStoriesControllerChanged)
                    },
                    comments = { request ->
                        IosCommentsContent(
                            app = app,
                            scene = scene,
                            request = request,
                            isTablet = isTabletDevice,
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
    onControllerChanged: (StoriesComposeController?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val defaultStoryHeightPx = with(LocalDensity.current) { 96.dp.roundToPx() }
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
            override fun isSplitLayout(): Boolean = false
        }
        created = StoriesComposeController.create(
            defaultStoryHeightPx = defaultStoryHeightPx,
            savedItemState = store.savedItemState,
            listener = StoriesFeatureListener(store, callbacks),
        )
        created
    }
    val state by store.state.collectAsState()
    val colors = HarmonicTheme.colors
    val filterColors = remember(colors) {
        HarmonicFilterButtonColors(
            checkedBackground = colors.storyNormal,
            checkedText = colors.background,
            checkedStroke = colors.storyNormal,
            uncheckedText = colors.storyNormal,
            uncheckedStroke = colors.outlineVariant,
        )
    }

    SideEffect { onControllerChanged(controller) }
    DisposableEffect(store) {
        store.start()
        store.onStart()
        onDispose {
            onControllerChanged(null)
            store.onStop()
            store.close()
        }
    }
    LaunchedEffect(state, controller) {
        val lastUpdatedText = state.lastUpdatedMillis?.let { millis ->
            PresentationCopy.lastUpdated(app.platform.timeFormatting.time(millis))
        }
        controller.updateContent(
            StoriesScreenStateFactory.create(
                state,
                StoriesPlatformPresentation(lastUpdatedText, contentInsetStartPx = 0),
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
                    }
                    scene.userMessages.show(effect.presentation.message)
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background),
    ) {
        SharedStoriesRoute(
            controller = controller,
            tintStore = app.storyResourceTints,
            commentText = { AnnotatedString(it) },
            filterColors = filterColors,
            extraCompactSelectedText = false,
            compactSelectedText = false,
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
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(adaptiveInfo) { calculatePaneScaffoldDirective(adaptiveInfo) }
    val isTwoPane = directive.maxHorizontalPartitions > 1
    val navigation = rememberSettingsNavigationStore(
        initialSection = initialSection,
        twoPane = isTwoPane,
    )

    LaunchedEffect(initialSection) { initialSection?.let(navigation::navigateTo) }

    // Compose iOS can leave LookaheadDelegate alignment-line ownership stale when a
    // Navigation 3 list/detail scene changes its pane count during a live resize. Recreate only
    // the scene machinery at the breakpoint; the navigation store stays outside this key so the
    // selected section and detail stack survive both directions of the layout change.
    key(isTwoPane) {
        SharedSettingsNavigationShell(
            navigation = navigation,
            directive = directive,
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
