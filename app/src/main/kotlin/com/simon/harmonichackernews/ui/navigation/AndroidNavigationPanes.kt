package com.simon.harmonichackernews.ui.navigation

import androidx.compose.material3.MaterialTheme
import android.os.Build
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.ui.comments.AndroidCommentActionOverlay
import com.simon.harmonichackernews.ui.comments.AndroidCommentLinkPreviewOverlay
import com.simon.harmonichackernews.ui.comments.CommentsSheetCollapsedHeight
import com.simon.harmonichackernews.ui.comments.CommentNavigationControls
import com.simon.harmonichackernews.ui.comments.CommentsScaffold
import com.simon.harmonichackernews.ui.comments.AndroidCommentsScreen
import com.simon.harmonichackernews.ui.comments.CommentsScreenController
import com.simon.harmonichackernews.ui.comments.LocalSideBySideCommentsPortal
import com.simon.harmonichackernews.ui.comments.rememberSideBySideCommentsContent
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.comments.CommentsHazeHost
import com.simon.harmonichackernews.ui.common.HazeHost
import com.simon.harmonichackernews.ui.comments.CommentsUpButton
import com.simon.harmonichackernews.ui.stories.StoryTapToUpdateButton
import com.simon.harmonichackernews.ui.stories.AndroidStoriesScreen
import com.simon.harmonichackernews.ui.stories.AndroidStoryPreviewOverlay
import com.simon.harmonichackernews.navigation.MainStoryRequest
import com.simon.harmonichackernews.navigation.MainDestination

@Composable
internal fun StoriesPane(
    controller: AndroidMainNavigationController,
    statusBarColor: Color = Color.Transparent,
    statusBarHeight: Dp = 0.dp,
    drawStatusBarProtection: Boolean = false,
) {
    val extraPadding = animatedExtraPanePadding()
    val extraPaddingPx = with(LocalDensity.current) { extraPadding.roundToPx() }
    SideEffect { controller.setStoriesExtraSidePadding(extraPaddingPx) }
    HazeHost {
        Box(Modifier.fillMaxSize()) {
            val storiesController = controller.storiesComposeController
            val mainListState = rememberLazyListState()
            var previewScrimAlpha by remember(storiesController) { mutableFloatStateOf(0f) }
            val previewVisible = storiesController?.storyPreviewOverlay != null
            LaunchedEffect(previewVisible) {
                if (!previewVisible) previewScrimAlpha = 0f
            }
            storiesController?.let {
                AndroidStoriesScreen(
                    controller = it,
                    mainListState = mainListState,
                    onVisibleStoriesChanged = controller::updateVisibleStories,
                )
            }
            if (drawStatusBarProtection) {
                StatusBarProtection(
                    color = statusBarColor,
                    statusBarHeight = statusBarHeight,
                    modalScrimAlpha = previewScrimAlpha,
                )
            }
            storiesController
                ?.takeIf { it.storyPreviewOverlay != null }
                ?.let {
                    Box(Modifier.fillMaxSize().zIndex(100f)) {
                        AndroidStoryPreviewOverlay(
                            controller = it,
                            onScrimAlphaChanged = { alpha -> previewScrimAlpha = alpha },
                        )
                    }
                }
            storiesController?.let {
                StoryTapToUpdateButton(
                    controller = it,
                    mainListState = mainListState,
                    modifier = Modifier.zIndex(101f),
                    modalScrimAlpha = previewScrimAlpha,
                    modalScrimActive = previewVisible,
                )
            }
        }
    }
}

