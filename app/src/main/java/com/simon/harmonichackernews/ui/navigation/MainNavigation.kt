package com.simon.harmonichackernews.ui.navigation

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.compose.PredictiveBackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.rememberNavigationEventState
import com.simon.harmonichackernews.CommentsFragment
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.StoriesFragment
import com.simon.harmonichackernews.ui.comments.EmptyCommentsScreen
import com.simon.harmonichackernews.ui.settings.DefaultActivityPredictiveBackAnimation
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data object StoriesDestination : NavKey

private data class CommentsDestination(
    val requestSerial: Int,
) : NavKey

internal data class MainStoryRequest(
    val serial: Int,
    val arguments: Bundle,
) {
    val storyId: Int = arguments.getInt(CommentsFragment.EXTRA_ID, -1)
}

/**
 * Imperative bridge used by the existing story-loading controller while Navigation 3 owns the
 * actual list/detail history and adaptive layout.
 */
@Stable
class MainNavigationController internal constructor() {
    internal var storyRequest by mutableStateOf<MainStoryRequest?>(null)
        private set
    internal var closeRequest by mutableIntStateOf(0)
        private set

    private var requestSerial = 0
    private var storiesFragment: StoriesFragment? = null
    private var commentsFragment: CommentsFragment? = null
    private var adaptiveTwoPane = false
    private var adaptiveFoldable = false

    fun openStory(arguments: Bundle) {
        storyRequest = MainStoryRequest(++requestSerial, Bundle(arguments))
    }

    fun closeStory() {
        closeRequest++
    }

    fun switchOpenStoryViewIfMatching(storyId: Int, showWebsite: Boolean): Boolean =
        commentsFragment?.switchStoryViewIfMatching(storyId, showWebsite) == true

    fun getStoriesFragment(): StoriesFragment? = storiesFragment

    fun getCommentsFragment(): CommentsFragment? = commentsFragment

    fun isAdaptiveTwoPane(): Boolean = adaptiveTwoPane

    fun isAdaptiveFoldable(): Boolean = adaptiveFoldable

    internal fun attachStoriesFragment(fragment: StoriesFragment) {
        storiesFragment = fragment
    }

    internal fun detachStoriesFragment(fragment: StoriesFragment) {
        if (storiesFragment === fragment) storiesFragment = null
    }

    internal fun attachCommentsFragment(fragment: CommentsFragment) {
        commentsFragment = fragment
    }

    internal fun detachCommentsFragment(fragment: CommentsFragment) {
        if (commentsFragment === fragment) commentsFragment = null
    }

    internal fun detailRemovedFromBackStack() {
        storyRequest = null
        commentsFragment = null
    }

    internal fun updateAdaptiveState(twoPane: Boolean, foldable: Boolean) {
        adaptiveTwoPane = twoPane
        adaptiveFoldable = foldable
    }
}

object MainNavigationHost {
    @JvmStatic
    fun install(activity: MainActivity): MainNavigationController {
        val controller = MainNavigationController()
        val composeView = ComposeView(activity).apply {
            id = View.generateViewId()
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HarmonicTheme {
                    MainNavigation(
                        activity = activity,
                        controller = controller,
                    )
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
    activity: AppCompatActivity,
    controller: MainNavigationController,
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val context = activity
    val supportsTwoPane = context.resources.configuration.smallestScreenWidthDp >= 600
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
    SideEffect {
        controller.updateAdaptiveState(
            twoPane = isTwoPane,
            foldable = isTwoPane && isFoldable,
        )
    }
    val paneProportion = if (isFoldable) 0.5f else 0.38f
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
    val backStack = remember {
        mutableStateListOf<NavKey>(StoriesDestination)
    }
    val backAnimationScope = rememberCoroutineScope()
    var activeBackAnimation by remember {
        mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    }

    fun popMainBackStack() {
        if (backStack.lastOrNull() is CommentsDestination) {
            backStack.removeLastOrNull()
            controller.detailRemovedFromBackStack()
        } else {
            activity.finish()
        }
    }

    val storyRequest = controller.storyRequest
    LaunchedEffect(storyRequest?.serial) {
        storyRequest ?: return@LaunchedEffect
        if (backStack.lastOrNull() is CommentsDestination) {
            backStack.removeLastOrNull()
        }
        backStack.add(CommentsDestination(storyRequest.serial))
    }
    LaunchedEffect(controller.closeRequest) {
        if (controller.closeRequest > 0 && backStack.lastOrNull() is CommentsDestination) {
            popMainBackStack()
        }
    }

    val provider = entryProvider<NavKey> {
        entry<StoriesDestination>(
            metadata = ListDetailSceneStrategy.listPane(
                detailPlaceholder = { EmptyCommentsScreen() },
            ),
        ) {
            StoriesFragmentPane(controller)
        }

        entry<CommentsDestination>(
            metadata = ListDetailSceneStrategy.detailPane(),
        ) { destination ->
            val request = controller.storyRequest
                ?.takeIf { it.serial == destination.requestSerial }
            if (request == null) {
                EmptyCommentsScreen()
            } else {
                CommentsFragmentPane(
                    request = request,
                    controller = controller,
                )
            }
        }
    }

    PredictiveBackHandler(
        enabled = backStack.lastOrNull() is CommentsDestination,
    ) { events ->
        if (isTwoPane) {
            try {
                events.collect { }
                popMainBackStack()
            } catch (_: CancellationException) {
                // A cancelled two-pane gesture keeps the current detail selected.
            }
            return@PredictiveBackHandler
        }

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
                popMainBackStack()
                return@PredictiveBackHandler
            }
            currentAnimation.finish()
            popMainBackStack()
            activeBackAnimation = null
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                animation?.cancel()
                if (activeBackAnimation === animation) activeBackAnimation = null
            }
        }
    }

