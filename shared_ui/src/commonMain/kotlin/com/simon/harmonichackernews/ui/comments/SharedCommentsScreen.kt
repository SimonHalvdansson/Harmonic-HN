package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.presentation.CommentsHeaderAction
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.presentation.CommentNavigationEdge
import com.simon.harmonichackernews.presentation.PortableVisibleComment
import com.simon.harmonichackernews.ui.common.SharedLazyContentList
import com.simon.harmonichackernews.ui.content.CommentItem
import com.simon.harmonichackernews.ui.content.CommentItemStyle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import org.jetbrains.compose.resources.painterResource

private const val COMMENT_NAVIGATION_SPEED_STEP = 50

private suspend fun LazyListState.animateToCommentNavigationTarget(
    index: Int,
    scrollOffset: Int,
    scaleLongScrollSpeed: Boolean,
) {
    if (!scaleLongScrollSpeed) {
        animateScrollToItem(index, scrollOffset)
        return
    }

    val distanceItems = abs(index - firstVisibleItemIndex)
    if (distanceItems <= COMMENT_NAVIGATION_SPEED_STEP) {
        animateScrollToItem(index, scrollOffset)
        return
    }

    // LazyListState's built-in animation is intentionally conservative for very distant targets.
    // Estimate the pixel distance from the currently composed rows and animate that distance with
    // a duration scaled in the same 50-item steps as the old RecyclerView implementation. The
    // final snap handles variable-height comment rows and the header exactly.
    val averageItemSize = layoutInfo.visibleItemsInfo
        .asSequence()
        .filter { it.index > 0 && it.size > 0 }
        .map { it.size }
        .average()
        .toFloat()
        .takeIf { it > 0f }
        ?: 1f
    val estimatedDistance =
        (index - firstVisibleItemIndex) * averageItemSize -
            firstVisibleItemScrollOffset - scrollOffset
    val speedMultiplier = ((distanceItems - 1) / COMMENT_NAVIGATION_SPEED_STEP) + 1
    val baseDuration = (distanceItems * 16).coerceIn(240, 1000)
    val durationMillis = (baseDuration / speedMultiplier).coerceIn(180, 520)

    scroll {
        var previousValue = 0f
        animate(
            initialValue = 0f,
            targetValue = estimatedDistance,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing,
            ),
        ) { value, _ ->
            scrollBy(value - previousValue)
            previousValue = value
        }
    }
    scrollToItem(index, scrollOffset)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedCommentsScreen(
    controller: CommentsComposeController,
    listModifier: Modifier,
    animateComments: Boolean,
    showScrollbar: Boolean,
    smoothScroll: Boolean,
    userTags: Map<String, String>,
    onOpenLink: (String) -> Unit,
    headerContent: @Composable () -> Unit,
    searchDialog: @Composable () -> Unit,
    actionOverlay: @Composable () -> Unit,
) {
    val settings = controller.displaySettings
    if (settings == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HarmonicTheme.colors.background),
            contentAlignment = Alignment.Center,
        ) {
            HarmonicLoadingIndicator(Modifier.size(42.dp))
        }
        return
    }

    val commentsHazeState = currentCommentsHazeState()
    val listState = rememberLazyListState()
    val visibleComments = controller.visibleComments
    val density = LocalDensity.current
    val topInsetPx = WindowInsets.statusBars.getTop(density)
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = navigationBottom + if (settings.showNavigationBar) 88.dp else 16.dp
    val contentInsetStart = with(density) { controller.contentInsetLeftPx.toDp() }
    val contentInsetEnd = with(density) { controller.contentInsetRightPx.toDp() }

    LaunchedEffect(listState, visibleComments) {
        snapshotFlow {
            val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            val coverage = if (header == null || topInsetPx <= 0) {
                0f
            } else {
                val overlap = minOf(header.offset + header.size, topInsetPx) -
                    maxOf(header.offset, 0)
                (overlap.toFloat() / topInsetPx).coerceIn(0f, 1f)
            }
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                coverage,
            )
        }.distinctUntilChanged().collect { (_, _, coverage) ->
            controller.updateScrollPosition(listState, visibleComments.map { item -> item.comment })
            controller.updateStatusBarHeaderCoverage(coverage)
            controller.listener.onHeaderCoverageChanged(coverage)
        }
    }

    val navigationRequest = controller.navigationRequest
    LaunchedEffect(navigationRequest, visibleComments) {
        val request = navigationRequest ?: return@LaunchedEffect
        val target = when (request.edge) {
            CommentNavigationEdge.First -> -1
            CommentNavigationEdge.Last ->
                visibleComments.indexOfLast { it.comment.depth == 0 }.coerceAtLeast(0)
            null -> findNavigationTarget(
                state = listState,
                comments = visibleComments,
                forward = request.forward,
                topLevelOnly = request.topLevelOnly,
                topOffsetPx = topInsetPx,
            )
        }
        val listIndex = target + 1
        val scrollOffset = if (listIndex == 0) 0 else -topInsetPx
        if (request.animate) {
            listState.animateToCommentNavigationTarget(
                index = listIndex,
                scrollOffset = scrollOffset,
                scaleLongScrollSpeed = request.scaleLongScrollSpeed,
            )
        } else {
            listState.scrollToItem(listIndex, scrollOffset)
        }
        controller.consumeNavigationRequest(request)
    }

    val websiteRequest = controller.showWebsiteRequest
    LaunchedEffect(websiteRequest) {
        if (websiteRequest > 0) {
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.animateScrollToItem(0)
            }
            controller.listener.onCollapseSheetForWebsite()
        }
    }

    val stopScrollRequest = controller.stopScrollRequest
    LaunchedEffect(stopScrollRequest) {
        if (stopScrollRequest > 0) listState.stopScroll()
    }

    val scrollToCommentRequest = controller.scrollToCommentRequest
    LaunchedEffect(scrollToCommentRequest, visibleComments) {
        val request = scrollToCommentRequest ?: return@LaunchedEffect
        val listIndex = if (request.commentId == 0) {
            0
        } else {
            visibleComments.indexOfFirst { it.comment.id == request.commentId }
                .takeIf { it >= 0 }
                ?.plus(1)
        }
        if (listIndex != null) {
            val scrollOffset = -request.topOffsetPx
            if (request.animate) {
                listState.animateScrollToItem(listIndex, scrollOffset)
            } else {
                listState.scrollToItem(listIndex, scrollOffset)
            }
            if (request.searchResult) {
                controller.revealSearchResult(request.commentId, listIndex)
            }
        }
        controller.consumeScrollToCommentRequest(request)
    }

    val highlightedCommentId = controller.highlightedCommentId
    LaunchedEffect(highlightedCommentId) {
        if (highlightedCommentId > 0) {
            delay(1_200)
            controller.clearSearchHighlight(highlightedCommentId)
        }
    }

    val searchScrollTopTargetId = controller.searchScrollTopTargetId
    val searchTargetVisible by remember(searchScrollTopTargetId, listState) {
        derivedStateOf {
            searchScrollTopTargetId > 0 &&
                listState.layoutInfo.visibleItemsInfo.any { it.key == searchScrollTopTargetId }
        }
    }
    LaunchedEffect(searchScrollTopTargetId, listState) {
        if (searchScrollTopTargetId <= 0) return@LaunchedEffect
        var wasVisible = false
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.any { it.key == searchScrollTopTargetId }
        }.distinctUntilChanged().collect { visible ->
            if (visible) {
                wasVisible = true
            } else if (wasVisible) {
                controller.clearSearchScrollTopTarget()
            }
        }
    }

    val list: @Composable () -> Unit = {
        SharedLazyContentList(
            items = visibleComments,
            key = { item -> item.comment.id },
            contentType = { if (settings.cardStyle) "comment-card" else "comment" },
            modifier = Modifier
                .fillMaxSize()
                .background(HarmonicTheme.colors.background)
                .then(listModifier),
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding),
            headerKey = "header",
            header = headerContent,
        ) { _, item ->
                DisposableEffect(item.comment.id) {
                    onDispose { controller.removeCommentBounds(item.comment.id) }
                }
                val tag = item.comment.by?.lowercase()?.trim()?.let(userTags::get)
                CommentItem(
                    comment = item.comment,
                    style = CommentItemStyle(
                        cardStyle = settings.cardStyle,
                        showCardBorder = settings.cardBorder,
                        textSize = settings.preferredTextSize,
                        collectLinks = settings.collectReferenceLinks,
                        emphasizeMeta = settings.highlightCommentMeta,
                        depthIndicatorMode = settings.commentDepthIndicatorMode,
                        showDivider = settings.showDividers,
                        preferredFont = settings.font,
                        animateChanges = animateComments,
                    ),
                    storyAuthor = controller.story.by,
                    accountUser = controller.accountUser,
                    userTag = tag,
                    hiddenReplyCount = item.hiddenReplyCount,
                    collapseParent = settings.collapseParent,
                    showTopLevelIndicator = settings.showTopLevelDepthIndicator,
                    highlighted = item.comment.id == controller.highlightedCommentId,
                    modifier = Modifier
                        .padding(start = contentInsetStart, end = contentInsetEnd)
                        .graphicsLayer(
                            alpha = if (item.comment.id in controller.suppressedCommentIds) 0f else 1f,
                        )
                        .onGloballyPositioned { coordinates ->
                            controller.updateCommentBounds(
                                item.comment.id,
                                coordinates.boundsInWindow(),
                            )
                        }
                        .then(if (animateComments) Modifier.animateItem() else Modifier),
                    onToggleExpanded = {
                        if (settings.swapLongPressTap) {
                            controller.showCommentActions(item.comment)
                        } else {
                            controller.listener.onToggleComment(item.comment, item.sourceIndex)
                        }
                    },
                    onShowActions = {
                        if (settings.swapLongPressTap) {
                            controller.listener.onToggleComment(item.comment, item.sourceIndex)
                        } else {
                            controller.showCommentActions(item.comment)
                        }
                    },
                    onLinkLongClick = { url, title, bounds ->
                        controller.showReferencePreview(
                            url = url,
                            title = title,
                            sourceBounds = bounds,
                            sourceCommentId = item.comment.id,
                        )
                    },
                    onReferenceLongClick = { link, bounds ->
                        controller.showReferencePreview(
                            link = link,
                            sourceBounds = bounds,
                            sourceCommentId = item.comment.id,
                        )
                    },
                    onLinkClick = onOpenLink,
                )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .commentsHazeSource(commentsHazeState),
    ) {
        if (controller.integratedWebView) {
            list()
        } else {
            PullToRefreshBox(
                isRefreshing = controller.commentsRefreshInProgress,
                onRefresh = { controller.listener.onHeaderAction(CommentsHeaderAction.REFRESH) },
                modifier = Modifier.fillMaxSize(),
            ) {
                list()
            }
        }

        AnimatedVisibility(
            visible = settings.showNavigationBar && visibleComments.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navigationBottom + 16.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            CommentNavigationButtons(
                onPrevious = { controller.navigatePrevious(true, false) },
                onNext = { controller.navigateNext(true, false) },
                onFirst = {
                    controller.navigationRequest?.let(controller::consumeNavigationRequest)
                    controller.navigateFirst()
                },
                onLast = controller::navigateLast,
            )
        }

        if (showScrollbar && controller.sheetSlideOffset >= 0.999f) {
            CommentsScrollbar(
                state = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = searchTargetVisible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = navigationBottom + if (settings.showNavigationBar) 88.dp else 16.dp,
                ),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (smoothScroll) {
                        controller.scrollToComment(0)
                    } else {
                        controller.scrollToComment(0, 0, false)
                    }
                    controller.clearSearchScrollTopTarget()
                },
                icon = {
                    Icon(
                        painterResource(Res.drawable.ic_arrow_upward),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                },
                text = {
                    Text(
                        "Scroll to top",
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                },
                containerColor = HarmonicTheme.colors.overlayButton,
                contentColor = Color.White,
                // Keep the shadow present but constant. AnimatedVisibility owns the appearance
                // transition, so Material's interaction elevation cannot flash it on entry.
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 3.dp,
                    pressedElevation = 3.dp,
                    focusedElevation = 3.dp,
                    hoveredElevation = 3.dp,
                ),
            )
        }
    }

    if (controller.searchDialogVisible) searchDialog()
    actionOverlay()
}

