package com.simon.harmonichackernews.ui.submissions

import android.text.Html
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.R as MaterialR
import com.google.android.material.button.MaterialButton
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.adapters.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.FaviconLoader
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemStyle
import com.simon.harmonichackernews.ui.content.StoryItemUiModel
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils

enum class SubmissionFilter {
    STORIES,
    BOTH,
    COMMENTS,
}

/**
 * State bridge used by the submissions coordinator inside MainActivity's Compose navigation.
 */
class SubmissionsComposeController internal constructor(
    internal val userName: String,
    initialFilter: SubmissionFilter,
    initialDisplaySettings: StoryDisplaySettings,
    internal val listener: Listener,
) {

    internal var submissions by mutableStateOf<List<Story>>(emptyList())
        private set
    internal var selectedFilter by mutableStateOf(initialFilter)
        private set
    internal var showFilter by mutableStateOf(false)
        private set
    internal var canLoadMore by mutableStateOf(false)
        private set
    internal var loadedSuccessfully by mutableStateOf(false)
        private set
    internal var loading by mutableStateOf(false)
        private set
    internal var showInitialLoading by mutableStateOf(false)
        private set
    internal var refreshing by mutableStateOf(false)
        private set
    internal var emptyText by mutableStateOf("No submissions")
        private set
    internal var displaySettings by mutableStateOf(initialDisplaySettings)
        private set
    internal var contentVersion by mutableIntStateOf(0)
        private set
    internal var scrollRestoreRequest by mutableStateOf<ScrollRestoreRequest?>(null)
        private set

    var firstVisibleStoryPosition: Int = 0
        private set
    var firstVisibleStoryTop: Int = 0
        private set
    var appBarCollapsed: Boolean = false
        private set

    fun updateContent(
        submissions: List<Story>,
        selectedFilter: SubmissionFilter,
        showFilter: Boolean,
        canLoadMore: Boolean,
        loadedSuccessfully: Boolean,
        emptyText: String,
    ) {
        this.submissions = submissions.toList()
        this.selectedFilter = selectedFilter
        this.showFilter = showFilter
        this.canLoadMore = canLoadMore
        this.loadedSuccessfully = loadedSuccessfully
        this.emptyText = emptyText
    }

    fun updateLoading(
        loading: Boolean,
        showInitialLoading: Boolean,
        refreshing: Boolean,
    ) {
        this.loading = loading
        this.showInitialLoading = showInitialLoading
        this.refreshing = refreshing
    }

    fun updateDisplaySettings(settings: StoryDisplaySettings) {
        displaySettings = settings
    }

    fun refreshStoryRows() {
        contentVersion++
    }

    fun restoreScrollState(
        firstVisiblePosition: Int,
        firstVisibleTop: Int,
        appBarCollapsed: Boolean,
    ) {
        scrollRestoreRequest = ScrollRestoreRequest(
            firstVisiblePosition = firstVisiblePosition,
            firstVisibleTop = firstVisibleTop,
            appBarCollapsed = appBarCollapsed,
        )
    }

    internal fun consumeScrollRestoreRequest(request: ScrollRestoreRequest) {
        if (scrollRestoreRequest == request) {
            scrollRestoreRequest = null
        }
    }

    internal fun updateScrollState(state: LazyListState) {
        val listIndex = state.firstVisibleItemIndex
        firstVisibleStoryPosition = (listIndex - 1).coerceAtLeast(0)
        firstVisibleStoryTop = if (listIndex == 0) {
            0
        } else {
            -state.firstVisibleItemScrollOffset
        }
        appBarCollapsed = listIndex > 0 || state.firstVisibleItemScrollOffset > 0
    }

    internal data class ScrollRestoreRequest(
        val firstVisiblePosition: Int,
        val firstVisibleTop: Int,
        val appBarCollapsed: Boolean,
    )

    interface Listener {
        fun onFilterSelected(filter: SubmissionFilter)
        fun onRefresh()
        fun onStoryLinkClick(story: Story)
        fun onStoryCommentsClick(story: Story)
        fun onCommentStoryClick(story: Story)
        fun onCommentRepliesClick(story: Story)
        fun onLoadMore()
    }

    companion object {
        @JvmStatic
        fun create(
            activity: ComponentActivity,
            userName: String,
            initialFilter: SubmissionFilter,
            listener: Listener,
        ): SubmissionsComposeController {
            return SubmissionsComposeController(
                userName = userName,
                initialFilter = initialFilter,
                initialDisplaySettings = StoryDisplaySettings.from(activity)
                    .withShowIndex(false),
                listener = listener,
            )
        }
    }
}

