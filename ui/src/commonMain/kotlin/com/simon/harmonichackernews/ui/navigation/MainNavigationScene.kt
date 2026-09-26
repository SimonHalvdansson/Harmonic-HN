package com.simon.harmonichackernews.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.rememberNavigationEventState
import com.simon.harmonichackernews.navigation.MainStoryRequest

private data object StoriesDestination : NavKey

private data class CommentsDestination(val request: MainStoryRequest) : NavKey

/** Shared Navigation3 list/detail scene used by every Compose host. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainNavigationScene(
    storyRequest: MainStoryRequest?,
    directive: PaneScaffoldDirective,
    paneProportion: Float,
    onBack: () -> Unit,
    stories: @Composable () -> Unit,
    emptyDetail: @Composable () -> Unit,
    comments: @Composable (MainStoryRequest) -> Unit,
    animateDetailVisibilityChanges: Boolean = false,
    modifier: Modifier = Modifier,
    isFoldable: Boolean = false,
) {
    val isTwoPane = directive.maxHorizontalPartitions > 1
    SplitPaneViewport(directive, paneProportion, modifier, isFoldable = isFoldable) { expansion ->
        val strategy = rememberListDetailSceneStrategy<NavKey>(
            directive = directive,
            paneExpansionState = expansion.takeIf { isTwoPane },
        )
        val backStack = remember {
            mutableStateListOf<NavKey>(StoriesDestination).apply {
                storyRequest?.let { add(CommentsDestination(it)) }
            }
        }
        var animatedStorySerial by remember { mutableIntStateOf(-1) }
        var emptyDetailAnimationVersion by remember { mutableIntStateOf(0) }
        LaunchedEffect(storyRequest?.serial) {
            val previousRequest = (backStack.lastOrNull() as? CommentsDestination)?.request
            val animation = mainDetailPaneAnimation(
                previousStorySerial = previousRequest?.serial,
                nextStorySerial = storyRequest?.serial,
                animateVisibilityChanges = animateDetailVisibilityChanges,
            )
            animatedStorySerial = animation.storySerial ?: -1
            if (animation.animateEmptyDetail) {
                emptyDetailAnimationVersion++
            }
            if (storyRequest == null) {
                if (backStack.lastOrNull() is CommentsDestination) backStack.removeLastOrNull()
            } else if (backStack.lastOrNull() is CommentsDestination) {
                backStack[backStack.lastIndex] = CommentsDestination(storyRequest)
            } else {
                backStack.add(CommentsDestination(storyRequest))
            }
        }
        val provider = entryProvider<NavKey> {
            entry<StoriesDestination>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = {
                        PaneDetailSwitchIn(
                            contentKey = emptyDetailAnimationVersion,
                            animate = emptyDetailAnimationVersion > 0,
                            initialScale = 1.15f,
                        ) {
                            emptyDetail()
                        }
                    },
                ),
            ) { stories() }
            entry<CommentsDestination>(
                metadata = ListDetailSceneStrategy.detailPane(),
            ) { destination ->
                if (!isTwoPane) {
                    comments(destination.request)
                } else {
                    PaneDetailSwitchIn(
                        contentKey = destination.request.serial,
                        animate = animatedStorySerial == destination.request.serial,
                    ) {
                        comments(destination.request)
                    }
                }
            }
        }
        val entries = rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = provider,
        )
        val sceneState = rememberSceneState(
            entries = entries,
            sceneStrategies = listOf(strategy),
            onBack = onBack,
        )
        val eventState = rememberNavigationEventState(SceneInfo(sceneState.currentScene))
        val transitionOffsetPx = with(LocalDensity.current) {
            ActivityNavigationTransitionOffset.roundToPx()
        }
        NavDisplay(
            sceneState = sceneState,
            navigationEventState = eventState,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { activityNavigationOpenContentTransform(transitionOffsetPx) },
            popTransitionSpec = { activityNavigationPopContentTransform(transitionOffsetPx) },
            predictivePopTransitionSpec = {
                activityNavigationPopContentTransform(transitionOffsetPx)
            },
        )
    }
}

internal data class MainDetailPaneAnimation(
    val storySerial: Int? = null,
    val animateEmptyDetail: Boolean = false,
)

internal fun mainDetailPaneAnimation(
    previousStorySerial: Int?,
    nextStorySerial: Int?,
    animateVisibilityChanges: Boolean,
): MainDetailPaneAnimation = when {
    nextStorySerial != null && previousStorySerial != nextStorySerial ->
        MainDetailPaneAnimation(
            storySerial = nextStorySerial.takeIf {
                previousStorySerial != null || animateVisibilityChanges
            },
        )
    nextStorySerial == null && previousStorySerial != null && animateVisibilityChanges ->
        MainDetailPaneAnimation(animateEmptyDetail = true)
    else -> MainDetailPaneAnimation()
}

/**
 * Shared single-pane destination composition. Platform hosts may supply predictive-back graphics
 * modifiers, while ownership of list/detail retention, transitions, semantics and z-order remains
 * common across Android, iOS and desktop.
 */
@Composable
fun SinglePaneNavigationScene(
    scene: MainNavigationScenePlan,
    completedPredictivePop: Boolean,
    predictiveBackActive: Boolean,
    stories: @Composable () -> Unit,
    comments: @Composable (MainStoryRequest) -> Unit,
    modifier: Modifier = Modifier,
    storiesPredictiveModifier: Modifier = Modifier,
    commentsPredictiveModifier: Modifier = Modifier,
    onStoryLayersEmpty: () -> Unit = {},
) {
    val storyRequests = scene.fullScreenStories
    val preview = remember(predictiveBackActive) {
        storyRequests.lastOrNull()?.takeIf { predictiveBackActive }?.let { source ->
            source.serial to storyRequests.dropLast(1).lastOrNull()?.serial
        }
    }
    ActivityNavigationStack(
        entries = storyRequests,
        entryKey = { it.serial },
        completedPredictiveBack = completedPredictivePop,
        rootVisible = scene.showsStoriesRoot,
        preview = preview?.let { (source, parent) ->
            ActivityBackPreview(source, parent, storiesPredictiveModifier, commentsPredictiveModifier)
        },
        animateInitialEntry = scene.animateInitialStory,
        replaceDisjointEntries = true,
        hideCoveredRoot = true,
        animateRootOnPop = false,
        onLayersEmpty = onStoryLayersEmpty,
        modifier = modifier,
        root = stories,
        content = comments,
    )
}
