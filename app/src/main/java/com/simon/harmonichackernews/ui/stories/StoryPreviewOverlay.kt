@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.simon.harmonichackernews.ui.stories

import android.text.Html
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.adapters.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.FaviconLoader
import com.simon.harmonichackernews.network.LinkSummaryLoader
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

private const val TransformDurationMillis = 280
private const val PredictiveBackTranslationXDp = 56f
private const val PredictiveBackTranslationYDp = 18f
private const val TextStorySummaryMaxChars = 600

@Composable
internal fun StoryPreviewOverlay(controller: StoriesComposeController) {
    val state = controller.storyPreviewOverlay ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val pagerState = rememberPagerState(
        initialPage = state.initialPage,
        pageCount = { state.stories.size },
    )
    val transformProgress = remember(state) { Animatable(0f) }
    val predictiveProgressAnimation = remember(state) { Animatable(0f) }
    var targetBounds by remember(state) { mutableStateOf<Rect?>(null) }
    var lastPagerPosition by remember(state) { mutableFloatStateOf(state.initialPage.toFloat()) }
    var pendingListScroll by remember(state) { mutableFloatStateOf(0f) }
    val dismissRequest = controller.storyPreviewDismissRequest
    val predictiveSettleRequest = controller.storyPreviewPredictiveBackSettleRequest

    LaunchedEffect(state, targetBounds) {
        if (targetBounds != null && dismissRequest == 0) {
            transformProgress.animateTo(
                1f,
                tween(TransformDurationMillis, easing = FastOutSlowInEasing),
            )
        }
    }
    LaunchedEffect(dismissRequest) {
        if (dismissRequest == 0) return@LaunchedEffect
        transformProgress.animateTo(
            0f,
            tween(TransformDurationMillis, easing = FastOutSlowInEasing),
        )
        controller.completeStoryPreviewDismiss()
    }
    LaunchedEffect(controller.storyPreviewPredictiveBackProgress, predictiveSettleRequest) {
        if (predictiveSettleRequest == null) {
            predictiveProgressAnimation.snapTo(
                controller.storyPreviewPredictiveBackProgress.coerceIn(0f, 1f),
            )
        }
    }
    LaunchedEffect(predictiveSettleRequest?.serial) {
        val request = predictiveSettleRequest ?: return@LaunchedEffect
        predictiveProgressAnimation.animateTo(
            request.target,
            tween(180, easing = FastOutSlowInEasing),
        )
        controller.finishStoryPreviewPredictiveBackSettle(request)
    }
    LaunchedEffect(pagerState, state) {
        snapshotFlow {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, (state.stories.size - 1).coerceAtLeast(0).toFloat())
        }.collect { position ->
            val lower = floor(position).toInt().coerceIn(state.stories.indices)
            val upper = ceil(position).toInt().coerceIn(state.stories.indices)
            val offset = position - lower
            controller.onStoryPreviewPagePosition(lower, upper, offset)

            val delta = position - lastPagerPosition
            if (delta != 0f && state.stories.size > 1) {
                val segment = if (delta > 0f) lower else (ceil(position).toInt() - 1)
                    .coerceAtLeast(0)
                    .coerceAtMost(state.stories.lastIndex - 1)
                val first = state.stories[segment]
                val second = state.stories[segment + 1]
                pendingListScroll += delta * controller.getStoryPagingDistance(first.id, second.id)
                val wholePixels = if (pendingListScroll > 0f) {
                    floor(pendingListScroll).toInt()
                } else {
                    ceil(pendingListScroll).toInt()
                }
                if (wholePixels != 0) {
                    controller.requestScrollBy(wholePixels)
                    pendingListScroll -= wholePixels
                }
            }
            lastPagerPosition = position
        }
    }
    LaunchedEffect(pagerState, state) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect(controller::onStoryPreviewPageSettled)
    }

    val progress = transformProgress.value
    val predictiveProgress = predictiveProgressAnimation.value
    val predictiveEased = 1f - (1f - predictiveProgress) * (1f - predictiveProgress)
    val backDirection = if (controller.storyPreviewPredictiveBackEdge == 1) -1f else 1f
    val backTranslationX = with(density) { PredictiveBackTranslationXDp.dp.toPx() } *
        predictiveEased * backDirection
    val backTranslationY = with(density) { PredictiveBackTranslationYDp.dp.toPx() } *
        predictiveEased
    val currentStory = state.stories[pagerState.currentPage]
    val sourceBounds = controller.sourceBoundsForStory(currentStory.id)
    val target = targetBounds
    val startScaleX = if (sourceBounds != null && target != null && target.width > 0f) {
        (sourceBounds.width / target.width).coerceIn(0.08f, 1.15f)
    } else 0.96f
    val startScaleY = if (sourceBounds != null && target != null && target.height > 0f) {
        (sourceBounds.height / target.height).coerceIn(0.08f, 1.15f)
    } else 0.96f
    val startTranslationX = if (sourceBounds != null && target != null) {
        sourceBounds.center.x - target.center.x
    } else 0f
    val startTranslationY = if (sourceBounds != null && target != null) {
        sourceBounds.center.y - target.center.y
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(
                    alpha = 0.32f * progress * (1f - 0.55f * predictiveEased),
                ),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = controller::requestDismissStoryPreview,
            ),
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2,
            userScrollEnabled = progress >= 0.999f && dismissRequest == 0,
            // A preview can contain repeated story IDs while a list is being merged/refreshed.
            // Pager keys must still be unique or a long press can crash during composition.
            key = { page -> "${state.stories[page].id}:$page" },
        ) { page ->
            val pageOffset = abs(
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction,
            )
            val pageAlpha = (1f - ((pageOffset - 0.75f) / 0.25f)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .graphicsLayer(alpha = pageAlpha),
                contentAlignment = Alignment.Center,
            ) {
                StoryPreviewCard(
                    controller = controller,
                    story = state.stories[page],
                    page = page,
                    cardColor = Color(state.cardColors[page]),
                    modifier = Modifier
                        .widthIn(
                            max = dimensionResource(
                                if (Utils.isTablet(context.resources)) {
                                    R.dimen.compose_comment_action_tablet_max_width
                                } else {
                                    R.dimen.compose_comment_action_max_width
                                },
                            ),
                        )
                        .fillMaxWidth()
                        .then(
                            if (page == pagerState.currentPage) {
                                Modifier
                                    .onGloballyPositioned { targetBounds = it.boundsInWindow() }
                                    .graphicsLayer {
                                        val sharedScaleX = startScaleX + (1f - startScaleX) * progress
                                        val sharedScaleY = startScaleY + (1f - startScaleY) * progress
                                        val backScale = 1f - 0.1f * predictiveEased
                                        scaleX = sharedScaleX * backScale
                                        scaleY = sharedScaleY * backScale
                                        translationX = startTranslationX * (1f - progress) + backTranslationX
                                        translationY = startTranslationY * (1f - progress) + backTranslationY
                                        alpha = if (sourceBounds == null) progress else max(0.7f, progress)
                                        transformOrigin = TransformOrigin(
                                            if (backDirection > 0f) 0f else 1f,
                                            0.5f,
                                        )
                                    }
                            } else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                )
            }
        }
    }
}

