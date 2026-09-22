package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.presentation.CommentsHeaderAction
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyLayoutScrollScope
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import com.simon.harmonichackernews.ui.common.HarmonicPullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.presentation.CommentNavigationEdge
import com.simon.harmonichackernews.presentation.PortableVisibleComment
import com.simon.harmonichackernews.ui.common.LazyContentList
import com.simon.harmonichackernews.ui.common.ModalControlScrim
import com.simon.harmonichackernews.ui.common.consumeAllPointerGestures
import com.simon.harmonichackernews.ui.content.CommentItem
import com.simon.harmonichackernews.ui.content.CommentItemStyleContext
import com.simon.harmonichackernews.ui.content.contentTween
import com.simon.harmonichackernews.ui.content.toCommentItemStyle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.ui.common.LocalHazeGlassEnabled
import com.simon.harmonichackernews.ui.common.HazeGlassAppearance
import com.simon.harmonichackernews.ui.common.sharedHazeBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val COMMENT_NAVIGATION_SPEED_STEP = 50
private const val COMMENT_NAVIGATION_APPROACH_VIEWPORTS = 3f
// Comment rows can be several viewports tall; prepare the next rows before they enter the screen.
private const val COMMENTS_CACHE_AHEAD_FRACTION = 2f
private const val COMMENTS_CACHE_BEHIND_FRACTION = 0.5f
private val COMMENTS_UP_BUTTON_NAVIGATION_INSET = 64.dp
private const val COMMENT_PLACEMENT_DURATION_MILLIS = 220

/** Only structural list changes need placement motion; animated row/header sizes already move it. */
@Composable
private fun rememberCommentPlacementAnimation(
    visibleComments: List<PortableVisibleComment>,
    enabled: Boolean,
): Boolean {
    val visibleIds = remember(visibleComments) { visibleComments.map { it.comment.id } }
    var previousIds by remember { mutableStateOf(visibleIds) }
    var animating by remember { mutableStateOf(false) }
    val visibilityChanged = previousIds != visibleIds && previousIds.isNotEmpty() && visibleIds.isNotEmpty()
    LaunchedEffect(visibleIds, enabled) {
        val animateChange = enabled && visibilityChanged
        previousIds = visibleIds
        animating = animateChange
        if (animateChange) {
            // Leave time for the next lazy-list measure to start the placement tween. Using the
            // animation clock (rather than delay) respects the device's animation duration scale.
            Animatable(0f).animateTo(1f, tween(COMMENT_PLACEMENT_DURATION_MILLIS + 100))
            animating = false
        }
    }
    // Enable on the composition that receives the changed list, before its first measure.
    return enabled && (visibilityChanged || animating)
}

internal fun commentScrollTopOffset(
    requestedTopOffsetPx: Int,
    searchResult: Boolean,
    navigationTopOffsetPx: Int,
    restorePosition: Boolean = false,
): Int = when {
    restorePosition -> requestedTopOffsetPx
    searchResult -> navigationTopOffsetPx
    else -> maxOf(requestedTopOffsetPx, navigationTopOffsetPx)
}