@Composable
internal fun CommentsPane(
    request: MainStoryRequest,
    controller: AndroidMainNavigationController,
    showUpButton: Boolean,
    statusBarHeight: Dp = 0.dp,
    drawStatusBarProtection: Boolean = false,
) {
    val activity = LocalActivity.current as MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val activeCoordinator = remember(controller, activity, request.serial) {
        controller.retainCommentsCoordinator(activity, request)
    }
    val extraPadding = animatedExtraPanePadding()
    val extraPaddingPx = with(LocalDensity.current) { extraPadding.roundToPx() }
    SideEffect {
        activeCoordinator.updatePaneLayout(showUpButton = showUpButton, extraPaddingPx = extraPaddingPx)
    }
    SideEffect { controller.attachCommentsCoordinator(activeCoordinator) }
    DisposableEffect(controller, activeCoordinator, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> activeCoordinator.onStart()
                Lifecycle.Event.ON_RESUME -> activeCoordinator.onResume()
                Lifecycle.Event.ON_STOP -> activeCoordinator.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.releaseCommentsCoordinator(activeCoordinator)
        }
    }
    val commentsController = activeCoordinator.composeUiController
    val navigation by controller.navigationState.state.collectAsStateWithLifecycle()
    AskNavigationBarAppearance(
        window = activity.window,
        active = navigation.currentDestination == MainDestination.STORY &&
            navigation.storyRequest?.serial == request.serial &&
            commentsController?.askSurfaceVisible == true,
    )
    // Each retained destination owns its bar color, just like its header and scroll state.
    // Reading the active controller here would repaint the parent with the child's tint.
    val background = MaterialTheme.colorScheme.pageBackground
    val statusBarColor by animateColorAsState(
        targetValue = lerp(
            background,
            commentsController?.statusBarHeaderColor ?: background,
            commentsController?.statusBarHeaderCoverage ?: 0f,
        ),
        animationSpec = tween(durationMillis = 90, easing = LinearEasing),
        label = "story ${request.serial} status bar",
    )
    CommentsHazeHost {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("comments-story-pane-${request.serial}")) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { activeCoordinator.webViewRoot },
            )
            commentsController?.let { commentsController ->
                val showFloatingUpButton = showUpButton &&
                    commentsController.displaySettings?.showUpButton == true
                val navigationBottom = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()
                val sheetPeekHeight = navigationBottom + CommentsSheetCollapsedHeight
                val sheetTravelPx = with(LocalDensity.current) {
                    (maxHeight - sheetPeekHeight).toPx().coerceAtLeast(0f)
                }
                var modalScrimAlpha by remember(commentsController) { mutableFloatStateOf(0f) }
                val modalOverlayVisible = !commentsController.searchDialogVisible &&
                    (
                        commentsController.linkPreviewOverlay != null ||
                            commentsController.isCommentActionOverlayShowing()
                    )
                LaunchedEffect(modalOverlayVisible) {
                    if (!modalOverlayVisible) modalScrimAlpha = 0f
                }
                val portal = LocalSideBySideCommentsPortal.current
                val settingsRepository = LocalHarmonicUiDependencies.current.settings
                val appSettings by settingsRepository.updates.collectAsStateWithLifecycle(
                    initialValue = settingsRepository.snapshot(),
                )
                SideEffect {
                    commentsController.updateSideBySideAvailability(
                        portal != null && appSettings.appearance.sideBySideEnabled &&
                            commentsController.integratedWebView && commentsController.story.isLink,
                    )
                }
                DisposableEffect(portal, commentsController) {
                    onDispose { commentsController.leaveSideBySideHost() }
                }
                val retainedContent = if (portal != null) {
                    rememberSideBySideCommentsContent(commentsController) {
                        TwoPaneCommentsSurface(commentsController, statusBarColor, statusBarHeight)
                    }
                } else null
                if (!commentsController.webViewFullscreen) {
                    CommentsScaffold(
                        controller = commentsController,
                        reserveUpButtonInset = showFloatingUpButton,
                        retainedContent = retainedContent,
                    )
                }
                val showStatusBarProtection = drawStatusBarProtection &&
                    !(commentsController.integratedWebView && commentsController.isScrolledToTop)
                // Ask lifts the overlay above the persistent controls and status bar protection.
                // Its scrim already dims them throughout the morph in both directions.
                val persistentControlScrimAlpha = if (commentsController.askSurfaceVisible) 0f
                    else modalScrimAlpha
                if (showStatusBarProtection && portal == null) {
                    StatusBarProtection(
                        color = statusBarColor,
                        statusBarHeight = statusBarHeight,
                        modalScrimAlpha = persistentControlScrimAlpha,
                    )
                }
                if (showFloatingUpButton) {
                    CommentsUpButton(
                        onClick = controller::closeStory,
                        modalScrimAlpha = persistentControlScrimAlpha,
                        modalScrimActive = modalOverlayVisible,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 4.dp)
                            .zIndex(101f),
                    )
                }
                if (modalOverlayVisible && portal == null) {
                    Box(Modifier.fillMaxSize().zIndex(
                        if (commentsController.askSurfaceVisible) 102f else 100f,
                    )) {
                        AndroidCommentLinkPreviewOverlay(
                            controller = commentsController,
                            onScrimAlphaChanged = { alpha -> modalScrimAlpha = alpha },
                        )
                        commentsController.displaySettings?.let { settings ->
                            AndroidCommentActionOverlay(
                                controller = commentsController,
                                settings = settings,
                                onScrimAlphaChanged = { alpha -> modalScrimAlpha = alpha },
                            )
                        }
                    }
                }
                if (!commentsController.webViewFullscreen && portal == null) {
                    CommentNavigationControls(
                        controller = commentsController,
                        modifier = Modifier
                            .zIndex(101f)
                            // The controls are hoisted for modal layering, so mirror the sheet's
                            // translation to keep them attached to the comments surface.
                            .graphicsLayer {
                                translationY = (
                                    1f - commentsController.sheetSlideOffset.coerceIn(0f, 1f)
                                ) * sheetTravelPx
                            },
                        modalScrimAlpha = persistentControlScrimAlpha,
                        modalScrimActive = modalOverlayVisible,
                    )
                }
            }
        }
    }
}

