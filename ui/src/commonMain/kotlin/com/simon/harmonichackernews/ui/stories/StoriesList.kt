@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.simon.harmonichackernews.ui.stories



import com.simon.harmonichackernews.ui.common.statusBarScrollToTop
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.resources.*

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.simon.harmonichackernews.data.ItemTimeFormatter
import com.simon.harmonichackernews.ui.common.MeasuredHeaderState
import com.simon.harmonichackernews.ui.common.measuredHeaderContent
import com.simon.harmonichackernews.ui.common.MeasuredHeaderLayout
import com.simon.harmonichackernews.ui.common.MeasuredHeaderPadding
import com.simon.harmonichackernews.ui.common.rememberTimeOnResume
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.Icon
import org.jetbrains.compose.resources.painterResource
import androidx.compose.material3.MaterialTheme
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.simon.harmonichackernews.ui.common.HarmonicPullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.categorization.ArticleCategoryPreferences
import com.simon.harmonichackernews.categorization.ArticleCategoryRuntime
import com.simon.harmonichackernews.categorization.ArticleCategoryRuntimeState
import com.simon.harmonichackernews.categorization.ArticleCategorySettings
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.time.Clock
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.ui.content.CommentFeedItem
import com.simon.harmonichackernews.ui.content.StoryRow
import com.simon.harmonichackernews.ui.content.StoryRowStyleContext
import com.simon.harmonichackernews.ui.content.StoryRowModel
import com.simon.harmonichackernews.ui.content.toStoryRowStyle
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.ui.common.LazyContentList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import com.simon.harmonichackernews.ui.navigation.LocalSplitPaneLayout

private const val StoriesRowMotionDurationMillis = 350

// Keep the boxed value once, rather than boxing a primitive in a State<Float> bridge per read.
private class FixedStoryState<T>(override val value: T) : State<T>
private val RevealedStoryAlpha: State<Float> = FixedStoryState(1f)

