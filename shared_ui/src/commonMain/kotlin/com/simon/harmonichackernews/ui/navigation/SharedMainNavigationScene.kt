package com.simon.harmonichackernews.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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

private data object SharedStoriesDestination : NavKey

private data class SharedCommentsDestination(val request: MainStoryRequest) : NavKey

/** Shared Navigation3 list/detail scene used by every Compose host. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SharedMainNavigationScene(
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
        mutableStateListOf<NavKey>(SharedStoriesDestination).apply {
            storyRequest?.let { add(SharedCommentsDestination(it)) }
        }
    }
    var animatedStorySerial by remember { mutableIntStateOf(-1) }
    var emptyDetailAnimationVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(storyRequest?.serial) {
        val previousRequest = (backStack.lastOrNull() as? SharedCommentsDestination)?.request
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
            if (backStack.lastOrNull() is SharedCommentsDestination) backStack.removeLastOrNull()
        } else if (backStack.lastOrNull() is SharedCommentsDestination) {
            backStack[backStack.lastIndex] = SharedCommentsDestination(storyRequest)
        } else {
            backStack.add(SharedCommentsDestination(storyRequest))
        }
    }
    val provider = entryProvider<NavKey> {
        entry<SharedStoriesDestination>(
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
        entry<SharedCommentsDestination>(
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
fun SharedSinglePaneNavigationScene(
    storyRequest: MainStoryRequest?,
    lastStoryRequest: MainStoryRequest?,
    completedPredictivePop: Boolean,
    predictiveBackActive: Boolean,
    showStoriesPane: Boolean,
    stories: @Composable () -> Unit,
    comments: @Composable (MainStoryRequest) -> Unit,
    modifier: Modifier = Modifier,
    storiesPredictiveModifier: Modifier = Modifier,
    commentsPredictiveModifier: Modifier = Modifier,
) {
    val displayedRequest = storyRequest ?: lastStoryRequest
    var paneWidth by remember { mutableIntStateOf(0) }
    val storiesOffset by animateFloatAsState(
        targetValue = if (storyRequest == null) 0f else -0.2f,
        animationSpec = if (storyRequest == null) {
            snap()
        } else {
            tween(
                durationMillis = NavigationTransitionDurationMillis,
                easing = navigationEasing(),
            )
        },
        label = "stories navigation offset",
    )

    Box(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { paneWidth = it.width }
                .graphicsLayer {
                    alpha = if (showStoriesPane) 1f else 0f
                    translationX = if (!predictiveBackActive && !completedPredictivePop) {
                        paneWidth * storiesOffset
                    } else {
                        0f
                    }
                }
                .then(
                    if (showStoriesPane) Modifier else Modifier.clearAndSetSemantics { },
                )
                .then(storiesPredictiveModifier),
        ) {
            stories()
        }

        AnimatedVisibility(
            visible = storyRequest != null,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .then(commentsPredictiveModifier),
            enter = commentsOpenEnter(),
            exit = if (completedPredictivePop) ExitTransition.None else commentsPopExit(),
        ) {
            if (storyRequest != null || !completedPredictivePop) {
                displayedRequest?.let { request ->
                    key(request.serial) { comments(request) }
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
