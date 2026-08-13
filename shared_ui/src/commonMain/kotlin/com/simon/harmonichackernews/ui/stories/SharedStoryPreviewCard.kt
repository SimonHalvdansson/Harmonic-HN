package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import com.simon.harmonichackernews.ui.common.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.StoryPreviewActionKind
import com.simon.harmonichackernews.data.ItemTimeFormatter
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.network.LinkSummary
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.content.SharedNetworkImage
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.DomainNamePolicy
import com.simon.harmonichackernews.utils.HtmlTextUtils
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private const val TextStorySummaryMaxChars = 600
private val SharedStoryPreviewShape = RoundedCornerShape(28.dp)

data class StoryPreviewSummaryState(
    val loading: Boolean,
    val result: LinkSummary? = null,
)

@Composable
fun SharedStoryPreviewCard(
    controller: StoriesComposeController,
    story: StoryListItemSnapshot,
    page: Int,
    cardColor: Color,
    settings: StoryDisplaySettings,
    summaryState: StoryPreviewSummaryState,
    previewResource: StoryPreviewResourceState? = null,
    hasAccount: Boolean,
    bookmarksEnabled: Boolean,
    faviconUrl: String?,
    textStyle: TextStyle,
    htmlToPlainText: (String) -> String,
    onPreviewImageLoaded: (storyId: Int, pageUrl: String, imageUrl: String) -> Unit =
        { _, _, _ -> },
    onPreviewImageError: (storyId: Int, pageUrl: String, imageUrl: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val typography = rememberContentTypography(
        settings.font,
        settings.storyTextSize,
        settings.commentTextSize,
    )
    val contentVersion = controller.contentVersion
    val upvoted = remember(contentVersion, story.id) { controller.isUpvoted(story.id) }
    val bookmarked = remember(contentVersion, story.id) { controller.isBookmarked(story.id) }
    val favorited = remember(contentVersion, story.id) { controller.isFavorited(story.id) }
    val voteLoading = controller.storyPreviewVoteLoadingId == story.id
    val favoriteLoading = controller.storyPreviewFavoriteLoadingId == story.id
    val imageUrl = summaryState.result?.imageUrl?.takeIf(String::isNotBlank)
        ?: previewResource?.imageUrl?.takeIf(String::isNotBlank)
        ?: story.presentation.previewImage.url?.takeIf(String::isNotBlank)
    var imageLoadFailed by remember(story.id, imageUrl, previewResource?.imageLoadFailed) {
        mutableStateOf(
            imageUrl != null &&
                previewResource?.imageLoadFailed == true &&
                previewResource.imageUrl == imageUrl,
        )
    }
    val displayedImageUrl = imageUrl?.takeIf { !imageLoadFailed }
    val description = if (story.isLink) {
        summaryState.result?.description?.takeIf(String::isNotBlank)
    } else {
        remember(story.id, story.text) {
            story.text
                ?.takeIf(String::isNotBlank)
                ?.let(htmlToPlainText)
                ?.let { HtmlTextUtils.normalizeAndTruncatePlainText(it, TextStorySummaryMaxChars) }
                .orEmpty()
        }
    }
    val title = summaryState.result?.title?.takeIf(String::isNotBlank)
        ?: story.presentation.pdfTitle?.takeIf(String::isNotBlank)
        ?: story.presentation.videoTitle?.takeIf(String::isNotBlank)
        ?: story.title.orEmpty()
    val domain = if (story.isLink) {
        story.url?.let { DomainNamePolicy.fromUrl(it) ?: it }
    } else {
        story.author
    }
    val meta = buildString {
        append(story.score)
        append(if (story.score == 1) " point" else " points")
        if (!domain.isNullOrBlank()) append(" • ").append(domain)
        append(" • ").append(ItemTimeFormatter.formatNow(story.createdAtEpochSeconds))
    }

    Surface(
        modifier = modifier.animateContentSize(tween(220, easing = FastOutSlowInEasing)),
        shape = SharedStoryPreviewShape,
        color = cardColor,
        shadowElevation = 0.dp,
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
                    targetState = displayedImageUrl to (summaryState.loading && story.isLink),
                    transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(150))) },
                    label = "story preview image content",
                ) { (currentImageUrl, imageLoading) ->
                    when {
                        currentImageUrl != null -> SharedNetworkImage(
                            url = currentImageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2.15f),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            crossfade = true,
                            onSuccess = {
                                onPreviewImageLoaded(
                                    story.id,
                                    story.url.orEmpty(),
                                    currentImageUrl,
                                )
                            },
                            onError = {
                                imageLoadFailed = true
                                onPreviewImageError(
                                    story.id,
                                    story.url.orEmpty(),
                                    currentImageUrl,
                                )
                            },
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
                        transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(150))) },
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
                                placeholder = tintedPainterResource(
                                    Res.drawable.ic_public,
                                    HarmonicTheme.colors.drawable,
                                ),
                                error = tintedPainterResource(
                                    Res.drawable.ic_public,
                                    HarmonicTheme.colors.drawable,
                                ),
                                fallback = tintedPainterResource(
                                    Res.drawable.ic_public,
                                    HarmonicTheme.colors.drawable,
                                ),
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
                    transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(150))) },
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
                                style = textStyle,
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
                            icon = if (upvoted) Res.drawable.ic_thumb_up_filled else Res.drawable.ic_thumb_up,
                            description = if (upvoted) "Remove upvote" else "Upvote",
                            loading = voteLoading,
                        ) {
                            controller.onStoryPreviewAction(
                                page,
                                StoryPreviewActionKind.Vote,
                            )
                        }
                    }
                    StoryPreviewActionIcon(
                        icon = if (story.clicked) Res.drawable.ic_visibility_off else Res.drawable.ic_visibility,
                        description = if (story.clicked) "Mark as unread" else "Mark as read",
                    ) {
                        controller.onStoryPreviewAction(
                            page,
                            StoryPreviewActionKind.Read,
                        )
                    }
                    if (bookmarksEnabled) {
                        StoryPreviewActionIcon(
                            icon = if (bookmarked) Res.drawable.ic_bookmark_filled else Res.drawable.ic_bookmark,
                            description = if (bookmarked) "Remove bookmark" else "Bookmark",
                        ) {
                            controller.onStoryPreviewAction(
                                page,
                                StoryPreviewActionKind.Bookmark,
                            )
                        }
                    }
                    if (hasAccount) {
                        StoryPreviewActionIcon(
                            icon = if (favorited) Res.drawable.ic_star_filled else Res.drawable.ic_star,
                            description = if (favorited) "Remove favorite" else "Favorite",
                            loading = favoriteLoading,
                        ) {
                            controller.onStoryPreviewAction(
                                page,
                                StoryPreviewActionKind.Favorite,
                            )
                        }
                    }
                    ElevatedButton(
                        onClick = { controller.onStoryPreviewNavigate(page, false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .padding(start = 4.dp)
                            .semantics {
                                contentDescription = "Comments (${story.descendantCount})"
                            },
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = HarmonicTheme.colors.storyNormal,
                        ),
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_comment),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (hasAccount) story.descendantCount.toString() else "Comments",
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
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
    icon: DrawableResource,
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
            targetState = StoryPreviewActionVisual(icon, description, loading),
            transitionSpec = { fadeIn(tween(150)).togetherWith(fadeOut(tween(150))) },
            label = "story preview action",
        ) { visual ->
            if (visual.loading) {
                HarmonicLoadingIndicator(Modifier.size(28.dp))
            } else {
                IconButton(onClick = onClick) {
                    Icon(
                        painterResource(visual.icon),
                        contentDescription = visual.description,
                        tint = HarmonicTheme.colors.drawable,
                    )
                }
            }
        }
    }
}

private data class StoryPreviewActionVisual(
    val icon: DrawableResource,
    val description: String,
    val loading: Boolean,
)

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