/**
 * The header is the first lazy item, scrolling is supplied by the LazyColumn, and every content
 * row retains the qualified `single_view_side_margin` used on phones, foldables, and tablets.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubmissionsScreen(controller: SubmissionsComposeController) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect {
            controller.updateScrollState(listState)
        }
    }

    val scrollRestoreRequest = controller.scrollRestoreRequest
    LaunchedEffect(
        scrollRestoreRequest,
        controller.submissions.size,
        controller.loadedSuccessfully,
    ) {
        val request = scrollRestoreRequest ?: return@LaunchedEffect
        if (!controller.loadedSuccessfully && controller.submissions.isEmpty()) {
            return@LaunchedEffect
        }
        if (request.appBarCollapsed && controller.submissions.isNotEmpty()) {
            val index = (request.firstVisiblePosition.coerceAtLeast(0) + 1)
                .coerceAtMost(controller.submissions.size)
            listState.scrollToItem(
                index = index,
                scrollOffset = (-request.firstVisibleTop).coerceAtLeast(0),
            )
        } else {
            listState.scrollToItem(0)
        }
        controller.consumeScrollRestoreRequest(request)
    }

    PullToRefreshBox(
        isRefreshing = controller.refreshing,
        onRefresh = controller.listener::onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background),
    ) {
        SubmissionsList(
            userName = controller.userName,
            submissions = controller.submissions,
            selectedFilter = controller.selectedFilter,
            showFilter = controller.showFilter,
            canLoadMore = controller.canLoadMore,
            loadedSuccessfully = controller.loadedSuccessfully,
            loading = controller.loading,
            emptyText = controller.emptyText,
            displaySettings = controller.displaySettings,
            contentVersion = controller.contentVersion,
            listState = listState,
            listener = controller.listener,
        )

        if (controller.showInitialLoading) {
            LoadingIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    listener: SubmissionsComposeController.Listener,
) {
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sideMargin = dimensionResource(R.dimen.single_view_side_margin)

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
                onFilterSelected = listener::onFilterSelected,
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
                        onStoryClick = { listener.onCommentStoryClick(story) },
                        onRepliesClick = { listener.onCommentRepliesClick(story) },
                    )
                } else {
                    val model = rememberStoryItemUiModel(story, displaySettings)
                    StoryItem(
                        model = model,
                        style = storyItemStyle(story, displaySettings),
                        listItem = true,
                        onLinkClick = { listener.onStoryLinkClick(story) },
                        onCommentClick = { listener.onStoryCommentsClick(story) },
                    )
                }
            }
        }

        if (canLoadMore) {
            item(key = "load-more") {
                LoadMoreButton(
                    loading = loading,
                    onClick = listener::onLoadMore,
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = sideMargin),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubmissionsHeader(
    userName: String,
    selectedFilter: SubmissionFilter,
    showFilter: Boolean,
    compact: Boolean,
    sideMargin: androidx.compose.ui.unit.Dp,
    onFilterSelected: (SubmissionFilter) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmonicTheme.colors.background)
            .padding(horizontal = sideMargin)
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Text(
            text = "$userName's submissions",
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (compact) 27.75.dp else 47.75.dp,
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
    val colors = rememberLegacyFilterColors()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val innerCorner by animateDpAsState(
        targetValue = if (isPressed) 4.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 800f,
        ),
        label = "submission filter button corners",
    )
    val shape = if (selected) {
        RoundedCornerShape(if (isPressed) 12.dp else 24.dp)
    } else {
        when (position) {
            0 -> RoundedCornerShape(
                topStart = 24.dp,
                topEnd = innerCorner,
                bottomEnd = innerCorner,
                bottomStart = 24.dp,
            )
            2 -> RoundedCornerShape(
                topStart = innerCorner,
                topEnd = 24.dp,
                bottomEnd = 24.dp,
                bottomStart = innerCorner,
            )
            else -> RoundedCornerShape(innerCorner)
        }
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (selected) colors.checkedBackground else Color.Transparent)
            .border(
                1.dp,
                if (selected) colors.checkedStroke else colors.uncheckedStroke,
                shape,
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
                interactionSource = interactionSource,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) colors.checkedText else colors.uncheckedText,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

private data class LegacyFilterColors(
    val checkedBackground: Color,
    val checkedText: Color,
    val checkedStroke: Color,
    val uncheckedText: Color,
    val uncheckedStroke: Color,
)

@Composable
private fun rememberLegacyFilterColors(): LegacyFilterColors {
    val context = LocalContext.current
    val fallback = HarmonicTheme.colors
    return remember(context, fallback) {
        val button = MaterialButton(
            context,
            null,
            MaterialR.attr.materialButtonOutlinedStyle,
        ).apply {
            isCheckable = true
        }
        val checkedState = intArrayOf(
            android.R.attr.state_enabled,
            android.R.attr.state_checkable,
            android.R.attr.state_checked,
        )
        val uncheckedState = intArrayOf(
            android.R.attr.state_enabled,
            android.R.attr.state_checkable,
            -android.R.attr.state_checked,
        )
        fun android.content.res.ColorStateList?.colorFor(
            state: IntArray,
            default: Color,
        ): Color = Color(this?.getColorForState(state, default.toArgb()) ?: default.toArgb())

        LegacyFilterColors(
            checkedBackground = button.backgroundTintList.colorFor(
                checkedState,
                fallback.storyNormal,
            ),
            checkedText = button.textColors.colorFor(
                checkedState,
                fallback.background,
            ),
            checkedStroke = button.strokeColor.colorFor(
                checkedState,
                fallback.storyNormal,
            ),
            uncheckedText = button.textColors.colorFor(
                uncheckedState,
                fallback.storyNormal,
            ),
            uncheckedStroke = button.strokeColor.colorFor(
                uncheckedState,
                fallback.outlineVariant,
            ),
        )
    }
}

@Composable
private fun rememberStoryItemUiModel(
    story: Story,
    settings: StoryDisplaySettings,
): StoryItemUiModel {
    val context = LocalContext.current
    val cachedPreviewUrl = remember(story.id, story.url) {
        story.previewImageUrl ?: runCatching {
            StoryPreviewImageLoader.getCachedPreviewImageUrl(context, story.id, story.url)
        }.getOrNull()
    }
    val cachedSummary = remember(story.id, story.url, settings.showSummary) {
        if (settings.showSummary) {
            StoryPreviewImageLoader.getCachedLinkSummary(context, story.url)?.description
        } else {
            null
        }
    }
    var previewUrl by remember(story.id, story.url) { mutableStateOf(cachedPreviewUrl) }
    var summary by remember(story.id, story.url) {
        mutableStateOf(story.summary ?: story.linkSummaryDescription ?: cachedSummary.orEmpty())
    }

    DisposableEffect(
        story.id,
        story.url,
        settings.previewImageMode,
        settings.showSummary,
    ) {
        val needsPreview = settings.previewImageMode != SettingsUtils.STORY_PREVIEW_IMAGE_OFF
        val request = if (story.isLink && !story.url.isNullOrBlank() &&
            (needsPreview || settings.showSummary)
        ) {
            StoryPreviewImageLoader.loadPreviewContent(
                context,
                story.id,
                story.url,
                settings.showSummary,
            ) { imageUrl, result ->
                story.previewImageUrl = imageUrl
                story.previewImageUrlLoaded = true
                previewUrl = imageUrl
                result?.description?.let {
                    story.linkSummaryDescription = it
                    story.linkSummaryLoaded = true
                    summary = it
                }
            }
        } else {
            null
        }
        onDispose { request?.cancel() }
    }

    val fullDomain = remember(story.url) {
        runCatching { story.getDisplayDomain(true) }.getOrDefault("")
    }
    val shortDomain = remember(story.url) {
        runCatching { story.getDisplayDomain(false) }.getOrDefault(fullDomain)
    }
    val faviconUrl = remember(story.url, settings.faviconProvider) {
        runCatching { FaviconLoader.getFaviconUrl(story.url, settings.faviconProvider) }
            .getOrNull()
    }
    val tintBaseColor = remember(context) {
        PreviewImageTintUtils.getTintBaseColor(context)
    }
    val previewTintArgb = remember(
        story.id,
        previewUrl,
        tintBaseColor,
        story.previewImageTintColorLoaded,
    ) {
        previewUrl?.let { sourceUrl ->
            if (story.previewImageTintColorLoaded &&
                story.previewImageTintSourceUrl == sourceUrl &&
                story.previewImageTintBaseColor == tintBaseColor
            ) {
                story.previewImageTintColor
            } else {
                StoryPreviewImageLoader.loadCachedPreviewImageTintColor(
                    context,
                    story.id,
                    sourceUrl,
                    tintBaseColor,
                )
            }
        }
    }
    val faviconTintArgb = remember(
        story.id,
        faviconUrl,
        tintBaseColor,
        story.faviconTintColorLoaded,
    ) {
        faviconUrl?.let { sourceUrl ->
            if (story.faviconTintColorLoaded &&
                story.faviconTintSourceUrl == sourceUrl &&
                story.faviconTintBaseColor == tintBaseColor
            ) {
                story.faviconTintColor
            } else {
                StoryPreviewImageLoader.loadCachedPreviewImageTintColor(
                    context,
                    story.id,
                    sourceUrl,
                    tintBaseColor,
                )
            }
        }
    }

    return StoryItemUiModel(
        index = "",
        title = story.title.orEmpty(),
        summary = summary,
        points = story.score,
        domain = fullDomain.orEmpty(),
        domainWithoutTopLevel = shortDomain.orEmpty(),
        age = story.timeFormatted,
        commentCount = story.descendants,
        faviconRes = R.drawable.ic_public,
        previewImageRes = null,
        faviconUrl = faviconUrl,
        previewImageUrl = previewUrl,
        faviconTintArgb = faviconTintArgb,
        previewImageTintArgb = previewTintArgb,
    )
}

private fun storyItemStyle(
    story: Story,
    settings: StoryDisplaySettings,
): StoryItemStyle = StoryItemStyle(
    previewImageMode = settings.previewImageMode,
    borderlessLargeImage = settings.borderlessLargePreviewImage,
    compact = settings.compactView,
    showSummary = settings.showSummary,
    showFavicon = settings.thumbnails,
    showPoints = settings.showPoints,
    compactPoints = settings.compactPoints,
    includeTopLevelDomain = settings.includeTopLevelDomain,
    showCommentCount = settings.showCommentsCount,
    showIndex = false,
    commentsOnLeft = settings.leftAlign,
    tintCard = settings.tintCardUsingPreview,
    cardStyle = settings.cardStyle,
    useHotnessIcon = settings.hotness > 0 &&
        story.score + story.descendants > settings.hotness,
    preferredFont = settings.font,
    textSize = settings.storyTextSize,
    dimmed = settings.grayOutClicked && story.clicked,
)

/** Compose equivalent of `submissions_comment.xml` and its optional card wrapper. */
@Composable
private fun SubmissionCommentItem(
    story: Story,
    displaySettings: StoryDisplaySettings,
    contentVersion: Int,
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
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SubmissionActionButton(
                label = "Story",
                icon = R.drawable.ic_newspaper,
                onClick = onStoryClick,
                enabled = story.commentMasterId > 0 || story.parentId > 0,
                modifier = Modifier.weight(1f),
            )
            SubmissionActionButton(
                label = "Replies",
                icon = R.drawable.ic_reply,
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
) {
    val context = LocalContext.current
    val linkColor = HarmonicTheme.colors.link
    val linkStyles = remember(linkColor) {
        TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline,
            ),
        )
    }
    val linkListener = remember(context) {
        LinkInteractionListener { annotation ->
            if (annotation is LinkAnnotation.Url) {
                Utils.openLinkMaybeHN(context, annotation.url)
            }
        }
    }
    val formatted = remember(html, linkStyles, linkListener) {
        runCatching {
            AnnotatedString.fromHtml(
                htmlString = Utils.expandShortenedAnchorText(html).orEmpty(),
                linkStyles = linkStyles,
                linkInteractionListener = linkListener,
            )
        }.getOrElse {
            AnnotatedString(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString())
        }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubmissionActionButton(
    label: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadMoreButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.height(56.dp),
            enabled = !loading,
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(
                text = "Load more",
                modifier = Modifier.padding(start = 8.dp),
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
            )
        }
        if (loading) {
            LoadingIndicator(modifier = Modifier.size(32.dp))
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
            painter = painterResource(R.drawable.ic_subject),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color.Unspecified,
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

private val legacyTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
private fun SubmissionsScreenPreview() {
    val context = LocalContext.current
    val comment = remember {
        Story().apply {
            id = 1
            isComment = true
            time = (System.currentTimeMillis() / 1000L).toInt() - 7200
            commentMasterTitle = "The Best Essay"
            commentMasterId = 2
            text = "In earlier drafts I wrote that the best essays form a partial order."
        }
    }
    val story = remember {
        Story().apply {
            id = 2
            loaded = true
            title = "If you're interested in eye-tracking, I'm interested in funding you"
            url = "https://example.com"
            score = 374
            descendants = 209
            time = (System.currentTimeMillis() / 1000L).toInt() - 7200
        }
    }
    val listener = remember {
        object : SubmissionsComposeController.Listener {
            override fun onFilterSelected(filter: SubmissionFilter) = Unit
            override fun onRefresh() = Unit
            override fun onStoryLinkClick(story: Story) = Unit
            override fun onStoryCommentsClick(story: Story) = Unit
            override fun onCommentStoryClick(story: Story) = Unit
            override fun onCommentRepliesClick(story: Story) = Unit
            override fun onLoadMore() = Unit
        }
    }

    HarmonicTheme {
        SubmissionsList(
            userName = "pg",
            submissions = listOf(comment, story),
            selectedFilter = SubmissionFilter.BOTH,
            showFilter = true,
            canLoadMore = false,
            loadedSuccessfully = true,
            loading = false,
            emptyText = "No submissions",
            displaySettings = StoryDisplaySettings.from(context).withShowIndex(false),
            contentVersion = 0,
            listState = rememberLazyListState(),
            listener = listener,
        )
    }
}
