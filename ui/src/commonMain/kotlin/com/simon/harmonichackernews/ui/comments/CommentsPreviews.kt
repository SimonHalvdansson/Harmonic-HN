package com.simon.harmonichackernews.ui.comments

import org.jetbrains.compose.resources.DrawableResource


import com.simon.harmonichackernews.resources.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.simon.harmonichackernews.ui.common.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.TextPreferences
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.presentation.CommentNavigationEdge
import com.simon.harmonichackernews.presentation.CommentNavigationRequest
import com.simon.harmonichackernews.presentation.CommentScrollRequest
import com.simon.harmonichackernews.presentation.CommentSheetRequest
import com.simon.harmonichackernews.presentation.CommentLinkPreview
import com.simon.harmonichackernews.presentation.CommentPredictiveBackSettleRequest
import com.simon.harmonichackernews.presentation.CommentsInteractionStore
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.ui.content.AnnotatedLinkGestureState
import com.simon.harmonichackernews.ui.content.CommentItem
import com.simon.harmonichackernews.ui.content.CommentItemStyle
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.content.detectAnnotatedLinkLongPress
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.content.rememberReferenceLinkLabel
import com.simon.harmonichackernews.ui.common.LazyContentList
import com.simon.harmonichackernews.ui.common.captureSharedTransformSourceContent
import com.simon.harmonichackernews.ui.common.onSecondaryClick
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import com.simon.harmonichackernews.utils.AgePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.data.LinkPreviewDetail
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.ui.settings.linkPreviewIcon

data class CommentsPreviewPlatform(
    val textStyle: TextStyle,
    val openLink: (String?) -> Unit,
    val downloadPdf: (String?) -> Unit,
    val openCustomTab: (String?) -> Unit,
    val plainText: (String) -> String,
    val annotatedHtml: (String, TextLinkStyles, LinkInteractionListener) -> AnnotatedString,
)

val LocalCommentsPreviewPlatform = staticCompositionLocalOf<CommentsPreviewPlatform> {
    error("CommentsPreviewPlatform was not provided")
}

@Composable
fun CommentsPreviewPlatformProvider(
    platform: CommentsPreviewPlatform,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalCommentsPreviewPlatform provides platform, content = content)
}