@Composable
private fun StoryPreviewCard(
    controller: StoriesComposeController,
    story: Story,
    page: Int,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val summaryState = rememberStorySummary(story, controller)
    val fallbackSettings = remember(context) { StoryDisplaySettings.from(context) }
    val settings = controller.displaySettings ?: fallbackSettings
    val typography = rememberContentTypography(
        settings.font,
        settings.storyTextSize,
        settings.commentTextSize,
    )
    val contentVersion = controller.contentVersion
    val hasAccount = remember(contentVersion) { AccountUtils.hasAccountDetails(context) }
    val bookmarksEnabled = remember(contentVersion) { SettingsUtils.shouldUseBookmarks(context) }
    val upvoted = remember(contentVersion, story.id) { Utils.isUpvoted(context, story.id, false) }
    val bookmarked = remember(contentVersion, story.id) { Utils.isBookmarked(context, story.id) }
    val favorited = remember(contentVersion, story.id) { Utils.isFavorited(context, story.id) }
    val voteLoading = controller.storyPreviewVoteLoadingId == story.id
    val favoriteLoading = controller.storyPreviewFavoriteLoadingId == story.id
    val imageUrl = summaryState.result?.imageUrl?.takeIf(String::isNotBlank)
        ?: story.previewImageUrl?.takeIf(String::isNotBlank)
    val description = if (story.isLink) {
        summaryState.result?.description?.takeIf(String::isNotBlank)
    } else {
        remember(story.id, story.text) { extractTextStorySummary(story.text) }
    }
    val title = summaryState.result?.title?.takeIf(String::isNotBlank)
        ?: story.pdfTitle?.takeIf(String::isNotBlank)
        ?: story.videoTitle?.takeIf(String::isNotBlank)
        ?: story.title.orEmpty()
    val domain = if (story.isLink) {
        val storyUrl = story.url
        if (storyUrl == null) null else runCatching { Utils.getDomainName(storyUrl) }.getOrDefault(storyUrl)
    } else story.by
    val meta = buildString {
        append(story.score)
        append(if (story.score == 1) " point" else " points")
        if (!domain.isNullOrBlank()) append(" • ").append(domain)
        append(" • ").append(story.timeFormatted)
    }
    val faviconUrl = remember(story.id, story.url) {
        if (!story.isLink || story.url.isNullOrBlank()) null else runCatching {
            FaviconLoader.getFaviconUrl(
                story.url,
                SettingsUtils.getPreferredFaviconProvider(context),
            )
        }.getOrNull()
    }

    Surface(
        modifier = modifier.animateContentSize(tween(220, easing = FastOutSlowInEasing)),
        shape = RoundedCornerShape(28.dp),
        color = cardColor,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { controller.onStoryPreviewNavigate(page, story.isLink) },
            ) {
                AnimatedContent(
                    targetState = imageUrl to (summaryState.loading && story.isLink),
                    transitionSpec = {
                        fadeIn(tween(220)).togetherWith(fadeOut(tween(150)))
                    },
                    label = "story preview image content",
                ) { (currentImageUrl, imageLoading) ->
                    when {
                        currentImageUrl != null -> AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(currentImageUrl)
                                .setHeader("User-Agent", NetworkComponent.USER_AGENT)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2.15f),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        imageLoading -> StoryPreviewShimmer(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(2.15f),
                        )
                        else -> Spacer(Modifier.height(0.dp))
                    }
                }

                Column(
                    Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 7.dp),
                ) {
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            fadeIn(tween(220)).togetherWith(fadeOut(tween(150)))
                        },
                        label = "story preview title content",
                    ) { currentTitle ->
                        Text(
                            text = currentTitle,
                            color = HarmonicTheme.colors.storyNormal,
                            fontFamily = typography.family,
                            fontWeight = FontWeight.Bold,
                            fontSize = (typography.storyTitleSize + 2.5f).sp,
                            lineHeight = (typography.storyTitleSize + 5.5f).sp,
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (faviconUrl != null) {
                            AsyncImage(
                                model = faviconUrl,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                placeholder = painterResource(R.drawable.ic_public),
                                error = painterResource(R.drawable.ic_public),
                                fallback = painterResource(R.drawable.ic_public),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = meta,
                            color = HarmonicTheme.colors.storyDisabled,
                            fontFamily = typography.family,
                            fontSize = typography.storyMetaSize.sp,
                            lineHeight = (typography.storyMetaSize + 3f).sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Column(
                Modifier.padding(start = 20.dp, top = 7.dp, end = 20.dp, bottom = 18.dp),
            ) {
                AnimatedContent(
                    targetState = description to (summaryState.loading && story.isLink),
                    transitionSpec = {
                        fadeIn(tween(220)).togetherWith(fadeOut(tween(150)))
                    },
                    label = "story preview description content",
                ) { (currentDescription, descriptionLoading) ->
                    when {
                        !currentDescription.isNullOrBlank() -> SelectionContainer {
                            Text(
                                text = currentDescription,
                                color = HarmonicTheme.colors.storyNormal,
                                fontFamily = typography.family,
                                fontSize = typography.commentTextSize.sp,
                                lineHeight = (typography.commentTextSize + 2f).sp,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                ),
                            )
                        }
                        descriptionLoading -> DescriptionShimmer()
                        else -> Spacer(Modifier.height(0.dp))
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(top = 10.dp),
                    color = HarmonicTheme.colors.commentDivider.copy(alpha = 0.45f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasAccount) {
                        StoryPreviewActionIcon(
                            icon = if (upvoted) R.drawable.ic_thumb_up_filled else R.drawable.ic_thumb_up,
                            description = if (upvoted) "Remove upvote" else "Upvote",
                            loading = voteLoading,
                        ) { controller.onStoryPreviewAction(page, StoriesComposeController.STORY_PREVIEW_ACTION_VOTE) }
                    }
                    StoryPreviewActionIcon(
                        icon = if (story.clicked) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                        description = if (story.clicked) "Mark as unread" else "Mark as read",
                    ) { controller.onStoryPreviewAction(page, StoriesComposeController.STORY_PREVIEW_ACTION_READ) }
                    if (bookmarksEnabled) {
                        StoryPreviewActionIcon(
                            icon = if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                            description = if (bookmarked) "Remove bookmark" else "Bookmark",
                        ) { controller.onStoryPreviewAction(page, StoriesComposeController.STORY_PREVIEW_ACTION_BOOKMARK) }
                    }
                    if (hasAccount) {
                        StoryPreviewActionIcon(
                            icon = if (favorited) R.drawable.ic_star_filled else R.drawable.ic_star,
                            description = if (favorited) "Remove favorite" else "Favorite",
                            loading = favoriteLoading,
                        ) { controller.onStoryPreviewAction(page, StoriesComposeController.STORY_PREVIEW_ACTION_FAVORITE) }
                    }
                    ElevatedButton(
                        onClick = { controller.onStoryPreviewNavigate(page, false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .padding(start = 4.dp)
                            .semantics {
                                contentDescription = "Comments (${story.descendants})"
                            },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = HarmonicTheme.colors.storyNormal,
                        ),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_comment),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (hasAccount) story.descendants.toString() else "Comments",
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.StoryPreviewActionIcon(
    icon: Int,
    description: String,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = {
                (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.72f))
                    .togetherWith(fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.72f))
            },
            label = "story preview action",
        ) { waiting ->
            if (waiting) {
                LoadingIndicator(Modifier.size(28.dp))
            } else {
                IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painterResource(icon),
                        contentDescription = description,
                        tint = HarmonicTheme.colors.drawable,
                    )
                }
            }
        }
    }
}

private data class StorySummaryState(
    val loading: Boolean,
    val result: LinkSummaryLoader.Result? = null,
)

@Composable
private fun rememberStorySummary(
    story: Story,
    controller: StoriesComposeController,
): StorySummaryState {
    val context = LocalContext.current
    var state by remember(story.id, story.url) {
        mutableStateOf(
            if (!story.isLink || story.url.isNullOrBlank()) {
                StorySummaryState(false)
            } else {
                StoryPreviewImageLoader.getCachedLinkSummary(context, story.url)?.let {
                    StorySummaryState(false, it)
                } ?: StorySummaryState(true)
            },
        )
    }
    LaunchedEffect(story.id, story.url, state.result?.imageUrl) {
        val imageUrl = state.result?.imageUrl?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        if (story.previewImageUrl != imageUrl || !story.previewImageUrlLoaded) {
            story.previewImageUrl = imageUrl
            story.previewImageUrlLoaded = true
            story.previewImageLoadFailed = false
            controller.invalidateStory(story.id)
        }
    }
    DisposableEffect(story.id, story.url) {
        if (!story.isLink || story.url.isNullOrBlank() || state.result != null) {
            onDispose { }
        } else {
            val requestedUrl = story.url
            val request = LinkSummaryLoader.load(
                context,
                requestedUrl.orEmpty(),
                story.title.orEmpty(),
                object : LinkSummaryLoader.Callback {
                    override fun onSuccess(result: LinkSummaryLoader.Result) {
                        if (story.url != requestedUrl) return
                        state = StorySummaryState(false, result)
                    }

                    override fun onFailure(message: String) {
                        if (story.url == requestedUrl) state = StorySummaryState(false)
                    }
                },
            )
            onDispose(request::cancel)
        }
    }
    return state
}

@Composable
private fun StoryPreviewShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "story preview shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "story preview shimmer progress",
    )
    val base = HarmonicTheme.colors.storyDisabled.copy(alpha = 0.14f)
    val highlight = HarmonicTheme.colors.storyDisabled.copy(alpha = 0.25f)
    Box(
        modifier.background(
            Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to base,
                    progress.coerceIn(0f, 1f) to highlight,
                    1f to base,
                ),
            ),
        ),
    )
}

