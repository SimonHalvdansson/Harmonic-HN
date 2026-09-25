@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.simon.harmonichackernews.ui.stories



import com.simon.harmonichackernews.resources.*

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.MaterialTheme
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.simon.harmonichackernews.ui.common.HarmonicPullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.ui.content.CommentFeedItem
import com.simon.harmonichackernews.ui.content.StoryRow
import com.simon.harmonichackernews.ui.content.StoryRowStyleContext
import com.simon.harmonichackernews.ui.content.StoryRowModel
import com.simon.harmonichackernews.ui.content.toStoryRowStyle
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.ui.common.LazyContentList
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Clock
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors

private const val StoriesRowMotionDurationMillis = 350

@Composable
internal fun StoriesList(
    controller: StoriesScreenController,
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
    ) -> StoryRowModel,
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
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            (visible.firstOrNull()?.index ?: 0) to (visible.lastOrNull()?.index ?: 0)
        }
            .distinctUntilChanged()
            .collect { (first, last) ->
                controller.listener.onVisibleStoryRange(first.coerceAtLeast(0), last.coerceAtLeast(0))
            }
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
                        bottom = bottomPadding + if (controller.showRefreshPrompt) 88.dp else 8.dp,
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
                            SavedCommentStoryRow(
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
                            val style = remember(story, settings, storyRevision, model.previewText) {
                                settings.toStoryRowStyle(
                                    StoryRowStyleContext(
                                        score = story.score,
                                        commentCount = story.descendantCount,
                                        isRead = story.isRead,
                                        previewTextAvailable = model.previewText.isNotBlank(),
                                    ),
                                )
                            }
                            val untintedStoryBackground = if (style.hasBackground) {
                                HarmonicTheme.colors.contentCardBackground
                            } else {
                                HarmonicTheme.colors.background
                            }
                            val storyTintBase = if (style.tintCard) {
                                model.tintFallbackArgb
                                    ?: HarmonicTheme.colors.contentCardBackground.toArgb()
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
                            StoryRow(
                                model = model,
                                style = style,
                                modifier = itemModifier,
                                listItem = true,
                                pageBackground = HarmonicTheme.colors.background,
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
                HarmonicPullToRefreshIndicator(
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

@Composable
private fun StoryLoadingItem(hasBackground: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (hasBackground) HarmonicTheme.colors.contentCardBackground else Color.Transparent,
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
private fun SavedCommentStoryRow(
    story: StoryListItemSnapshot,
    settings: StoryDisplaySettings,
    onStory: () -> Unit,
    onReplies: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val links = LocalHarmonicUiDependencies.current.links
    CommentFeedItem(
        rootStoryTitle = story.presentation.rootStory?.title,
        timeText = story.timeFormatted,
        html = story.text.orEmpty(),
        canOpenStory = story.rootStoryId > 0 || story.parentId > 0,
        displaySettings = settings,
        onOpenLink = remember(links) { { url -> links.open(url).let { } } },
        onStoryClick = onStory,
        onRepliesClick = onReplies,
        modifier = modifier,
    )
}
