@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.simon.harmonichackernews.ui.stories

import org.jetbrains.compose.resources.DrawableResource


import com.simon.harmonichackernews.resources.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.simon.harmonichackernews.ui.common.TextButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.settings.StoryListSelector
import com.simon.harmonichackernews.presentation.SavedItemFilter
import com.simon.harmonichackernews.presentation.StoriesMenuAction
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.presentation.StoryFrontDatePickerRequest
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemStyleContext
import com.simon.harmonichackernews.ui.content.StoryItemUiModel
import com.simon.harmonichackernews.ui.content.toStoryItemStyle
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.common.LazyContentList
import com.simon.harmonichackernews.ui.common.ModalControlScrim
import com.simon.harmonichackernews.ui.common.currentSharedHazeState
import com.simon.harmonichackernews.ui.common.HazeGlassAppearance
import com.simon.harmonichackernews.ui.common.sharedHazeBackground
import com.simon.harmonichackernews.ui.common.LocalHazeGlassEnabled
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Clock
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.common.HarmonicFilterButton

private val StoriesEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val StoriesRowMotionDurationMillis = 350
private val NoTapToUpdateExitProgress: () -> Float = { 0f }

@Composable
fun StoriesScreen(
    controller: StoriesComposeController,
    mainListState: LazyListState = rememberLazyListState(),
    showTapToUpdateButton: Boolean = true,
    storyItemModelCacheKey: Int,
    storyItemModel: (
        StoryListItemSnapshot,
        Int,
        StoryDisplaySettings,
        StoryPreviewResourceState?,
        Long,
    ) -> StoryItemUiModel,
    filterColors: HarmonicFilterButtonColors,
    pullToRefreshEnabled: Boolean = true,
    showRefreshMenuItem: Boolean = false,
    onVisibleStoriesChanged: (List<StoryListItemSnapshot>) -> Unit = {},
) {
    val settings = controller.displaySettings ?: return
    val localHazeState = rememberHazeState()
    val hazeState = currentSharedHazeState() ?: localHazeState
    val mainState = mainListState
    val searchState = rememberLazyListState()
    val tapToUpdateExitClock = remember { Animatable(0f) }
    val tapToUpdateExitProgress = remember(tapToUpdateExitClock) {
        { tapToUpdateExitClock.value.coerceIn(0f, 1f) }
    }
    var suppressTapToUpdateRowExit by remember { mutableStateOf(false) }

    val tapToUpdateExitRequestVersion = controller.tapToUpdateExitRequestVersion
    LaunchedEffect(tapToUpdateExitRequestVersion) {
        if (tapToUpdateExitRequestVersion <= 0) return@LaunchedEffect
        var refreshStarted = false
        try {
            suppressTapToUpdateRowExit = true
            tapToUpdateExitClock.snapTo(0f)
            // Fade the existing list as one stable layer before the refresh reaches the store.
            tapToUpdateExitClock.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = SavedListTransitionDurationMillis,
                    easing = StoriesEasing,
                ),
            )
            // Keep the list anchored until its rows have faded, then let the collapsed header
            // return with normal scroll motion instead of jumping fully into view in one frame.
            mainState.animateScrollToItem(0)
            controller.completeTapToUpdateExit()
            refreshStarted = true
            snapshotFlow { controller.tapToUpdateExitInProgress }.first { inProgress ->
                !inProgress
            }
            // Let the replacement publication commit while the layer is hidden, then reverse the
            // same layer transition. The retained keyed rows are not additions from LazyList's
            // perspective, so animateItem cannot provide their entrance animation.
            withFrameNanos { }
            tapToUpdateExitClock.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SavedListTransitionDurationMillis,
                    easing = StoriesEasing,
                ),
            )
        } finally {
            if (!refreshStarted) controller.cancelTapToUpdateExit()
            tapToUpdateExitClock.snapTo(0f)
            suppressTapToUpdateRowExit = false
        }
    }

    val scrollToTopRequestVersion = controller.scrollToTopRequestVersion
    var appliedTopAnchorVersion by remember(mainState) { mutableIntStateOf(0) }
    SideEffect {
        if (scrollToTopRequestVersion != appliedTopAnchorVersion) {
            appliedTopAnchorVersion = scrollToTopRequestVersion
            if (mainState.firstVisibleItemIndex == 0 &&
                mainState.firstVisibleItemScrollOffset == 0
            ) {
                // Keep the viewport at the top in the SAME measurement as the new keys.
                // Retaining the old first key would briefly collapse the header when that
                // story moves down the feed. Keeping the same index preserves row animations.
                mainState.requestScrollToItem(0)
            }
        }
    }
    LaunchedEffect(scrollToTopRequestVersion) {
        if (scrollToTopRequestVersion <= 0 || controller.mainStories.isEmpty()) {
            return@LaunchedEffect
        }
        // Menu refreshes can start partway down the feed; return with normal scroll motion.
        mainState.animateScrollToItem(0)
    }

    val settleRequest = controller.predictiveBackSettleRequest
    LaunchedEffect(settleRequest?.serial) {
        val request = settleRequest ?: return@LaunchedEffect
        val start = controller.predictiveBackProgress.coerceIn(0f, 1f)
        val distance = kotlin.math.abs(request.target - start)
        val animation = Animatable(start)
        animation.animateTo(
            targetValue = request.target,
            animationSpec = tween(
                durationMillis = (180 * distance).roundToInt().coerceAtLeast(1),
                easing = StoriesEasing,
            ),
        ) {
            controller.updatePredictiveBack(value)
        }
        if (request.target == 1f) {
            controller.listener.onCloseSearch()
            // Keep the completed predictive frame in place until the feature state confirms the
            // search has closed. Resetting the gesture after an arbitrary frame can briefly reveal
            // the still-active search layer when that state update arrives a frame later.
            snapshotFlow { controller.searching }.first { searching -> !searching }
        }
        controller.endPredictiveBack(request)
    }

    val scrollRequest = controller.scrollByRequest
    LaunchedEffect(scrollRequest) {
        scrollRequest?.let { request ->
            // dispatchRawDelta is synchronous: applying and acknowledging this accumulated delta
            // cannot be split by cancellation when a newer pager sample arrives.
            val consumed = (if (controller.searching) searchState else mainState)
                .dispatchRawDelta(request.dy.toFloat())
                .roundToInt()
            // A list boundary cannot consume the requested movement. Treat that request as handled
            // so an unreachable anchor cannot keep getting re-issued.
            controller.consumeScrollBy(
                request,
                consumedDy = consumed.takeIf { it != 0 } ?: request.dy,
            )
        }
    }

    StoriesRoot(
        searching = controller.searching,
        suppressSearchAutoFocus = controller.suppressSearchAutoFocus,
        predictiveBackActive = controller.predictiveBackActive,
        predictiveBackProgress = controller.predictiveBackProgress,
        backgroundColor = HarmonicTheme.colors.settingsPageBackground,
        mainLayer = {
            Box(Modifier.fillMaxSize().sharedHazeSource(hazeState)) {
                StoriesList(
                    controller = controller,
                    settings = settings,
                    stories = controller.mainStories,
                    listState = mainState,
                    searchMode = false,
                    // The animation clock, rather than the controller hand-off flag, owns visibility.
                    // This keeps the old rows hidden until the replacement frame has been committed.
                    tapToUpdateExitProgress = tapToUpdateExitProgress,
                    suppressTapToUpdateRowExit = suppressTapToUpdateRowExit,
                    storyItemModelCacheKey = storyItemModelCacheKey,
                    storyItemModel = storyItemModel,
                    filterColors = filterColors,
                    pullToRefreshEnabled = pullToRefreshEnabled,
                    showRefreshMenuItem = showRefreshMenuItem,
                    onVisibleStoriesChanged = onVisibleStoriesChanged,
                )
            }
        },
        searchLayer = {
            StoriesList(
                controller = controller,
                settings = settings,
                stories = controller.searchStories,
                listState = searchState,
                searchMode = true,
                tapToUpdateExitProgress = NoTapToUpdateExitProgress,
                suppressTapToUpdateRowExit = false,
                storyItemModelCacheKey = storyItemModelCacheKey,
                storyItemModel = storyItemModel,
                filterColors = filterColors,
                pullToRefreshEnabled = pullToRefreshEnabled,
                showRefreshMenuItem = showRefreshMenuItem,
                onVisibleStoriesChanged = {},
            )
        },
        overlay = {
            if (showTapToUpdateButton) {
                StoryTapToUpdateButton(
                    controller = controller,
                    mainListState = mainState,
                    modifier = Modifier.zIndex(2f),
                    hazeState = hazeState,
                )
            }
        },
    )

    controller.frontDatePickerRequest?.let { request ->
        FrontPageDatePickerDialog(
            request = request,
            onDismiss = controller::dismissFrontDatePicker,
            onSelected = controller::selectFrontDate,
        )
    }
}

