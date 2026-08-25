package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.rememberNavigationEventState
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.navigation.paneDetailSwitchTransition

private data object SettingsListDestination : NavKey

private data object SettingsTwoPaneDetailDestination : NavKey

private data class SettingsDetailDestination(val section: SettingsSection) : NavKey

data class SettingsPredictiveBackOverlay(
    val enterModifier: Modifier,
    val exitModifier: Modifier,
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
    val transitionOffsetPx = with(LocalDensity.current) { 96.dp.roundToPx() }

    Box(
        modifier = modifier.fillMaxSize().background(HarmonicTheme.colors.background),
    ) {
        val previousScene = sceneState.previousScenes.lastOrNull()
        if (showDetailNavigation && predictiveBackOverlay != null && previousScene != null) {
            key(previousScene.key) {
                Box(Modifier.fillMaxSize().then(predictiveBackOverlay.enterModifier)) {
                    previousScene.content()
                }
            }
            key(sceneState.currentScene.key) {
                Box(Modifier.fillMaxSize().then(predictiveBackOverlay.exitModifier)) {
                    sceneState.currentScene.content()
                }
            }
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
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
                navigationEventState = navigationEventState,
                modifier = Modifier.fillMaxSize().padding(horizontal = tabletPaneHorizontalPadding),
                transitionSpec = { activityOpenTransition(transitionOffsetPx) },
                popTransitionSpec = { activityPopTransition(transitionOffsetPx) },
                predictivePopTransitionSpec = { activityPopTransition(transitionOffsetPx) },
            )
        }
    }
}

private const val ActivityTransitionDurationMillis = 450
private const val ActivityEnterAlphaDelayMillis = 50
private const val ActivityExitAlphaDelayMillis = 35
private const val ActivityAlphaDurationMillis = 83

private fun aospFastOutExtraSlowInEasing(): Easing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
)

private fun activityOpenTransition(offsetPx: Int): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        tween(ActivityTransitionDurationMillis, easing = aospFastOutExtraSlowInEasing()),
    ) { offsetPx } + fadeIn(
        tween(ActivityAlphaDurationMillis, ActivityEnterAlphaDelayMillis, LinearEasing),
    ),
    initialContentExit = slideOutHorizontally(
        tween(ActivityTransitionDurationMillis, easing = aospFastOutExtraSlowInEasing()),
    ) { -offsetPx },
    targetContentZIndex = 1f,
)

private fun activityPopTransition(offsetPx: Int): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        tween(ActivityTransitionDurationMillis, easing = aospFastOutExtraSlowInEasing()),
    ) { -offsetPx },
    initialContentExit = slideOutHorizontally(
        tween(ActivityTransitionDurationMillis, easing = aospFastOutExtraSlowInEasing()),
    ) { offsetPx } + fadeOut(
        tween(ActivityAlphaDurationMillis, ActivityExitAlphaDelayMillis, LinearEasing),
    ),
    targetContentZIndex = -1f,
)
