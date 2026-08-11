package com.simon.harmonichackernews.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.rememberNavigationEventState
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.ui.about.AboutScreen
import com.simon.harmonichackernews.ui.licenses.LicensesScreen
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data object SettingsListDestination : NavKey

private data class SettingsDetailDestination(
    val section: SettingsSection,
) : NavKey

private const val SettingsListSavedRoute = "__settings_list__"

private const val ActivityTransitionDurationMillis = 450
private const val ActivityEnterAlphaDelayMillis = 50
private const val ActivityExitAlphaDelayMillis = 35
private const val ActivityAlphaDurationMillis = 83
private const val DetailTransitionDurationMillis = 300
private const val DetailAlphaDelayMillis = 50
private const val DetailAlphaDurationMillis = 50

private fun aospFastOutExtraSlowInEasing(): Easing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
)

private fun activityOpenTransition(offsetPx: Int): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        animationSpec = tween(
            durationMillis = ActivityTransitionDurationMillis,
            easing = aospFastOutExtraSlowInEasing(),
        ),
        initialOffsetX = { offsetPx },
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = ActivityAlphaDurationMillis,
            delayMillis = ActivityEnterAlphaDelayMillis,
            easing = LinearEasing,
        ),
    ),
    initialContentExit = slideOutHorizontally(
        animationSpec = tween(
            durationMillis = ActivityTransitionDurationMillis,
            easing = aospFastOutExtraSlowInEasing(),
        ),
        targetOffsetX = { -offsetPx },
    ),
    targetContentZIndex = 1f,
)

private fun activityPopTransition(offsetPx: Int): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        animationSpec = tween(
            durationMillis = ActivityTransitionDurationMillis,
            easing = aospFastOutExtraSlowInEasing(),
        ),
        initialOffsetX = { -offsetPx },
    ),
    initialContentExit = slideOutHorizontally(
        animationSpec = tween(
            durationMillis = ActivityTransitionDurationMillis,
            easing = aospFastOutExtraSlowInEasing(),
        ),
        targetOffsetX = { offsetPx },
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = ActivityAlphaDurationMillis,
            delayMillis = ActivityExitAlphaDelayMillis,
            easing = LinearEasing,
        ),
    ),
    targetContentZIndex = -1f,
)