/** A host-positionable update control that can stay above story preview transition content. */
@Composable
fun BoxScope.StoryTapToUpdateButton(
    controller: StoriesComposeController,
    mainListState: LazyListState,
    modifier: Modifier = Modifier,
    modalScrimAlpha: Float = 0f,
    modalScrimActive: Boolean = modalScrimAlpha > 0f,
    hazeState: HazeState? = currentSharedHazeState(),
) {
    val shape = RoundedCornerShape(16.dp)
    AnimatedVisibility(
        visible = controller.showUpdate && !controller.searching &&
            !controller.tapToUpdateExitInProgress,
        enter = fadeIn(tween(180, easing = StoriesEasing)),
        exit = fadeOut(tween(140, easing = StoriesEasing)),
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 8.dp,
            ),
    ) {
        Box {
            ExtendedFloatingActionButton(
                onClick = {
                    val alreadyAtTop = mainListState.firstVisibleItemIndex == 0 &&
                        mainListState.firstVisibleItemScrollOffset == 0
                    if (alreadyAtTop) {
                        controller.refresh()
                    } else {
                        controller.beginTapToUpdateExit()
                    }
                },
                modifier = Modifier.widthIn(min = 189.dp)
                    .shadow(if (LocalHazeGlassEnabled.current) 2.dp else 6.dp, shape, clip = false)
                    .sharedHazeBackground(
                        glassAppearance = HazeGlassAppearance.FloatingButton,
                        hazeState = hazeState,
                        surfaceColor = HarmonicTheme.colors.overlayButton.copy(alpha = 0.8f),
                        shape = shape,
                    )
                    .semantics { contentDescription = "Tap to update" },
                shape = shape,
                containerColor = Color.Transparent,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
                contentColor = HarmonicTheme.colors.overlayButtonContent,
                icon = {
                    Icon(painterResource(Res.drawable.ic_refresh), contentDescription = null)
                },
                text = {
                    Text(
                        "Tap to update",
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
            ModalControlScrim(modalScrimAlpha, shape, modalScrimActive)
        }
    }
}

@Composable
private fun FrontPageDatePickerDialog(
    request: StoryFrontDatePickerRequest,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit,
) {
    val selectableDates = remember(request.earliestDay, request.latestDay) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in request.earliestDay..request.latestDay
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = request.initialDay,
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let(onSelected) },
                enabled = state.selectedDateMillis != null,
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        FrontPageDatePickerContent(state = state)
    }
}

@Composable
private fun FrontPageDatePickerContent(
    state: androidx.compose.material3.DatePickerState,
) {
    DatePicker(
        state = state,
        title = {
            Text(
                text = "Select front page day",
                modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
            )
        },
    )
}