@Composable
private fun CommentsScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val metrics by remember(state) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems <= 0 || visibleItems.isEmpty() || visibleItems.size >= totalItems) {
                return@derivedStateOf null
            }

            val first = visibleItems.first()
            val averageItemSize = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
            val firstFraction = if (first.size == 0) 0f else {
                (-first.offset).coerceAtLeast(0).toFloat() / first.size
            }
            ScrollbarMetrics(
                scrollPosition = (first.index + firstFraction) / totalItems,
                visibleFraction = (layoutInfo.viewportSize.height / (averageItemSize * totalItems))
                    .coerceIn(0.04f, 1f),
            )
        }
    }
    val currentMetrics = metrics ?: return
    val thumbColor = HarmonicTheme.colors.storyDisabled.copy(alpha = 0.55f)
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        val widthPx = with(density) { 3.dp.toPx() }
        val endPaddingPx = with(density) { 1.dp.toPx() }
        val minimumHeightPx = with(density) { 24.dp.toPx() }
        val thumbHeight = (size.height * currentMetrics.visibleFraction).coerceAtLeast(minimumHeightPx)
        val top = ((size.height - thumbHeight) * currentMetrics.scrollPosition)
            .coerceIn(0f, size.height - thumbHeight)
        drawRoundRect(
            color = thumbColor,
            topLeft = androidx.compose.ui.geometry.Offset(size.width - widthPx - endPaddingPx, top),
            size = androidx.compose.ui.geometry.Size(widthPx, thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(widthPx / 2f),
        )
    }
}

