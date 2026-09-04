package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.simon.harmonichackernews.network.LinkSummary
import com.simon.harmonichackernews.network.LinkSummaryParser
import com.simon.harmonichackernews.ui.common.TransformOverlay
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.DomainNamePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.roundToInt

private const val ReferenceContentDurationMillis = 240
private const val ReferenceContentFadeOutDurationMillis = 80
private const val ReferenceContentFadeInDurationMillis =
    ReferenceContentDurationMillis - ReferenceContentFadeOutDurationMillis
private const val ReferenceImageDurationMillis = 360
private const val ReferenceImageMetadataFadeOutDurationMillis = 100
private const val ReferenceImageMetadataFadeInDurationMillis = 140
private const val ReferenceImageCollapsedSizeDp = 104
private const val ReferenceImageStandardMarginDp = 20
private const val ReferenceImageCollapsedGapDp = 16
private const val ReferenceImageExpandedGapDp = 18

@Composable
fun CommentLinkPreviewOverlay(
    controller: CommentsComposeController,
    tablet: Boolean,
    referenceContent: @Composable (CommentLinkPreviewOverlayState.Reference) -> Unit,
    imageContent: @Composable (CommentLinkPreviewOverlayState.Image) -> Unit,
    onScrimAlphaChanged: (Float) -> Unit = {},
) {
    val state = controller.linkPreviewOverlay ?: return
    val predictiveProgressAnimation = remember(state) { Animatable(0f) }
    val settleRequest = controller.linkPreviewPredictiveBackSettleRequest

    LaunchedEffect(controller.linkPreviewPredictiveBackProgress, settleRequest) {
        if (settleRequest == null) {
            predictiveProgressAnimation.snapTo(
                controller.linkPreviewPredictiveBackProgress.coerceIn(0f, 1f),
            )
        }
    }
    LaunchedEffect(settleRequest?.serial) {
        val request = settleRequest ?: return@LaunchedEffect
        predictiveProgressAnimation.animateTo(
            request.target,
            tween(180, easing = FastOutSlowInEasing),
        )
        controller.finishLinkPreviewPredictiveBackSettle(request)
    }

    val imageOnly = state is CommentLinkPreviewOverlayState.Image
    val referenceRowSource =
        (state as? CommentLinkPreviewOverlayState.Reference)?.sourceIsReferenceRow == true
    val sharedSourceLayer = when (state) {
        is CommentLinkPreviewOverlayState.Reference ->
            state.sourceContentLayer.takeIf { referenceRowSource }
        is CommentLinkPreviewOverlayState.Image -> state.sourceContentLayer
    }
    val previewContainerColor = if (imageOnly) {
        Color.Transparent
    } else {
        HarmonicTheme.colors.surfaceContainerHigh
    }
    TransformOverlay(
        contentKey = state,
        sourceBounds = state.sourceBounds,
        dismissRequestVersion = controller.linkPreviewDismissRequest,
        predictiveBackProgress = predictiveProgressAnimation.value,
        predictiveBackEdge = controller.linkPreviewPredictiveBackEdge,
        maxWidth = if (tablet) {
            HarmonicDimens.compose_comment_action_tablet_max_width
        } else {
            HarmonicDimens.compose_comment_action_max_width
        },
        horizontalPadding = HarmonicDimens.compose_comment_action_screen_padding_horizontal,
        verticalPadding = HarmonicDimens.compose_comment_action_screen_padding_vertical,
        targetCornerRadius = 28.dp,
        sourceCornerRadius = when {
            imageOnly -> 8.dp
            referenceRowSource -> 6.dp
            else -> 0.dp
        },
        containerColor = previewContainerColor,
        sourceContainerColor = if (referenceRowSource) {
            state.sourceContainerColor
                ?: HarmonicTheme.colors.background
        } else {
            previewContainerColor.copy(alpha = 0f)
        },
        sourceBorderColor = HarmonicTheme.colors.commentDivider,
        sourceBorderWidth = if (referenceRowSource) 1.dp else 0.dp,
        sourceAnchorSize = if (imageOnly || referenceRowSource) null else 8.dp,
        shadowElevation = if (imageOnly) 0.dp else 8.dp,
        scaleContentWithContainer = imageOnly,
        preserveContentAspectRatio = imageOnly,
        keepContentOpaqueWithSource = imageOnly || referenceRowSource,
        consumeAllGestures = false,
        verticalSwipeDismissEnabled = imageOnly,
        sourceContentLayer = sharedSourceLayer,
        sharedHazeSourceZIndex = CommentsModalHazeSourceZIndex,
        onSourceReadyToCover = if (sharedSourceLayer != null) {
            controller::coverLinkPreviewSource
        } else {
            null
        },
        onDismissRequest = controller::requestDismissLinkPreview,
        onDismissAnimationFinished = controller::completeLinkPreviewDismiss,
        onScrimAlphaChanged = onScrimAlphaChanged,
    ) {
        when (state) {
            is CommentLinkPreviewOverlayState.Reference -> referenceContent(state)
            is CommentLinkPreviewOverlayState.Image -> imageContent(state)
        }
    }
}