@Composable
fun HeaderLinkInfo(story: StoryListItemSnapshot, settings: CommentDisplaySettings) {
    if (!story.loaded || !story.isLink || story.isComment || story.url.isNullOrBlank()) return
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(preferredFont = settings.font)
    val domain = remember(story.url) {
        runCatching { story.getDisplayDomain(true) }.getOrDefault("")
    }
    val favicon = remember(story.url, settings.faviconProvider) {
        runCatching {
            FaviconUrlBuilder.faviconUrl(story.url.orEmpty(), settings.faviconProvider)
        }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (settings.showThumbnail) {
            AsyncImage(
                model = favicon,
                fallback = tintedPainterResource(Res.drawable.ic_public, HarmonicTheme.colors.drawable),
                error = tintedPainterResource(Res.drawable.ic_public, HarmonicTheme.colors.drawable),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(17.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }
        Text(
            text = "($domain)",
            color = colors.storyDisabled,
            fontFamily = typography.family,
            fontSize = typography.commentsHeaderMetaSize.sp,
            style = LocalCommentsPreviewPlatform.current.textStyle,
        )
    }
}

@Composable
fun HeaderStoryBody(
    story: StoryListItemSnapshot,
    settings: CommentDisplaySettings,
    suppressedReferenceUrl: String?,
    onReferenceLongClick: (
        CollectedReferenceLinks.ReferenceLink,
        androidx.compose.ui.geometry.Rect,
        GraphicsLayer?,
    ) -> Unit,
    onLinkLongClick: (String, String, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    if (story.text.isNullOrBlank()) return
    val typography = rememberContentTypography(
        preferredFont = settings.font,
        commentTextSize = settings.preferredTextSize,
    )
    val references = remember(story.text, settings.collectReferenceLinks) {
        if (settings.collectReferenceLinks) CollectedReferenceLinks.parse(story.text) else null
    }
    val contentBlocks = references
        ?.takeIf(CollectedReferenceLinks.Result::hasLinks)
        ?.contentBlocks
        ?: listOf(CollectedReferenceLinks.ContentBlock.text(story.text))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 3.dp),
    ) {
        contentBlocks.forEach { block ->
            val link = block.getLink()
            if (link == null) {
                HeaderStoryTextBlock(
                    bodyHtml = block.bodyHtml.orEmpty(),
                    fontFamily = typography.family,
                    fontSize = typography.commentTextSize,
                    onLinkLongClick = onLinkLongClick,
                )
            } else {
                HeaderReferenceRow(
                    link = link,
                    settings = settings,
                    suppressed = link.url == suppressedReferenceUrl,
                    onLongClick = onReferenceLongClick,
                )
            }
        }
    }
}

@Composable
private fun HeaderStoryTextBlock(
    bodyHtml: String,
    fontFamily: FontFamily,
    fontSize: Float,
    onLinkLongClick: (String, String, androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val platform = LocalCommentsPreviewPlatform.current
    val colors = HarmonicTheme.colors
    val hapticFeedback = LocalHapticFeedback.current
    val linkStyles = remember(colors.link) {
        TextLinkStyles(
            style = SpanStyle(colors.link, textDecoration = TextDecoration.Underline),
        )
    }
    val linkGestureState = remember(bodyHtml) { AnnotatedLinkGestureState() }
    val linkListener = remember(platform.openLink, linkGestureState) {
        LinkInteractionListener { link ->
            if (link is LinkAnnotation.Url &&
                !linkGestureState.consumeSuppressedLinkClick()
            ) {
                platform.openLink(link.url)
            }
        }
    }
    val annotated = remember(bodyHtml, linkStyles, linkListener) {
        platform.annotatedHtml(bodyHtml, linkStyles, linkListener)
    }
    var textLayout by remember(annotated) { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember(annotated) { mutableStateOf<LayoutCoordinates?>(null) }

    if (annotated.isNotEmpty()) {
        Text(
            text = annotated,
            modifier = Modifier
                .onGloballyPositioned { textCoordinates = it }
                .detectAnnotatedLinkLongPress(
                    text = annotated,
                    layoutResult = { textLayout },
                    coordinates = { textCoordinates },
                    linkGestureState = linkGestureState,
                    hapticFeedback = hapticFeedback,
                    onLongPress = onLinkLongClick,
                ),
            color = colors.storyNormal,
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            style = LocalCommentsPreviewPlatform.current.textStyle,
            onTextLayout = { textLayout = it },
        )
    }
}

@Composable
private fun HeaderReferenceRow(
    link: CollectedReferenceLinks.ReferenceLink,
    settings: CommentDisplaySettings,
    suppressed: Boolean,
    onLongClick: (
        CollectedReferenceLinks.ReferenceLink,
        androidx.compose.ui.geometry.Rect,
        GraphicsLayer?,
    ) -> Unit,
) {
    val platform = LocalCommentsPreviewPlatform.current
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(
        preferredFont = settings.font,
        commentTextSize = settings.preferredTextSize,
    )
    var bounds by remember(link.url) { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var sourceContentLayer by remember(link.url) { mutableStateOf<GraphicsLayer?>(null) }
    Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(alpha = if (suppressed) 0f else 1f)
                .defaultMinSize(minHeight = 38.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, colors.commentDivider, RoundedCornerShape(6.dp))
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = colors.storyDisabled.copy(alpha = 0.35f)),
                    onClick = { platform.openLink(link.url) },
                    onLongClick = { onLongClick(link, bounds, sourceContentLayer) },
                )
                .onSecondaryClick { onLongClick(link, bounds, sourceContentLayer) }
                .captureSharedTransformSourceContent { sourceContentLayer = it }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = runCatching {
                    FaviconUrlBuilder.faviconUrl(link.url.orEmpty(), settings.faviconProvider)
                }.getOrNull(),
                fallback = tintedPainterResource(Res.drawable.ic_public, HarmonicTheme.colors.drawable),
                error = tintedPainterResource(Res.drawable.ic_public, HarmonicTheme.colors.drawable),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(17.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            if (link.hasNumber()) {
                Text(
                    link.markerLabel.orEmpty(),
                    modifier = Modifier.padding(end = 8.dp),
                    color = colors.storyDisabled,
                    fontFamily = typography.family,
                    fontWeight = FontWeight.Bold,
                    fontSize = typography.referenceMarkerSize.sp,
                )
            }
            Text(
                rememberReferenceLinkLabel(link),
                modifier = Modifier.weight(1f),
                color = colors.storyNormal,
                fontFamily = typography.family,
                fontSize = typography.referenceLabelSize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun LinkPreviewContent(
    story: StoryListItemSnapshot,
    contentVersion: Int,
    settings: CommentDisplaySettings,
) {
    val previewType = remember(story, contentVersion) { story.loadedLinkPreviewType() }
    AnimatedVisibility(
        visible = previewType != null || story.linkPreviewLoading,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val colors = HarmonicTheme.colors
        AnimatedContent(
            targetState = previewType,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, colors.storyDisabled, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            transitionSpec = {
                (fadeIn(tween(160)) togetherWith fadeOut(tween(160))).using(
                    SizeTransform(clip = false) { _, _ -> tween(220) },
                )
            },
            label = "comments link preview",
        ) {
            when (it) {
                LinkPreviewType.GITHUB_REPOSITORY -> GitHubPreview(story)
                LinkPreviewType.GITLAB_PROJECT -> GitLabPreview(story)
                LinkPreviewType.HUGGING_FACE_MODEL -> HuggingFacePreview(story)
                LinkPreviewType.OPENROUTER_MODEL -> OpenRouterPreview(story)
                LinkPreviewType.STACK_EXCHANGE -> StackExchangePreview(story)
                LinkPreviewType.ARXIV -> ArxivPreview(story, settings)
                LinkPreviewType.WIKIPEDIA -> WikipediaPreview(story)
                LinkPreviewType.TWITTER_X -> NitterPreview(story)
                null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    HarmonicLoadingIndicator(Modifier.size(44.dp))
                }
                else -> RichLinkPreview(story)
            }
        }
    }
}

@Composable
private fun PreviewHeader(
    text: String,
    icon: DrawableResource? = null,
    logoUrl: String? = null,
    logoTint: Color? = null,
    tintIcon: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            val fallback = if (tintIcon) {
                tintedPainterResource(icon, HarmonicTheme.colors.drawable)
            } else {
                painterResource(icon)
            }
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    placeholder = fallback,
                    fallback = fallback,
                    error = fallback,
                    colorFilter = logoTint?.let(ColorFilter::tint),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(5.dp)),
                )
            } else {
                Icon(
                    painter = fallback,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified,
                )
            }
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text.uppercase(),
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            style = LocalCommentsPreviewPlatform.current.textStyle,
        )
    }
}