/** All comments-local UI travels with the retained list, including menus and long-press overlays. */
@Composable
private fun TwoPaneCommentsSurface(
    controller: CommentsScreenController,
    statusBarColor: Color,
    statusBarHeight: Dp,
) {
    CommentsHazeHost {
        Box(Modifier.fillMaxSize()) {
            var scrim by remember(controller) { mutableFloatStateOf(0f) }
            val modalVisible = !controller.searchDialogVisible &&
                (controller.linkPreviewOverlay != null || controller.isCommentActionOverlayShowing())
            LaunchedEffect(modalVisible) { if (!modalVisible) scrim = 0f }
            AndroidCommentsScreen(controller, reserveUpButtonInset = false)
            if (controller.sideBySideActive || !(controller.integratedWebView && controller.isScrolledToTop)) {
                StatusBarProtection(
                    color = statusBarColor,
                    statusBarHeight = statusBarHeight,
                    modalScrimAlpha = if (controller.askSurfaceVisible) 0f else scrim,
                )
            }
            if (modalVisible) {
                Box(Modifier.fillMaxSize().zIndex(if (controller.askSurfaceVisible) 102f else 100f)) {
                    AndroidCommentLinkPreviewOverlay(controller, onScrimAlphaChanged = { scrim = it })
                    controller.displaySettings?.let { settings ->
                        AndroidCommentActionOverlay(controller, settings, onScrimAlphaChanged = { scrim = it })
                    }
                }
            }
            CommentNavigationControls(
                controller = controller,
                modifier = Modifier.zIndex(101f),
                modalScrimAlpha = if (controller.askSurfaceVisible) 0f else scrim,
                modalScrimActive = modalVisible,
            )
        }
    }
}

internal const val LEGACY_COMMENTS_PANE_WEIGHT = 5f

@Suppress("DEPRECATION")
@Composable
private fun AskNavigationBarAppearance(window: Window, active: Boolean) {
    DisposableEffect(window, active) {
        if (!active) return@DisposableEffect onDispose { }
        val previousColor = window.navigationBarColor
        val previousContrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else null
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        onDispose {
            window.navigationBarColor = previousColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && previousContrast != null) {
                window.isNavigationBarContrastEnforced = previousContrast
            }
        }
    }
}
