@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.simon.harmonichackernews.ui.stories

import org.jetbrains.compose.resources.DrawableResource


import com.simon.harmonichackernews.resources.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import com.simon.harmonichackernews.ui.common.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.simon.harmonichackernews.adapters.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.StoriesInteractionStore
import com.simon.harmonichackernews.presentation.StoryFrontDatePickerRequest
import com.simon.harmonichackernews.presentation.StoryPredictiveBackSettleRequest
import com.simon.harmonichackernews.presentation.StoryPreviewActionKind
import com.simon.harmonichackernews.presentation.StoryPreviewOverlayState
import com.simon.harmonichackernews.presentation.StoryScrollRequest
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.settings.StoryCachePreferences
import com.simon.harmonichackernews.settings.StoryPreviewTintState
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemStyle
import com.simon.harmonichackernews.ui.content.StoryItemUiModel
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.common.SharedLazyContentList
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.common.SharedHarmonicFilterButton

private val StoriesEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
fun SharedStoriesScreen(
    controller: StoriesComposeController,
    storyItemModel: (Story, Int, StoryDisplaySettings) -> StoryItemUiModel,
    commentText: (String) -> AnnotatedString,
    filterColors: HarmonicFilterButtonColors,
    extraCompactSelectedText: Boolean,
    compactSelectedText: Boolean,
    onStoryTintExtracted: (
        story: Story,
        sourceUrl: String,
        baseColor: Int,
        paletteTintConfigKey: String,
        tintColor: Int,
        favicon: Boolean,
    ) -> Unit = { _, _, _, _, _, _ -> },
) {
    val settings = controller.displaySettings ?: return
    val mainState = rememberLazyListState()
    val searchState = rememberLazyListState()

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
            withFrameNanos { }
        }
        controller.endPredictiveBack(request)
    }

    val scrollRequest = controller.scrollByRequest
    LaunchedEffect(scrollRequest) {
        scrollRequest?.let { request ->
            (if (controller.searching) searchState else mainState).scrollBy(request.dy.toFloat())
            controller.consumeScrollBy(request)
        }
    }

    SharedStoriesRoot(
        searching = controller.searching,
        suppressSearchAutoFocus = controller.suppressSearchAutoFocus,
        predictiveBackActive = controller.predictiveBackActive,
        predictiveBackProgress = controller.predictiveBackProgress,
        backgroundColor = HarmonicTheme.colors.background,
        mainLayer = {
            StoriesList(
                controller = controller,
                settings = settings,
                stories = controller.mainStories,
                listState = mainState,
                searchMode = false,
                storyItemModel = storyItemModel,
                commentText = commentText,
                filterColors = filterColors,
                extraCompactSelectedText = extraCompactSelectedText,
                compactSelectedText = compactSelectedText,
                onStoryTintExtracted = onStoryTintExtracted,
            )
        },
        searchLayer = {
            StoriesList(
                controller = controller,
                settings = settings,
                stories = controller.searchStories,
                listState = searchState,
                searchMode = true,
                storyItemModel = storyItemModel,
                commentText = commentText,
                filterColors = filterColors,
                extraCompactSelectedText = extraCompactSelectedText,
                compactSelectedText = compactSelectedText,
                onStoryTintExtracted = onStoryTintExtracted,
            )
        },
        overlay = {
            AnimatedVisibility(
                visible = controller.showUpdate && !controller.searching,
                enter = fadeIn(tween(180, easing = StoriesEasing)),
                exit = fadeOut(tween(140, easing = StoriesEasing)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f)
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() + 8.dp,
                    ),
            ) {
                ExtendedFloatingActionButton(
                    onClick = controller.listener::onRefresh,
                    modifier = Modifier.widthIn(min = 189.dp),
                    containerColor = HarmonicTheme.colors.overlayButton,
                    contentColor = Color.White,
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
    stories: List<Story>,
    listState: LazyListState,
    searchMode: Boolean,
    storyItemModel: (Story, Int, StoryDisplaySettings) -> StoryItemUiModel,
    commentText: (String) -> AnnotatedString,
    filterColors: HarmonicFilterButtonColors,
    extraCompactSelectedText: Boolean,
    compactSelectedText: Boolean,
    onStoryTintExtracted: (
        story: Story,
        sourceUrl: String,
        baseColor: Int,
        paletteTintConfigKey: String,
        tintColor: Int,
        favicon: Boolean,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleCount = if (searchMode) controller.searchVisibleCount else controller.mainVisibleCount
    val visibleStories = remember(stories, visibleCount, controller.contentVersion) {
        stories.take(visibleCount.coerceAtLeast(0).coerceAtMost(stories.size))
    }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val safeStart = safeDrawingPadding.calculateStartPadding(layoutDirection)
    val safeEnd = safeDrawingPadding.calculateEndPadding(layoutDirection)
    val startInset = with(density) { controller.contentInsetStartPx.toDp() }
    var headerHeightPx by remember(searchMode) { mutableIntStateOf(0) }
    val headerHeight = with(density) { headerHeightPx.toDp() }
    val headerPinnedForPreview = controller.headerPinnedForPreview
    val headerCollapsePx by remember(listState, headerPinnedForPreview, headerHeightPx) {
        derivedStateOf {
            if (headerPinnedForPreview) {
                0
            } else if (listState.firstVisibleItemIndex > 0) {
                headerHeightPx
            } else {
                listState.firstVisibleItemScrollOffset.coerceAtMost(headerHeightPx)
            }
        }
    }
    val userScrollConnection = remember(controller) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    controller.unpinPreviewHeader()
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState, searchMode) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> controller.listener.onVisibleStoryRange(last.coerceAtLeast(0)) }
    }

    PullToRefreshBox(
        isRefreshing = controller.refreshing && !searchMode,
        onRefresh = controller.listener::onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            SharedLazyContentList(
                items = visibleStories,
                state = listState,
                key = { story -> "${if (searchMode) "search" else "main"}-${story.id}" },
                contentType = { story -> if (story.isComment) "comment" else "story" },
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(userScrollConnection),
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
                    val itemBoundsModifier = Modifier.onGloballyPositioned { bounds ->
                        controller.updateStoryItemHeight(story.id, bounds.size.height)
                    }
                    if (story.isComment) {
                        SavedCommentStoryItem(
                            story = story,
                            settings = settings,
                            onStory = { controller.listener.onCommentStoryClick(story) },
                            onReplies = { controller.listener.onCommentRepliesClick(story) },
                            commentText = commentText,
                            modifier = Modifier.animateItem().then(itemBoundsModifier),
                        )
                    } else if (!story.loaded && !story.loadingFailed) {
                        StoryLoadingItem(modifier = Modifier.animateItem().then(itemBoundsModifier))
                    } else {
                        val pagingAlpha = controller.storyPagingAlphas[story.id] ?: 1f
                        val suppressed = controller.isStorySuppressed(story.id)
                        var revealed by rememberSaveable(story.id) { mutableStateOf(false) }
                        LaunchedEffect(story.id) { revealed = true }
                        val revealAlpha by animateFloatAsState(
                            targetValue = if (revealed) 1f else 0f,
                            animationSpec = tween(220, easing = StoriesEasing),
                            label = "loaded story reveal",
                        )
                        val contentVersion = controller.contentVersion
                        val storyRevision = controller.storyRevision(story.id)
                        val model = remember(story, index, settings, contentVersion, storyRevision) {
                            storyItemModel(story, index, settings)
                        }
                        val style = remember(story, settings, contentVersion, storyRevision) {
                            settings.toItemStyle(story)
                        }
                        val itemModifier = Modifier
                            .animateItem()
                            .graphicsLayer(
                                alpha = (if (suppressed) 0f else pagingAlpha) * revealAlpha,
                            )
                        Box(modifier = itemBoundsModifier) {
                            StoryItem(
                                model = model,
                                style = style,
                                modifier = itemModifier,
                                listItem = true,
                                onLinkClick = { controller.listener.onLinkClick(story) },
                                onLinkLongClick = { controller.listener.onStoryLongClick(story) },
                                onCommentClick = { controller.listener.onCommentClick(story) },
                                onBoundsChanged = { bounds ->
                                    controller.updateStoryBounds(story.id, bounds)
                                },
                                onPreviewLoadFailed = {
                                    if (!story.previewImageLoadFailed) {
                                        story.previewImageLoadFailed = true
                                        controller.invalidateStory(story.id)
                                    }
                                },
                                onPreviewTintExtracted = { tintColor ->
                                    val sourceUrl = model.previewImageUrl
                                    val baseColor = model.tintFallbackArgb
                                    if (
                                        sourceUrl != null &&
                                        baseColor != null &&
                                        StoryPreviewTintState.applyPreview(
                                            story,
                                            sourceUrl,
                                            baseColor,
                                            style.paletteTintConfigKey,
                                            tintColor,
                                        )
                                    ) {
                                        controller.invalidateStory(story.id)
                                        onStoryTintExtracted(
                                            story,
                                            sourceUrl,
                                            baseColor,
                                            style.paletteTintConfigKey,
                                            tintColor,
                                            false,
                                        )
                                    }
                                },
                                onFaviconTintExtracted = { tintColor ->
                                    val sourceUrl = model.faviconUrl
                                    val baseColor = model.tintFallbackArgb
                                    if (
                                        sourceUrl != null &&
                                        baseColor != null &&
                                        StoryPreviewTintState.applyFavicon(
                                            story,
                                            sourceUrl,
                                            baseColor,
                                            style.paletteTintConfigKey,
                                            tintColor,
                                        )
                                    ) {
                                        controller.invalidateStory(story.id)
                                        onStoryTintExtracted(
                                            story,
                                            sourceUrl,
                                            baseColor,
                                            style.paletteTintConfigKey,
                                            tintColor,
                                            true,
                                        )
                                    }
                                },
                            )
                        }
                    }
            }

            StoriesHeader(
                controller = controller,
                searchMode = searchMode,
                filterColors = filterColors,
                extraCompactSelectedText = extraCompactSelectedText,
                compactSelectedText = compactSelectedText,
                modifier = Modifier
                    .zIndex(1f)
                    .graphicsLayer(translationY = -headerCollapsePx.toFloat())
                    .onGloballyPositioned { headerHeightPx = it.size.height },
            )
        }
    }
}