@Composable
private fun PreviewBody(
    text: String,
    bold: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    topPadding: Dp = 6.dp,
    bottomPadding: Dp = 4.dp,
    fontFamily: FontFamily = ProductSansFontFamily,
    fontSize: Float = 14f,
    lineHeight: Float = 17f,
) {
    if (text.isBlank()) return
    Text(
        text = text,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding),
        color = HarmonicTheme.colors.storyNormal,
        fontFamily = fontFamily,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = LocalCommentsPreviewPlatform.current.textStyle,
    )
}

@Composable
private fun PreviewInfoRow(
    icon: DrawableResource,
    text: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (text.isNullOrBlank()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 22.dp)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = null)
                } else {
                    Modifier
                },
            )
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(20.dp),
            tint = HarmonicTheme.colors.drawable,
        )
        Text(
            text,
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = LocalCommentsPreviewPlatform.current.textStyle,
        )
    }
}

@Composable
private fun PreviewInfoColumns(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(2f), content = left)
        Column(Modifier.weight(3f), content = right)
    }
}

@Composable
private fun GitHubPreview(story: StoryListItemSnapshot) {
    val platform = LocalCommentsPreviewPlatform.current
    val info = story.repoInfo ?: return
    Column {
        PreviewHeader(
            text = "${info.owner} / ${info.name}",
            icon = Res.drawable.ic_link_preview_github,
            logoUrl = info.avatarUrl,
        )
        PreviewBody(info.about.orEmpty())
        PreviewInfoColumns(
            left = {
                PreviewInfoRow(Res.drawable.ic_star, info.formatStars())
                PreviewInfoRow(Res.drawable.ic_visibility, info.formatWatching())
                PreviewInfoRow(Res.drawable.ic_fork_right, info.formatForks())
            },
            right = {
                PreviewInfoRow(Res.drawable.ic_link, info.shortenedUrl) {
                    platform.openLink(info.website)
                }
                PreviewInfoRow(Res.drawable.ic_attribution, info.license)
                PreviewInfoRow(Res.drawable.ic_library_books, info.language)
            },
        )
    }
}