/** Preserves the legacy two-pane detail transition timing. */
private fun detailOpenTransition(): ContentTransform = ContentTransform(
    targetContentEnter = scaleIn(
        animationSpec = tween(
            durationMillis = DetailTransitionDurationMillis,
            easing = aospFastOutExtraSlowInEasing(),
        ),
        initialScale = 0.85f,
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = DetailAlphaDurationMillis,
            delayMillis = DetailAlphaDelayMillis,
            easing = LinearEasing,
        ),
    ),
    initialContentExit = scaleOut(
        animationSpec = tween(
            durationMillis = DetailTransitionDurationMillis,
            easing = aospFastOutExtraSlowInEasing(),
        ),
        targetScale = 1.15f,
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = DetailAlphaDurationMillis,
            delayMillis = DetailAlphaDelayMillis,
            easing = LinearEasing,
        ),
    ),
    targetContentZIndex = 1f,
)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingsShell(
    initialSection: SettingsSection?,
    onBackFromSettings: () -> Unit,
    onSectionChanged: (SettingsSection) -> Unit,
    onThemeChanged: () -> Unit,
    onRequestRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val configuration = LocalConfiguration.current
    val supportsTwoPane = configuration.smallestScreenWidthDp >= 600
    val hasHingeAngleSensor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    val isFoldable = adaptiveInfo.windowPosture.hingeList.isNotEmpty() || hasHingeAngleSensor
    val directive = remember(adaptiveInfo, supportsTwoPane, isFoldable) {
        val adaptiveDirective = calculatePaneScaffoldDirective(adaptiveInfo)
        adaptiveDirective.copy(
            maxHorizontalPartitions = if (supportsTwoPane) {
                adaptiveDirective.maxHorizontalPartitions
            } else {
                1
            },
            horizontalPartitionSpacerSize = if (isFoldable) 12.dp else 16.dp,
        )
    }
    val isTwoPane = directive.maxHorizontalPartitions > 1
    val paneProportion = if (isFoldable) 0.5f else 0.4f
    val paneExpansionState = rememberPaneExpansionState(
        anchors = remember(paneProportion) {
            listOf(PaneExpansionAnchor.Proportion(paneProportion))
        },
        initialAnchoredIndex = 0,
    )
    val sceneStrategy = rememberListDetailSceneStrategy<NavKey>(
        directive = directive,
        paneExpansionState = paneExpansionState.takeIf { isTwoPane },
    )
    val showDetailNavigation = !isTwoPane
    val tabletPaneHorizontalPadding = if (isTwoPane && !isFoldable) {
        dimensionResource(R.dimen.settings_extra_pane_padding)
    } else {
        0.dp
    }
    val backStack = rememberSaveable(
        saver = listSaver(
            save = { stack ->
                stack.map { destination ->
                    when (destination) {
                        SettingsListDestination -> SettingsListSavedRoute
                        is SettingsDetailDestination -> destination.section.route
                        else -> error("Unknown settings destination: $destination")
                    }
                }
            },
            restore = { savedRoutes ->
                mutableStateListOf<NavKey>().apply {
                    savedRoutes.forEach { route ->
                        if (route == SettingsListSavedRoute) {
                            add(SettingsListDestination)
                        } else {
                            SettingsSection.fromRoute(route)?.let { section ->
                                add(SettingsDetailDestination(section))
                            }
                        }
                    }
                    if (isEmpty()) add(SettingsListDestination)
                    if (isTwoPane && lastOrNull() !is SettingsDetailDestination) {
                        add(SettingsDetailDestination(SettingsSection.Appearance))
                    }
                }
            },
        ),
    ) {
        mutableStateListOf<NavKey>().apply {
            add(SettingsListDestination)
            val initialDetailSection = initialSection ?: if (isTwoPane) {
                SettingsSection.Appearance
            } else {
                null
            }
            initialDetailSection?.let { add(SettingsDetailDestination(it)) }
        }
    }
    val selectedSection =
        (backStack.lastOrNull() as? SettingsDetailDestination)?.section
            ?: SettingsSection.Appearance
    val detailPaneTransition = updateTransition(
        targetState = selectedSection,
        label = "Settings detail pane",
    )

    LaunchedEffect(selectedSection) {
        onSectionChanged(selectedSection)
    }

    val currentShowDetailNavigation by rememberUpdatedState(showDetailNavigation)
    val activityTransitionOffsetPx = with(LocalDensity.current) { 96.dp.roundToPx() }
    val backAnimationScope = rememberCoroutineScope()
    var activeBackAnimation by remember {
        mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    }
    var isBackAnimationRunning by remember { mutableStateOf(false) }

    fun navigateTo(
        section: SettingsSection,
        preserveCurrentDetail: Boolean = false,
    ) {
        if ((backStack.lastOrNull() as? SettingsDetailDestination)?.section == section) {
            return
        }
        if (!preserveCurrentDetail) {
            while (backStack.lastOrNull() is SettingsDetailDestination) {
                backStack.removeLastOrNull()
            }
        }
        backStack.add(SettingsDetailDestination(section))
    }

    fun popSettingsBackStack() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            onBackFromSettings()
        }
    }

    fun navigateBack() {
        if (backStack.size <= 1) {
            onBackFromSettings()
            return
        }
        if (isBackAnimationRunning) return
        popSettingsBackStack()
    }

    PredictiveBackHandler(
        enabled = (showDetailNavigation && backStack.size > 1) ||
            (!showDetailNavigation && backStack.size > 2),
    ) { events ->
        if (!showDetailNavigation) {
            try {
                events.collect {}
                popSettingsBackStack()
            } catch (_: CancellationException) {
                // A cancelled two-pane gesture leaves the selected detail unchanged.
            }
            return@PredictiveBackHandler
        }

        isBackAnimationRunning = true
        var animation: DefaultActivityPredictiveBackAnimation? = null

        try {
            events.collect { event ->
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
                isBackAnimationRunning = false
                popSettingsBackStack()
                return@PredictiveBackHandler
            }
            currentAnimation.finish()
            popSettingsBackStack()
            activeBackAnimation = null
            isBackAnimationRunning = false
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                animation?.cancel()
                if (activeBackAnimation === animation) {
                    activeBackAnimation = null
                }
                isBackAnimationRunning = false
            }
        }
    }

    LaunchedEffect(initialSection) {
        initialSection?.let { navigateTo(it) }
    }

    LaunchedEffect(isTwoPane) {
        if (isTwoPane && backStack.lastOrNull() !is SettingsDetailDestination) {
            backStack.add(SettingsDetailDestination(SettingsSection.Appearance))
        }
    }

    val settingsEntryProvider = entryProvider<NavKey> {
        entry<SettingsListDestination>(
            metadata = ListDetailSceneStrategy.listPane(
                detailPlaceholder = {
                    AppearanceSettingsScreen(
                        showNavigation = false,
                        onBack = ::navigateBack,
                        onNavigate = { navigateTo(it) },
                        onThemeChanged = onThemeChanged,
                    )
                },
            ),
        ) {
            SettingsListScreen(
                selectedSection = selectedSection,
                showSelection = !currentShowDetailNavigation,
                showDebugSettings = BuildConfig.DEBUG_SETTINGS_ENABLED,
                onBack = onBackFromSettings,
                onSectionSelected = { navigateTo(it) },
            )
        }

        entry<SettingsDetailDestination>(
            metadata = ListDetailSceneStrategy.detailPane(),
        ) { destination ->
            val singlePane = currentShowDetailNavigation
            val detailContent: @Composable (SettingsSection) -> Unit = { section ->
                when (section) {
                    SettingsSection.Appearance -> AppearanceSettingsScreen(
                        showNavigation = singlePane,
                        onBack = ::navigateBack,
                        onNavigate = { section ->
                            navigateTo(
                                section = section,
                                preserveCurrentDetail = singlePane,
                            )
                        },
                        onThemeChanged = onThemeChanged,
                    )

                    SettingsSection.Stories -> StoriesSettingsScreen(
                        showNavigation = singlePane,
                        onBack = ::navigateBack,
                        onRequestRestart = onRequestRestart,
                    )

                    SettingsSection.Comments -> CommentsSettingsScreen(
                        showNavigation = singlePane,
                        onBack = ::navigateBack,
                    )

                    SettingsSection.WebLinks -> WebLinksSettingsScreen(
                        showNavigation = singlePane,
                        onBack = ::navigateBack,
                    )

                    SettingsSection.FiltersTags -> FiltersTagsSettingsScreen(
                        showNavigation = singlePane,
                        onBack = ::navigateBack,
                    )

                    SettingsSection.AiSummary -> AiSummarySettingsScreen(
                        showNavigation = singlePane,
                        onBack = ::navigateBack,
                    )

                    SettingsSection.Data -> DataSettingsScreen(
                        showNavigation = singlePane,
                        onBack = ::navigateBack,
                        onRequestRestart = onRequestRestart,
                    )

                    SettingsSection.Debug -> DebugSettingsScreen(
                        showNavigation = singlePane,
                        onBack = ::navigateBack,
                    )

                    SettingsSection.About -> AboutScreen(
                        onBack = ::navigateBack,
                        onOpenGithub = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/SimonHalvdansson/Harmonic-HN".toUri(),
                                ),
                            )
                        },
                        onOpenChangelog = {
                            ChangelogDialogController.show()
                        },
                        onOpenLicenses = {
                            backStack.add(
                                SettingsDetailDestination(SettingsSection.Licenses),
                            )
                        },
                        onOpenPrivacy = {
                            Utils.launchCustomTab(
                                context,
                                "https://simonhalvdansson.github.io/harmonic_privacy.html",
                            )
                        },
                        showNavigation = singlePane,
                        singlePane = singlePane,
                    )

                    SettingsSection.Licenses -> LicensesScreen(
                        onBack = ::navigateBack,
                        onOpenLicense = { url ->
                            Utils.launchCustomTab(
                                context,
                                url,
                            )
                        },
                        singlePane = singlePane,
                    )
                }
            }

            if (singlePane) {
                detailContent(destination.section)
            } else {
                detailPaneTransition.AnimatedContent(
                    transitionSpec = { detailOpenTransition() },
                    content = { section ->
                        detailContent(section)
                    },
                )
            }
        }
    }
    val decoratedEntries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = settingsEntryProvider,
    )
    val sceneState = rememberSceneState(
        entries = decoratedEntries,
        sceneStrategies = listOf(sceneStrategy),
        onBack = ::navigateBack,
    )
    val navDisplayEventState = rememberNavigationEventState(
        currentInfo = SceneInfo(sceneState.currentScene),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background),
    ) {
        val animation = activeBackAnimation
        val previousScene = sceneState.previousScenes.lastOrNull()

        if (showDetailNavigation && animation != null && previousScene != null) {
            key(previousScene.key) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(animation.enterModifier),
                ) {
                    previousScene.content()
                }
            }
            key(sceneState.currentScene.key) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(animation.exitModifier),
                ) {
                    sceneState.currentScene.content()
                }
            }
            Box(
                modifier = Modifier
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
        } else {
            NavDisplay(
                sceneState = sceneState,
                navigationEventState = navDisplayEventState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = tabletPaneHorizontalPadding),
                transitionSpec = {
                    activityOpenTransition(activityTransitionOffsetPx)
                },
                popTransitionSpec = {
                    activityPopTransition(activityTransitionOffsetPx)
                },
                predictivePopTransitionSpec = { _ ->
                    activityPopTransition(activityTransitionOffsetPx)
                },
            )
        }
    }

    SimpleMessageDialogController.Content()
    ChangelogDialogController.Content()
}
