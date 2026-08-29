package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
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
import com.simon.harmonichackernews.ui.content.NetworkImage
import com.simon.harmonichackernews.ui.content.StoryTitleText
import com.simon.harmonichackernews.ui.content.storyTitlePresentation
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.DomainNamePolicy
import com.simon.harmonichackernews.utils.HtmlTextUtils
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private const val TextStorySummaryMaxChars = 600

internal fun shouldReserveStoryPreviewImage(
    canLoadLinkPreview: Boolean,
    displayedImageUrl: String?,
    imageLoadFailed: Boolean,
    previewResource: StoryPreviewResourceState?,
    imageKnownAbsent: Boolean = false,
): Boolean = canLoadLinkPreview && displayedImageUrl == null && !imageLoadFailed &&
    !imageKnownAbsent && previewResource?.contentLoadFailed != true &&
    previewResource?.imageUrlResolved != true

data class StoryPreviewSummaryState(
    val result: LinkSummary? = null,
)

@Composable
fun StoryPreviewCard(
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
    val read = controller.isStoryPreviewRead(story.id, story.clicked)
    val voteLoading = controller.isStoryPreviewVoteLoading(story.id)
    val favoriteLoading = controller.isStoryPreviewFavoriteLoading(story.id)
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
            ?: story.presentation.linkSummaryDescription
                ?.takeIf { story.presentation.linkSummaryLoaded && it.isNotBlank() }
    } else {
        remember(story.id, story.text) {
            story.text
                ?.takeIf(String::isNotBlank)
                ?.let(htmlToPlainText)
                ?.let { HtmlTextUtils.normalizeAndTruncatePlainText(it, TextStorySummaryMaxChars) }
                .orEmpty()
        }
    }
    val canLoadLinkPreview = story.isLink && !story.url.isNullOrBlank()
    val contentLoadFailed = previewResource?.contentLoadFailed == true
    val imagePending = shouldReserveStoryPreviewImage(
        canLoadLinkPreview = canLoadLinkPreview,
        displayedImageUrl = displayedImageUrl,
        imageLoadFailed = imageLoadFailed,
        previewResource = previewResource,
        imageKnownAbsent = controller.isStoryPreviewImageKnownAbsent(story.id),
    )
    val summaryResolved = previewResource?.summaryResolved == true ||
        story.presentation.linkSummaryLoaded
    val descriptionPending = canLoadLinkPreview && description.isNullOrBlank() &&
        !contentLoadFailed && !summaryResolved
    val storyTitle = remember(
        story.title,
        story.presentation.pdfTitle,
        story.presentation.videoTitle,
    ) {
        storyTitlePresentation(
            title = story.title,
            pdfTitle = story.presentation.pdfTitle,
            videoTitle = story.presentation.videoTitle,
        )
    }
    val title = if (storyTitle.badge != null) {
        storyTitle.text
    } else {
        summaryState.result?.title?.takeIf(String::isNotBlank) ?: storyTitle.text
    }
    val domain = remember(story.isLink, story.url, story.author) {
        if (story.isLink) {
            story.url?.let { DomainNamePolicy.fromUrl(it) ?: it }
        } else {
            story.author
        }
    }
    val meta = remember(story.score, story.createdAtEpochSeconds, domain) {
        buildString {
            append(story.score)
            append(if (story.score == 1) " point" else " points")
            if (!domain.isNullOrBlank()) append(" • ").append(domain)
            append(" • ").append(ItemTimeFormatter.formatNow(story.createdAtEpochSeconds))
        }
    }

    Box(modifier = modifier) {
        StoryPreviewContainerBackground(
            color = cardColor,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { controller.onStoryPreviewNavigate(page, story.isLink) },
            ) {
                AnimatedVisibility(
                    visible = displayedImageUrl != null || imagePending,
                    modifier = Modifier.clipToBounds(),
                    enter = expandVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top,
                    ) + fadeIn(tween(160)),
                    exit = shrinkVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(tween(90)),
                    label = "story preview image area",
                ) {
                    val currentImageUrl = displayedImageUrl
                    if (currentImageUrl != null) {
                        StoryPreviewSharedElement(
                            element = StoryPreviewSharedElement.Image,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2.15f),
                        ) {
                            StoryPreviewNetworkImage(
                                storyId = story.id,
                                pageUrl = story.url.orEmpty(),
                                imageUrl = currentImageUrl,
                                initiallyLoaded = previewResource?.imageLoaded == true &&
                                    previewResource.imageUrl == currentImageUrl,
                                onLoaded = onPreviewImageLoaded,
                                onError = {
                                    imageLoadFailed = true
                                    onPreviewImageError(story.id, story.url.orEmpty(), currentImageUrl)
                                },
                            )
                        }
                    } else {
                        // A speculative placeholder affects dialog layout, but is not a shared
                        // element. If parsing confirms a miss, there is no stale image layer for
                        // the container transform to keep drawing while this area collapses.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2.15f)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 28.dp,
                                        topEnd = 28.dp,
                                    ),
                                ),
                        ) {
                            StoryPreviewShimmer(Modifier.fillMaxSize())
                        }
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
                        StoryPreviewSharedElement(StoryPreviewSharedElement.Title) {
                            StoryTitleText(
                                text = currentTitle,
                                badge = storyTitle.badge,
                                color = HarmonicTheme.colors.storyNormal,
                                fontFamily = typography.family,
                                fontWeight = FontWeight.Bold,
                                fontSize = (typography.storyTitleSize + 2.5f).sp,
                                lineHeight = (typography.storyTitleSize + 5.5f).sp,
                            )
                        }
                    }
                    StoryPreviewSharedElement(
                        element = StoryPreviewSharedElement.Meta,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (faviconUrl != null) {
                                AsyncImage(
                                    model = faviconUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
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
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = meta,
                                color = HarmonicTheme.colors.storyDisabled,
                                fontFamily = typography.storyMetaFamily,
                                fontSize = typography.storyMetaSize.sp,
                                lineHeight = (typography.storyMetaSize + 3f).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = storyPreviewMetaTextStyle,
                            )
                        }
                    }
                }
            }

            Column(
                Modifier.padding(start = 20.dp, top = 7.dp, end = 20.dp, bottom = 18.dp),
            ) {
                StoryPreviewSharedElement(StoryPreviewSharedElement.Summary) {
                    StoryPreviewDescriptionVisibility(
                        visible = !description.isNullOrBlank() || descriptionPending,
                    ) {
                        AnimatedContent(
                            targetState = description,
                            transitionSpec = {
                                fadeIn(tween(220)).togetherWith(fadeOut(tween(150)))
                            },
                            label = "story preview description content",
                        ) { currentDescription ->
                            if (!currentDescription.isNullOrBlank()) {
                                SelectionContainer {
                                    Text(
                                        text = currentDescription,
                                        color = HarmonicTheme.colors.storyNormal,
                                        fontFamily = typography.family,
                                        fontSize = typography.commentTextSize.sp,
                                        lineHeight = (typography.commentTextSize + 2f).sp,
                                        style = textStyle,
                                    )
                                }
                            } else {
                                DescriptionShimmer()
                            }
                        }
                    }
                }
                StoryPreviewSharedElement(
                    element = StoryPreviewSharedElement.Supplementary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth()) {
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
                                    icon = if (upvoted) {
                                        Res.drawable.ic_thumb_up_filled
                                    } else {
                                        Res.drawable.ic_thumb_up
                                    },
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
                                icon = if (read) {
                                    Res.drawable.ic_visibility_off
                                } else {
                                    Res.drawable.ic_visibility
                                },
                                description = if (read) {
                                    "Mark as unread"
                                } else {
                                    "Mark as read"
                                },
                            ) {
                                controller.onStoryPreviewAction(
                                    page,
                                    StoryPreviewActionKind.Read,
                                )
                            }
                            if (bookmarksEnabled) {
                                StoryPreviewActionIcon(
                                    icon = if (bookmarked) {
                                        Res.drawable.ic_bookmark_filled
                                    } else {
                                        Res.drawable.ic_bookmark
                                    },
                                    description = if (bookmarked) {
                                        "Remove bookmark"
                                    } else {
                                        "Bookmark"
                                    },
                                ) {
                                    controller.onStoryPreviewAction(
                                        page,
                                        StoryPreviewActionKind.Bookmark,
                                    )
                                }
                            }
                            if (hasAccount) {
                                StoryPreviewActionIcon(
                                    icon = if (favorited) {
                                        Res.drawable.ic_star_filled
                                    } else {
                                        Res.drawable.ic_star
                                    },
                                    description = if (favorited) {
                                        "Remove favorite"
                                    } else {
                                        "Favorite"
                                    },
                                    loading = favoriteLoading,
                                ) {
                                    controller.onStoryPreviewAction(
                                        page,
                                        StoryPreviewActionKind.Favorite,
                                    )
                                }
                            }
                            StoryPreviewTooltip(
                                description = "Comments",
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 4.dp),
                            ) {
                                ElevatedButton(
                                    onClick = {
                                        controller.onStoryPreviewNavigate(page, false)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .trackStoryPreviewCommentsButton()
                                        .semantics {
                                            contentDescription =
                                                "Comments (${story.descendantCount})"
                                        },
                                    colors = ButtonDefaults.elevatedButtonColors(
                                        containerColor =
                                            MaterialTheme.colorScheme.surfaceContainerLow,
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
                                        if (hasAccount) {
                                            story.descendantCount.toString()
                                        } else {
                                            "Comments"
                                        },
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
        }
    }
}

@Composable
private fun StoryPreviewDescriptionVisibility(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.clipToBounds(),
        enter = expandVertically(
            animationSpec = tween(180, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top,
        ) + fadeIn(tween(160)),
        exit = shrinkVertically(
            animationSpec = tween(180, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top,
        ) + fadeOut(tween(90)),
        label = "story preview description area",
    ) {
        content()
    }
}

@Composable
private fun StoryPreviewNetworkImage(
    storyId: Int,
    pageUrl: String,
    imageUrl: String,
    initiallyLoaded: Boolean,
    onLoaded: (storyId: Int, pageUrl: String, imageUrl: String) -> Unit,
    onError: () -> Unit,
) {
    var loaded by remember(imageUrl) { mutableStateOf(initiallyLoaded) }
    val imageAlpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(220),
        label = "story preview loaded image alpha",
    )
    Box(Modifier.fillMaxSize()) {
        if (imageAlpha < 1f) {
            StoryPreviewShimmer(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - imageAlpha },
            )
        }
        NetworkImage(
            url = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = imageAlpha },
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            // The shared-element transform supplies its own source/destination blend. This local
            // alpha starts only after Coil succeeds, so cold late arrivals fade over the shimmer
            // without making the opening transform's captured destination translucent.
            crossfade = false,
            onSuccess = {
                loaded = true
                onLoaded(storyId, pageUrl, imageUrl)
            },
            onError = onError,
        )
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
        StoryPreviewTooltip(description) {
            AnimatedContent(
                targetState = StoryPreviewActionVisual(icon, description, loading),
                transitionSpec = {
                    (
                        fadeIn(
                            tween(
                                durationMillis = StoryPreviewActionIconSwapInDurationMillis,
                                delayMillis = StoryPreviewActionIconSwapOutDurationMillis,
                            ),
                        ) + scaleIn(
                            animationSpec = tween(
                                durationMillis = StoryPreviewActionIconSwapInDurationMillis,
                                delayMillis = StoryPreviewActionIconSwapOutDurationMillis,
                            ),
                            initialScale = StoryPreviewActionIconSwapMinScale,
                        )
                    ).togetherWith(
                        fadeOut(tween(StoryPreviewActionIconSwapOutDurationMillis)) + scaleOut(
                            animationSpec = tween(StoryPreviewActionIconSwapOutDurationMillis),
                            targetScale = StoryPreviewActionIconSwapMinScale,
                        ),
                    )
                },
                contentAlignment = Alignment.Center,
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
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun StoryPreviewTooltip(
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberTooltipState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.isVisible) {
        if (state.isVisible) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = { PlainTooltip { Text(description) } },
        state = state,
        modifier = modifier,
        content = content,
    )
}

private const val StoryPreviewActionIconSwapOutDurationMillis = 90
private const val StoryPreviewActionIconSwapInDurationMillis = 150
private const val StoryPreviewActionIconSwapMinScale = 0.72f

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
                .clip(RoundedCornerShape(8.dp)),
        )
        StoryPreviewShimmer(
            Modifier
                .fillMaxWidth(0.58f)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

private val storyPreviewMetaTextStyle = TextStyle(
    textMotion = TextMotion.Static,
)
