package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import com.simon.harmonichackernews.ui.navigation.ActivityBackPreview
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationStack
import com.simon.harmonichackernews.ui.navigation.SplitPaneViewport
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationTransitionOffset
import com.simon.harmonichackernews.ui.navigation.activityNavigationOpenContentTransform
import com.simon.harmonichackernews.ui.navigation.activityNavigationPopContentTransform
import com.simon.harmonichackernews.ui.navigation.paneDetailSwitchTransition
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

/** The retained destination keeps its own navigation origin, including in two-pane layouts. */
internal val LocalSettingsParentSection = staticCompositionLocalOf<SettingsSection?> { null }

private data object SettingsListDestination : NavKey

private data object SettingsTwoPaneDetailDestination : NavKey

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
    supportsTwoPane: Boolean = directive.maxHorizontalPartitions > 1,
) {
    val isTwoPane = directive.maxHorizontalPartitions > 1
    val paneProportion = if (isFoldable) 0.5f else 0.4f
    SplitPaneViewport(
        directive = directive,
        defaultRatio = paneProportion,
        supportsTwoPane = supportsTwoPane,
        isFoldable = isFoldable,
        modifier = modifier.fillMaxSize().background(HarmonicTheme.colors.background)
            .padding(horizontal = if (isTwoPane) tabletPaneHorizontalPadding else 0.dp),
    ) { paneExpansionState ->
        val navigationState by navigation.state.collectAsState()
        val renderDetailWithOrigin: @Composable (SettingsSection, Boolean, () -> Unit, (SettingsSection, Boolean) -> Unit) -> Unit =
            { section, singlePane, onBack, onNavigate ->
                val parent = navigationState.detailStack
                    .getOrNull(navigationState.detailStack.indexOf(section) - 1)
                CompositionLocalProvider(LocalSettingsParentSection provides parent) {
                    renderDetail(section, singlePane, onBack, onNavigate)
                }
            }
        val selectedSection = navigationState.selectedSection

        fun navigateTo(section: SettingsSection, preserveCurrentDetail: Boolean = false) {
            navigation.navigateTo(section, preserveCurrentDetail)
        }

        fun navigateBack() {
            handleSettingsBack(navigation, onBackFromSettings)
        }

        // An empty one-pane stack represents the settings list. `selectedSection` intentionally has
        // an Appearance fallback for list highlighting and the two-pane placeholder, but that fallback
        // is not a real navigation destination and must not be persisted. Persisting it immediately
        // pushes Appearance again and makes Up from every detail land there instead of on the list.
        LaunchedEffect(navigationState.detailStack) {
            navigationState.detailStack.lastOrNull()?.let(onSectionChanged)
        }
        LaunchedEffect(isTwoPane) { navigation.updateLayout(isTwoPane) }

        if (!isTwoPane) {
            SinglePaneSettingsNavigation(
                detailStack = navigationState.detailStack,
                selectedSection = selectedSection,
                onBackFromSettings = onBackFromSettings,
                onNavigateBack = ::navigateBack,
                onNavigateTo = ::navigateTo,
                renderList = renderList,
                renderDetail = renderDetailWithOrigin,
                predictiveBackOverlay = predictiveBackOverlay,
                completedPredictiveBack = completedPredictiveBack,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val sceneStrategy = rememberListDetailSceneStrategy<NavKey>(
                directive = directive,
                paneExpansionState = paneExpansionState,
            )
            val detailPaneTransition = updateTransition(selectedSection, label = "Settings detail pane")
            val provider = entryProvider<NavKey> {
                entry<SettingsListDestination>(
                    metadata = ListDetailSceneStrategy.listPane(
                        detailPlaceholder = {
                            renderDetailWithOrigin(
                                SettingsSection.Appearance,
                                false,
                                ::navigateBack,
                                ::navigateTo,
                            )
                        },
                    ),
                ) {
                    renderList(selectedSection, true, onBackFromSettings, ::navigateTo)
                }
                entry<SettingsTwoPaneDetailDestination>(
                    metadata = ListDetailSceneStrategy.detailPane(),
                ) {
                    if (!animateDetailChanges) {
                        renderDetailWithOrigin(selectedSection, false, ::navigateBack, ::navigateTo)
                    } else {
                        detailPaneTransition.AnimatedContent(
                            transitionSpec = { paneDetailSwitchTransition() },
                        ) { section ->
                            renderDetailWithOrigin(section, false, ::navigateBack, ::navigateTo)
                        }
                    }
                }
            }
            val entries = rememberDecoratedNavEntries(
                backStack = listOf(SettingsListDestination, SettingsTwoPaneDetailDestination),
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

            NavDisplay(
                sceneState = sceneState,
                navigationEventState = navigationEventState,
                modifier = Modifier.fillMaxSize(),
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
    ActivityNavigationStack(
        entries = detailStack,
        entryKey = { it },
        completedPredictiveBack = completedPredictiveBack,
        preview = predictiveBackOverlay?.let {
            ActivityBackPreview(it.sourceSection, it.parentSection, it.enterModifier, it.exitModifier)
        },
        modifier = modifier,
        root = {
            Box(Modifier.fillMaxSize().background(HarmonicTheme.colors.background)) {
                renderList(selectedSection, false, onBackFromSettings) { onNavigateTo(it, false) }
            }
        },
        content = { renderDetail(it, true, onNavigateBack, onNavigateTo) },
    )
}
