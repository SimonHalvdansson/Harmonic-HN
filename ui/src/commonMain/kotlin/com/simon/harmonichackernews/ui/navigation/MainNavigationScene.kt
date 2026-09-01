package com.simon.harmonichackernews.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.delay
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
    val transitionOffsetPx = with(LocalDensity.current) {
        ActivityNavigationTransitionOffset.roundToPx()
    }
    NavDisplay(
        sceneState = sceneState,
        navigationEventState = eventState,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { activityNavigationOpenContentTransform(transitionOffsetPx) },
        popTransitionSpec = { activityNavigationPopContentTransform(transitionOffsetPx) },
        predictivePopTransitionSpec = {
            activityNavigationPopContentTransform(transitionOffsetPx)
        },
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
    val requestedStorySerials = storyRequests.mapTo(mutableSetOf()) { it.serial }
    val replacesRetainedStoryRun = requestedStorySerials.isNotEmpty() &&
        retainedStories.none { it.request.serial in requestedStorySerials }
    LaunchedEffect(storyRequests) {
        if (replacesRetainedStoryRun) {
            // A story selected from Stories, Settings, or Submissions enters over that parent
            // surface. The previous, unrelated story run is not an outgoing transition layer.
            retainedStories.removeAll { it.request.serial !in requestedStorySerials }
        }
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
    val transitionOffsetPx = with(LocalDensity.current) {
        ActivityNavigationTransitionOffset.roundToPx()
    }
    val storiesOffset by animateFloatAsState(
        targetValue = if (showStoriesRoot && storyRequests.isNotEmpty()) {
            -transitionOffsetPx.toFloat()
        } else {
            0f
        },
        animationSpec = if (storyRequests.isEmpty()) {
            snap()
        } else {
            tween(
                durationMillis = ActivityNavigationTransitionDurationMillis,
                easing = activityNavigationEasing(),
            )
        },
        label = "stories navigation offset",
    )
    val storiesArePredictiveParent = predictiveBackActive &&
        predictivePreviousSerial == null && showStoriesRoot
    var storySurfaceCoversStories by remember { mutableStateOf(false) }
    LaunchedEffect(
        storyRequests.lastOrNull()?.serial,
        showStoriesRoot,
        predictiveBackActive,
        completedPredictivePop,
    ) {
        storySurfaceCoversStories = false
        if (
            showStoriesRoot &&
            storyRequests.isNotEmpty() &&
            !predictiveBackActive &&
            !completedPredictivePop
        ) {
            delay(
                ActivityNavigationOpenContentOpaqueMillis.toLong(),
            )
            storySurfaceCoversStories = true
        }
    }

    Box(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (!storySurfaceCoversStories) drawContent()
                }
                .then(
                    if (storiesArePredictiveParent) {
                        Modifier.background(HarmonicTheme.colors.background)
                    } else {
                        Modifier
                    },
                )
                .graphicsLayer {
                    alpha = if (showStoriesRoot) 1f else 0f
                    translationX = if (!predictiveBackActive && !completedPredictivePop) {
                        storiesOffset
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
            // Key the whole layer so a remaining AndroidView moves with its story when an
            // outgoing layer is removed instead of being recreated in the vacated slot.
            key(layer.request.serial) {
                val retainedInStack = storyRequests.any { it.serial == layer.request.serial }
                val currentStorySerial = storyRequests.lastOrNull()?.serial
                val layerOffset by animateFloatAsState(
                    targetValue = if (
                        retainedInStack && layer.request.serial != currentStorySerial
                    ) {
                        -transitionOffsetPx.toFloat()
                    } else {
                        0f
                    },
                    animationSpec = tween(
                        durationMillis = ActivityNavigationTransitionDurationMillis,
                        easing = activityNavigationEasing(),
                    ),
                    label = "story ${layer.request.serial} navigation offset",
                )
                val predictiveModifier = when (layer.request.serial) {
                    predictiveCurrentSerial -> commentsPredictiveModifier
                    predictivePreviousSerial -> storiesPredictiveModifier
                    else -> Modifier
                }
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
                            alpha = if (replacesRetainedStoryRun && !retainedInStack) 0f else 1f
                        }
                        .then(
                            if (layer.request.serial == currentStorySerial) {
                                Modifier
                            } else {
                                Modifier.clearAndSetSemantics { }
                            },
                        ),
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                ) {
                    ActivityNavigationTransitionViewport(
                        transition = transition,
                        transitionOffsetPx = transitionOffsetPx,
                        baseTranslationX = if (
                            !predictiveBackActive && !completedPredictivePop
                        ) {
                            layerOffset
                        } else {
                            0f
                        },
                        skipExitAnimation = completedPredictivePop,
                        modifier = Modifier.fillMaxSize(),
                        contentModifier = predictiveModifier,
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
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