@Composable
private fun StoriesList(
    controller: StoriesComposeController,
    settings: StoryDisplaySettings,
    stories: List<StoryListItemSnapshot>,
    listState: LazyListState,
    searchMode: Boolean,
    tapToUpdateExitProgress: () -> Float,
    suppressTapToUpdateRowExit: Boolean,
    storyItemModelCacheKey: Int,
    storyItemModel: (
        StoryListItemSnapshot,
        Int,
        StoryDisplaySettings,
        StoryPreviewResourceState?,
        Long,
    ) -> StoryItemUiModel,
    filterColors: HarmonicFilterButtonColors,
    pullToRefreshEnabled: Boolean,
    showRefreshMenuItem: Boolean,
    onVisibleStoriesChanged: (List<StoryListItemSnapshot>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var sectionChangeJob by remember { mutableStateOf<Job?>(null) }
    val onTypeSelected: (Int) -> Unit = { index ->
        sectionChangeJob?.cancel()
        sectionChangeJob = scope.launch {
            // Changing sections clears the feed, which resets LazyListState immediately.
            // Expand the header while the current rows still provide a scrollable viewport.
            listState.animateScrollToItem(0)
            controller.listener.onTypeSelected(index)
        }
    }
    fun dismissSearchKeyboard() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    val visibleCount = (
        if (searchMode) controller.searchVisibleCount else controller.mainVisibleCount
    ).coerceIn(0, stories.size)
    val modelNowMillis = remember(stories, settings) {
        Clock.System.now().toEpochMilliseconds()
    }
    val centerFailure = !searchMode && visibleCount == 0 &&
        (controller.loadingFailed || controller.loadingFailedServerError)
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val tapToUpdateExitOffsetPx = with(density) { 8.dp.toPx() }
    val pullToRefreshState = rememberPullToRefreshState()
    val pullIndicatorTopInset = with(density) {
        WindowInsets.safeDrawing.getTop(density).toDp()
    }
    val pullIndicatorRestingInset = (pullIndicatorTopInset - 32.dp).coerceAtLeast(0.dp)
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val safeStart = safeDrawingPadding.calculateStartPadding(layoutDirection)
    val safeEnd = safeDrawingPadding.calculateEndPadding(layoutDirection)
    val startInset = with(density) { controller.contentInsetStartPx.toDp() }
    // Keep one lazy list throughout header changes. Do not draw its initial zero-padding
    // measurement while the header size is being delivered to the next composition.
    var headerHeightPx by remember(searchMode) { mutableIntStateOf(0) }
    val headerHeight = with(density) { headerHeightPx.toDp() }
    val headerCollapsePx by remember(listState, headerHeightPx, stories) {
        derivedStateOf {
            calculateStoriesHeaderCollapsePx(
                headerHeightPx = headerHeightPx,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            ) { precedingIndex ->
                stories.getOrNull(precedingIndex)
                    ?.let { story -> controller.getAdjacentStoryPagingDistance(story.id) }
                    ?: headerHeightPx
            }
        }
    }

    LaunchedEffect(listState, searchMode) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> controller.listener.onVisibleStoryRange(last.coerceAtLeast(0)) }
    }

    LaunchedEffect(listState, stories, visibleCount, onVisibleStoriesChanged) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                item.index.takeIf { it in 0 until visibleCount }?.let(stories::getOrNull)
            }
        }
            .distinctUntilChanged()
            .collect(onVisibleStoriesChanged)
    }

    val content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
        Box(Modifier.fillMaxSize()) {
            LookaheadScope {
                LazyContentList(
                    contentGeneration = if (searchMode) 0 else controller.mainListGeneration,
                    items = stories,
                    itemCount = visibleCount,
                    state = listState,
                    key = { story -> story.id },
                    contentType = { story -> if (story.isComment) "comment" else "story" },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val progress = tapToUpdateExitProgress()
                            alpha = if (headerHeight > 0.dp) 1f - progress else 0f
                            translationY = -tapToUpdateExitOffsetPx * progress
                        },
                    contentPadding = PaddingValues(
                        start = startInset + safeStart,
                        top = headerHeight,
                        end = safeEnd,
                        bottom = bottomPadding + if (controller.showUpdate) 88.dp else 8.dp,
                    ),
                    footerKey = "${if (searchMode) "search" else "main"}-load-more",
                    footer = if (controller.showLoadMore) {
                        {
                            Box(
                                Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (controller.loadMoreLoading) {
                                    HarmonicLoadingIndicator(modifier = Modifier.size(40.dp))
                                } else {
                                    OutlinedButton(
                                        onClick = controller.listener::onLoadMore,
                                    ) { Text("Load more") }
                                }
                            }
                        }
                    } else {
                        null
                    },
                ) { index, story ->
                    // Lookahead gives placement and size transitions the same final geometry,
                    // including when a short skeleton becomes a taller loaded story.
                    val itemAnimationModifier = Modifier.animateItem(
                        fadeInSpec = tween(
                            SavedListTransitionDurationMillis,
                            easing = StoriesEasing,
                        ),
                        placementSpec = tween(StoriesRowMotionDurationMillis, easing = StoriesEasing),
                        // The parent list already fades for Tap to update. A second row exit would
                        // become visible after replacement, briefly resurrecting the old rows.
                        fadeOutSpec = if (suppressTapToUpdateRowExit) {
                            null
                        } else {
                            tween(
                                SavedListTransitionDurationMillis,
                                easing = StoriesEasing,
                            )
                        },
                    )
                    Box(
                        // Constrain width before the size animator: content reflows immediately during
                        // pane resizing, while loaded rows can still animate their height.
                        itemAnimationModifier.fillMaxWidth().animateContentSize(
                            animationSpec = tween(StoriesRowMotionDurationMillis, easing = StoriesEasing),
                        ),
                    ) {
                        if (story.isComment) {
                            val itemHeightModifier = Modifier.onGloballyPositioned { coordinates ->
                                controller.updateStoryItemHeight(story.id, coordinates.size.height)
                            }
                            SavedCommentStoryItem(
                                story = story,
                                settings = settings,
                                onStory = {
                                    dismissSearchKeyboard()
                                    controller.listener.onCommentStoryClick(story)
                                },
                                onReplies = {
                                    dismissSearchKeyboard()
                                    controller.listener.onCommentRepliesClick(story)
                                },
                                modifier = itemHeightModifier,
                            )
                        } else if (!story.loaded && !story.loadingFailed) {
                            val itemHeightModifier = Modifier.onGloballyPositioned { coordinates ->
                                controller.updateStoryItemHeight(story.id, coordinates.size.height)
                            }
                            StoryLoadingItem(
                                hasBackground = settings.hasBackground,
                                modifier = itemHeightModifier,
                            )
                        } else {
                            val pagingAlpha = controller.storyPagingAlphaState(story.id)
                            val suppressed = controller.isStorySuppressed(story.id)
                            val keepPreviewSourceVisible =
                                controller.shouldKeepStoryPreviewSourceVisible(story.id)
                            var revealed by rememberSaveable(story.id) { mutableStateOf(false) }
                            LaunchedEffect(story.id) { revealed = true }
                            val revealAlpha by animateFloatAsState(
                                targetValue = if (revealed) 1f else 0f,
                                animationSpec = tween(220, easing = StoriesEasing),
                                label = "loaded story reveal",
                            )
                            val storyRevision = controller.storyRevision(story.id)
                            val previewResource = controller.previewResource(story.id)
                                ?.takeIf { it.pageUrl == story.url }
                            // Palette tints are resolved against the theme's card background. Retain
                            // row-model caching normally, but rebuild when that base color changes.
                            val model = remember(
                                story,
                                index,
                                settings,
                                storyRevision,
                                previewResource,
                                storyItemModelCacheKey,
                            ) {
                                storyItemModel(
                                    story,
                                    index,
                                    settings,
                                    previewResource,
                                    modelNowMillis,
                                )
                            }
                            val style = remember(story, settings, storyRevision, model.summary) {
                                settings.toStoryItemStyle(
                                    StoryItemStyleContext(
                                        score = story.score,
                                        commentCount = story.descendantCount,
                                        clicked = story.clicked,
                                        summaryAvailable = model.summary.isNotBlank(),
                                    ),
                                )
                            }
                            val untintedStoryBackground = if (style.hasBackground) {
                                HarmonicTheme.colors.storyCardBackground
                            } else {
                                HarmonicTheme.colors.settingsPageBackground
                            }
                            val storyTintBase = if (style.tintCard) {
                                model.tintFallbackArgb
                                    ?: HarmonicTheme.colors.storyCardBackground.toArgb()
                            } else {
                                untintedStoryBackground.toArgb()
                            }
                            val itemModifier = Modifier
                                .graphicsLayer {
                                    alpha = if (keepPreviewSourceVisible) {
                                        revealAlpha
                                    } else {
                                        (if (suppressed) 0f else pagingAlpha.floatValue) * revealAlpha
                                    }
                                }
                            val returningPreviewSource =
                                controller.visibleStoryPreviewId == story.id &&
                                    controller.storyPreviewDismissRequest != 0
                            val sourceAccessoryAlpha by animateFloatAsState(
                                targetValue = if (returningPreviewSource) 0f else 1f,
                                animationSpec = if (returningPreviewSource) {
                                    snap()
                                } else {
                                    tween(
                                        durationMillis = 180,
                                        delayMillis = 40,
                                        easing = StoriesEasing,
                                    )
                                },
                                label = "story preview source accessories",
                            )
                            StoryItem(
                                model = model,
                                style = style,
                                modifier = itemModifier,
                                listItem = true,
                                pageBackground = HarmonicTheme.colors.settingsPageBackground,
                                animateChanges = true,
                                onLinkClick = {
                                    dismissSearchKeyboard()
                                    controller.listener.onLinkClick(story)
                                },
                                onLinkLongClick = {
                                    dismissSearchKeyboard()
                                    controller.listener.onStoryLongClick(
                                        story,
                                        storyTintBase,
                                    )?.let { deck ->
                                        controller.showStoryPreview(
                                            if (style.tintCard) {
                                                deck
                                            } else {
                                                deck.copy(
                                                    cardColors = List(deck.stories.size) {
                                                        storyTintBase
                                                    },
                                                )
                                            },
                                        )
                                    }
                                },
                                onCommentClick = {
                                    dismissSearchKeyboard()
                                    controller.listener.onCommentClick(story)
                                },
                                onGeometryChanged = { bounds, itemHeightPx ->
                                    controller.updateStoryItemHeight(story.id, itemHeightPx)
                                    controller.updateStoryBounds(story.id, bounds)
                                },
                                onPreviewSourceGeometryChanged = { geometry ->
                                    controller.updateStoryPreviewSourceGeometry(story.id, geometry)
                                },
                                capturePreviewSourceGeometry =
                                    controller.visibleStoryPreviewId == story.id,
                                sourceAccessoryAlpha = sourceAccessoryAlpha,
                                onPreviewLoadSuccess = {
                                    model.previewImageUrl?.let { imageUrl ->
                                        controller.listener.onStoryPreviewImageLoaded(
                                            story.id,
                                            story.url.orEmpty(),
                                            imageUrl,
                                        )
                                    }
                                },
                                onPreviewLoadFailed = {
                                    model.previewImageUrl?.let { imageUrl ->
                                        controller.listener.onStoryPreviewImageLoadFailed(
                                            story.id,
                                            story.url.orEmpty(),
                                            imageUrl,
                                        )
                                    }
                                },
                                onPreviewTintExtracted = { tintColor ->
                                    val sourceUrl = model.previewImageUrl
                                    val baseColor = model.tintFallbackArgb
                                    if (sourceUrl != null && baseColor != null) {
                                        controller.listener.onStoryTintExtracted(
                                            story,
                                            sourceUrl,
                                            baseColor,
                                            style.paletteTintConfigKey,
                                            tintColor,
                                            false,
                                        )
                                        controller.invalidateStory(story.id)
                                    }
                                },
                                onFaviconTintExtracted = { tintColor ->
                                    val sourceUrl = model.faviconUrl
                                    val baseColor = model.tintFallbackArgb
                                    if (sourceUrl != null && baseColor != null) {
                                        controller.listener.onStoryTintExtracted(
                                            story,
                                            sourceUrl,
                                            baseColor,
                                            style.paletteTintConfigKey,
                                            tintColor,
                                            true,
                                        )
                                        controller.invalidateStory(story.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            StoriesHeader(
                controller = controller,
                onTypeSelected = onTypeSelected,
                searchMode = searchMode,
                tapToUpdateExitProgress = tapToUpdateExitProgress,
                suppressLastUpdated = controller.tapToUpdateRefreshStarted,
                filterColors = filterColors,
                showRefreshMenuItem = showRefreshMenuItem,
                showFailureStatus = !centerFailure,
                modifier = Modifier
                    .zIndex(1f)
                    .graphicsLayer { translationY = -headerCollapsePx.toFloat() }
                    .onSizeChanged { headerHeightPx = it.height },
            )
            if (centerFailure) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f),
                ) {
                    HeaderStatus(
                        controller = controller,
                        searchMode = searchMode,
                        centerFailure = true,
                    )
                }
            }
        }
    }

    LaunchedEffect(controller.refreshing) {
        if (!controller.refreshing) {
            controller.finishPullToRefresh()
        }
    }

    if (pullToRefreshEnabled) {
        PullToRefreshBox(
            isRefreshing = controller.pullToRefreshInProgress &&
                controller.refreshing && !searchMode,
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // Begin at the physical top edge, then approach the inset-aware refresh
                        // position without leaving the spinner as low as the full safe inset.
                        .offset(
                            y = pullIndicatorRestingInset *
                                pullToRefreshState.distanceFraction.coerceIn(0f, 1f),
                        ),
                    isRefreshing = controller.pullToRefreshInProgress &&
                        controller.refreshing && !searchMode,
                    state = pullToRefreshState,
                )
            },
            onRefresh = {
                controller.beginPullToRefresh()
                controller.refresh()
            },
            modifier = modifier.fillMaxSize(),
            content = content,
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            content = content,
        )
    }
}

