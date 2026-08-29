package com.simon.harmonichackernews.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.rememberNavigationEventState
import com.simon.harmonichackernews.navigation.MainStoryRequest
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlinx.coroutines.flow.first

private data object StoriesDestination : NavKey

private data class CommentsDestination(val request: MainStoryRequest) : NavKey

private class RetainedStoryLayer(
    val request: MainStoryRequest,
    initiallyVisible: Boolean,
) {
    val visibility = MutableTransitionState(initiallyVisible).apply {
        targetState = true
    }
}

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
) {
    val isTwoPane = directive.maxHorizontalPartitions > 1
    val expansion = rememberPaneExpansionState(
        anchors = remember(paneProportion) {
            listOf(PaneExpansionAnchor.Proportion(paneProportion))
        },
        initialAnchoredIndex = 0,
    )
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
    NavDisplay(
        sceneState = sceneState,
        navigationEventState = eventState,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { mainOpenTransition() },
        popTransitionSpec = { mainPopTransition() },
        predictivePopTransitionSpec = { mainPopTransition() },
    )
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
    storyRequests: List<MainStoryRequest>,
    completedPredictivePop: Boolean,
    predictiveBackActive: Boolean,
    showStoriesRoot: Boolean,
    animateInitialStory: Boolean = false,
    stories: @Composable () -> Unit,
    comments: @Composable (MainStoryRequest) -> Unit,
    modifier: Modifier = Modifier,
    storiesPredictiveModifier: Modifier = Modifier,
    commentsPredictiveModifier: Modifier = Modifier,
) {
    val retainedStories = remember {
        mutableStateListOf<RetainedStoryLayer>().apply {
            storyRequests.forEachIndexed { index, request ->
                add(
                    RetainedStoryLayer(
                        request = request,
                        initiallyVisible = !animateInitialStory || index < storyRequests.lastIndex,
                    ),
                )
            }
        }
    }
    LaunchedEffect(storyRequests) {
        storyRequests.forEach { request ->
            if (retainedStories.none { it.request.serial == request.serial }) {
                retainedStories += RetainedStoryLayer(request, initiallyVisible = false)
            }
        }
        retainedStories.forEach { layer ->
            layer.visibility.targetState = storyRequests.any {
                it.serial == layer.request.serial
            }
        }
    }
    val predictiveCurrentSerial = remember(predictiveBackActive) {
        storyRequests.lastOrNull()?.serial.takeIf { predictiveBackActive }
    }
    val predictivePreviousSerial = remember(predictiveBackActive) {
        storyRequests.dropLast(1).lastOrNull()?.serial.takeIf { predictiveBackActive }
    }
    var paneWidth by remember { mutableIntStateOf(0) }
    val storiesOffset by animateFloatAsState(
        targetValue = if (showStoriesRoot && storyRequests.isNotEmpty()) -0.2f else 0f,
        animationSpec = if (storyRequests.isEmpty()) {
            snap()
        } else {
            tween(
                durationMillis = NavigationTransitionDurationMillis,
                easing = navigationEasing(),
            )
        },
        label = "stories navigation offset",
    )
    val storiesArePredictiveParent = predictiveBackActive &&
        predictivePreviousSerial == null && showStoriesRoot

    Box(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (storiesArePredictiveParent) {
                        Modifier.background(HarmonicTheme.colors.background)
                    } else {
                        Modifier
                    },
                )
                .onSizeChanged { paneWidth = it.width }
                .graphicsLayer {
                    alpha = if (showStoriesRoot) 1f else 0f
                    translationX = if (!predictiveBackActive && !completedPredictivePop) {
                        paneWidth * storiesOffset
                    } else {
                        0f
                    }
                }
                .then(
                    if (showStoriesRoot) Modifier else Modifier.clearAndSetSemantics { },
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (storiesArePredictiveParent) {
                            storiesPredictiveModifier
                        } else {
                            Modifier
                        },
                    ),
            ) {
                stories()
            }
        }

        retainedStories.forEachIndexed { index, layer ->
            val retainedInStack = storyRequests.any { it.serial == layer.request.serial }
            val currentStorySerial = storyRequests.lastOrNull()?.serial
            val layerOffset by animateFloatAsState(
                targetValue = if (
                    retainedInStack && layer.request.serial != currentStorySerial
                ) {
                    -0.2f
                } else {
                    0f
                },
                animationSpec = tween(
                    durationMillis = NavigationTransitionDurationMillis,
                    easing = navigationEasing(),
                ),
                label = "story ${layer.request.serial} navigation offset",
            )
            val predictiveModifier = when (layer.request.serial) {
                predictiveCurrentSerial -> commentsPredictiveModifier
                predictivePreviousSerial -> storiesPredictiveModifier
                else -> Modifier
            }
            val isPredictiveParent = layer.request.serial == predictivePreviousSerial

            LaunchedEffect(layer) {
                snapshotFlow {
                    layer.visibility.isIdle &&
                        !layer.visibility.currentState &&
                        !layer.visibility.targetState
                }.first { it }
                retainedStories.remove(layer)
            }

            AnimatedVisibility(
                visibleState = layer.visibility,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(index + 1f)
                    .graphicsLayer {
                        translationX = if (!predictiveBackActive && !completedPredictivePop) {
                            paneWidth * layerOffset
                        } else {
                            0f
                        }
                    }
                    .then(
                        if (layer.request.serial == currentStorySerial) {
                            Modifier
                        } else {
                            Modifier.clearAndSetSemantics { }
                        },
                    ),
                enter = commentsOpenEnter(),
                exit = if (completedPredictivePop) {
                    ExitTransition.None
                } else {
                    commentsPopExit()
                },
            ) {
                key(layer.request.serial) {
                    // The screen applies insets internally. Keep the retained navigation layer
                    // itself opaque edge-to-edge so a destination two levels back cannot show
                    // through the status/navigation-bar gutters during predictive back.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (isPredictiveParent) {
                                    Modifier.background(HarmonicTheme.colors.background)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .then(predictiveModifier)
                                .background(HarmonicTheme.colors.background),
                        ) {
                            comments(layer.request)
                        }
                    }
                }
            }
        }
    }
}

private fun mainOpenTransition(): ContentTransform = ContentTransform(
    targetContentEnter = commentsOpenEnter(),
    initialContentExit = slideOutHorizontally(
        tween(NavigationTransitionDurationMillis, easing = navigationEasing()),
    ) { -it / 5 },
    targetContentZIndex = 1f,
)

private fun commentsOpenEnter(): EnterTransition = slideInHorizontally(
    tween(NavigationTransitionDurationMillis, easing = navigationEasing()),
) { it } + fadeIn(tween(NavigationFadeDurationMillis, 45, LinearEasing))

private fun mainPopTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        tween(NavigationTransitionDurationMillis, easing = navigationEasing()),
    ) { -it / 5 },
    initialContentExit = commentsPopExit(),
    targetContentZIndex = -1f,
)

private fun commentsPopExit(): ExitTransition = slideOutHorizontally(
    tween(NavigationTransitionDurationMillis, easing = navigationEasing()),
) { it } + fadeOut(tween(NavigationFadeDurationMillis, 35, LinearEasing))

private fun navigationEasing(): Easing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
)

private const val NavigationTransitionDurationMillis = 450
private const val NavigationFadeDurationMillis = 83
