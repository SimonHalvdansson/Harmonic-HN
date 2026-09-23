package com.simon.harmonichackernews.ui.navigation

import androidx.activity.compose.LocalActivity
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.ui.comments.AndroidCommentActionOverlay
import com.simon.harmonichackernews.ui.comments.AndroidCommentLinkPreviewOverlay
import com.simon.harmonichackernews.ui.comments.CommentsSheetCollapsedHeight
import com.simon.harmonichackernews.ui.comments.CommentNavigationControls
import com.simon.harmonichackernews.ui.comments.CommentsScaffold
import com.simon.harmonichackernews.ui.comments.CommentsHazeHost
import com.simon.harmonichackernews.ui.common.HazeHost
import com.simon.harmonichackernews.ui.comments.CommentsUpButton
import com.simon.harmonichackernews.ui.stories.StoryTapToUpdateButton
import com.simon.harmonichackernews.ui.stories.AndroidStoriesScreen
import com.simon.harmonichackernews.ui.stories.AndroidStoryPreviewOverlay
import com.simon.harmonichackernews.navigation.MainStoryRequest

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
    statusBarColor: Color = Color.Transparent,
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
    SideEffect { activeCoordinator.setExtraSidePadding(extraPaddingPx) }
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
    CommentsHazeHost {
        BoxWithConstraints(Modifier.fillMaxSize()) {
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
                            commentsController.commentActionOverlay != null
                    )
                LaunchedEffect(modalOverlayVisible) {
                    if (!modalOverlayVisible) modalScrimAlpha = 0f
                }
                if (!commentsController.webViewFullscreen) {
                    CommentsScaffold(
                        controller = commentsController,
                        reserveUpButtonInset = showFloatingUpButton,
                    )
                }
                val showStatusBarProtection = drawStatusBarProtection &&
                    !(commentsController.integratedWebView && commentsController.isScrolledToTop)
                if (showStatusBarProtection) {
                    StatusBarProtection(
                        color = statusBarColor,
                        statusBarHeight = statusBarHeight,
                        modalScrimAlpha = modalScrimAlpha,
                    )
                }
                if (showFloatingUpButton) {
                    CommentsUpButton(
                        onClick = controller::closeStory,
                        modalScrimAlpha = modalScrimAlpha,
                        modalScrimActive = modalOverlayVisible,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 4.dp)
                            .zIndex(101f),
                    )
                }
                if (modalOverlayVisible) {
                    Box(Modifier.fillMaxSize().zIndex(100f)) {
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
                if (!commentsController.webViewFullscreen) {
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
                        modalScrimAlpha = modalScrimAlpha,
                        modalScrimActive = modalOverlayVisible,
                    )
                }
            }
        }
    }
}

internal const val LEGACY_COMMENTS_PANE_WEIGHT = 5f