internal suspend fun LazyListState.animateToCommentNavigationTarget(
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

    // Scrolling an estimated whole-thread distance makes the lazy list measure every skipped
    // row, often hundreds in one frame. Resolve the destination first, then animate only the
    // final few viewports. Pixel bounds also cover comments that are taller than the screen.
    val forward = index > firstVisibleItemIndex
    val viewportSize = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .coerceAtLeast(0)
    val speedMultiplier = ((distanceItems - 1) / COMMENT_NAVIGATION_SPEED_STEP) + 1
    val baseDuration = (distanceItems * 16).coerceIn(240, 1000)
    val durationMillis = (baseDuration / speedMultiplier).coerceIn(180, 520)

    scroll {
        val lazyScrollScope = LazyLayoutScrollScope(this@animateToCommentNavigationTarget, this)
        lazyScrollScope.snapToItem(index, scrollOffset)
        // Both moves happen in the same scroll session before the next frame. Use the consumed
        // distance so either end of the list (including a short final comment) clamps naturally.
        val approachPixels = viewportSize * COMMENT_NAVIGATION_APPROACH_VIEWPORTS
        val approachDistance = -scrollBy(if (forward) -approachPixels else approachPixels)
        var previousValue = 0f
        animate(
            initialValue = 0f,
            targetValue = approachDistance,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing,
            ),
        ) { value, _ ->
            scrollBy(value - previousValue)
            previousValue = value
        }
        lazyScrollScope.snapToItem(index, scrollOffset)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    controller: CommentsComposeController,
    listModifier: Modifier,
    reserveUpButtonInset: Boolean,
    pullToRefreshEnabled: Boolean = true,
    showNavigationControls: Boolean = true,
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
                .background(HarmonicTheme.colors.settingsPageBackground),
            contentAlignment = Alignment.Center,
        ) {
            HarmonicLoadingIndicator(Modifier.size(42.dp))
        }
        return
    }

    val colors = HarmonicTheme.colors
    val commentsHazeState = currentCommentsHazeState()
    val listState = rememberLazyListState(
        cacheWindow = LazyLayoutCacheWindow(
            aheadFraction = COMMENTS_CACHE_AHEAD_FRACTION,
            behindFraction = COMMENTS_CACHE_BEHIND_FRACTION,
        ),
    )
    val pullToRefreshState = rememberPullToRefreshState()
    val visibleComments = controller.visibleComments
    val animatedRows = rememberAnimatedCommentRows(visibleComments, listState, animateComments)
    val animateCommentPlacement = rememberCommentPlacementAnimation(visibleComments, animateComments)
    com.simon.harmonichackernews.ui.content.PrefetchCommentContent(
        listState = listState,
        comments = remember(visibleComments) { visibleComments.map { it.comment } },
        collectLinks = settings.collectReferenceLinks,
        headerItems = 1,
    )
    val density = LocalDensity.current
    val topInsetPx = WindowInsets.statusBars.getTop(density)
    val navigationTopOffsetPx = topInsetPx + if (reserveUpButtonInset) {
        with(density) { COMMENTS_UP_BUTTON_NAVIGATION_INSET.roundToPx() }
    } else {
        0
    }
    val statusBarInset = with(density) { topInsetPx.toDp() }
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navigationVisible = settings.showNavigationBar && visibleComments.size > 1
    val bottomPadding = navigationBottom + if (navigationVisible) 88.dp else 16.dp
    val contentInsetStart = with(density) { controller.contentInsetLeftPx.toDp() }
    val contentInsetEnd = with(density) { controller.contentInsetRightPx.toDp() }
    val itemStyle = remember(settings, animateComments) {
        settings.toCommentItemStyle(
            CommentItemStyleContext.Thread(animateChanges = animateComments),
        )
    }

    LaunchedEffect(listState, visibleComments, animatedRows.exitingIds) {
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
            // During collapse, lazy indices also contain retained exiting children.
            if (animatedRows.exitingIds.isEmpty()) controller.updateScrollPosition(listState, visibleComments)
            controller.updateStatusBarHeaderCoverage(coverage)
            controller.listener.onHeaderCoverageChanged(coverage)
        }
    }

    val navigationRequest = controller.navigationRequest
    LaunchedEffect(navigationRequest, visibleComments, navigationTopOffsetPx) {
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
                topOffsetPx = navigationTopOffsetPx,
            )
        }
        val listIndex = target + 1
        val scrollOffset = if (listIndex == 0) 0 else -navigationTopOffsetPx
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
    LaunchedEffect(scrollToCommentRequest, visibleComments, navigationTopOffsetPx) {
        val request = scrollToCommentRequest ?: return@LaunchedEffect
        val listIndex = if (request.commentId == 0) {
            0
        } else {
            visibleComments.indexOfFirst { it.comment.id == request.commentId }
                .takeIf { it >= 0 }
                ?.plus(1)
        }
        if (listIndex != null) {
            val scrollOffset = if (listIndex == 0) -request.topOffsetPx else -commentScrollTopOffset(
                requestedTopOffsetPx = request.topOffsetPx,
                searchResult = request.searchResult,
                navigationTopOffsetPx = navigationTopOffsetPx,
                restorePosition = request.restorePosition,
            )
            if (request.animate) {
                listState.animateToCommentNavigationTarget(
                    index = listIndex,
                    scrollOffset = scrollOffset,
                    scaleLongScrollSpeed = controller.initialScrollRestorationPending,
                )
            } else {
                listState.scrollToItem(listIndex, scrollOffset)
            }
            if (request.searchResult) {
                controller.revealSearchResult(request.commentId, listIndex)
            }
        }
        controller.consumeScrollToCommentRequest(request)
        controller.completeInitialScrollRestoration()
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
        LazyContentList(
            items = animatedRows.rows,
            key = { item -> item.comment.id },
            contentType = { if (settings.hasBackground) "comment-card" else "comment" },
            modifier = Modifier
                .fillMaxSize()
                .background(HarmonicTheme.colors.settingsPageBackground)
                // Hide the provisional position while a saved reading position is applied.
                .graphicsLayer {
                    alpha = if (
                        controller.initialScrollRestorationPending &&
                        !controller.loadingFailed &&
                        visibleComments.isNotEmpty() &&
                        scrollToCommentRequest?.animate != true
                    ) 0f else 1f
                }
                .then(listModifier)
                .commentsHazeSource(commentsHazeState),
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding),
            headerKey = "header",
            header = {
                Column {
                    headerContent()
                    AnimatedVisibility(
                        visible = controller.usingOfficialApiFallback,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        AlgoliaFallbackNotice()
                    }
                }
            },
        ) { index, item ->
                val exiting = item.comment.id in animatedRows.exitingIds
                val tag = item.comment.by?.lowercase()?.trim()?.let(userTags::get)
                val suppressed = item.comment.id in controller.suppressedCommentIds
                val keepActionSourceVisible =
                    controller.shouldKeepCommentActionSourceVisible(item.comment.id)
                val suppressRow = suppressed && !keepActionSourceVisible
                val suppressedReferenceUrl =
                    controller.suppressedReferenceUrlForComment(item.comment.id)
                CommentItem(
                    comment = item.comment,
                    style = itemStyle,
                    storyAuthor = controller.story.by,
                    accountUser = controller.accountUser,
                    userTag = tag,
                    hiddenReplyCount = item.hiddenReplyCount,
                    collapseParent = settings.collapseParent,
                    showTopLevelIndicator = settings.showTopLevelDepthIndicator,
                    nextCommentDepth = animatedRows.rows.getOrNull(index + 1)?.comment?.depth,
                    highlighted = item.comment.id == controller.highlightedCommentId,
                    suppressedReferenceUrl = suppressedReferenceUrl,
                    captureActionSource =
                        item.comment.id == controller.getVisibleCommentActionId(),
                    suppressActionSource = suppressRow,
                    showActionsOnClick = settings.swapLongPressTap,
                    modifier = Modifier
                        .testTag("comment-row")
                        .padding(start = contentInsetStart, end = contentInsetEnd)
                        .then(if (exiting) Modifier
                            .clearAndSetSemantics { }
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val clippedTop = animatedRows.exitGeometry.getValue(item.comment.id)
                                    .clippedTop(animatedRows.exitProgress()).coerceAtMost(placeable.height)
                                layout(placeable.width, placeable.height - clippedTop) {
                                    placeable.placeRelative(0, -clippedTop)
                                }
                            }
                            .graphicsLayer { alpha = animatedRows.exitProgress() }
                        else Modifier)
                        // A subtree entering/leaving the list needs sibling placement motion.
                        // Otherwise follow animated header/body bounds directly without a second
                        // spring making rows lag behind the header when cached threads reopen.
                        .then(if (animateComments) Modifier.animateItem(
                            placementSpec = if (animateCommentPlacement && animatedRows.exitingIds.isEmpty()) {
                                contentTween()
                            } else {
                                null
                            },
                        ) else Modifier),
                    onToggleExpanded = { sourceBounds ->
                        if (settings.swapLongPressTap) {
                            controller.showCommentActions(item.comment, sourceBounds)
                        } else {
                            controller.listener.onToggleComment(item.comment, item.sourceIndex)
                        }
                    },
                    onShowActions = { sourceBounds ->
                        if (settings.swapLongPressTap) {
                            controller.listener.onToggleComment(item.comment, item.sourceIndex)
                        } else {
                            controller.showCommentActions(item.comment, sourceBounds)
                        }
                    },
                    onActionSourceGeometryChanged = { geometry ->
                        controller.updateCommentActionSourceGeometry(item.comment.id, geometry)
                    },
                    onLinkLongClick = { url, title, bounds ->
                        controller.showReferencePreview(
                            url = url,
                            title = title,
                            sourceBounds = bounds,
                            sourceCommentId = item.comment.id,
                        )
                    },
                    onReferenceLongClick = { link, bounds, sourceContentLayer ->
                        controller.showReferencePreview(
                            link = link,
                            sourceBounds = bounds,
                            sourceCommentId = item.comment.id,
                            sourceContainerColor = if (settings.hasBackground) {
                                colors.storyCardBackground
                            } else {
                                colors.settingsPageBackground
                            },
                            sourceContentLayer = sourceContentLayer,
                        )
                    },
                    onLinkClick = onOpenLink,
                )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LaunchedEffect(controller.commentsRefreshInProgress) {
            if (!controller.commentsRefreshInProgress) {
                controller.finishPullToRefresh()
            }
        }

        if (controller.integratedWebView || !pullToRefreshEnabled) {
            list()
        } else {
            PullToRefreshBox(
                isRefreshing = controller.pullToRefreshInProgress &&
                    controller.commentsRefreshInProgress,
                state = pullToRefreshState,
                indicator = {
                    HarmonicPullToRefreshIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = statusBarInset),
                        isRefreshing = controller.pullToRefreshInProgress &&
                            controller.commentsRefreshInProgress,
                        state = pullToRefreshState,
                    )
                },
                onRefresh = {
                    controller.beginPullToRefresh()
                    controller.listener.onHeaderAction(CommentsHeaderAction.REFRESH)
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                list()
            }
        }

        if (showNavigationControls) CommentNavigationControls(controller)

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
                    bottom = navigationBottom + if (navigationVisible) 88.dp else 16.dp,
                ),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val scrollTopShape = RoundedCornerShape(16.dp)
            val scrollTopSurface = HarmonicTheme.colors.overlayButton.copy(alpha = 0.8f)
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
                        tint = HarmonicTheme.colors.overlayButtonContent.copy(alpha = 0.8f),
                    )
                },
                text = {
                    Text(
                        "Scroll to top",
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                },
                modifier = Modifier
                    // A drag cancels the click and stays on the button, without moving
                    // either the comments list or its parent sheet.
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ -> change.consume() }
                    }
                    .shadow(3.dp, scrollTopShape, clip = false)
                    .sharedHazeBackground(
                        glassAppearance = HazeGlassAppearance.FloatingButton,
                        hazeState = commentsHazeState,
                        surfaceColor = scrollTopSurface,
                        shape = scrollTopShape,
                    ),
                shape = scrollTopShape,
                containerColor = Color.Transparent,
                contentColor = HarmonicTheme.colors.overlayButtonContent,
                // Keep the shadow present but constant. AnimatedVisibility owns the appearance
                // transition, so Material's interaction elevation cannot flash it on entry.
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
            )
        }
    }

    if (controller.searchDialogVisible) searchDialog()
    actionOverlay()
}