@Composable
private fun StoriesHeader(
    controller: StoriesComposeController,
    searchMode: Boolean,
    filterColors: HarmonicFilterButtonColors,
    extraCompactSelectedText: Boolean,
    compactSelectedText: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val safeStart = safeDrawingPadding.calculateStartPadding(layoutDirection)
    val safeEnd = safeDrawingPadding.calculateEndPadding(layoutDirection)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val startInset = with(density) { controller.contentInsetStartPx.toDp() }
    val compact = controller.displaySettings?.compactHeader == true
    val topSpacing = if (compact) 20.dp else 40.dp
    val bottomSpacing = if (!searchMode && controller.lastUpdatedText != null) {
        if (compact) 4.dp else 8.dp
    } else if (compact) {
        10.dp
    } else {
        26.dp
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HarmonicTheme.colors.background)
            .padding(
                top = topInset + topSpacing,
                bottom = bottomSpacing,
            )
            .animateContentSize(tween(220, easing = StoriesEasing)),
    ) {
        val sideStart = 16.dp + startInset + safeStart
        val sideEnd = 16.dp + safeEnd
        if (searchMode) {
            SearchHeader(controller, sideStart, sideEnd)
        } else {
            MainHeader(
                controller = controller,
                extraCompactSelectedText = extraCompactSelectedText,
                compactSelectedText = compactSelectedText,
                modifier = Modifier.padding(start = sideStart, end = sideEnd),
            )
        }

        AnimatedVisibility(visible = !searchMode && controller.showSavedFilter) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = sideStart, top = 10.dp, end = sideEnd)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SavedFilterButton("Stories", StoriesComposeController.FILTER_STORIES, 0, controller, filterColors, Modifier.weight(1f))
                SavedFilterButton("Both", StoriesComposeController.FILTER_BOTH, 1, controller, filterColors, Modifier.weight(1f))
                SavedFilterButton("Comments", StoriesComposeController.FILTER_COMMENTS, 2, controller, filterColors, Modifier.weight(1f))
            }
        }

        AnimatedVisibility(visible = !searchMode && controller.showFrontDate) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = sideStart, top = 10.dp, end = sideEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { controller.listener.onShiftFrontDate(-1) },
                    enabled = controller.frontPreviousEnabled,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(painterResource(Res.drawable.ic_chevron_left), "Previous front page day")
                }
                OutlinedButton(
                    onClick = controller.listener::onPickFrontDate,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(56.dp),
                ) {
                    Icon(painterResource(Res.drawable.ic_calendar_today), null)
                    Spacer(Modifier.width(8.dp))
                    Text(controller.frontDateLabel, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { controller.listener.onShiftFrontDate(1) },
                    enabled = controller.frontNextEnabled,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(painterResource(Res.drawable.ic_chevron_right), "Next front page day")
                }
            }
        }

        controller.lastUpdatedText?.takeIf { !searchMode }?.let { value ->
            Text(
                text = value,
                color = HarmonicTheme.colors.storyDisabled,
                fontFamily = ProductSansFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = sideStart, top = 4.dp, end = sideEnd),
                textAlign = TextAlign.Center,
            )
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

        Box(Modifier.padding(start = sideStart, end = sideEnd)) {
            HeaderStatus(controller, searchMode)
        }
    }
}