@Composable
private fun GitLabPreview(story: StoryListItemSnapshot) {
    val platform = LocalCommentsPreviewPlatform.current
    val info = story.gitLabInfo ?: return
    Column {
        PreviewHeader(
            text = "${info.namespace} / ${info.name}",
            icon = Res.drawable.ic_link_preview_gitlab,
        )
        PreviewBody(info.description.orEmpty())
        PreviewInfoColumns(
            left = {
                PreviewInfoRow(Res.drawable.ic_star, info.formatStars())
                PreviewInfoRow(Res.drawable.ic_fork_right, info.formatForks())
            },
            right = {
                PreviewInfoRow(Res.drawable.ic_link, info.shortenedUrl) {
                    platform.openLink(info.website)
                }
                PreviewInfoRow(Res.drawable.ic_visibility, info.formatVisibility())
                PreviewInfoRow(Res.drawable.ic_library_books, info.language)
            },
        )
    }
}

@Composable
private fun HuggingFacePreview(story: StoryListItemSnapshot) {
    val info = story.huggingFaceInfo ?: return
    Column {
        PreviewHeader(
            text = "${info.author} / ${info.name}",
            icon = Res.drawable.ic_link_preview_hugging_face,
            logoUrl = info.logoUrl,
            tintIcon = false,
        )
        PreviewBody(info.formatCapability())
        PreviewInfoColumns(
            left = {
                PreviewInfoRow(Res.drawable.ic_favorite, info.formatLikes())
                PreviewInfoRow(Res.drawable.ic_file_download, info.formatDownloads())
                PreviewInfoRow(Res.drawable.ic_deployed_code, info.formatParameters())
            },
            right = {
                PreviewInfoRow(Res.drawable.ic_attribution, info.formatLicense())
                PreviewInfoRow(Res.drawable.ic_schedule, info.formatUpdated())
            },
        )
    }
}

@Composable
private fun OpenRouterPreview(story: StoryListItemSnapshot) {
    val info = story.openRouterInfo ?: return
    Column {
        PreviewHeader(
            text = "${info.provider} / ${info.name}",
            icon = Res.drawable.ic_link_preview_openrouter,
            logoUrl = info.providerIconUrl,
            logoTint = HarmonicTheme.colors.drawable,
        )
        PreviewBody(info.description.orEmpty(), maxLines = 12)
        PreviewInfoColumns(
            left = {
                PreviewInfoRow(Res.drawable.ic_file_download, info.formatPromptPrice())
                PreviewInfoRow(Res.drawable.ic_arrow_upward, info.formatCompletionPrice())
                PreviewInfoRow(Res.drawable.ic_stacks, info.formatContext())
            },
            right = {
                PreviewInfoRow(Res.drawable.ic_perm_media, info.formatModalities())
                PreviewInfoRow(Res.drawable.ic_open_in_new, info.formatMaxOutput())
                PreviewInfoRow(Res.drawable.ic_calendar_today, info.formatKnowledgeCutoff())
            },
        )
    }
}

@Composable
private fun StackExchangePreview(story: StoryListItemSnapshot) {
    val info = story.stackExchangeInfo ?: return
    Column {
        PreviewHeader("Stack Exchange:")
        PreviewBody(
            text = info.title.orEmpty(),
            bold = true,
            fontSize = 15f,
            lineHeight = 18f,
        )
        PreviewBody(info.formatBy().orEmpty(), maxLines = 20, topPadding = 0.dp)
        PreviewInfoColumns(
            left = {
                PreviewInfoRow(Res.drawable.ic_star, info.formatScore())
                PreviewInfoRow(Res.drawable.ic_comment, info.formatAnswerCount())
                PreviewInfoRow(Res.drawable.ic_visibility, info.formatViewCount())
            },
            right = {
                PreviewInfoRow(Res.drawable.ic_check, info.formatAnswerState())
                PreviewInfoRow(Res.drawable.ic_library_books, info.formatTags())
                PreviewInfoRow(Res.drawable.ic_person, info.formatAuthor())
            },
        )
    }
}