    val decoratedEntries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = provider,
    )
    val sceneState = rememberSceneState(
        entries = decoratedEntries,
        sceneStrategies = listOf(sceneStrategy),
        onBack = ::popMainBackStack,
    )
    val navDisplayEventState = rememberNavigationEventState(
        currentInfo = SceneInfo(sceneState.currentScene),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background),
    ) {
        val animation = activeBackAnimation
        val previousScene = sceneState.previousScenes.lastOrNull()
        if (!isTwoPane && animation != null && previousScene != null) {
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
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { mainOpenTransition() },
                popTransitionSpec = { mainPopTransition() },
                predictivePopTransitionSpec = { _ -> mainPopTransition() },
            )
        }
    }
}

@Composable
private fun StoriesFragmentPane(controller: MainNavigationController) {
    val attachedFragment = remember { arrayOfNulls<StoriesFragment>(1) }
    DisposableEffect(controller) {
        onDispose {
            attachedFragment[0]?.let(controller::detachStoriesFragment)
        }
    }
    AndroidFragment<StoriesFragment>(
        modifier = Modifier.fillMaxSize(),
        fragmentState = rememberFragmentState(),
        onUpdate = { fragment ->
            attachedFragment[0] = fragment
            controller.attachStoriesFragment(fragment)
        },
    )
}

@Composable
private fun CommentsFragmentPane(
    request: MainStoryRequest,
    controller: MainNavigationController,
) {
    val attachedFragment = remember { arrayOfNulls<CommentsFragment>(1) }
    val arguments = remember(request.serial) { Bundle(request.arguments) }
    DisposableEffect(controller, request.serial) {
        onDispose {
            attachedFragment[0]?.let(controller::detachCommentsFragment)
        }
    }
    AndroidFragment<CommentsFragment>(
        modifier = Modifier.fillMaxSize(),
        fragmentState = rememberFragmentState(),
        arguments = arguments,
        onUpdate = { fragment ->
            attachedFragment[0] = fragment
            controller.attachCommentsFragment(fragment)
        },
    )
}

private const val NavigationTransitionDurationMillis = 450
private const val NavigationFadeDurationMillis = 90

private fun navigationEasing(): Easing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
)

private fun mainOpenTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        animationSpec = tween(
            durationMillis = NavigationTransitionDurationMillis,
            easing = navigationEasing(),
        ),
        initialOffsetX = { it },
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = NavigationFadeDurationMillis,
            delayMillis = 45,
            easing = LinearEasing,
        ),
    ),
    initialContentExit = slideOutHorizontally(
        animationSpec = tween(
            durationMillis = NavigationTransitionDurationMillis,
            easing = navigationEasing(),
        ),
        targetOffsetX = { -it / 5 },
    ),
    targetContentZIndex = 1f,
)

private fun mainPopTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        animationSpec = tween(
            durationMillis = NavigationTransitionDurationMillis,
            easing = navigationEasing(),
        ),
        initialOffsetX = { -it / 5 },
    ),
    initialContentExit = slideOutHorizontally(
        animationSpec = tween(
            durationMillis = NavigationTransitionDurationMillis,
            easing = navigationEasing(),
        ),
        targetOffsetX = { it },
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = NavigationFadeDurationMillis,
            delayMillis = 35,
            easing = LinearEasing,
        ),
    ),
    targetContentZIndex = -1f,
)