internal inline fun calculateStoriesHeaderCollapsePx(
    headerHeightPx: Int,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    precedingItemHeightPx: (Int) -> Int,
): Int {
    if (headerHeightPx <= 0) return 0

    var collapsePx = firstVisibleItemScrollOffset.coerceAtLeast(0)
    var precedingIndex = 0
    while (precedingIndex < firstVisibleItemIndex && collapsePx < headerHeightPx) {
        collapsePx += precedingItemHeightPx(precedingIndex).coerceAtLeast(0)
        precedingIndex++
    }
    return collapsePx.coerceAtMost(headerHeightPx)
}

@Composable
private fun StoriesHeader(
    controller: StoriesComposeController,
    onTypeSelected: (Int) -> Unit,
    searchMode: Boolean,
    tapToUpdateExitProgress: () -> Float,
    suppressLastUpdated: Boolean,
    filterColors: HarmonicFilterButtonColors,
    showRefreshMenuItem: Boolean,
    showFailureStatus: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val safeStart = safeDrawingPadding.calculateStartPadding(layoutDirection)
    val safeEnd = safeDrawingPadding.calculateEndPadding(layoutDirection)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val startInset = with(density) { controller.contentInsetStartPx.toDp() }
    val settings = controller.displaySettings ?: return
    val compact = settings.compactHeader
    val topSpacing = if (compact) 20.dp else 40.dp
    val bottomSpacing = if (compact) 4.dp else 8.dp

    val sideStart = 16.dp + startInset + safeStart
    val sideEnd = 16.dp + safeEnd
    // Status content grows over rows retained by animateItem during their exit fade. Only the
    // controls need an opaque surface; extending it behind the spinner wipes those rows away.
    Column(modifier = modifier.fillMaxWidth().padding(bottom = bottomSpacing)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HarmonicTheme.colors.settingsPageBackground)
                .padding(top = topInset + topSpacing),
        ) {
            if (searchMode) {
                SearchHeader(controller, sideStart, sideEnd)
            } else {
                MainHeader(
                    controller = controller,
                    onTypeSelected = onTypeSelected,
                    showRefreshMenuItem = showRefreshMenuItem,
                    modifier = Modifier.padding(start = sideStart, end = sideEnd),
                )
                if (settings.listSelector == StoryListSelector.CHIPS) {
                    StoryTypeChips(
                        labels = controller.typeLabels,
                        selectedIndex = controller.selectedTypeIndex,
                        fontFamily = rememberContentTypography(
                            settings.font,
                            settings.storyTextSize,
                        ).family,
                        contentPadding = PaddingValues(start = sideStart, end = sideEnd),
                        onSelected = onTypeSelected,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            AnimatedVisibility(visible = !searchMode && controller.showSavedFilter) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = sideStart, top = 10.dp, end = sideEnd)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SavedFilterButton(
                        "Stories",
                        Res.drawable.ic_newspaper,
                        SavedItemFilter.STORIES,
                        0,
                        controller,
                        filterColors,
                        Modifier.weight(1f),
                    )
                    SavedFilterButton(
                        "All",
                        Res.drawable.ic_stacks,
                        SavedItemFilter.BOTH,
                        1,
                        controller,
                        filterColors,
                        Modifier.weight(1f),
                    )
                    SavedFilterButton(
                        "Comments",
                        Res.drawable.ic_comment,
                        SavedItemFilter.COMMENTS,
                        2,
                        controller,
                        filterColors,
                        Modifier.weight(1f),
                    )
                }
            }

            AnimatedVisibility(visible = !searchMode && controller.showFrontDate) {
                val dateButtonColors = ButtonDefaults.filledTonalButtonColors()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = sideStart, top = 10.dp, end = sideEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { controller.listener.onShiftFrontDate(-1) },
                        enabled = controller.frontPreviousEnabled,
                        colors = dateButtonColors,
                        elevation = null,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(painterResource(Res.drawable.ic_chevron_left), "Previous front page day")
                    }
                    Button(
                        onClick = controller.listener::onPickFrontDate,
                        colors = dateButtonColors,
                        elevation = null,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(56.dp),
                    ) {
                        Icon(painterResource(Res.drawable.ic_calendar_today), null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            controller.frontDateLabel,
                            fontFamily = rememberContentTypography(settings.font).family,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    Button(
                        onClick = { controller.listener.onShiftFrontDate(1) },
                        enabled = controller.frontNextEnabled,
                        colors = dateButtonColors,
                        elevation = null,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(painterResource(Res.drawable.ic_chevron_right), "Next front page day")
                    }
                }
            }

            val lastUpdated = controller.lastUpdatedText.takeIf { !searchMode }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = tween(220, easing = StoriesEasing),
                        alignment = Alignment.TopCenter,
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (lastUpdated == null) {
                    Spacer(Modifier.height(if (compact) 6.dp else 18.dp))
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = lastUpdated != null,
                    enter = fadeIn(tween(160, easing = StoriesEasing)),
                    exit = fadeOut(tween(120, easing = StoriesEasing)),
                ) {
                    Text(
                        text = lastUpdated.orEmpty(),
                        color = HarmonicTheme.colors.storyDisabled,
                        fontFamily = ProductSansFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = sideStart, top = 4.dp, end = sideEnd)
                            .graphicsLayer {
                                val progress = tapToUpdateExitProgress()
                                alpha = if (suppressLastUpdated) {
                                    0f
                                } else {
                                    1f - progress
                                }
                                translationY = -8.dp.toPx() * progress
                            },
                        textAlign = TextAlign.Center,
                    )
                }
            }

            AnimatedVisibility(
                visible = !searchMode && controller.cacheProgressVisible,
                enter = fadeIn(
                    tween(
                        durationMillis = 180,
                        delayMillis = 220,
                        easing = StoriesEasing,
                    ),
                ) + expandVertically(
                    animationSpec = tween(220, easing = StoriesEasing),
                ),
                exit = fadeOut(
                    tween(140, easing = StoriesEasing),
                ) + shrinkVertically(
                    animationSpec = tween(
                        durationMillis = 220,
                        delayMillis = 140,
                        easing = StoriesEasing,
                    ),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = sideStart, top = 8.dp, end = sideEnd),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AnimatedContent(
                        targetState = controller.cacheProgressStatus,
                        transitionSpec = {
                            fadeIn(
                                tween(
                                    durationMillis = 120,
                                    delayMillis = 90,
                                    easing = StoriesEasing,
                                ),
                            ) togetherWith fadeOut(
                                tween(90, easing = StoriesEasing),
                            )
                        },
                        label = "story cache status",
                    ) { status ->
                        Text(
                            text = status,
                            color = HarmonicTheme.colors.storyDisabled,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    val targetProgress =
                        (controller.cacheProgress.toFloat() / controller.cacheProgressMax)
                            .coerceIn(0f, 1f)
                    val animatedProgress by animateFloatAsState(
                        targetValue = targetProgress,
                        animationSpec = tween(300, easing = StoriesEasing),
                        label = "story cache progress",
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

        }

        Box(Modifier.padding(start = sideStart, end = sideEnd)) {
            HeaderStatus(
                controller = controller,
                searchMode = searchMode,
                showFailure = showFailureStatus,
            )
        }
    }
}

@Composable
private fun MainHeader(
    controller: StoriesComposeController,
    onTypeSelected: (Int) -> Unit,
    showRefreshMenuItem: Boolean,
    modifier: Modifier = Modifier,
) {
    var typesExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    val menuVisible = typesExpanded || moreExpanded
    LaunchedEffect(menuVisible) {
        controller.updateHeaderMenuVisibility(menuVisible)
    }
    LaunchedEffect(controller.headerMenuDismissRequestVersion) {
        if (controller.headerMenuDismissRequestVersion > 0) {
            typesExpanded = false
            moreExpanded = false
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.updateHeaderMenuVisibility(false) }
    }
    val settings = controller.displaySettings ?: return
    val typography = rememberContentTypography(settings.font, settings.storyTextSize)
    val useDropdown = settings.listSelector == StoryListSelector.DROPDOWN
    LaunchedEffect(useDropdown) {
        if (!useDropdown) typesExpanded = false
    }
    val density = LocalDensity.current
    val title = if (controller.showingCached) {
        "Cached stories"
    } else {
        controller.typeLabels.getOrNull(controller.selectedTypeIndex) ?: "Stories"
    }
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth().height(56.dp)) {
        val preferredSize = typography.storiesDropdownSelectedSize
        val minimumSize = typography.storiesDropdownSelectedSize * 0.65f
        val titleWidth = with(density) {
            textMeasurer.measure(
                title,
                TextStyle(
                    fontFamily = typography.family,
                    fontWeight = FontWeight.Bold,
                    fontSize = preferredSize.dp.toSp(),
                    letterSpacing = 0.sp,
                ),
                softWrap = false,
            ).size.width.toDp().value
        }
        // Leave a pixel-rounding margin, and use the same tracking for measuring and drawing.
        val sizing = storyHeaderSizing(maxWidth.value, titleWidth + 1f, minimumSize / preferredSize)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        // Grow the hit/ripple bounds around the existing text origin.
                        .offset(x = (-4).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (useDropdown) Modifier.combinedClickable(
                                onClick = { typesExpanded = true },
                                onLongClick = null,
                            ) else Modifier,
                        )
                        .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = Triple(title, sizing, controller.selectedTypeIndex),
                        contentKey = { it.first },
                        modifier = Modifier.weight(1f, fill = false),
                        contentAlignment = Alignment.CenterStart,
                        transitionSpec = {
                            val direction = when {
                                targetState.third > initialState.third -> 1
                                targetState.third < initialState.third -> -1
                                else -> 0
                            }
                            val travel = with(density) { 8.dp.roundToPx() } * direction
                            val enter = fadeIn(tween(120, delayMillis = 310, easing = LinearEasing)) +
                                slideInVertically(
                                    tween(120, delayMillis = 310, easing = FastOutSlowInEasing),
                                    initialOffsetY = { travel },
                                )
                            val exit = fadeOut(tween(90, easing = LinearEasing)) +
                                slideOutVertically(
                                    tween(90, easing = FastOutSlowInEasing),
                                    targetOffsetY = { -travel },
                                )
                            (enter togetherWith exit)
                                .using(SizeTransform(clip = false) { _, _ ->
                                    tween(220, delayMillis = 90, easing = FastOutSlowInEasing)
                                })
                        },
                        label = "story section title",
                    ) { (visibleTitle, visibleSizing) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = visibleTitle,
                                color = HarmonicTheme.colors.storyNormal,
                                fontFamily = typography.family,
                                fontWeight = FontWeight.Bold,
                                fontSize = with(density) {
                                    (preferredSize * visibleSizing.textScale).dp.toSp()
                                },
                                letterSpacing = 0.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                            )
                            Spacer(Modifier.width(visibleSizing.arrowGap.dp))
                        }
                    }
                    if (useDropdown) {
                        Icon(
                            painterResource(Res.drawable.ic_keyboard_arrow_down),
                            contentDescription = "Choose story list",
                            modifier = Modifier.size(24.dp),
                            tint = HarmonicTheme.colors.drawable,
                        )
                    }
                }
                HarmonicDropdownMenu(
                    expanded = useDropdown && typesExpanded,
                    onDismiss = { typesExpanded = false },
                    modifier = Modifier.width(240.dp),
                ) {
                    controller.typeLabels.forEachIndexed { index, label ->
                        val isSelected = index == controller.selectedTypeIndex
                        DropdownMenuItem(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) HarmonicTheme.colors.accent.copy(alpha = 0.08f)
                                    else Color.Transparent,
                                )
                                .semantics { selected = isSelected },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            text = {
                                HarmonicMenuText(
                                    text = label,
                                    color = HarmonicTheme.colors.storyNormal,
                                    fontFamily = typography.family,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = typography.storiesDropdownItemSize.sp,
                                )
                            },
                            onClick = {
                                typesExpanded = false
                                onTypeSelected(index)
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(StoryType.fromLabel(label).menuIcon),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = HarmonicTheme.colors.drawable,
                                )
                            },
                        )
                    }
                }
            }
            if (!sizing.searchInMenu) StoriesTooltip("Search") {
                IconButton(
                    onClick = controller.listener::onOpenSearch,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_search),
                        "Search",
                        tint = HarmonicTheme.colors.drawable,
                    )
                }
            }
            Box {
                StoriesTooltip("More options") {
                    IconButton(
                        onClick = { moreExpanded = true },
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_more_vert),
                            "More options",
                            tint = HarmonicTheme.colors.drawable,
                        )
                    }
                }
                StoriesMoreMenu(
                    controller = controller,
                    expanded = moreExpanded,
                    showRefreshItem = showRefreshMenuItem,
                    showSearchItem = sizing.searchInMenu,
                    dismiss = { moreExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun StoriesTooltip(
    description: String,
    content: @Composable () -> Unit,
) {
    val tooltipState = rememberTooltipState()
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = { PlainTooltip { Text(description) } },
        state = tooltipState,
        content = content,
    )
}

@Composable
private fun SearchHeader(
    controller: StoriesComposeController,
    sideStart: androidx.compose.ui.unit.Dp,
    sideEnd: androidx.compose.ui.unit.Dp,
) {
    val colors = HarmonicTheme.colors
    StorySearchHeader(
        state = StorySearchPresentationState(
            active = controller.searching,
            draft = controller.searchDraft,
            suppressAutoFocus = controller.suppressSearchAutoFocus,
            sortLabel = controller.searchSortLabel,
            dateLabel = controller.searchDateLabel,
            pointsLabel = controller.searchPointsLabel,
            commentsLabel = controller.searchCommentsLabel,
            sortLabels = controller.searchSortLabels,
            dateLabels = controller.searchDateLabels,
            pointsLabels = controller.searchPointsLabels,
            commentsLabels = controller.searchCommentsLabels,
            onlyClicked = controller.searchOnlyClicked,
        ),
        sideStart = sideStart,
        sideEnd = sideEnd,
        iconColor = colors.drawable,
        menuColor = colors.popupMenuBackground,
        menuTextColor = colors.textPrimary,
        fontFamily = ProductSansFontFamily,
        onDraftChanged = controller::updateSearchDraft,
        onSearch = controller.listener::onSearch,
        onClose = controller.listener::onCloseSearch,
        onOptionSelected = controller.listener::onSearchOption,
        onToggleOnlyClicked = controller.listener::onToggleOnlyClicked,
    )
}

@Composable
private fun StoriesMoreMenu(
    controller: StoriesComposeController,
    expanded: Boolean,
    showRefreshItem: Boolean,
    showSearchItem: Boolean,
    dismiss: () -> Unit,
) {
    HarmonicDropdownMenu(
        expanded = expanded,
        onDismiss = dismiss,
        modifier = Modifier.width(196.dp),
    ) {
        if (showSearchItem) {
            DropdownMenuItem(
                text = { HarmonicMenuText("Search") },
                onClick = {
                    dismiss()
                    controller.listener.onOpenSearch()
                },
            )
        }
        if (showRefreshItem) {
            DropdownMenuItem(
                text = { HarmonicMenuText("Refresh") },
                onClick = {
                    dismiss()
                    controller.refresh()
                },
            )
        }
        if (controller.loggedIn) {
            MoreItem("Profile", StoriesMenuAction.PROFILE, controller, dismiss)
            MoreItem("Submit", StoriesMenuAction.SUBMIT, controller, dismiss)
        }
        MoreItem(if (controller.loggedIn) "Log out" else "Log in", StoriesMenuAction.ACCOUNT, controller, dismiss)
        if (controller.canCache) {
            MoreItem("Cache stories", StoriesMenuAction.CACHE, controller, dismiss)
        }
        if (controller.canClearHistory) {
            MoreItem("Clear history", StoriesMenuAction.CLEAR_HISTORY, controller, dismiss)
        }
        MoreItem("Settings", StoriesMenuAction.SETTINGS, controller, dismiss)
    }
}

@Composable
private fun MoreItem(
    label: String,
    action: StoriesMenuAction,
    controller: StoriesComposeController,
    dismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { HarmonicMenuText(label) },
        onClick = {
            dismiss()
            controller.listener.onMoreAction(action)
        },
    )
}

