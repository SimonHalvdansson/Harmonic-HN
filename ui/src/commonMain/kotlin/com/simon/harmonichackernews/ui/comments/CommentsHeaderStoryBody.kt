package com.simon.harmonichackernews.ui.comments



import com.simon.harmonichackernews.resources.*

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.ui.content.AnnotatedLinkGestureState
import com.simon.harmonichackernews.ui.content.detectAnnotatedLinkLongPress
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.content.ReferenceLinkRow
import com.simon.harmonichackernews.ui.content.rememberReferenceLinkLabel
import com.simon.harmonichackernews.ui.content.trimmedCommentText
import com.simon.harmonichackernews.ui.content.referenceBlockTopPadding
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import com.simon.harmonichackernews.network.FaviconUrlBuilder

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
        if (settings.showFavicons) {
            var faviconLoaded by remember(favicon) { mutableStateOf(false) }
            val fallback = painterResource(Res.drawable.ic_public)
            AsyncImage(
                model = favicon,
                placeholder = fallback,
                fallback = fallback,
                error = fallback,
                colorFilter = if (faviconLoaded) null else ColorFilter.tint(colors.iconTint),
                onLoading = { faviconLoaded = false },
                onSuccess = { faviconLoaded = true },
                onError = { faviconLoaded = false },
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(17.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }
        Text(
            text = domain.orEmpty(),
            color = colors.mutedText,
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
    val interleaved = references?.hasInterleavedLinks() == true
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 3.dp),
    ) {
        contentBlocks.forEachIndexed { index, block ->
            if (interleaved) Spacer(Modifier.height(referenceBlockTopPadding(contentBlocks, index)))
            val link = block.getLink()
            if (link == null) {
                HeaderStoryTextBlock(
                    bodyHtml = block.bodyHtml.orEmpty(),
                    trimParagraphEdges = interleaved,
                    fontFamily = typography.family,
                    fontSize = typography.commentTextSize,
                    onLinkLongClick = onLinkLongClick,
                )
            } else {
                HeaderReferenceRow(
                    link = link,
                    settings = settings,
                    topPadding = if (interleaved) 0.dp else 4.dp,
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
    trimParagraphEdges: Boolean,
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
    val annotated = remember(bodyHtml, linkStyles, linkListener, trimParagraphEdges) {
        platform.annotatedHtml(bodyHtml, linkStyles, linkListener).let {
            if (trimParagraphEdges) it.trimmedCommentText() else it
        }
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
            color = colors.contentPrimary,
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
    topPadding: Dp,
    suppressed: Boolean,
    onLongClick: (
        CollectedReferenceLinks.ReferenceLink,
        androidx.compose.ui.geometry.Rect,
        GraphicsLayer?,
    ) -> Unit,
) {
    val platform = LocalCommentsPreviewPlatform.current
    val typography = rememberContentTypography(
        preferredFont = settings.font,
        commentTextSize = settings.preferredTextSize,
    )
    ReferenceLinkRow(
        topPadding = topPadding,
        marker = if (link.hasNumber()) link.markerLabel.orEmpty() else "",
        label = rememberReferenceLinkLabel(link, settings.expandedReferenceLinks),
        typography = typography,
        expandedUrl = link.url.orEmpty().takeIf { settings.expandedReferenceLinks },
        faviconUrl = runCatching {
            FaviconUrlBuilder.faviconUrl(link.url.orEmpty(), settings.faviconProvider)
        }.getOrNull(),
        suppressed = suppressed,
        onClick = { platform.openLink(link.url) },
        onLongClick = { bounds, layer -> onLongClick(link, bounds, layer) },
    )
}