/** Recycled rows that were already revealed need no reveal state, effect, or animation. */
@Composable
internal fun rememberStoryRevealAlpha(storyId: Int, controller: StoriesScreenController): State<Float> {
    // Capture the initial flag: marking a new row must not switch branches and cut its fade short.
    val initiallyRevealed = remember(storyId, controller) { controller.hasRevealedStory(storyId) }
    if (initiallyRevealed) return RevealedStoryAlpha

    var revealed by remember(storyId, controller) { mutableStateOf(false) }
    LaunchedEffect(storyId, controller) {
        controller.markStoryRevealed(storyId)
        revealed = true
    }
    return animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(220, easing = StoriesEasing),
        label = "loaded story reveal",
    )
}

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
    showWelcomeHint: Boolean = false,
    onDismissWelcomeHint: () -> Unit = {},
    categorySettings: ArticleCategorySettings? = null,
    categoryRuntimeService: ArticleCategoryRuntime? = null,
    userTags: com.simon.harmonichackernews.settings.UserTagsRepository? = null,
) {
    val authorTags = userTags?.updates?.collectAsStateWithLifecycle(initialValue = userTags.current())?.value
        ?: com.simon.harmonichackernews.settings.UserTagsSnapshot(emptyMap(), emptyMap())
    val categoryPreferences = categorySettings?.updates?.collectAsStateWithLifecycle(
        initialValue = categorySettings.snapshot(),
    )?.value ?: ArticleCategoryPreferences()
    val categoryRuntime = categoryRuntimeService?.state?.collectAsStateWithLifecycle()?.value ?: ArticleCategoryRuntimeState()
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
        if (searchMode) controller.presentation.searchVisibleCount else controller.presentation.mainVisibleCount
    ).coerceIn(0, stories.size)
    // Refresh cached ages per feed/resume. A single arriving row must not expire other models.
    val modelTimeRefresh = rememberTimeOnResume(
        searchMode,
        controller.presentation.selectedTypeIndex,
        controller.mainListGeneration,
        controller.scrollToTopRequestVersion,
        controller.presentation.lastSearch.takeIf { searchMode },
    )
    val centerFailure = !searchMode && visibleCount == 0 &&
        (controller.presentation.loadingFailed || controller.presentation.loadingFailedServerError)
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val tapToUpdateExitOffsetPx = with(density) { 8.dp.toPx() }
    val pullToRefreshState = rememberPullToRefreshState()
    val pullIndicatorTopInset = with(density) {
        WindowInsets.safeDrawing.getTop(density).toDp()
    }
    val pullIndicatorRestingInset = (pullIndicatorTopInset - 32.dp).coerceAtLeast(0.dp)
    val startInset = with(density) { controller.presentation.contentInsetStartPx.toDp() }
    val headerHeight = remember(searchMode) { MeasuredHeaderState() }
    val contentPadding = remember(startInset, bottomPadding, controller.presentation.showRefreshPrompt, headerHeight, density) {
        MeasuredHeaderPadding(
            PaddingValues(start = startInset,
                bottom = bottomPadding + if (controller.presentation.showRefreshPrompt) 88.dp else 8.dp),
            headerHeight.height,
            density,
        )
    }
    val headerCollapsePx by remember(listState, headerHeight, stories) {
        derivedStateOf {
            val headerHeightPx = headerHeight.height.intValue
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

    val paneInsetsHandled = LocalSplitPaneLayout.current.supportsTwoPane
    val content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
        // SplitPaneListContent resolves insets before the pane slides off-screen. Recalculating
        // them here would squeeze the list by the amount translated beyond the window edge.
        MeasuredHeaderLayout(
            headerHeight = headerHeight,
            modifier = Modifier.fillMaxSize().then(if (paneInsetsHandled) Modifier else Modifier.recalculateWindowInsets()),
            header = {
                StoriesHeader(
                    controller = controller,
                    onTypeSelected = onTypeSelected,
                    searchMode = searchMode,
                    tapToUpdateExitProgress = tapToUpdateExitProgress,
                    suppressLastUpdated = controller.tapToUpdateRefreshStarted,
                    filterColors = filterColors,
                    showRefreshMenuItem = showRefreshMenuItem,
                    showFailureStatus = !centerFailure,
                    showWelcomeHint = showWelcomeHint,
                    onDismissWelcomeHint = onDismissWelcomeHint,
                    modifier = Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = -headerCollapsePx.toFloat() },
                )
            },
            foreground = if (centerFailure) {
                {
                    Box(Modifier.fillMaxSize().zIndex(2f)) {
                        HeaderStatus(controller = controller, searchMode = searchMode, centerFailure = true)
                    }
                }
            } else null,
        ) {
            LookaheadScope {
                LazyContentList(
                    contentGeneration = if (searchMode) 0 else controller.mainListGeneration,
                    items = stories,
                    itemCount = visibleCount,
                    state = listState,
                    key = { story -> story.id },
                    contentType = { story -> if (story.isComment) "comment" else "story" },
                    modifier = Modifier
                        .statusBarScrollToTop(
                            enabled = searchMode == controller.searching && !controller.isStoryPreviewShowing(),
                        ) { listState.animateScrollToItem(0) }
                        .fillMaxSize()
                        .measuredHeaderContent(headerHeight)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .graphicsLayer {
                            val progress = tapToUpdateExitProgress()
                            alpha = 1f - progress
                            translationY = -tapToUpdateExitOffsetPx * progress
                        },
                    contentPadding = contentPadding,
                    footerKey = "${if (searchMode) "search" else "main"}-load-more",
                    footer = if (controller.presentation.showLoadMore) {
                        {
                            Box(
                                Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (controller.presentation.loadMoreLoading) {
                                    HarmonicLoadingIndicator(modifier = Modifier.size(40.dp))
                                } else {
                                    OutlinedButton(
                                        onClick = controller.listener::onLoadMore,
                                    ) {
                                        Icon(painterResource(Res.drawable.ic_add), contentDescription = null)
                                        Text("Load more", Modifier.padding(start = 8.dp))
                                    }
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
                            val itemHeightModifier = Modifier.onSizeChanged { size ->
                                controller.updateStoryItemHeight(story.id, size.height)
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
                                timeText = remember(story.createdAtEpochSeconds, modelTimeRefresh) {
                                    ItemTimeFormatter.formatNow(story.createdAtEpochSeconds)
                                },
                                modifier = itemHeightModifier,
                            )
                        } else if (!story.loaded && story.loadingFailed) {
                            StoryLoadFailureItem(
                                onRetry = {
                                    dismissSearchKeyboard()
                                    controller.listener.onLinkClick(story)
                                },
                                modifier = Modifier.onSizeChanged { size ->
                                    controller.updateStoryItemHeight(story.id, size.height)
                                },
                            )
                        } else if (!story.loaded) {
                            val itemHeightModifier = Modifier.onSizeChanged { size ->
                                controller.updateStoryItemHeight(story.id, size.height)
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
                            val revealAlpha by rememberStoryRevealAlpha(story.id, controller)
                            val storyRevision = controller.storyRevision(story.id)
                            val previewResource = controller.previewResource(story.id)
                                ?.takeIf { it.pageUrl == story.url }
                            val categoryDescription = previewResource?.summary?.description
                                ?: story.presentation.linkSummaryDescription
                            val categories = com.simon.harmonichackernews.ui.content.rememberArticleCategoryPresentation(
                                storyId = story.id, title = story.title, url = story.url, description = categoryDescription,
                                preferences = categoryPreferences, runtime = categoryRuntimeService, runtimeState = categoryRuntime,
                                eligible = !story.isFrontpageLink,
                            )
                            // Rebuild cached ages on resume, and palette tints when the theme's
                            // card background changes.
                            val authorTag = authorTags.tags[story.by].orEmpty()
                            val authorColor = authorTags.colors[story.by] ?: com.simon.harmonichackernews.settings.UserTagColor.Theme
                            val model = remember(
                                story,
                                index,
                                settings,
                                storyRevision,
                                previewResource,
                                storyItemModelCacheKey,
                                modelTimeRefresh,
                                categories,
                                authorTag,
                                authorColor,
                            ) {
                                storyItemModel(
                                    story,
                                    index,
                                    settings,
                                    previewResource,
                                    // New or changed rows still need the current time during a long session.
                                    Clock.System.now().toEpochMilliseconds(),
                                ).copy(categoryLabels = categories.labels,
                                    categoryIds = categories.inspection?.labelScores?.map { it.categoryId }.orEmpty(),
                                    authorName = story.by.orEmpty(),
                                    authorTag = authorTag,
                                    authorTagColor = authorColor)
                            }
                            val commented = story.id in controller.commentedStoryIds
                            val style = remember(story, settings, storyRevision, model.previewText, commented) {
                                settings.toStoryRowStyle(
                                    StoryRowStyleContext(
                                        score = story.score,
                                        commentCount = story.descendantCount,
                                        isRead = story.isRead,
                                        commented = commented,
                                        previewTextAvailable = model.previewText.isNotBlank(),
                                    ),
                                )
                            }
                            val untintedStoryBackground = if (style.hasBackground) {
                                MaterialTheme.colorScheme.cardBackground
                            } else {
                                MaterialTheme.colorScheme.pageBackground
                            }
                            val storyTintBase = if (style.tintCard) {
                                model.tintFallbackArgb
                                    ?: HarmonicTheme.targetColorScheme.cardBackground.toArgb()
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
                            SwipeToHideStory(
                                enabled = controller.canHidePosts && !searchMode && !story.isFrontpageLink,
                                onHide = { controller.listener.onHidePost(story) },
                            ) {
                                StoryRow(
                                    model = model,
                                    style = style,
                                    modifier = itemModifier,
                                    listItem = true,
                                    pageBackground = MaterialTheme.colorScheme.pageBackground,
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
            }
        }
    }

    LaunchedEffect(controller.presentation.refreshing) {
        if (!controller.presentation.refreshing) {
            controller.finishPullToRefresh()
        }
    }

    if (pullToRefreshEnabled) {
        PullToRefreshBox(
            isRefreshing = controller.pullToRefreshInProgress &&
                controller.presentation.refreshing && !searchMode,
            state = pullToRefreshState,
            indicator = {
                HarmonicPullToRefreshIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // Begin at the physical top edge, then approach the inset-aware refresh
                        // position without leaving the spinner as low as the full safe inset.
                        .offset {
                            IntOffset(0, (pullIndicatorRestingInset.toPx() *
                                pullToRefreshState.distanceFraction.coerceIn(0f, 1f)).toInt())
                        },
                    isRefreshing = controller.pullToRefreshInProgress &&
                        controller.presentation.refreshing && !searchMode,
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
        color = if (hasBackground) MaterialTheme.colorScheme.cardBackground else Color.Transparent,
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
    timeText: String,
    modifier: Modifier = Modifier,
) {
    val links = LocalHarmonicUiDependencies.current.links
    CommentFeedItem(
        rootStoryTitle = story.presentation.rootStory?.title,
        timeText = timeText,
        html = story.text.orEmpty(),
        canOpenStory = story.rootStoryId > 0 || story.parentId > 0,
        displaySettings = settings,
        onOpenLink = remember(links) { { url -> links.open(url).let { } } },
        onStoryClick = onStory,
        onRepliesClick = onReplies,
        modifier = modifier,
    )
}