/**
 * Persistent comment navigation chrome. Hosts with pane-level modal transforms can place this as
 * their later sibling so captured source content always travels underneath the controls.
 */
@Composable
fun BoxScope.CommentNavigationControls(
    controller: CommentsComposeController,
    modifier: Modifier = Modifier,
    modalScrimAlpha: Float = 0f,
    modalScrimActive: Boolean = modalScrimAlpha > 0f,
) {
    val settings = controller.displaySettings ?: return
    val navigationBottom = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    AnimatedVisibility(
        visible = settings.showNavigationBar && controller.visibleComments.size > 1,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = navigationBottom),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        // Keep the shadow inside AnimatedVisibility's bounds while its layer fades in.
        Box(modifier = Modifier.padding(16.dp)) {
            CommentNavigationButtons(
                onPrevious = { controller.navigatePrevious(true, false) },
                onNext = { controller.navigateNext(true, false) },
                onFirst = {
                    controller.navigationRequest?.let(controller::consumeNavigationRequest)
                    controller.navigateFirst()
                },
                onLast = controller::navigateLast,
                modalScrimAlpha = modalScrimAlpha,
                modalScrimActive = modalScrimActive,
            )
        }
    }
}

@Composable
private fun AlgoliaFallbackNotice() {
    val colors = HarmonicTheme.colors
    val containerColor = if (colors.background.luminance() > colors.onSurface.luminance()) {
        colors.surfaceContainerHighest
    } else {
        colors.surfaceContainerHigh
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.comments_algolia_fallback),
            color = colors.textSecondary,
            fontFamily = ProductSansFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CommentsScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val metrics by remember(state) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems <= 0 || visibleItems.isEmpty()) {
                return@derivedStateOf null
            }

            val first = visibleItems.first()
            val last = visibleItems.last()
            val firstFraction = if (first.size == 0) 0f else {
                ((layoutInfo.viewportStartOffset - first.offset).toFloat() / first.size)
                    .coerceIn(0f, 1f)
            }
            val lastFraction = if (last.size == 0) 1f else {
                ((layoutInfo.viewportEndOffset - last.offset).toFloat() / last.size)
                    .coerceIn(0f, 1f)
            }
            commentsScrollbarMetrics(
                totalItems = totalItems,
                firstIndex = first.index,
                firstFraction = firstFraction,
                lastIndex = last.index,
                lastFraction = lastFraction,
                canScrollBackward = state.canScrollBackward,
                canScrollForward = state.canScrollForward,
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
        val thumbHeight = (size.height * currentMetrics.visibleFraction)
            .coerceAtLeast(minimumHeightPx).coerceAtMost(size.height)
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

@Composable
fun EmptyCommentsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.settingsPageBackground),
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
    modalScrimAlpha: Float = 0f,
    modalScrimActive: Boolean = modalScrimAlpha > 0f,
) {
    val shape = RoundedCornerShape(28.dp)
    val navigationRipple = ripple(bounded = true, radius = 48.dp)
    val hazeState = currentCommentsHazeState()
    val surfaceColor = HarmonicTheme.colors.overlayButton.copy(alpha = 0.8f)

    Box {
        Row(
            modifier = Modifier
                .shadow(if (LocalHazeGlassEnabled.current) 2.dp else 6.dp, shape, clip = false)
                .sharedHazeBackground(
                    glassAppearance = HazeGlassAppearance.FloatingButton,
                    hazeState = hazeState,
                    surfaceColor = surfaceColor,
                    shape = shape,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(
                        topStart = 28.dp,
                        bottomStart = 28.dp,
                        topEnd = 12.dp,
                        bottomEnd = 12.dp,
                    ))
                    .combinedClickable(
                        interactionSource = null,
                        indication = navigationRipple,
                        onClick = onPrevious,
                        onLongClick = onFirst,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_keyboard_arrow_up_dark),
                    "Previous top-level comment",
                    tint = HarmonicTheme.colors.overlayButtonContent,
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .consumeAllPointerGestures(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_explore_dark),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = HarmonicTheme.colors.overlayButtonContent,
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(
                        topStart = 12.dp,
                        bottomStart = 12.dp,
                        topEnd = 28.dp,
                        bottomEnd = 28.dp,
                    ))
                    .combinedClickable(
                        interactionSource = null,
                        indication = navigationRipple,
                        onClick = onNext,
                        onLongClick = onLast,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_keyboard_arrow_down_dark),
                    "Next top-level comment",
                    tint = HarmonicTheme.colors.overlayButtonContent,
                )
            }
        }
        ModalControlScrim(modalScrimAlpha, shape, modalScrimActive)
    }
}