data class ReferenceSummaryUiState(
    val loading: Boolean = false,
    val showFallback: Boolean = false,
    val result: LinkSummary? = null,
    val error: String? = null,
    val retrying: Boolean = false,
)

private data class ReferenceSummaryContentState(
    val loading: Boolean,
    val showFallback: Boolean,
    val result: LinkSummary?,
    val error: String?,
)

@Composable
fun ReferenceCardContent(
    url: String,
    fallbackTitle: String,
    summary: ReferenceSummaryUiState,
    preferredFont: String,
    commentTextSize: Float,
    favicon: String?,
    offline: Boolean,
    textStyle: TextStyle,
    referenceImage: @Composable (
        imageUrl: String?,
        loading: Boolean,
        expanded: Boolean,
        shape: Shape,
        imageRatio: Float,
        onImageRatio: (Float) -> Unit,
        onClick: () -> Unit,
        modifier: Modifier,
    ) -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val typography = rememberContentTypography(
        preferredFont = preferredFont,
        commentTextSize = commentTextSize,
    )
    val requestedSummaryContent = ReferenceSummaryContentState(
        loading = summary.loading,
        showFallback = summary.showFallback,
        result = summary.result,
        error = summary.error,
    )
    var summaryContent by remember(url) { mutableStateOf(requestedSummaryContent) }
    val summaryContentAlpha = remember(url) { Animatable(1f) }
    LaunchedEffect(requestedSummaryContent) {
        if (requestedSummaryContent == summaryContent) return@LaunchedEffect
        summaryContentAlpha.animateTo(
            0f,
            tween(
                ReferenceContentFadeOutDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        )
        summaryContent = requestedSummaryContent
        summaryContentAlpha.animateTo(
            1f,
            tween(
                ReferenceContentFadeInDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    val result = summaryContent.result
    val title = firstNotBlank(result?.title, fallbackTitle, url)
    val description = result?.description?.takeIf(String::isNotBlank)
    val domain = if (
        result != null &&
        result.contentType == LinkSummaryParser.HACKER_NEWS_ITEM_CONTENT_TYPE &&
        result.siteName.isNotBlank()
    ) {
        result.siteName
    } else {
        DomainNamePolicy.fromUrl(url) ?: url
    }
    val imageUrl = result?.imageUrl?.takeIf(String::isNotBlank)
    val directImage = result?.contentType?.startsWith("image/", ignoreCase = true) == true
    var imageExpanded by remember(url, imageUrl, directImage) { mutableStateOf(directImage) }
    var imageBoundsAnimating by remember(url, imageUrl) { mutableStateOf(false) }
    val metadataAlpha = remember(url, imageUrl) { Animatable(1f) }
    var imageRatio by remember(url, imageUrl) { mutableFloatStateOf(1f) }
    val imageTopCornerRadius by animateDpAsState(
        targetValue = if (imageExpanded) 28.dp else 8.dp,
        animationSpec = tween(ReferenceImageDurationMillis, easing = FastOutSlowInEasing),
        label = "reference image top corners",
    )
    val imageBottomCornerRadius by animateDpAsState(
        targetValue = if (imageExpanded) 0.dp else 8.dp,
        animationSpec = tween(ReferenceImageDurationMillis, easing = FastOutSlowInEasing),
        label = "reference image bottom corners",
    )
    val imageShape = RoundedCornerShape(
        topStart = imageTopCornerRadius,
        topEnd = imageTopCornerRadius,
        bottomStart = imageBottomCornerRadius,
        bottomEnd = imageBottomCornerRadius,
    )
    val retryable = summaryContent.error?.let(::isRetryableReferenceError) == true
    val offlineMessage = stringResource(Res.string.link_summary_offline_message)
    val genericErrorMessage = stringResource(Res.string.link_summary_error_message)
    val errorMessage = summaryContent.error?.let {
        referenceErrorMessage(offline, it, offlineMessage, genericErrorMessage)
    }
    val showImage = (summaryContent.loading && !summaryContent.showFallback) || imageUrl != null
    val onImageClick = {
        if (imageUrl != null && !imageBoundsAnimating) {
            val expanded = !imageExpanded
            coroutineScope.launch {
                metadataAlpha.animateTo(
                    0f,
                    tween(
                        ReferenceImageMetadataFadeOutDurationMillis,
                        easing = FastOutSlowInEasing,
                    ),
                )
                imageBoundsAnimating = true
                imageExpanded = expanded
                delay(
                    (ReferenceImageDurationMillis - ReferenceImageMetadataFadeInDurationMillis)
                        .toLong(),
                )
                metadataAlpha.animateTo(
                    1f,
                    tween(
                        ReferenceImageMetadataFadeInDurationMillis,
                        easing = FastOutSlowInEasing,
                    ),
                )
                imageBoundsAnimating = false
            }
        }
    }

    LookaheadScope {
        val rootBoundsTransform = BoundsTransform { _, _ ->
            tween(
                durationMillis = if (imageBoundsAnimating) {
                    ReferenceImageDurationMillis
                } else {
                    ReferenceContentDurationMillis
                },
                easing = FastOutSlowInEasing,
            )
        }
        val headerBoundsTransform = BoundsTransform { _, _ ->
            tween(
                durationMillis = if (imageBoundsAnimating) {
                    ReferenceImageDurationMillis
                } else {
                    ReferenceContentDurationMillis
                },
                easing = FastOutSlowInEasing,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .animateBounds(
                    lookaheadScope = this@LookaheadScope,
                    boundsTransform = rootBoundsTransform,
                )
                .verticalScroll(rememberScrollState()),
        ) {
            ReferenceImageHeader(
                showImage = showImage,
                imageUrl = imageUrl,
                loading = summaryContent.loading,
                expanded = imageExpanded,
                imageShape = imageShape,
                imageRatio = imageRatio,
                metadataAlpha = metadataAlpha.value,
                summaryContentAlpha = summaryContentAlpha.value,
                domain = domain,
                favicon = favicon,
                title = title,
                metadataLoading = summaryContent.loading && !summaryContent.showFallback,
                fontFamily = typography.family,
                metaSize = typography.storyMetaSize,
                titleSize = typography.storyTitleSize + 0.5f,
                lookaheadScope = this@LookaheadScope,
                boundsTransform = headerBoundsTransform,
                referenceImage = referenceImage,
                onImageRatio = { imageRatio = it },
                onImageClick = onImageClick,
            )

            Column(
                modifier = Modifier
                    .animateBounds(
                        lookaheadScope = this@LookaheadScope,
                        boundsTransform = headerBoundsTransform,
                    )
                    .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 18.dp),
            ) {
                Box(Modifier.graphicsLayer { alpha = summaryContentAlpha.value }) {
                    when {
                        !description.isNullOrBlank() -> SelectionContainer {
                            Text(
                                text = description,
                                color = HarmonicTheme.colors.storyNormal,
                                fontFamily = typography.family,
                                fontSize = typography.commentTextSize.sp,
                                lineHeight = (typography.commentTextSize + 2f).sp,
                                style = textStyle,
                            )
                        }
                        summaryContent.loading && !summaryContent.showFallback ->
                            ReferenceDescriptionShimmer()
                        summaryContent.error != null -> ReferenceErrorContent(
                            offline = offline,
                            message = errorMessage.orEmpty(),
                            retryVisible = offline || retryable,
                            retrying = summary.retrying,
                            fontFamily = typography.family,
                            errorTextSize = typography.commentTextSize - 1f,
                            onRetry = onRetry,
                        )
                        else -> Spacer(Modifier.height(0.dp))
                    }
                }

                ElevatedButton(
                    onClick = onOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp)
                        .height(52.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = HarmonicTheme.colors.storyNormal,
                    ),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_link),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.link_summary_open_short),
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferenceImageHeader(
    showImage: Boolean,
    imageUrl: String?,
    loading: Boolean,
    expanded: Boolean,
    imageShape: Shape,
    imageRatio: Float,
    metadataAlpha: Float,
    summaryContentAlpha: Float,
    domain: String,
    favicon: String?,
    title: String,
    metadataLoading: Boolean,
    fontFamily: FontFamily,
    metaSize: Float,
    titleSize: Float,
    lookaheadScope: LookaheadScope,
    boundsTransform: BoundsTransform,
    referenceImage: @Composable (
        imageUrl: String?,
        loading: Boolean,
        expanded: Boolean,
        shape: Shape,
        imageRatio: Float,
        onImageRatio: (Float) -> Unit,
        onClick: () -> Unit,
        modifier: Modifier,
    ) -> Unit,
    onImageRatio: (Float) -> Unit,
    onImageClick: () -> Unit,
) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            if (showImage) {
                Box(
                    modifier = Modifier.animateBounds(
                        lookaheadScope = lookaheadScope,
                        boundsTransform = boundsTransform,
                    ),
                ) {
                    referenceImage(
                        imageUrl,
                        loading,
                        expanded,
                        imageShape,
                        imageRatio,
                        onImageRatio,
                        onImageClick,
                        Modifier.fillMaxSize(),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .animateBounds(
                        lookaheadScope = lookaheadScope,
                        boundsTransform = boundsTransform,
                    )
                    .graphicsLayer { alpha = metadataAlpha },
            ) {
                ReferenceMetadata(
                    domain = domain,
                    favicon = favicon,
                    title = title,
                    loading = metadataLoading,
                    contentAlpha = summaryContentAlpha,
                    fontFamily = fontFamily,
                    metaSize = metaSize,
                    titleSize = titleSize,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val standardMargin = ReferenceImageStandardMarginDp.dp.roundToPx()
        val imageMeasurable = measurables.firstOrNull().takeIf { showImage }
        val metadataMeasurable = measurables.last()
        val imageSize = ReferenceImageCollapsedSizeDp.dp.roundToPx()
        val imageHeight = if (expanded) {
            (width / imageRatio.coerceIn(0.45f, 3f)).roundToInt().coerceAtLeast(1)
        } else {
            imageSize
        }
        val imageWidth = if (expanded) width else imageSize
        val imagePlaceable = imageMeasurable?.measure(
            androidx.compose.ui.unit.Constraints.fixed(imageWidth, imageHeight),
        )
        val metadataWidth = when {
            !showImage || expanded -> (width - standardMargin * 2).coerceAtLeast(1)
            else -> (width - standardMargin * 2 - imageSize -
                ReferenceImageCollapsedGapDp.dp.roundToPx()).coerceAtLeast(1)
        }
        val metadataPlaceable = metadataMeasurable.measure(
            androidx.compose.ui.unit.Constraints(
                minWidth = metadataWidth,
                maxWidth = metadataWidth,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            ),
        )
        val imageTop = if (expanded) 0 else standardMargin
        val metadataTop = when {
            expanded -> imageHeight + ReferenceImageExpandedGapDp.dp.roundToPx()
            else -> standardMargin
        }
        val targetHeight = when {
            expanded -> metadataTop + metadataPlaceable.height
            showImage -> standardMargin + max(imageHeight, metadataPlaceable.height)
            else -> standardMargin + metadataPlaceable.height
        }
        layout(width, targetHeight.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            imagePlaceable?.placeRelative(
                x = if (expanded) 0 else standardMargin,
                y = imageTop,
            )
            metadataPlaceable.placeRelative(
                x = when {
                    expanded || !showImage -> standardMargin
                    else -> standardMargin + imageSize +
                        ReferenceImageCollapsedGapDp.dp.roundToPx()
                },
                y = metadataTop,
            )
        }
    }
}

@Composable
private fun ReferenceMetadata(
    domain: String,
    favicon: String?,
    title: String,
    loading: Boolean,
    contentAlpha: Float,
    fontFamily: FontFamily,
    metaSize: Float,
    titleSize: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = favicon,
                fallback = tintedPainterResource(Res.drawable.ic_public, HarmonicTheme.colors.drawable),
                error = tintedPainterResource(Res.drawable.ic_public, HarmonicTheme.colors.drawable),
                contentDescription = null,
                modifier = Modifier.size(17.dp).clip(RoundedCornerShape(3.dp)),
            )
            Text(
                text = domain,
                modifier = Modifier.padding(start = 6.dp),
                color = HarmonicTheme.colors.storyDisabled,
                fontFamily = fontFamily,
                fontSize = metaSize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.graphicsLayer { alpha = contentAlpha }) {
            if (loading) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LinkPreviewShimmer(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(6.dp)))
                    LinkPreviewShimmer(Modifier.width(140.dp).height(18.dp).clip(RoundedCornerShape(6.dp)))
                }
            } else {
                SelectionContainer {
                    Text(
                        text = title,
                        modifier = Modifier.padding(top = 5.dp),
                        color = HarmonicTheme.colors.storyNormal,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize.sp,
                        lineHeight = (titleSize + 3f).sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferenceDescriptionShimmer() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinkPreviewShimmer(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(6.dp)))
        LinkPreviewShimmer(Modifier.fillMaxWidth(0.65f).height(16.dp).clip(RoundedCornerShape(6.dp)))
    }
}

@Composable
private fun ReferenceErrorContent(
    offline: Boolean,
    message: String,
    retryVisible: Boolean,
    retrying: Boolean,
    fontFamily: FontFamily,
    errorTextSize: Float,
    onRetry: () -> Unit,
) {
    val retryingDescription = stringResource(Res.string.link_summary_retrying)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (offline) {
            Icon(
                painterResource(Res.drawable.ic_cloud_off),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = HarmonicTheme.colors.drawable,
            )
        }
        Text(
            text = stringResource(
                if (offline) Res.string.link_summary_offline_title else Res.string.link_summary_error_title,
            ),
            modifier = Modifier.padding(top = if (offline) 12.dp else 0.dp),
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (errorTextSize + 4f).sp,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 6.dp),
            color = HarmonicTheme.colors.storyDisabled,
            fontFamily = fontFamily,
            fontSize = errorTextSize.sp,
            lineHeight = (errorTextSize + 2f).sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (retryVisible) {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (retrying) {
                    HarmonicLoadingIndicator(
                        modifier = Modifier
                            .size(32.dp)
                            .semantics { contentDescription = retryingDescription },
                    )
                } else {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.height(48.dp)) {
                        Icon(
                            painterResource(Res.drawable.ic_refresh),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(Res.string.link_summary_retry),
                            fontFamily = ProductSansFontFamily,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LinkPreviewShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "link preview shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "link preview shimmer progress",
    )
    val base = HarmonicTheme.colors.storyDisabled.copy(alpha = 0.15f)
    val highlight = HarmonicTheme.colors.storyDisabled.copy(alpha = 0.22f)
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

private fun isRetryableReferenceError(message: String): Boolean =
    !message.startsWith("This link contains ")

private fun referenceErrorMessage(
    offline: Boolean,
    loaderMessage: String,
    offlineMessage: String,
    genericErrorMessage: String,
): String {
    if (offline) return offlineMessage
    return if (
        loaderMessage.startsWith("The page returned HTTP ") ||
        loaderMessage.startsWith("This link contains ") ||
        loaderMessage.startsWith("This link does not use ") ||
        loaderMessage.startsWith("The page is too large ")
    ) {
        loaderMessage
    } else {
        genericErrorMessage
    }
}

private fun firstNotBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }.orEmpty()
