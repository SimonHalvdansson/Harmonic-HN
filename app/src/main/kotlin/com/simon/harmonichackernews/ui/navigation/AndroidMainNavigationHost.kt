package com.simon.harmonichackernews.ui.navigation

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelProvider
import com.simon.harmonichackernews.AndroidCommentsCoordinator
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.HarmonicSceneViewModel
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.AndroidStoriesCoordinator
import com.simon.harmonichackernews.ui.comments.EmptyCommentsScreen
import com.simon.harmonichackernews.ui.common.CaptchaDialog
import com.simon.harmonichackernews.ui.common.FailureDetailDialog
import com.simon.harmonichackernews.ui.common.AndroidLoginDialog
import com.simon.harmonichackernews.ui.common.UserMessageSnackbarHost
import com.simon.harmonichackernews.ui.debug.CoulombGasScreen
import com.simon.harmonichackernews.ui.editor.EditorSubmissionState
import com.simon.harmonichackernews.ui.editor.AndroidEditorCoordinator
import com.simon.harmonichackernews.ui.editor.ComposeEditorScreen
import com.simon.harmonichackernews.ui.settings.SettingsChangelogDialog
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.settings.SettingsShell
import com.simon.harmonichackernews.ui.settings.ProvideSettingsPlatformStyle
import com.simon.harmonichackernews.ui.settings.SettingsPlatformStyle
import com.simon.harmonichackernews.ui.settings.AndroidUserSettingsDialog
import com.simon.harmonichackernews.ui.settings.AndroidWelcomeSettingsDialog
import com.simon.harmonichackernews.ui.submissions.AndroidSubmissionsCoordinator
import com.simon.harmonichackernews.ui.submissions.AndroidSubmissionsScreen
import com.simon.harmonichackernews.ui.stories.CacheStoriesDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.ProvideHarmonicUiDependencies
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.MainNavigationEntry
import com.simon.harmonichackernews.settings.AppLaunchDialog
import com.simon.harmonichackernews.utils.AndroidActivityTheme
import com.simon.harmonichackernews.widget.refreshStoryWidgets
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AndroidMainNavigationHost {
    @JvmStatic
    fun install(activity: MainActivity, savedState: Bundle?): AndroidMainNavigationController {
        val appComposition = activity.harmonicAppComposition
        val scene = ViewModelProvider(activity)[HarmonicSceneViewModel::class.java].scene
        val controller = AndroidMainNavigationController(
            scene,
            savedState,
        )
        when (
            appComposition.launchState.consumeLaunchDialog(
                currentVersion = appComposition.metadata.versionCode,
                showChangelog = appComposition.userSettings.general.showChangelog,
            )
        ) {
            AppLaunchDialog.WELCOME -> controller.showWelcomeDialog()
            AppLaunchDialog.CHANGELOG -> controller.showChangelogDialog()
            AppLaunchDialog.NONE -> Unit
        }
        val storiesCoordinator = AndroidStoriesCoordinator(
            activity = activity,
            savedInstanceState = savedState,
            navigation = controller,
        )
        controller.attachStoriesCoordinator(storiesCoordinator)
        storiesCoordinator.composeController?.let(controller::attachStoriesScreenController)
        val composeView = ComposeView(activity).apply {
            id = R.id.main_navigation_compose
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val themeSelection by appComposition.appearance.selections.collectAsState(
                    initial = appComposition.appearance.selection(),
                )
                ProvideHarmonicUiDependencies(
                    HarmonicUiDependencies(appComposition, scene),
                ) {
                    HarmonicTheme(
                        selection = themeSelection,
                    ) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalPredictiveBackCompletion provides remember { PredictiveBackCompletion() },
                        ) {
                            MainNavigation(
                                activity = activity,
                                controller = controller,
                            )
                        }
                    }
                }
            }
        }
        activity.setContentView(composeView)
        return controller
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun MainNavigation(
    activity: MainActivity,
    controller: AndroidMainNavigationController,
) {
    val navigationSnapshot by controller.navigationState.state.collectAsState()
    val backCompletion = LocalPredictiveBackCompletion.current
    // Include asynchronous feed population in launcher time-to-full-display. Other
    // destinations have their own loading lifecycle and must not wait for the hidden feed.
    ReportDrawnWhen {
        navigationSnapshot.currentDestination != MainDestination.STORIES ||
            controller.storiesComposeController?.let { stories ->
                stories.loadingFailed || stories.showEmptySavedList ||
                    (!stories.loading && stories.mainStories.any { it.loaded })
            } == true
    }
    val appearance = controller.scene.app.appearance
    val uiDependencies = LocalHarmonicUiDependencies.current
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val context = activity
    val supportsTwoPane = context.resources.configuration.smallestScreenWidthDp >= 600
    val hasHingeAngleSensor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    val isFoldable = adaptiveInfo.windowPosture.hingeList.isNotEmpty() || hasHingeAngleSensor
    val density = LocalDensity.current
    val tabletPaneSpacer = with(density) {
        context.resources.getDimensionPixelSize(R.dimen.margin_between_panes).toDp()
    }
    val tabletStoriesWeight = context.resources.getInteger(R.integer.stories_pane_weight)
    val directive = remember(adaptiveInfo, supportsTwoPane, isFoldable, tabletPaneSpacer) {
        val adaptiveDirective = calculatePaneScaffoldDirective(adaptiveInfo)
        adaptiveDirective.copy(
            maxHorizontalPartitions = if (supportsTwoPane) {
                adaptiveDirective.maxHorizontalPartitions
            } else {
                1
            },
            horizontalPartitionSpacerSize = if (isFoldable) 12.dp else tabletPaneSpacer,
        )
    }
    val isTwoPane = directive.maxHorizontalPartitions > 1
    SideEffect {
        controller.updateAdaptiveState(
            twoPane = isTwoPane,
            foldable = isTwoPane && isFoldable,
        )
        controller.updateStoriesHostDestination(navigationSnapshot.currentDestination)
        controller.updateCommentsHostDestination(navigationSnapshot.currentDestination)
    }
    val paneProportion = if (isFoldable) {
        0.5f
    } else {
        tabletStoriesWeight / (tabletStoriesWeight + LEGACY_COMMENTS_PANE_WEIGHT)
    }
    val backAnimationScope = rememberCoroutineScope()
    var activeBackAnimation by remember {
        mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    }
    var completedStoryPopStack by remember {
        mutableStateOf<List<MainNavigationEntry>?>(null)
    }
    // Completion belongs to this committed stack change, not every later story exit.
    // A subsequent button/hardware pop changes the stack and gets its own exit animation.
    val completedPredictivePop = completedStoryPopStack == navigationSnapshot.destinationStack
    LaunchedEffect(navigationSnapshot.destinationStack) {
        if (!completedPredictivePop) completedStoryPopStack = null
    }

    fun popMainBackStack() {
        if (controller.isExternalStoryEntry) {
            activity.finish()
        } else if (controller.navigationState.state.value.currentDestination == MainDestination.STORY) {
            controller.detailRemovedFromBackStack()
        } else {
            activity.finish()
        }
    }

    val storyParentDestination = navigationSnapshot.storyParentDestination
    val presentation = rememberMainNavigationPresentation(
        navigation = navigationSnapshot,
        isTwoPane = isTwoPane,
        completedStoryPredictiveBack = completedPredictivePop,
    )
    val scenePlan = presentation.scene
    val submissionsInTwoPane = scenePlan.submissionsInTwoPane
    val paneStatusBarColor = HarmonicTheme.colors.background
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(navigationSnapshot.closeRequest) {
        if (
            navigationSnapshot.closeRequest > 0 &&
            navigationSnapshot.currentDestination == MainDestination.STORY
        ) {
            popMainBackStack()
        }
    }

    PredictiveBackHandler(
        // Let Android own the return-to-caller gesture at an external entry. Nested web/history
        // navigation still consumes Back before reaching this boundary.
        enabled = navigationSnapshot.currentDestination == MainDestination.STORY &&
            (!controller.isExternalStoryEntry ||
                controller.getCommentsCoordinator()?.handlesBackInternally() == true),
    ) { events ->
        val storySerialAtGestureStart = controller.navigationState.state.value.storyRequest?.serial
        if (backCompletion.handleFollowingBack(events, activity.onBackPressedDispatcher)) {
            return@PredictiveBackHandler
        }
        fun popGestureStoryIfStillCurrent(completedGesture: Boolean = false) {
            if (controller.navigationState.state.value.storyRequest?.serial == storySerialAtGestureStart) {
                controller.detailRemovedFromBackStack()
                completedStoryPopStack = if (completedGesture) {
                    controller.navigationState.state.value.destinationStack
                } else {
                    null
                }
            }
        }
        val internalBackCoordinator = controller.getCommentsCoordinator()
            ?.takeIf(AndroidCommentsCoordinator::handlesBackInternally)
        if (internalBackCoordinator != null) {
            var predictiveBackStarted = false
            try {
                events.collect { event ->
                    if (predictiveBackStarted) {
                        internalBackCoordinator.updateInternalPredictiveBack(event)
                    } else {
                        predictiveBackStarted = true
                        internalBackCoordinator.startInternalPredictiveBack(event)
                    }
                }
                internalBackCoordinator.commitInternalBack()
            } catch (_: CancellationException) {
                if (predictiveBackStarted) {
                    internalBackCoordinator.cancelInternalPredictiveBack()
                }
            }
            return@PredictiveBackHandler
        }

        if (scenePlan.storyUsesTwoPane) {
            var frozenWebViewCoordinator: AndroidCommentsCoordinator? = null
            try {
                events.collect {
                    val currentFrozenCoordinator = frozenWebViewCoordinator
                    if (currentFrozenCoordinator == null) {
                        controller.getCommentsCoordinator()
                            ?.takeIf { it.beginWebViewPredictiveBack() }
                            ?.let { frozenWebViewCoordinator = it }
                    } else {
                        currentFrozenCoordinator
                            .maintainWebViewPredictiveBack()
                    }
                }
                popGestureStoryIfStillCurrent()
            } catch (_: CancellationException) {
                // A cancelled two-pane gesture keeps the current detail selected.
            } finally {
                frozenWebViewCoordinator?.endWebViewPredictiveBack()
            }
            return@PredictiveBackHandler
        }

        var animation: DefaultActivityPredictiveBackAnimation? = null
        var frozenWebViewCoordinator: AndroidCommentsCoordinator? = null
        try {
            events.collect { event ->
                val currentFrozenCoordinator = frozenWebViewCoordinator
                if (currentFrozenCoordinator == null) {
                    controller.getCommentsCoordinator()
                        ?.takeIf { it.beginWebViewPredictiveBack() }
                        ?.let { frozenWebViewCoordinator = it }
                } else {
                    currentFrozenCoordinator.maintainWebViewPredictiveBack()
                }
                val currentAnimation = animation
                    ?: DefaultActivityPredictiveBackAnimation(event).also {
                        animation = it
                        activeBackAnimation = it
                    }
                backAnimationScope.launch {
                    currentAnimation.animate(event)
                }
            }

            val currentAnimation = animation
            if (currentAnimation == null) {
                popGestureStoryIfStillCurrent()
                return@PredictiveBackHandler
            }
            val retainedWebView = frozenWebViewCoordinator
            frozenWebViewCoordinator = null
            backCompletion.finish(
                scope = backAnimationScope,
                animation = currentAnimation,
                frameHoldCount = 3,
                onCommit = {
                    popGestureStoryIfStillCurrent(completedGesture = true)
                },
                onFinished = {
                    if (activeBackAnimation === currentAnimation) activeBackAnimation = null
                    retainedWebView?.endWebViewPredictiveBack()
                },
            )
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                animation?.cancel()
                if (activeBackAnimation === animation) activeBackAnimation = null
            }
        } finally {
            frozenWebViewCoordinator?.endWebViewPredictiveBack()
        }
    }

    val settingsRequest = navigationSnapshot.settingsRequest
    fun closeSettings() {
        controller.closeSettings()
        controller.applySettingsChanges()
    }

    val settingsPredictiveBack = rememberDefaultActivityPredictiveBackState(
        requestKey = settingsRequest?.serial,
        enabled = navigationSnapshot.currentDestination == MainDestination.SETTINGS,
        completedFrameHoldCount = 3,
        onBack = ::closeSettings,
    )

    val submissionsRequest = navigationSnapshot.submissionsRequest
    val submissionsPredictiveBack = rememberDefaultActivityPredictiveBackState(
        requestKey = submissionsRequest?.serial,
        enabled = navigationSnapshot.currentDestination == MainDestination.SUBMISSIONS,
        onBack = controller::closeSubmissions,
    )
    // Capture the owner at gesture start: a nested visit animates inside the Submissions
    // stack, while the first visit animates against the underlying main destination.
    val nestedSubmissionsGesture = remember(submissionsPredictiveBack.animation) {
        submissionsInTwoPane && scenePlan.submissionsScenes.size > 1
    }

    val editorRequest = navigationSnapshot.editorRequest
    var editorPredictiveBackEnabled by remember(editorRequest?.serial) {
        mutableStateOf(false)
    }
    val editorPredictiveBack = rememberDefaultActivityPredictiveBackState(
        requestKey = editorRequest?.serial,
        enabled = navigationSnapshot.currentDestination == MainDestination.EDITOR &&
            editorPredictiveBackEnabled,
        completedFrameHoldCount = 3,
        onBack = controller::closeEditor,
    )

    PredictiveBackHandler(
        enabled = navigationSnapshot.currentDestination == MainDestination.IMMERSIVE,
    ) { events ->
        try {
            events.collect { }
            controller.closeCoulombGas()
        } catch (_: CancellationException) {
            // A cancelled gesture leaves the simulation open.
        }
    }

    val storiesController = controller.storiesComposeController
    PredictiveBackHandler(
        // Let the preview's Back callback dismiss the overlay before leaving search.
        enabled = storiesController?.searching == true &&
            !storiesController.isStoryPreviewShowing() &&
            navigationSnapshot.currentDestination == MainDestination.STORIES,
    ) { events ->
        val searchController = storiesController ?: return@PredictiveBackHandler
        var started = false
        try {
            events.collect { event ->
                if (started) {
                    searchController.updateSearchBack(event.progress)
                } else {
                    started = searchController.startSearchBack(event.progress)
                }
            }
            searchController.finishSearchBack()
        } catch (_: CancellationException) {
            if (started) searchController.cancelSearchBack()
        }
    }

    val settingsTransitionOffsetPx = with(LocalDensity.current) { 96.dp.roundToPx() }
    fun parentBackModifier(destination: MainDestination): Modifier {
        var modifier: Modifier = Modifier
        if (navigationSnapshot.parentDestination(MainDestination.SETTINGS) == destination) {
            modifier = modifier.then(settingsPredictiveBack.enterModifier)
        }
        if (!nestedSubmissionsGesture &&
            navigationSnapshot.parentDestination(MainDestination.SUBMISSIONS) == destination
        ) {
            modifier = modifier.then(submissionsPredictiveBack.enterModifier)
        }
        if (navigationSnapshot.parentDestination(MainDestination.EDITOR) == destination) {
            modifier = modifier.then(editorPredictiveBack.enterModifier)
        }
        return modifier
    }
    HarmonicAppRoot(
        navigation = navigationSnapshot,
        transitionOffsetPx = settingsTransitionOffsetPx,
        completedSettingsPredictiveBack = settingsPredictiveBack.completed,
        completedSubmissionsPredictiveBack = submissionsPredictiveBack.completed,
        completedEditorPredictiveBack = editorPredictiveBack.completed,
        completedStoryPredictiveBack = completedPredictivePop,
        storyExitInProgress = presentation.storyExitInProgress,
        submissionsInTwoPane = submissionsInTwoPane,
        modifier = Modifier.background(HarmonicTheme.colors.background)
            .semantics { testTagsAsResourceId = true },
        basePredictiveModifier = parentBackModifier(MainDestination.STORIES)
            .then(parentBackModifier(MainDestination.STORY)),
        settingsPredictiveModifier = settingsPredictiveBack.exitModifier
            .then(parentBackModifier(MainDestination.SETTINGS))
            .then(
                if (storyParentDestination == MainDestination.SETTINGS) {
                    activeBackAnimation?.enterModifier ?: Modifier
                } else {
                    Modifier
                },
            ),
        submissionsPredictiveModifier = (if (nestedSubmissionsGesture) Modifier else submissionsPredictiveBack.exitModifier)
            .then(parentBackModifier(MainDestination.SUBMISSIONS)).then(
            if (!submissionsInTwoPane && storyParentDestination == MainDestination.SUBMISSIONS) {
                activeBackAnimation?.enterModifier ?: Modifier
            } else {
                Modifier
            },
        ),
        editorPredictiveModifier = editorPredictiveBack.exitModifier,
        base = {
            if (presentation.renderTwoPaneStoryScene) {
                MainNavigationScene(
                    storyRequest = scenePlan.baseStoryRequest,
                    directive = directive,
                    paneProportion = paneProportion,
                    isFoldable = isFoldable,
                    onBack = ::popMainBackStack,
                    stories = {
                        StoriesPane(
                            controller = controller,
                            statusBarColor = paneStatusBarColor,
                            statusBarHeight = statusBarHeight,
                            drawStatusBarProtection = true,
                        )
                    },
                    emptyDetail = { EmptyCommentsScreen() },
                    comments = { request ->
                        CommentsPane(
                            request = request,
                            controller = controller,
                            showUpButton = false,
                            statusBarHeight = statusBarHeight,
                            drawStatusBarProtection = true,
                        )
                    },
                )
            } else {
                SinglePaneNavigationScene(
                    scene = scenePlan,
                    onStoryLayersEmpty = presentation.onStoryLayersEmpty,
                    completedPredictivePop = completedPredictivePop,
                    predictiveBackActive = activeBackAnimation != null,
                    storiesPredictiveModifier = if (
                        storyParentDestination == MainDestination.STORIES ||
                        storyParentDestination == MainDestination.STORY
                    ) {
                        activeBackAnimation?.enterModifier ?: Modifier
                    } else {
                        Modifier
                    },
                    commentsPredictiveModifier = activeBackAnimation?.exitModifier ?: Modifier,
                    stories = {
                        StoriesPane(
                            controller = controller,
                            statusBarColor = paneStatusBarColor,
                            statusBarHeight = statusBarHeight,
                            drawStatusBarProtection = true,
                        )
                    },
                    comments = { request ->
                        CommentsPane(
                            request = request,
                            controller = controller,
                            showUpButton = true,
                            statusBarHeight = statusBarHeight,
                            drawStatusBarProtection = true,
                        )
                    },
                )
            }
        },
        settings = {
            if (
                settingsRequest != null ||
                !settingsPredictiveBack.completed
            ) {
                navigationSnapshot.lastSettingsRequest?.let { request ->
                    key(request.serial) {
                        ProvideSettingsPlatformStyle(
                            style = SettingsPlatformStyle(
                                topBarHeight = dimensionResource(
                                    R.dimen.compose_settings_toolbar_height,
                                ),
                                topBarNavigationHeight = dimensionResource(
                                    R.dimen.detail_toolbar_navigation_height,
                                ),
                                topBarNavigationInset = dimensionResource(
                                    R.dimen.detail_toolbar_navigation_inset,
                                ),
                                textStyle = TextStyle(
                                    platformStyle = PlatformTextStyle(
                                        includeFontPadding = true,
                                    ),
                                ),
                            ),
                        ) {
                            SettingsShell(
                                initialSection = controller
                                    .getInitialSettingsSectionRoute(request)
                                    ?.let(SettingsSection::fromRoute),
                                onBackFromSettings = ::closeSettings,
                                backHandlerEnabled = navigationSnapshot.currentDestination ==
                                    MainDestination.SETTINGS,
                                onSectionChanged = controller::updateSettingsSection,
                                onThemeChanged = {
                                    AndroidActivityTheme.setupTheme(activity)
                                    appearance.refreshSelection()
                                    refreshStoryWidgets(activity, reloadStories = false)
                                },
                            )
                        }
                    }
                }
            }
        },
        submissions = {
            val submissionsScene: @Composable (MainSubmissionsScene) -> Unit = { scene ->
                val request = scene.request
                key(request.serial) {
                    val coordinator = remember(request.serial) {
                        AndroidSubmissionsCoordinator(
                            activity = activity,
                            sessionKey = request.serial,
                            userName = request.userName,
                            scene = controller.scene,
                            navigator = AndroidSubmissionsCoordinator.Navigator { destination ->
                                controller.openSubmissionStory(destination)
                            },
                        )
                    }
                    DisposableEffect(coordinator) {
                        onDispose(coordinator::close)
                    }
                    val submissionsContent: @Composable () -> Unit = {
                        val startInset = animatedExtraPanePadding()
                        Box(Modifier.fillMaxSize().padding(start = startInset)) {
                            AndroidSubmissionsScreen(
                                userName = coordinator.userName,
                                store = coordinator.store,
                                displaySettings = coordinator.displaySettings,
                                initialScrollRestoration = coordinator.initialScrollRestoration,
                                onBack = controller::closeSubmissions,
                            )
                            StatusBarProtection(
                                color = paneStatusBarColor,
                                statusBarHeight = statusBarHeight,
                            )
                        }
                    }
                    if (isTwoPane) {
                        MainNavigationScene(
                            storyRequest = scene.storyRequest,
                            directive = directive,
                            paneProportion = paneProportion,
                            isFoldable = isFoldable,
                            onBack = ::popMainBackStack,
                            stories = submissionsContent,
                            emptyDetail = { EmptyCommentsScreen() },
                            modifier = Modifier.background(HarmonicTheme.colors.background),
                            comments = { detail ->
                                CommentsPane(
                                    request = detail,
                                    controller = controller,
                                    showUpButton = false,
                                    statusBarHeight = statusBarHeight,
                                    drawStatusBarProtection = true,
                                )
                            },
                        )
                    } else {
                        submissionsContent()
                    }
                }
            }
            if (isTwoPane) {
                SubmissionsNavigationStack(
                    scenes = scenePlan.submissionsScenes,
                    predictiveBackActive = submissionsPredictiveBack.animation != null,
                    completedPredictiveBack = submissionsPredictiveBack.completed,
                    enterModifier = submissionsPredictiveBack.enterModifier,
                    exitModifier = submissionsPredictiveBack.exitModifier,
                    content = submissionsScene,
                )
            } else if (submissionsRequest != null || !submissionsPredictiveBack.completed) {
                (submissionsRequest ?: navigationSnapshot.lastSubmissionsRequest)?.let { request ->
                    submissionsScene(MainSubmissionsScene(request, null))
                }
            }
        },
        editor = {
            if (editorRequest != null || !editorPredictiveBack.completed) {
                navigationSnapshot.lastEditorRequest?.let { request ->
                    key(request.serial) {
                        Box(Modifier.fillMaxSize()) {
                            // The editor is a modal sibling of the story navigation. Its opaque
                            // surface is not itself a pointer target, so keep an explicit barrier
                            // behind it to prevent taps in field gutters reaching the story layer.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            do {
                                                val event = awaitPointerEvent()
                                                event.changes.forEach { it.consume() }
                                            } while (event.changes.any { it.pressed })
                                        }
                                    },
                            )

                            val editorController = remember(request.serial) {
                                EditorSubmissionState()
                            }
                            val coordinator = remember(request.serial) {
                                AndroidEditorCoordinator(
                                    activity,
                                    request.destination,
                                    controller,
                                    controller::closeEditor,
                                )
                            }
                            DisposableEffect(coordinator) {
                                onDispose(coordinator::close)
                            }
                            SideEffect {
                                coordinator.attachController(editorController)
                            }
                            ComposeEditorScreen(
                                type = coordinator.type,
                                parentText = coordinator.parentText,
                                postTitle = coordinator.postTitle,
                                user = coordinator.user,
                                submitting = editorController.submitting,
                                onPredictiveBackEnabledChanged = {
                                    editorPredictiveBackEnabled = it
                                },
                                onClose = controller::closeEditor,
                                onSubmit = coordinator::submit,
                                onOpenLink = { uiDependencies.links.open(it) },
                            )
                        }
                    }
                }
            }
        },
        immersive = {
            DisposableEffect(activity) {
                activity.setImmersiveContentEnabled(true)
                onDispose {
                    activity.setImmersiveContentEnabled(false)
                }
            }
            CoulombGasScreen()
        },
        foreground = {
            if (navigationSnapshot.welcomeDialogVisible) {
            AndroidWelcomeSettingsDialog(
                styleChooser = false,
                onDismiss = controller::dismissWelcomeDialog,
            )
        }

        if (navigationSnapshot.changelogDialogVisible) {
            SettingsChangelogDialog(
                onDismiss = controller::dismissChangelogDialog,
                onOpenGithub = {
                    uiDependencies.links.open(uiDependencies.metadata.projectUrl)
                },
            )
        }

        if (navigationSnapshot.cacheStoriesDialogVisible) {
            CacheStoriesDialog(
                initialStoryCount = uiDependencies.userSettings
                    .cache.storiesToCache,
                integratedWebView = uiDependencies.userSettings.reading.integratedWebView,
                onDismiss = controller::dismissCacheStoriesDialog,
                onConfirm = controller::confirmCacheStories,
            )
        }

        if (navigationSnapshot.loginDialogVisible) {
            AndroidLoginDialog(
                onDismiss = controller::dismissLoginDialog,
            )
        }

        navigationSnapshot.captchaRequest?.let { request ->
            key(request.serial) {
                CaptchaDialog(
                    challenge = request.challenge,
                    onDismiss = controller::dismissCaptchaDialog,
                    onCaptchaResponse = controller::completeCaptchaDialog,
                )
            }
        }

        navigationSnapshot.userRequest?.let { request ->
            key(request.serial) {
                AndroidUserSettingsDialog(
                    userName = request.userName,
                    onDismiss = controller::dismissUserDialog,
                    onTagChanged = controller::notifyUserTagChanged,
                )
            }
        }

        navigationSnapshot.failureRequest?.let { request ->
            key(request.serial) {
                FailureDetailDialog(
                    title = request.title,
                    message = request.message,
                    showCopyComment = request.clipboardText != null,
                    onCopyComment = {
                        request.clipboardText?.let { text ->
                            uiDependencies.platform.clipboard.copy("Hacker News comment", text)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                controller.showMessage("Comment copied to clipboard")
                            }
                        }
                        controller.dismissFailureDetailDialog()
                    },
                    onDismiss = controller::dismissFailureDetailDialog,
                )
            }
        }

            UserMessageSnackbarHost(
                messages = uiDependencies.userMessages,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(100f),
            )
        },
    )
}