@Composable
private fun ArxivPreview(story: StoryListItemSnapshot, settings: CommentDisplaySettings) {
    val platform = LocalCommentsPreviewPlatform.current
    val info = story.arxivInfo ?: return
    val typography = rememberContentTypography(preferredFont = settings.font)
    val abstractTextSize = if (TextPreferences.sanitizeFont(settings.font) == "googlesansflexrounded") {
        14.5f
    } else {
        15f
    }
    Column {
        PreviewHeader("Abstract:")
        PreviewBody(
            text = info.arxivAbstract.orEmpty(),
            topPadding = 0.dp,
            bottomPadding = 0.dp,
            fontFamily = typography.family,
            fontSize = abstractTextSize,
            lineHeight = 18f,
        )
        PreviewInfoRow(Res.drawable.ic_calendar_today, runCatching(info::formatDate).getOrNull())
        PreviewInfoRow(
            when (info.authors.size) {
                1 -> Res.drawable.ic_person
                2 -> Res.drawable.ic_group
                else -> Res.drawable.ic_groups
            },
            runCatching(info::concatNames).getOrNull(),
        )
        PreviewInfoRow(Res.drawable.ic_library_books, runCatching(info::formatSubjects).getOrNull())
        val actionColors = ButtonDefaults.buttonColors(
            containerColor = HarmonicTheme.colors.secondaryContainer,
            contentColor = HarmonicTheme.colors.onSecondaryContainer,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            info.htmlUrl?.let { htmlUrl ->
                Button(
                    onClick = { platform.openLink(htmlUrl) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(
                        topStart = 24.dp,
                        bottomStart = 24.dp,
                        topEnd = 8.dp,
                        bottomEnd = 8.dp,
                    ),
                    colors = actionColors,
                ) {
                    ArxivActionLabel("HTML")
                }
            }
            Button(
                onClick = { platform.downloadPdf(info.pDFURL) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = if (info.htmlUrl != null) {
                    RoundedCornerShape(
                        topStart = 8.dp,
                        bottomStart = 8.dp,
                        topEnd = 24.dp,
                        bottomEnd = 24.dp,
                    )
                } else {
                    RoundedCornerShape(24.dp)
                },
                colors = actionColors,
            ) {
                ArxivActionLabel("PDF")
            }
        }
    }
}

@Composable
private fun ArxivActionLabel(text: String) {
    Text(
        text = text,
        fontFamily = ProductSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    )
}

@Composable
private fun WikipediaPreview(story: StoryListItemSnapshot) {
    val platform = LocalCommentsPreviewPlatform.current
    val info = story.wikiInfo ?: return
    Column {
        PreviewHeader("Wikipedia summary:")
        PreviewBody(
            platform.plainText(info.summary.orEmpty()),
            maxLines = 40,
            topPadding = 0.dp,
            bottomPadding = 3.dp,
            fontSize = 15f,
            lineHeight = 18f,
        )
    }
}

@Composable
private fun RichLinkPreview(story: StoryListItemSnapshot) {
    val info = story.linkPreviewInfo ?: return
    val details = remember(info.details) { splitRichPreviewDetails(info.details) }
    val isRelease = info.type == LinkPreviewType.GITHUB_RELEASE
    Column {
        PreviewHeader(
            text = info.title,
            icon = info.type.linkPreviewIcon(),
            logoUrl = info.imageUrl.takeUnless { isRelease },
        )
        PreviewBody(
            text = info.subtitle.orEmpty(),
            bold = true,
            topPadding = 5.dp,
            bottomPadding = 0.dp,
        )
        if (isRelease) {
            ReleaseMarkdownContent(
                markdown = info.description.orEmpty(),
                pageUrl = info.url,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else if (info.type.hasMarkdownDescription()) {
            SummaryMarkdownText(
                markdown = info.description.orEmpty(),
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                color = HarmonicTheme.colors.storyNormal,
                linkColor = HarmonicTheme.colors.link,
                fontFamily = ProductSansFontFamily,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            PreviewBody(
                text = info.description.orEmpty(),
                maxLines = 12,
                topPadding = 4.dp,
            )
        }
        PreviewInfoColumns(
            left = {
                details.left.forEach { detail ->
                    RichPreviewDetail(detail)
                }
            },
            right = {
                details.right.forEach { detail ->
                    RichPreviewDetail(detail)
                }
            },
        )
    }
}

private fun LinkPreviewType.hasMarkdownDescription(): Boolean = when (this) {
    LinkPreviewType.GITHUB_ISSUE,
    LinkPreviewType.GITHUB_PULL_REQUEST,
    LinkPreviewType.GITHUB_RELEASE,
    LinkPreviewType.GITHUB_DISCUSSION,
    -> true
    else -> false
}

@Composable
private fun RichPreviewDetail(detail: LinkPreviewDetail) {
    PreviewInfoRow(
        icon = when (detail.label.lowercase()) {
            "author", "authors" -> Res.drawable.ic_person
            "published", "updated", "started" -> Res.drawable.ic_calendar_today
            "likes", "favourites", "upvotes" -> Res.drawable.ic_favorite
            "comments", "replies" -> Res.drawable.ic_comment
            "downloads", "recent downloads", "installs (30d)" -> Res.drawable.ic_file_download
            "license" -> Res.drawable.ic_attribution
            "version", "revision" -> Res.drawable.ic_tag
            "state", "status", "impact", "access" -> Res.drawable.ic_info
            "files", "items", "dependencies" -> Res.drawable.ic_library_books
            else -> Res.drawable.ic_subject
        },
        text = detail.displayText ?: "${detail.label}: ${detail.value}",
    )
}

@Composable
private fun NitterPreview(story: StoryListItemSnapshot) {
    val platform = LocalCommentsPreviewPlatform.current
    val info = story.nitterInfo ?: return
    Column {
        PreviewHeader("${info.userName.orEmpty()} ${info.userTag.orEmpty()}")
        PreviewBody(
            platform.plainText(info.text.orEmpty()),
            topPadding = 0.dp,
            fontSize = 15f,
            lineHeight = 18f,
        )
        if (!info.imgSrc.isNullOrBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { platform.openCustomTab(story.url) },
                        onLongClick = null,
                    ),
            ) {
                AsyncImage(
                    model = info.imgSrc,
                    contentDescription = if (info.hasVideo) "Tweet video" else "Tweet image",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
                if (info.hasVideo) {
                    Text(
                        "VIDEO",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
                Row {
                    PreviewCompactInfo(
                        icon = Res.drawable.ic_calendar_today,
                        text = info.date,
                        iconWidth = 14.dp,
                    )
                    PreviewCompactInfo(
                        icon = Res.drawable.ic_reply,
                        text = info.replyCount,
                        startPadding = 1.dp,
                        endPadding = 7.dp,
                    )
                }
                Row {
                    PreviewCompactInfo(
                        icon = Res.drawable.ic_action_retweet,
                        text = info.reposts,
                        startPadding = 1.dp,
                    )
                    PreviewCompactInfo(
                        icon = Res.drawable.ic_thumb_up,
                        text = info.likes,
                        iconWidth = 12.dp,
                        endPadding = 4.dp,
                    )
                }
            }
            Button(
                onClick = { platform.openCustomTab(story.url) },
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                ),
            ) {
                Icon(painterResource(Res.drawable.ic_link_preview_x), contentDescription = null)
                Text(
                    "Open on X",
                    modifier = Modifier.padding(start = 8.dp),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PreviewCompactInfo(
    icon: DrawableResource,
    text: String?,
    iconWidth: Dp = 15.dp,
    startPadding: Dp = 2.dp,
    endPadding: Dp = 8.dp,
) {
    if (text.isNullOrBlank()) return
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(width = iconWidth, height = 16.dp),
        tint = HarmonicTheme.colors.drawable,
    )
    Text(
        text,
        modifier = Modifier.padding(start = startPadding, end = endPadding),
        color = HarmonicTheme.colors.storyNormal,
        fontFamily = ProductSansFontFamily,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        style = LocalCommentsPreviewPlatform.current.textStyle,
    )
}
