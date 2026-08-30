package com.simon.harmonichackernews.ui.submissions

import org.jetbrains.compose.resources.DrawableResource

import com.simon.harmonichackernews.resources.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import com.simon.harmonichackernews.ui.common.currentSharedHazeState
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import androidx.compose.material3.Icon
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.StoryListResourceRuntime
import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.presentation.SubmissionFilter
import com.simon.harmonichackernews.presentation.SubmissionsIntent
import com.simon.harmonichackernews.presentation.SubmissionsScrollRestoration
import com.simon.harmonichackernews.presentation.SubmissionsUiState
import com.simon.harmonichackernews.ui.content.htmlAnnotatedString
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemStyleContext
import com.simon.harmonichackernews.ui.content.StoryItemUiModel
import com.simon.harmonichackernews.ui.content.toStoryItemStyle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.HtmlTextUtils

/**
 * The header is the first lazy item, scrolling is supplied by the LazyColumn, and every content
 * row retains the qualified `single_view_side_margin` used on phones, foldables, and tablets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionsScreen(
    userName: String,
    state: SubmissionsUiState,
    displaySettings: StoryDisplaySettings,
    initialScrollRestoration: SubmissionsScrollRestoration?,
    onIntent: (SubmissionsIntent) -> Unit,
    previewResources: StoryListResourceRuntime,
    includeStatusBarInset: Boolean = true,
    reserveBackButtonSpace: Boolean = false,
    pullToRefreshEnabled: Boolean = true,
    storyItemModel: @Composable (Story, StoryDisplaySettings) -> StoryItemUiModel,
    onOpenLink: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val hazeState = currentSharedHazeState()
    val currentOnIntent by rememberUpdatedState(onIntent)
    var pendingScrollRestoration by remember(initialScrollRestoration) {
        mutableStateOf(initialScrollRestoration)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect {
            val listIndex = listState.firstVisibleItemIndex
            currentOnIntent(
                SubmissionsIntent.RecordScrollPosition(
                    firstVisibleStoryPosition = (listIndex - 1).coerceAtLeast(0),
                    firstVisibleStoryTop = if (listIndex == 0) {
                        0
                    } else {
                        -listState.firstVisibleItemScrollOffset
                    },
                    appBarCollapsed = listIndex > 0 || listState.firstVisibleItemScrollOffset > 0,
                ),
            )
        }
    }

    LaunchedEffect(
        pendingScrollRestoration,
        state.items.size,
        state.loadedSuccessfully,
    ) {
        val restoration = pendingScrollRestoration ?: return@LaunchedEffect
        if (!state.loadedSuccessfully && state.items.isEmpty()) {
            return@LaunchedEffect
        }
        if (restoration.appBarCollapsed && state.items.isNotEmpty()) {
            val index = (restoration.firstVisibleStoryPosition.coerceAtLeast(0) + 1)
                .coerceAtMost(state.items.size)
            listState.scrollToItem(
                index = index,
                scrollOffset = (-restoration.firstVisibleStoryTop).coerceAtLeast(0),
            )
        } else {
            listState.scrollToItem(0)
        }
        pendingScrollRestoration = null
    }

    val modifier = Modifier
        .fillMaxSize()
        .background(HarmonicTheme.colors.background)
        .sharedHazeSource(hazeState)
    val content: @Composable BoxScope.() -> Unit = {
        SubmissionsList(
            userName = userName,
            submissions = state.items,
            selectedFilter = state.filter,
            showFilter = state.hasUnfilteredItems,
            canLoadMore = state.canLoadMore,
            loadedSuccessfully = state.loadedSuccessfully,
            loading = state.loading,
            emptyText = state.emptyText,
            displaySettings = displaySettings,
            contentVersion = state.revision,
            listState = listState,
            onIntent = onIntent,
            previewResources = previewResources,
            includeStatusBarInset = includeStatusBarInset,
            reserveBackButtonSpace = reserveBackButtonSpace,
            storyItemModel = storyItemModel,
            onOpenLink = onOpenLink,
        )

        if (state.showInitialLoading) {
            HarmonicLoadingIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp),
            )
        }
    }

    if (pullToRefreshEnabled) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { onIntent(SubmissionsIntent.Refresh) },
            modifier = modifier,
            content = content,
        )
    } else {
        Box(
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
private fun SubmissionsList(
    userName: String,
    submissions: List<Story>,
    selectedFilter: SubmissionFilter,
    showFilter: Boolean,
    canLoadMore: Boolean,
    loadedSuccessfully: Boolean,
    loading: Boolean,
    emptyText: String,
    displaySettings: StoryDisplaySettings,
    contentVersion: Int,
    listState: LazyListState,
    onIntent: (SubmissionsIntent) -> Unit,
    previewResources: StoryListResourceRuntime,
    includeStatusBarInset: Boolean,
    reserveBackButtonSpace: Boolean,
    storyItemModel: @Composable (Story, StoryDisplaySettings) -> StoryItemUiModel,
    onOpenLink: (String) -> Unit,
) {
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sideMargin = 0.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = navigationBottom),
    ) {
        item(key = "header") {
            SubmissionsHeader(
                userName = userName,
                selectedFilter = selectedFilter,
                showFilter = showFilter,
                compact = displaySettings.compactHeader,
                sideMargin = sideMargin,
                includeStatusBarInset = includeStatusBarInset,
                reserveBackButtonSpace = reserveBackButtonSpace,
                onFilterSelected = { onIntent(SubmissionsIntent.SelectFilter(it)) },
            )
        }

        if (loadedSuccessfully && !loading && submissions.isEmpty()) {
            item(key = "empty") {
                EmptySubmissions(
                    text = emptyText,
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = sideMargin),
                )
            }
        }

        items(
            items = submissions,
            key = { it.id },
            contentType = { if (it.isComment) "comment" else "story" },
        ) { story ->
            Box(
                modifier = Modifier
                    .animateItem()
                    .padding(horizontal = sideMargin),
            ) {
                if (story.isComment) {
                    SubmissionCommentItem(
                        story = story,
                        displaySettings = displaySettings,
                        contentVersion = contentVersion,
                        onOpenLink = onOpenLink,
                        onStoryClick = { onIntent(SubmissionsIntent.OpenCommentMaster(story)) },
                        onRepliesClick = { onIntent(SubmissionsIntent.OpenCommentReplies(story)) },
                    )
                } else {
                    val model = storyItemModel(story, displaySettings)
                    StoryItem(
                        model = model,
                        style = displaySettings.toStoryItemStyle(
                            StoryItemStyleContext(
                                score = story.score,
                                commentCount = story.descendants,
                                clicked = story.clicked,
                                showIndex = false,
                            ),
                        ),
                        listItem = true,
                        onLinkClick = { onIntent(SubmissionsIntent.OpenStoryLink(story)) },
                        onCommentClick = { onIntent(SubmissionsIntent.OpenStoryComments(story)) },
                        onPreviewLoadSuccess = {
                            model.previewImageUrl?.let { imageUrl ->
                                previewResources.completePreviewImageLoad(
                                    story.id,
                                    story.url.orEmpty(),
                                    imageUrl,
                                    success = true,
                                )
                            }
                        },
                        onPreviewLoadFailed = {
                            model.previewImageUrl?.let { imageUrl ->
                                previewResources.completePreviewImageLoad(
                                    story.id,
                                    story.url.orEmpty(),
                                    imageUrl,
                                    success = false,
                                )
                            }
                        },
                        onPreviewTintExtracted = { tintColor ->
                            val sourceUrl = model.previewImageUrl
                            val baseColor = model.tintFallbackArgb
                            if (sourceUrl != null && baseColor != null) {
                                previewResources.recordTint(
                                    story = story,
                                    kind = StoryResourceTintKind.PREVIEW_IMAGE,
                                    sourceUrl = sourceUrl,
                                    baseColorArgb = baseColor,
                                    paletteConfigKey = displaySettings.paletteTintMode,
                                    tintColorArgb = tintColor,
                                )
                            }
                        },
                        onFaviconTintExtracted = { tintColor ->
                            val sourceUrl = model.faviconUrl
                            val baseColor = model.tintFallbackArgb
                            if (sourceUrl != null && baseColor != null) {
                                previewResources.recordTint(
                                    story = story,
                                    kind = StoryResourceTintKind.FAVICON,
                                    sourceUrl = sourceUrl,
                                    baseColorArgb = baseColor,
                                    paletteConfigKey = displaySettings.paletteTintMode,
                                    tintColorArgb = tintColor,
                                )
                            }
                        },
                    )
                }
            }
        }

        if (canLoadMore) {
            item(key = "load-more") {
                LoadMoreButton(
                    loading = loading,
                    onClick = { onIntent(SubmissionsIntent.LoadMore) },
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = sideMargin),
                )
            }
        }
    }
}

@Composable
private fun SubmissionsHeader(
    userName: String,
    selectedFilter: SubmissionFilter,
    showFilter: Boolean,
    compact: Boolean,
    sideMargin: androidx.compose.ui.unit.Dp,
    includeStatusBarInset: Boolean,
    reserveBackButtonSpace: Boolean,
    onFilterSelected: (SubmissionFilter) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmonicTheme.colors.background)
            .padding(horizontal = sideMargin)
            .padding(horizontal = 16.dp)
            .then(
                if (includeStatusBarInset) {
                    Modifier.windowInsetsPadding(WindowInsets.statusBars)
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = "$userName's submissions",
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (reserveBackButtonSpace) {
                        64.dp
                    } else if (compact) {
                        27.75.dp
                    } else {
                        47.75.dp
                    },
                    bottom = if (compact) 8.dp else 16.dp,
                )
                .semantics {
                    heading()
                    contentDescription = "Submissions by $userName"
                },
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            style = legacyTextStyle,
        )

        if (showFilter) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SubmissionFilterButton(
                    label = "Stories",
                    selected = selectedFilter == SubmissionFilter.STORIES,
                    position = 0,
                    onClick = { onFilterSelected(SubmissionFilter.STORIES) },
                    modifier = Modifier.weight(1f),
                )
                SubmissionFilterButton(
                    label = "Both",
                    selected = selectedFilter == SubmissionFilter.BOTH,
                    position = 1,
                    onClick = { onFilterSelected(SubmissionFilter.BOTH) },
                    modifier = Modifier.weight(1f),
                )
                SubmissionFilterButton(
                    label = "Comments",
                    selected = selectedFilter == SubmissionFilter.COMMENTS,
                    position = 2,
                    onClick = { onFilterSelected(SubmissionFilter.COMMENTS) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SubmissionFilterButton(
    label: String,
    selected: Boolean,
    position: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = when (position) {
        0 -> RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp)
        2 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(8.dp)
    }
    val colors = HarmonicTheme.colors
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (selected) colors.storyNormal else Color.Transparent)
            .border(1.dp, if (selected) colors.storyNormal else colors.outlineVariant, shape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) colors.background else colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

/** Compose equivalent of `submissions_comment.xml` and its optional card wrapper. */
@Composable
private fun SubmissionCommentItem(
    story: Story,
    displaySettings: StoryDisplaySettings,
    contentVersion: Int,
    onOpenLink: (String) -> Unit,
    onStoryClick: () -> Unit,
    onRepliesClick: () -> Unit,
) {
    val colors = HarmonicTheme.colors
    val commentMasterTitle = remember(story.commentMasterTitle, contentVersion) {
        story.commentMasterTitle
    }
    val cardStyle = displaySettings.cardStyle
    val shape = RoundedCornerShape(8.dp)
    val container = Modifier
        .fillMaxWidth()
        .padding(
            horizontal = if (cardStyle) 8.dp else 0.dp,
            vertical = if (cardStyle) 4.dp else 0.dp,
        )
        .shadow(if (cardStyle) 1.dp else 0.dp, shape, clip = false)
        .clip(shape)
        .background(if (cardStyle) colors.surfaceContainerHigh else colors.background)
        .border(
            1.dp,
            if (cardStyle) colors.commentDivider else Color.Transparent,
            shape,
        )

    Column(
        modifier = container.padding(
            start = 16.dp,
            top = 10.dp,
            end = 16.dp,
            bottom = 10.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (commentMasterTitle.isNullOrBlank()) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "On",
                        color = colors.storyDisabled,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        style = legacyTextStyle,
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(width = 150.dp, height = 17.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(colors.surfaceContainerHighest),
                    )
                }
            } else {
                Text(
                    text = "On \"$commentMasterTitle\"",
                    modifier = Modifier.weight(1f),
                    color = colors.storyDisabled,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    style = legacyTextStyle,
                )
            }
            Text(
                text = story.timeFormatted,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .defaultMinSize(minHeight = 22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.submissionsCommentTimeBackground)
                    .border(
                        1.dp,
                        colors.submissionsCommentTimeOutline,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(start = 7.dp, top = 2.5.dp, end = 7.dp, bottom = 1.5.dp),
                color = colors.storyDisabled,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                style = legacyTextStyle,
            )
        }

        SubmissionCommentBody(
            html = story.text.orEmpty(),
            preferredFont = displaySettings.font,
            textSize = displaySettings.commentTextSize,
            background = if (cardStyle) colors.surfaceContainerHigh else colors.background,
            onOpenLink = onOpenLink,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SubmissionActionButton(
                label = "Story",
                icon = Res.drawable.ic_newspaper,
                onClick = onStoryClick,
                enabled = story.commentMasterId > 0 || story.parentId > 0,
                modifier = Modifier.weight(1f),
            )
            SubmissionActionButton(
                label = "Replies",
                icon = Res.drawable.ic_reply,
                onClick = onRepliesClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SubmissionCommentBody(
    html: String,
    preferredFont: String,
    textSize: Float,
    background: Color,
    onOpenLink: (String) -> Unit,
) {
    val linkColor = HarmonicTheme.colors.link
    val linkListener = remember(onOpenLink) {
        LinkInteractionListener { annotation ->
            if (annotation is LinkAnnotation.Url) {
                onOpenLink(annotation.url)
            }
        }
    }
    val formatted = remember(html, linkColor, linkListener) {
        htmlAnnotatedString(
            HtmlTextUtils.expandShortenedAnchorText(html).orEmpty(),
            linkColor,
            linkListener,
        )
    }
    val typography = com.simon.harmonichackernews.ui.content.rememberContentTypography(
        preferredFont = preferredFont,
        commentTextSize = textSize,
    )
    var truncated by remember(formatted) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatted,
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = typography.family,
            fontSize = typography.commentTextSize.sp,
            maxLines = 16,
            overflow = TextOverflow.Ellipsis,
            style = legacyTextStyle,
            onTextLayout = { truncated = it.hasVisualOverflow },
        )
        if (truncated) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, background),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun SubmissionActionButton(
    label: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = HarmonicTheme.colors.storyNormal,
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun LoadMoreButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            HarmonicLoadingIndicator(modifier = Modifier.size(32.dp))
        } else {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.height(56.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_add), contentDescription = null)
                Text(
                    text = "Load more",
                    modifier = Modifier.padding(start = 8.dp),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun EmptySubmissions(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_subject),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = HarmonicTheme.colors.drawable,
        )
        Text(
            text = text,
            modifier = Modifier.padding(top = 4.dp, bottom = 36.dp),
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            style = legacyTextStyle,
        )
    }
}

private val legacyTextStyle = TextStyle()