@Composable
private fun SavedFilterButton(
    label: String,
    icon: DrawableResource,
    value: SavedItemFilter,
    position: Int,
    controller: StoriesComposeController,
    colors: HarmonicFilterButtonColors,
    modifier: Modifier,
) {
    HarmonicFilterButton(
        label = label,
        selected = controller.savedFilter == value,
        onClick = { controller.listener.onSavedFilterSelected(value) },
        position = position,
        colors = colors,
        modifier = modifier,
        icon = icon,
    )
}

@Composable
private fun HeaderStatus(
    controller: StoriesComposeController,
    searchMode: Boolean,
    centerFailure: Boolean = false,
    showFailure: Boolean = true,
) {
    val colors = HarmonicTheme.colors
    StoryListStatus(
        state = StoryListStatusState(
            loading = controller.loading,
            loadingFailed = controller.loadingFailed,
            serverError = controller.loadingFailedServerError,
            failureMessage = controller.loadingFailedMessage,
            showCachedAction = controller.showCachedAction,
            showEmptySavedList = controller.showEmptySavedList,
            emptySavedListText = controller.emptySavedListText,
            emptySavedListIcon = controller.emptySavedListIcon,
            showEmptySearch = controller.showEmptySearch,
        ),
        searchMode = searchMode,
        normalColor = colors.storyNormal,
        disabledColor = colors.storyDisabled,
        fontFamily = ProductSansFontFamily,
        loadingIndicator = { HarmonicLoadingIndicator(Modifier.size(48.dp)) },
        centerFailure = centerFailure,
        showFailure = showFailure,
        onRetry = controller::refresh,
        onShowCached = controller.listener::onShowCached,
    )
}