private data class ScrollbarMetrics(
    val scrollPosition: Float,
    val visibleFraction: Float,
)

@Composable
fun EmptyCommentsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(Res.drawable.ic_newspaper),
            contentDescription = null,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .size(48.dp),
            tint = HarmonicTheme.colors.drawable,
        )
        Text(
            "Open a story",
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun findNavigationTarget(
    state: LazyListState,
    comments: List<PortableVisibleComment>,
    forward: Boolean,
    topLevelOnly: Boolean,
    topOffsetPx: Int,
): Int {
    if (comments.isEmpty()) return -1

    // The header is a separate LazyColumn item. Use the last comment whose top has reached the
    // navigation anchor as the current position; the first comment may be well below the header
    // while the header is still visible. This also keeps a preceding reply from becoming the
    // current target when a top-level comment is anchored just below the inset.
    val current = state.layoutInfo.visibleItemsInfo
        .asSequence()
        .filter { item ->
            item.index > 0 &&
                item.index - 1 in comments.indices &&
                item.offset <= topOffsetPx
        }
        .maxByOrNull { it.index }
        ?.index
        ?.minus(1)
        ?: -1
    val range = if (forward) {
        (current + 1)..comments.lastIndex
    } else {
        (current - 1 downTo 0)
    }
    for (index in range) {
        if (!topLevelOnly || comments[index].comment.depth == 0) return index
    }
    return if (forward) comments.lastIndex else -1
}

@Composable
private fun CommentNavigationButtons(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFirst: () -> Unit,
    onLast: () -> Unit,
) {
    Row(
        modifier = Modifier
            .shadow(8.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(HarmonicTheme.colors.overlayButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .combinedClickable(onClick = onPrevious, onLongClick = onFirst),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(Res.drawable.ic_keyboard_arrow_up_dark), "Previous top-level comment", tint = Color.Unspecified)
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(Res.drawable.ic_explore_dark),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color.Unspecified,
            )
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .combinedClickable(onClick = onNext, onLongClick = onLast),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(Res.drawable.ic_keyboard_arrow_down_dark), "Next top-level comment", tint = Color.Unspecified)
        }
    }
}
