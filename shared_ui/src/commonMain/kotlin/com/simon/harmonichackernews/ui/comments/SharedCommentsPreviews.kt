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
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
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
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.VisibleComment
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
import com.simon.harmonichackernews.ui.common.SharedLazyContentList
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import com.simon.harmonichackernews.utils.ReferenceLinkRowUtils
import com.simon.harmonichackernews.utils.AgePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.simon.harmonichackernews.network.FaviconUrlBuilder

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
fun HeaderLinkInfo(story: Story, settings: CommentDisplaySettings) {
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
                    .size(17.dp),
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
    story: Story,
    settings: CommentDisplaySettings,
    suppressedReferenceUrl: String?,
    onReferenceLongClick: (
        CollectedReferenceLinks.ReferenceLink,
        androidx.compose.ui.geometry.Rect,
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
    ) -> Unit,
) {
    val platform = LocalCommentsPreviewPlatform.current
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(
        preferredFont = settings.font,
        commentTextSize = settings.preferredTextSize,
    )
    var bounds by remember(link.url) { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = if (suppressed) 0f else 1f)
            .padding(top = 4.dp)
            .defaultMinSize(minHeight = 38.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, colors.commentDivider, RoundedCornerShape(6.dp))
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .combinedClickable(
                onClick = { platform.openLink(link.url) },
                onLongClick = { onLongClick(link, bounds) },
            )
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
                .size(17.dp),
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
            ReferenceLinkRowUtils.getReferenceLinkLabel(link),
            modifier = Modifier.weight(1f),
            color = colors.storyNormal,
            fontFamily = typography.family,
            fontSize = typography.referenceLabelSize.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LinkPreviewContent(
    story: Story,
    contentVersion: Int,
    settings: CommentDisplaySettings,
) {
    val previewType = remember(story, contentVersion) {
        when {
            story.repoInfo != null -> "github"
            story.gitLabInfo != null -> "gitlab"
            story.stackExchangeInfo != null -> "stackexchange"
            story.arxivInfo != null -> "arxiv"
            story.wikiInfo != null -> "wikipedia"
            story.nitterInfo != null -> "nitter"
            story.linkPreviewLoading -> "loading"
            else -> "none"
        }
    }
    AnimatedVisibility(
        visible = previewType != "none",
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
                "github" -> GitHubPreview(story)
                "gitlab" -> GitLabPreview(story)
                "stackexchange" -> StackExchangePreview(story)
                "arxiv" -> ArxivPreview(story, settings)
                "wikipedia" -> WikipediaPreview(story)
                "nitter" -> NitterPreview(story)
                else -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(44.dp))
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(text: String) {
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
private fun GitHubPreview(story: Story) {
    val platform = LocalCommentsPreviewPlatform.current
    val info = story.repoInfo ?: return
    Column {
        PreviewHeader("${info.owner} / ${info.name}")
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
private fun GitLabPreview(story: Story) {
    val platform = LocalCommentsPreviewPlatform.current
    val info = story.gitLabInfo ?: return
    Column {
        PreviewHeader("${info.namespace} / ${info.name}")
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
private fun StackExchangePreview(story: Story) {
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
private fun ArxivPreview(story: Story, settings: CommentDisplaySettings) {
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
        Button(
            onClick = { platform.downloadPdf(info.pDFURL) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(56.dp),
        ) {
            Icon(painterResource(Res.drawable.ic_file_download), contentDescription = null)
            Text(
                "Download PDF",
                modifier = Modifier.padding(start = 8.dp),
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun WikipediaPreview(story: Story) {
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
private fun NitterPreview(story: Story) {
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