@Composable
private fun StoryLoadingItem(hasBackground: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (hasBackground) HarmonicTheme.colors.storyCardBackground else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Box(Modifier.fillMaxWidth(0.78f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            Box(Modifier.padding(top = 10.dp).fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
    }
}

@Composable
private fun SavedCommentStoryItem(
    story: StoryListItemSnapshot,
    settings: StoryDisplaySettings,
    onStory: () -> Unit,
    onReplies: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val links = com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies.current.links
    com.simon.harmonichackernews.ui.content.CommentFeedItem(
        commentMasterTitle = story.presentation.commentMaster?.title,
        timeText = story.timeFormatted,
        html = story.text.orEmpty(),
        canOpenStory = story.commentMasterId > 0 || story.parentId > 0,
        displaySettings = settings,
        onOpenLink = remember(links) { { url -> links.open(url).let { } } },
        onStoryClick = onStory,
        onRepliesClick = onReplies,
        modifier = modifier,
    )
}

internal data class StoryHeaderSizing(val textScale: Float, val arrowGap: Float, val searchInMenu: Boolean)

/** Prefer a 20dp caret gap, reducing it before shrinking the title or moving Search. */
internal fun storyHeaderSizing(width: Float, titleWidth: Float, minimumScale: Float): StoryHeaderSizing {
    // Title padding, dropdown arrow, and two 48dp action targets.
    val fixedWidth = 12f + 24f + 96f
    val minimumGap = 4f
    val minimum = minimumScale.coerceIn(0f, 1f)
    val searchInMenu = titleWidth * minimum + fixedWidth + minimumGap > width
    val available = (width - fixedWidth + if (searchInMenu) 48f else 0f).coerceAtLeast(0f)
    val scale = if (titleWidth > 0f) ((available - minimumGap) / titleWidth).coerceIn(minimum, 1f) else 1f
    val gap = (available - titleWidth * scale).coerceIn(minimumGap, 20f)
    return StoryHeaderSizing(scale, gap, searchInMenu)
}
