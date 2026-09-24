@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.comments


import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.presentation.CommentsHeaderAction
import com.simon.harmonichackernews.presentation.CommentsMoreAction
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
fun OpFilterBanner(controller: CommentsScreenController) {
    val colors = HarmonicTheme.colors
    val bannerColor = if (HarmonicTheme.isDark) colors.surfaceContainerHigh else colors.secondaryContainer
    val contentColor = if (HarmonicTheme.isDark) colors.contentPrimary else colors.onSecondaryContainer
    AnimatedVisibility(
        visible = controller.opThreadFilterEnabled,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bannerColor)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Showing comment threads with OP",
                modifier = Modifier.weight(1f),
                color = contentColor,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            CommentsTooltip("Show all comments") {
                IconButton(
                    onClick = { controller.listener.onMoreAction(CommentsMoreAction.COMMENTS_BY_OP) },
                ) {
                    Icon(painterResource(Res.drawable.ic_close), contentDescription = "Show all comments", tint = contentColor)
                }
            }
        }
    }
}

private enum class HeaderStatusState {
    Loading,
    Failed,
    Empty,
    Refresh,
    None,
}

internal fun shouldShowCommentsHeaderLoading(
    loadingFailed: Boolean,
    pullToRefreshInProgress: Boolean,
    headerRefreshInProgress: Boolean,
    commentsLoaded: Boolean,
    initialThreadCached: Boolean,
): Boolean = !pullToRefreshInProgress &&
    (headerRefreshInProgress || (!loadingFailed && !commentsLoaded && !initialThreadCached))

@Composable
fun CommentsHeaderStatus(controller: CommentsScreenController, lastRefreshedText: String?) {
    val showLoading = !controller.refreshButtonInProgress && shouldShowCommentsHeaderLoading(
        loadingFailed = controller.loadingFailed,
        pullToRefreshInProgress = controller.pullToRefreshInProgress,
        headerRefreshInProgress = controller.headerRefreshInProgress,
        commentsLoaded = controller.commentsLoaded,
        initialThreadCached = controller.initialThreadCached,
    )
    val showEmpty = !controller.loadingFailed && controller.commentsLoaded &&
        controller.comments.size <= 1
    AnimatedContent(
        modifier = Modifier.fillMaxWidth(),
        targetState = when {
            showLoading -> HeaderStatusState.Loading
            controller.loadingFailed -> HeaderStatusState.Failed
            showEmpty -> HeaderStatusState.Empty
            controller.showRefreshPrompt -> HeaderStatusState.Refresh
            else -> HeaderStatusState.None
        },
        transitionSpec = {
            // One size animation moves the comments on every window size. Expanding each
            // child as well made AnimatedContent chase a changing height and clip the spinner.
            // The loading layer owns its exit alpha so its translated pixels are not
            // clipped by an outer fading layer as the header expands underneath it.
            val exit = if (initialState == HeaderStatusState.Loading) ExitTransition.None else fadeOut(tween(90))
            (fadeIn(tween(180, delayMillis = 80)) togetherWith exit).using(
                SizeTransform(clip = false) { _, _ ->
                    tween(260, easing = FastOutSlowInEasing)
                },
            )
        },
        label = "comments header status",
    ) { state ->
        when (state) {
            HeaderStatusState.Loading -> {
                val exitAlpha by transition.animateFloat(
                    transitionSpec = { tween(90) },
                    label = "stationary loading indicator exit",
                ) { if (it == EnterExitState.PostExit) 0f else 1f }
                HeaderLoadingStatus(
                    visible = showLoading,
                    commentsLoaded = controller.commentsLoaded,
                    exitAlpha = { exitAlpha },
                )
            }
            HeaderStatusState.Failed -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_cloud_off),
                    null,
                    Modifier.size(40.dp),
                    tint = HarmonicTheme.colors.textPrimary,
                )
                Text(
                    if (controller.loadingFailedServerError) "Loading failed" else "No internet connection",
                    color = HarmonicTheme.colors.contentPrimary,
                    modifier = Modifier.padding(top = 6.dp),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                )
                OutlinedButton(
                    onClick = {
                        controller.beginHeaderRefresh()
                        controller.listener.onHeaderAction(CommentsHeaderAction.REFRESH)
                    },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .height(48.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Try again",
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
            HeaderStatusState.Empty -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(painterResource(Res.drawable.ic_comment), null, Modifier.size(42.dp))
                Text(
                    if (controller.story.isComment) "No replies" else "No comments",
                    modifier = Modifier.padding(top = 4.dp),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
            HeaderStatusState.Refresh -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (lastRefreshedText != null) {
                    Text(
                        text = lastRefreshedText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = HarmonicTheme.colors.textSecondary,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        controller.beginHeaderRefresh()
                        controller.listener.onHeaderAction(CommentsHeaderAction.REFRESH)
                    },
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = HarmonicTheme.colors.overlayButton,
                    contentColor = HarmonicTheme.colors.overlayButtonContent,
                    icon = {
                        Icon(painterResource(Res.drawable.ic_refresh), contentDescription = null)
                    },
                    text = {
                        Text(
                            "Tap to refresh",
                            fontFamily = ProductSansFontFamily,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                )
            }
            HeaderStatusState.None -> Spacer(Modifier.height(0.dp))
        }
    }
}

@Composable
private fun HeaderLoadingStatus(
    visible: Boolean,
    commentsLoaded: Boolean,
    exitAlpha: () -> Float,
) {
    // Retain the loading layout throughout its exit; newly loaded comments must not
    // change its padding. The header above can grow while this layer fades in place.
    val topPadding = remember { if (commentsLoaded) 16.dp else 44.dp }
    var lastVisiblePosition by remember { mutableStateOf<Offset?>(null) }
    var exitTranslation by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxWidth()
            .onPlaced { coordinates ->
                val position = coordinates.positionInRoot()
                if (visible) lastVisiblePosition = position
                exitTranslation = if (visible) Offset.Zero else {
                    lastVisiblePosition?.minus(position) ?: Offset.Zero
                }
            }
            .graphicsLayer {
                alpha = exitAlpha()
                translationX = exitTranslation.x
                translationY = exitTranslation.y
            }
            .padding(top = topPadding, bottom = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        HarmonicLoadingIndicator(Modifier.size(42.dp).testTag("comments-loading-indicator"))
    }
}