@Composable
private fun MainHeader(
    controller: StoriesComposeController,
    extraCompactSelectedText: Boolean,
    compactSelectedText: Boolean,
    modifier: Modifier = Modifier,
) {
    var typesExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    val settings = controller.displaySettings ?: return
    val typography = rememberContentTypography(settings.font, settings.storyTextSize)
    val density = LocalDensity.current
    val selectedTextSize = with(density) {
        when {
            extraCompactSelectedText ->
                (typography.storiesDropdownSelectedSize * 0.8f).dp.toSp()
            compactSelectedText ->
                typography.storiesDropdownCompactSelectedSize.dp.toSp()
            else -> typography.storiesDropdownSelectedSize.dp.toSp()
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .combinedClickable(
                        onClick = { typesExpanded = true },
                        onLongClick = null,
                    )
                    .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (controller.showingCached) {
                        "Cached stories"
                    } else {
                        controller.typeLabels.getOrNull(controller.selectedTypeIndex) ?: "Stories"
                    },
                    color = HarmonicTheme.colors.storyNormal,
                    fontFamily = typography.family,
                    fontWeight = FontWeight.Bold,
                    fontSize = selectedTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.width(40.dp))
                Icon(
                    painterResource(Res.drawable.ic_keyboard_arrow_down),
                    contentDescription = "Choose story list",
                    modifier = Modifier.size(24.dp),
                    tint = HarmonicTheme.colors.drawable,
                )
            }
            HarmonicDropdownMenu(
                expanded = typesExpanded,
                onDismiss = { typesExpanded = false },
                modifier = Modifier.width(196.dp),
            ) {
                controller.typeLabels.forEachIndexed { index, label ->
                    DropdownMenuItem(
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
                            controller.listener.onTypeSelected(index)
                        },
                    )
                }
            }
        }
        StoriesTooltip("Search") {
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
            StoriesMoreMenu(controller, moreExpanded) { moreExpanded = false }
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
    SharedStorySearchHeader(
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
    dismiss: () -> Unit,
) {
    HarmonicDropdownMenu(
        expanded = expanded,
        onDismiss = dismiss,
        modifier = Modifier.width(196.dp),
    ) {
        if (controller.loggedIn) {
            MoreItem("Profile", StoriesComposeController.MORE_PROFILE, controller, dismiss)
            MoreItem("Submit", StoriesComposeController.MORE_SUBMIT, controller, dismiss)
        }
        MoreItem(if (controller.loggedIn) "Log out" else "Log in", StoriesComposeController.MORE_LOGIN, controller, dismiss)
        if (controller.canCache) {
            MoreItem("Cache stories", StoriesComposeController.MORE_CACHE, controller, dismiss)
        }
        if (controller.canClearHistory) {
            MoreItem("Clear history", StoriesComposeController.MORE_CLEAR_HISTORY, controller, dismiss)
        }
        MoreItem("Settings", StoriesComposeController.MORE_SETTINGS, controller, dismiss)
    }
}

@Composable
private fun MoreItem(
    label: String,
    action: Int,
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
    value: Int,
    position: Int,
    controller: StoriesComposeController,
    colors: HarmonicFilterButtonColors,
    modifier: Modifier,
) {
    SharedHarmonicFilterButton(
        label = label,
        selected = controller.savedFilter == value,
        onClick = { controller.listener.onSavedFilterSelected(value) },
        position = position,
        colors = colors,
        modifier = modifier,
    )
}

@Composable
private fun HeaderStatus(controller: StoriesComposeController, searchMode: Boolean) {
    val colors = HarmonicTheme.colors
    SharedStoryListStatus(
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
        onRetry = controller.listener::onRefresh,
        onShowCached = controller.listener::onShowCached,
    )
}

@Composable
private fun StoryLoadingItem(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Box(Modifier.fillMaxWidth(0.78f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            Box(Modifier.padding(top = 10.dp).fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
    }
}

@Composable
private fun SavedCommentStoryItem(
    story: Story,
    settings: StoryDisplaySettings,
    onStory: () -> Unit,
    onReplies: () -> Unit,
    commentText: (String) -> AnnotatedString,
    modifier: Modifier = Modifier,
) {
    val typography = rememberContentTypography(settings.font, settings.storyTextSize)
    Surface(
        color = if (settings.cardStyle) MaterialTheme.colorScheme.surfaceContainerHigh else HarmonicTheme.colors.background,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (settings.cardStyle) 1.dp else 0.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "On “${story.commentMasterTitle ?: "Loading story…"}”",
                fontFamily = typography.family,
                fontWeight = FontWeight.Bold,
                fontSize = typography.storyTitleSize.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.combinedClickable(onClick = onStory, onLongClick = null),
            )
            Text(
                text = commentText(story.text.orEmpty()),
                fontFamily = typography.family,
                fontSize = settings.commentTextSize.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onStory) { Text("Story") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onReplies) {
                    Icon(painterResource(Res.drawable.ic_comment), null)
                    Spacer(Modifier.width(6.dp))
                    Text("Replies")
                }
            }
        }
    }
}

private fun StoryDisplaySettings.toItemStyle(story: Story) = StoryItemStyle(
    previewImageMode = previewImageMode,
    borderlessLargeImage = borderlessLargePreviewImage,
    compact = compactView,
    showSummary = showSummary && !story.linkSummaryDescription.isNullOrBlank(),
    showFavicon = thumbnails,
    showPoints = showPoints,
    compactPoints = compactPoints,
    includeTopLevelDomain = includeTopLevelDomain,
    showCommentCount = showCommentsCount,
    showIndex = showIndex,
    commentsOnLeft = leftAlign,
    tintCard = tintCardUsingPreview,
    cardStyle = cardStyle,
    useHotnessIcon = hotness > 0 && story.score + story.descendants > hotness,
    preferredFont = font,
    textSize = storyTextSize,
    dimmed = grayOutClicked && story.clicked,
    paletteTintConfigKey = paletteTintMode,
)
