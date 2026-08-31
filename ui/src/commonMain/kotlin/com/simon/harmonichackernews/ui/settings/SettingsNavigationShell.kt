package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.rememberNavigationEventState
import com.simon.harmonichackernews.ui.common.consumeAllPointerGestures
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationTransitionDurationMillis
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationTransitionOffset
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationTransitionViewport
import com.simon.harmonichackernews.ui.navigation.activityNavigationEasing
import com.simon.harmonichackernews.ui.navigation.activityNavigationOpenContentTransform
import com.simon.harmonichackernews.ui.navigation.activityNavigationPopContentTransform
import com.simon.harmonichackernews.ui.navigation.paneDetailSwitchTransition
import kotlinx.coroutines.flow.first

private data object SettingsListDestination : NavKey

private data object SettingsTwoPaneDetailDestination : NavKey

private data class SettingsDetailDestination(val section: SettingsSection) : NavKey

private class RetainedSettingsLayer(
    val section: SettingsSection,
    initiallyVisible: Boolean,
) {
    val visibility = MutableTransitionState(initiallyVisible).apply {
        targetState = true
    }
    var predictiveExitCompleted by mutableStateOf(false)
}

data class SettingsPredictiveBackOverlay(
    val enterModifier: Modifier,
    val exitModifier: Modifier,
    val sourceSection: SettingsSection,
    val parentSection: SettingsSection?,
)

@Composable
fun rememberSettingsNavigationStore(
    initialSection: SettingsSection?,
    twoPane: Boolean,
): SettingsNavigationStore = rememberSaveable(
    saver = Saver<SettingsNavigationStore, List<String>>(
        save = { it.savedRoutes() },
        restore = { SettingsNavigationStore(twoPane = twoPane, restoredRoutes = it) },
    ),
) {
    SettingsNavigationStore(initialSection = initialSection, twoPane = twoPane)
}