@Composable
private fun DescriptionShimmer() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StoryPreviewShimmer(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(Color.Transparent, RoundedCornerShape(8.dp)),
        )
        StoryPreviewShimmer(
            Modifier
                .fillMaxWidth(0.58f)
                .height(16.dp)
                .background(Color.Transparent, RoundedCornerShape(8.dp)),
        )
    }
}

private fun extractTextStorySummary(html: String?): String {
    if (html.isNullOrBlank()) return ""
    val summary = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00a0', ' ')
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[\\t\\u000B\\f ]+"), " ")
        .replace(Regex(" *\\n *"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
    if (summary.length <= TextStorySummaryMaxChars) return summary
    val minimumBoundary = (TextStorySummaryMaxChars * 0.75f).toInt()
    val end = (TextStorySummaryMaxChars - 1 downTo minimumBoundary)
        .firstOrNull { summary[it].isWhitespace() }
        ?: TextStorySummaryMaxChars
    return summary.substring(0, end).trim() + "…"
}

@Preview(name = "Story preview phone", device = Devices.PHONE, showBackground = true)
@Composable
private fun StoryPreviewCardPreview() {
    val story = remember {
        Story().apply {
            id = 123
            title = "A surveillance treaty in disguise"
            by = "example"
            score = 101
            descendants = 30
            isLink = false
            text = "The preview keeps the story, its useful context, and its actions together."
        }
    }
    HarmonicTheme {
        Surface(color = HarmonicTheme.colors.background) {
            // The runtime controller owns actions; this preview intentionally exercises the
            // card's real typography through a lightweight controller shell.
            Text(
                text = story.title.orEmpty(),
                modifier = Modifier.padding(24.dp),
                fontFamily = ProductSansFontFamily,
            )
        }
    }
}