/**
 * KMP list/detail settings scene. A host supplies native settings adapters and optional Android
 * predictive-back modifiers, while stack ownership, restoration and adaptive scene layout stay
 * shared.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingsNavigationShell(
    navigation: SettingsNavigationStore,
    directive: PaneScaffoldDirective,
    isFoldable: Boolean,
    tabletPaneHorizontalPadding: Dp,
    onBackFromSettings: () -> Unit,
    onSectionChanged: (SettingsSection) -> Unit,
    renderList: @Composable (
        selectedSection: SettingsSection,
        showSelection: Boolean,
        onBack: () -> Unit,
        onSectionSelected: (SettingsSection) -> Unit,
    ) -> Unit,
    renderDetail: @Composable (
        section: SettingsSection,
        singlePane: Boolean,
        onBack: () -> Unit,
        onNavigate: (SettingsSection, Boolean) -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
    predictiveBackOverlay: SettingsPredictiveBackOverlay? = null,
    completedPredictiveBack: Boolean = false,
    animateDetailChanges: Boolean = true,
) {
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
    val navigationState by navigation.state.collectAsState()
    val showDetailNavigation = !isTwoPane
    val selectedSection = navigationState.selectedSection
    val backStack = remember(navigationState.detailStack, isTwoPane) {
        buildList<NavKey> {
            add(SettingsListDestination)
            if (isTwoPane) {
                add(SettingsTwoPaneDetailDestination)
            } else {
                navigationState.detailStack.forEach { add(SettingsDetailDestination(it)) }
            }
        }
    }
    val detailPaneTransition = updateTransition(selectedSection, label = "Settings detail pane")

    fun navigateTo(section: SettingsSection, preserveCurrentDetail: Boolean = false) {
        navigation.navigateTo(section, preserveCurrentDetail)
    }

    fun navigateBack() {
        if (!navigation.navigateBack()) onBackFromSettings()
    }

    // An empty one-pane stack represents the settings list. `selectedSection` intentionally has
    // an Appearance fallback for list highlighting and the two-pane placeholder, but that fallback
    // is not a real navigation destination and must not be persisted. Persisting it immediately
    // pushes Appearance again and makes Up from every detail land there instead of on the list.
    LaunchedEffect(navigationState.detailStack) {
        navigationState.detailStack.lastOrNull()?.let(onSectionChanged)
    }
    LaunchedEffect(isTwoPane) { navigation.updateLayout(isTwoPane) }

    val provider = entryProvider<NavKey> {
        entry<SettingsListDestination>(
            metadata = ListDetailSceneStrategy.listPane(
                detailPlaceholder = {
                    renderDetail(
                        SettingsSection.Appearance,
                        false,
                        ::navigateBack,
                        ::navigateTo,
                    )
                },
            ),
        ) {
            renderList(selectedSection, !showDetailNavigation, onBackFromSettings, ::navigateTo)
        }
        entry<SettingsTwoPaneDetailDestination>(
            metadata = ListDetailSceneStrategy.detailPane(),
        ) {
            if (!animateDetailChanges) {
                renderDetail(selectedSection, false, ::navigateBack, ::navigateTo)
            } else {
                detailPaneTransition.AnimatedContent(
                    transitionSpec = { paneDetailSwitchTransition() },
                ) { section ->
                    renderDetail(section, false, ::navigateBack, ::navigateTo)
                }
            }
        }
        entry<SettingsDetailDestination>(
            metadata = ListDetailSceneStrategy.detailPane(),
        ) { destination ->
            renderDetail(destination.section, true, ::navigateBack, ::navigateTo)
        }
    }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = provider,
    )
    val sceneState = rememberSceneState(
        entries = entries,
        sceneStrategies = listOf(sceneStrategy),
        onBack = ::navigateBack,
    )
    val navigationEventState = rememberNavigationEventState(
        currentInfo = SceneInfo(sceneState.currentScene),
    )
    val transitionOffsetPx = with(LocalDensity.current) {
        ActivityNavigationTransitionOffset.roundToPx()
    }

    Box(
        modifier = modifier.fillMaxSize().background(settingsPageBackgroundColor()),
    ) {
        if (showDetailNavigation) {
            SinglePaneSettingsNavigation(
                detailStack = navigationState.detailStack,
                selectedSection = selectedSection,
                onBackFromSettings = onBackFromSettings,
                onNavigateBack = ::navigateBack,
                onNavigateTo = ::navigateTo,
                renderList = renderList,
                renderDetail = renderDetail,
                predictiveBackOverlay = predictiveBackOverlay,
                completedPredictiveBack = completedPredictiveBack,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            NavDisplay(
                sceneState = sceneState,
                navigationEventState = navigationEventState,
                modifier = Modifier.fillMaxSize().padding(horizontal = tabletPaneHorizontalPadding),
                transitionSpec = {
                    activityNavigationOpenContentTransform(transitionOffsetPx)
                },
                popTransitionSpec = {
                    activityNavigationPopContentTransform(transitionOffsetPx)
                },
                predictivePopTransitionSpec = {
                    activityNavigationPopContentTransform(transitionOffsetPx)
                },
            )
        }
    }
}

/**
 * Retains the one-pane Settings back stack so full-screen changes can use the same composed
 * surface animation as the app's other activity-style destinations. Navigation3's ordinary
 * slide/fade transform cannot extend the destination edge into the translated gap.
 */
@Composable
private fun SinglePaneSettingsNavigation(
    detailStack: List<SettingsSection>,
    selectedSection: SettingsSection,
    onBackFromSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateTo: (SettingsSection, Boolean) -> Unit,
    renderList: @Composable (
        selectedSection: SettingsSection,
        showSelection: Boolean,
        onBack: () -> Unit,
        onSectionSelected: (SettingsSection) -> Unit,
    ) -> Unit,
    renderDetail: @Composable (
        section: SettingsSection,
        singlePane: Boolean,
        onBack: () -> Unit,
        onNavigate: (SettingsSection, Boolean) -> Unit,
    ) -> Unit,
    predictiveBackOverlay: SettingsPredictiveBackOverlay? = null,
    completedPredictiveBack: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val retainedDetails = remember {
        mutableStateListOf<RetainedSettingsLayer>().apply {
            detailStack.forEach { section ->
                add(RetainedSettingsLayer(section, initiallyVisible = true))
            }
        }
    }
    LaunchedEffect(detailStack, completedPredictiveBack) {
        detailStack.forEach { section ->
            if (retainedDetails.none { it.section == section }) {
                retainedDetails += RetainedSettingsLayer(section, initiallyVisible = false)
            }
        }
        retainedDetails.forEach { layer ->
            val retainedInStack = layer.section in detailStack
            if (completedPredictiveBack && !retainedInStack) {
                // The host can clear its short-lived completion flag before AnimatedVisibility
                // disposes this layer. Keep this specific outgoing layer snapped invisible until
                // disposal so it cannot flash between the predictive and retained-layer states.
                layer.predictiveExitCompleted = true
            }
            layer.visibility.targetState = retainedInStack
        }
    }

    val transitionOffsetPx = with(LocalDensity.current) {
        ActivityNavigationTransitionOffset.roundToPx()
    }
    val listOffset by animateFloatAsState(
        targetValue = if (detailStack.isEmpty()) 0f else -transitionOffsetPx.toFloat(),
        animationSpec = if (
            completedPredictiveBack ||
            retainedDetails.isEmpty() && detailStack.isEmpty()
        ) {
            snap()
        } else {
            tween(
                durationMillis = ActivityNavigationTransitionDurationMillis,
                easing = activityNavigationEasing(),
            )
        },
        label = "settings list navigation offset",
    )
    val pageBackground = settingsPageBackgroundColor()
    val predictiveBackActive = predictiveBackOverlay != null
    // These identities are captured when the gesture starts. Deriving them from detailStack would
    // reassign the exit modifier to the parent in the composition that observes the completed pop.
    val predictiveCurrentSection = predictiveBackOverlay?.sourceSection
    val predictivePreviousSection = predictiveBackOverlay?.parentSection
    val listIsPredictiveParent = predictiveBackActive && predictivePreviousSection == null

    Box(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBackground)
                .graphicsLayer {
                    translationX = if (!predictiveBackActive && !completedPredictiveBack) {
                        listOffset
                    } else {
                        0f
                    }
                }
                .then(
                    if (detailStack.isEmpty()) Modifier else Modifier.clearAndSetSemantics { },
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (listIsPredictiveParent) {
                            predictiveBackOverlay.enterModifier
                        } else {
                            Modifier
                        },
                    ),
            ) {
                renderList(
                    selectedSection,
                    false,
                    onBackFromSettings,
                    { section -> onNavigateTo(section, false) },
                )
            }
        }

        retainedDetails.forEachIndexed { index, layer ->
            val retainedInStack = layer.section in detailStack
            val isCurrent = layer.section == detailStack.lastOrNull()
            // detailStack changes before the LaunchedEffect above can retire the AnimatedVisibility
            // layer. Conceal a completed predictive source synchronously in that first composition.
            val completedPredictiveExit = completedPredictiveBack && !retainedInStack
            val skipExitAnimation = completedPredictiveBack || layer.predictiveExitCompleted
            val layerOffset by animateFloatAsState(
                targetValue = if (retainedInStack && !isCurrent) {
                    -transitionOffsetPx.toFloat()
                } else {
                    0f
                },
                animationSpec = if (skipExitAnimation) {
                    snap()
                } else {
                    tween(
                        durationMillis = ActivityNavigationTransitionDurationMillis,
                        easing = activityNavigationEasing(),
                    )
                },
                label = "${layer.section.route} navigation offset",
            )
            val predictiveModifier = when (layer.section) {
                predictiveCurrentSection -> predictiveBackOverlay.exitModifier
                predictivePreviousSection -> predictiveBackOverlay.enterModifier
                else -> Modifier
            }
            LaunchedEffect(layer) {
                snapshotFlow {
                    layer.visibility.isIdle &&
                        !layer.visibility.currentState &&
                        !layer.visibility.targetState
                }.first { it }
                retainedDetails.remove(layer)
            }

            AnimatedVisibility(
                visibleState = layer.visibility,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(index + 1f)
                    .graphicsLayer {
                        alpha = if (completedPredictiveExit || layer.predictiveExitCompleted) {
                            0f
                        } else {
                            1f
                        }
                    }
                    .then(if (isCurrent) Modifier else Modifier.clearAndSetSemantics { }),
                enter = EnterTransition.None,
                exit = ExitTransition.None,
            ) {
                key(layer.section) {
                    ActivityNavigationTransitionViewport(
                        transition = transition,
                        transitionOffsetPx = transitionOffsetPx,
                        baseTranslationX = if (
                            !predictiveBackActive && !skipExitAnimation
                        ) {
                            layerOffset
                        } else {
                            0f
                        },
                        skipExitAnimation = skipExitAnimation,
                        modifier = Modifier.fillMaxSize(),
                        contentModifier = predictiveModifier,
                    ) {
                        Box(Modifier.fillMaxSize().background(pageBackground)) {
                            renderDetail(
                                layer.section,
                                true,
                                onNavigateBack,
                                onNavigateTo,
                            )
                        }
                    }
                }
            }
        }

        if (predictiveBackActive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(Float.MAX_VALUE)
                    .consumeAllPointerGestures(),
            )
        }
    }
}
