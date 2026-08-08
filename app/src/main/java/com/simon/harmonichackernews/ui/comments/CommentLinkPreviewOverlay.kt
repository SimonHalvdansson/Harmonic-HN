@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.simon.harmonichackernews.ui.comments

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.FaviconLoader
import com.simon.harmonichackernews.network.LinkSummaryLoader
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.networkHeader
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import kotlin.math.max

private const val TransformDurationMillis = 280
private const val PredictiveBackTranslationXDp = 56f
private const val PredictiveBackTranslationYDp = 18f
private const val ReferenceContentDurationMillis = 240
private const val ReferenceImageDurationMillis = 360
private const val PdfContentTypeError = "This link contains application/pdf, not a web page"

@Composable
internal fun CommentLinkPreviewOverlay(controller: CommentsComposeController) {
    val state = controller.linkPreviewOverlay ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val transformProgress = remember(state) { Animatable(0f) }
    val predictiveProgressAnimation = remember(state) { Animatable(0f) }
    var targetBounds by remember(state) { mutableStateOf<Rect?>(null) }
    val dismissRequest = controller.linkPreviewDismissRequest
    val settleRequest = controller.linkPreviewPredictiveBackSettleRequest

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
        controller.completeLinkPreviewDismiss()
    }
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

    val progress = transformProgress.value
    val predictiveProgress = predictiveProgressAnimation.value
    val predictiveEased = 1f - (1f - predictiveProgress) * (1f - predictiveProgress)
    val backDirection = if (controller.linkPreviewPredictiveBackEdge == 1) -1f else 1f
    val backTranslationX = with(density) { PredictiveBackTranslationXDp.dp.toPx() } *
        predictiveEased * backDirection
    val backTranslationY = with(density) { PredictiveBackTranslationYDp.dp.toPx() } * predictiveEased
    val source = state.sourceBounds
    val target = targetBounds
    val startScaleX = if (source != null && target != null && target.width > 0f) {
        (source.width / target.width).coerceIn(0.08f, 1.15f)
    } else 0.96f
    val startScaleY = if (source != null && target != null && target.height > 0f) {
        (source.height / target.height).coerceIn(0.08f, 1.15f)
    } else 0.96f
    val startTranslationX = if (source != null && target != null) {
        source.center.x - target.center.x
    } else 0f
    val startTranslationY = if (source != null && target != null) {
        source.center.y - target.center.y
    } else 0f
    val tablet = controller.displaySettings?.isTablet == true || Utils.isTablet(context.resources)

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
                onClick = controller::requestDismissLinkPreview,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val cardColor = when (state) {
                is CommentLinkPreviewOverlayState.Reference -> HarmonicTheme.colors.surfaceContainerHigh
                is CommentLinkPreviewOverlayState.Image -> Color.Transparent
            }
            Surface(
                modifier = Modifier
                    .widthIn(
                        max = dimensionResource(
                            if (tablet) {
                                R.dimen.compose_comment_action_tablet_max_width
                            } else {
                                R.dimen.compose_comment_action_max_width
                            },
                        ),
                    )
                    .fillMaxWidth()
                    .onGloballyPositioned { targetBounds = it.boundsInWindow() }
                    .graphicsLayer {
                        val sharedScaleX = startScaleX + (1f - startScaleX) * progress
                        val sharedScaleY = startScaleY + (1f - startScaleY) * progress
                        val backScale = 1f - 0.1f * predictiveEased
                        scaleX = sharedScaleX * backScale
                        scaleY = sharedScaleY * backScale
                        translationX = startTranslationX * (1f - progress) + backTranslationX
                        translationY = startTranslationY * (1f - progress) + backTranslationY
                        alpha = when {
                            source == null -> progress
                            state is CommentLinkPreviewOverlayState.Image -> 1f
                            else -> max(0.7f, progress)
                        }
                        transformOrigin = TransformOrigin(
                            if (backDirection > 0f) 0f else 1f,
                            0.5f,
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(28.dp),
                color = cardColor,
                shadowElevation = if (state is CommentLinkPreviewOverlayState.Image) 0.dp else 8.dp,
            ) {
                when (state) {
                    is CommentLinkPreviewOverlayState.Reference -> ReferencePreviewCard(
                        controller = controller,
                        state = state,
                    )
                    is CommentLinkPreviewOverlayState.Image -> ImageOnlyPreviewCard(state)
                }
            }
        }
    }
}

@Composable
private fun ReferencePreviewCard(
    controller: CommentsComposeController,
    state: CommentLinkPreviewOverlayState.Reference,
) {
    val context = LocalContext.current
    var attempt by remember(state) { mutableIntStateOf(0) }
    val initialCached = remember(state.originalUrl) {
        StoryPreviewImageLoader.getCachedLinkSummary(context, state.originalUrl)?.takeIf { cached ->
            !Utils.isHackerNewsItemUri(Uri.parse(state.originalUrl)) ||
                LinkSummaryLoader.isHackerNewsItemResult(cached)
        }
    }
    var summary by remember(state) {
        mutableStateOf(
            when {
                initialCached != null -> ReferenceSummaryUiState(result = initialCached)
                !state.resolvedTitle.isNullOrBlank() -> ReferenceSummaryUiState(
                    loading = true,
                    showFallback = true,
                )
                else -> ReferenceSummaryUiState(loading = true)
            },
        )
    }

    DisposableEffect(state, attempt) {
        if (attempt == 0 && initialCached != null) {
            initialCached.finalUrl?.let {
                controller.updateLinkPreviewVisibleUrl(state.originalUrl, it)
            }
            onDispose { }
        } else {
            if (attempt > 0) summary = summary.copy(retrying = true)
            val request = LinkSummaryLoader.load(
                context,
                state.originalUrl,
                state.fallbackTitle,
                object : LinkSummaryLoader.Callback {
                    override fun onSuccess(result: LinkSummaryLoader.Result) {
                        controller.updateLinkPreviewVisibleUrl(state.originalUrl, result.finalUrl)
                        summary = ReferenceSummaryUiState(result = result)
                    }

                    override fun onFailure(message: String) {
                        summary = when {
                            message.trim().equals(PdfContentTypeError, ignoreCase = true) ->
                                ReferenceSummaryUiState(showFallback = true)
                            attempt == 0 && !state.resolvedTitle.isNullOrBlank() ->
                                ReferenceSummaryUiState(showFallback = true)
                            else -> ReferenceSummaryUiState(
                                showFallback = true,
                                error = message,
                            )
                        }
                    }
                },
            )
            onDispose(request::cancel)
        }
    }

    val currentUrl = controller.linkPreviewVisibleUrl ?: state.originalUrl
    ReferenceCardContent(
        url = currentUrl,
        fallbackTitle = state.fallbackTitle,
        summary = summary,
        preferredFont = controller.displaySettings?.font
            ?: SettingsUtils.getPreferredFont(context),
        commentTextSize = controller.displaySettings?.preferredTextSize
            ?: SettingsUtils.getPreferredCommentTextSize(context),
        faviconProvider = controller.displaySettings?.faviconProvider
            ?: SettingsUtils.getPreferredFaviconProvider(context),
        onOpen = { Utils.openLinkMaybeHN(context, currentUrl) },
        onRetry = {
            if (Utils.isNetworkAvailable(context) && !summary.retrying) attempt++
        },
    )
}

@Composable
private fun ReferenceCardContent(
    url: String,
    fallbackTitle: String,
    summary: ReferenceSummaryUiState,
    preferredFont: String,
    commentTextSize: Float,
    faviconProvider: String,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val typography = rememberContentTypography(
        preferredFont = preferredFont,
        commentTextSize = commentTextSize,
    )
    val result = summary.result
    val title = firstNotBlank(result?.title, fallbackTitle, url)
    val description = result?.description?.takeIf(String::isNotBlank)
    val domain = if (
        result != null && LinkSummaryLoader.isHackerNewsItemResult(result) &&
        !result.siteName.isNullOrBlank()
    ) {
        result.siteName
    } else {
        runCatching { Utils.getDomainName(url) }.getOrDefault(url)
    }
    val favicon = remember(url, faviconProvider) {
        runCatching { FaviconLoader.getFaviconUrl(url, faviconProvider) }.getOrNull()
    }
    val imageUrl = result?.imageUrl?.takeIf(String::isNotBlank)
    var imageExpanded by remember(url, imageUrl) { mutableStateOf(false) }
    var imageRatio by remember(url, imageUrl) { mutableFloatStateOf(1f) }
    val offline = summary.error != null && !Utils.isNetworkAvailable(context)
    val retryable = summary.error?.let(::isRetryableReferenceError) == true
    val errorMessage = summary.error?.let { referenceErrorMessage(context, it) }
    val showImage = (summary.loading && !summary.showFallback) || imageUrl != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .animateContentSize(tween(ReferenceContentDurationMillis, easing = FastOutSlowInEasing)),
    ) {
        AnimatedContent(
            targetState = imageExpanded && imageUrl != null,
            transitionSpec = {
                (fadeIn(tween(140)) togetherWith fadeOut(tween(70))).using(
                    SizeTransform(clip = false) {
                            _, _ -> tween(ReferenceImageDurationMillis, easing = FastOutSlowInEasing)
                    },
                )
            },
            label = "reference image expansion",
        ) { expanded ->
            if (expanded) {
                Column {
                    ReferencePreviewImage(
                        imageUrl = imageUrl,
                        loading = false,
                        expanded = true,
                        imageRatio = imageRatio,
                        onImageRatio = { imageRatio = it },
                        onClick = { imageExpanded = false },
                    )
                    ReferenceMetadata(
                        domain = domain,
                        favicon = favicon,
                        title = title,
                        loading = false,
                        fontFamily = typography.family,
                        metaSize = typography.storyMetaSize,
                        titleSize = typography.storyTitleSize + 0.5f,
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (showImage) {
                        ReferencePreviewImage(
                            imageUrl = imageUrl,
                            loading = summary.loading,
                            expanded = false,
                            imageRatio = imageRatio,
                            onImageRatio = { imageRatio = it },
                            onClick = { if (imageUrl != null) imageExpanded = true },
                            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 16.dp),
                        )
                    }
                    ReferenceMetadata(
                        domain = domain,
                        favicon = favicon,
                        title = title,
                        loading = summary.loading && !summary.showFallback,
                        fontFamily = typography.family,
                        metaSize = typography.storyMetaSize,
                        titleSize = typography.storyTitleSize + 0.5f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                start = if (showImage) 0.dp else 20.dp,
                                top = 20.dp,
                                end = 20.dp,
                            ),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 18.dp),
        ) {
            AnimatedContent(
                targetState = Triple(description, summary.loading, summary.error),
                transitionSpec = {
                    (fadeIn(tween(ReferenceContentDurationMillis)) togetherWith
                        fadeOut(tween(ReferenceContentDurationMillis))).using(
                        SizeTransform(clip = false) {
                                _, _ -> tween(ReferenceContentDurationMillis, easing = FastOutSlowInEasing)
                        },
                    )
                },
                label = "reference summary state",
            ) { (currentDescription, loading, error) ->
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
                    loading && !summary.showFallback -> ReferenceDescriptionShimmer()
                    error != null -> ReferenceErrorContent(
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
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = HarmonicTheme.colors.storyNormal,
                ),
            ) {
                Icon(
                    painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.link_summary_open_short),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ReferencePreviewImage(
    imageUrl: String?,
    loading: Boolean,
    expanded: Boolean,
    imageRatio: Float,
    onImageRatio: (Float) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = if (expanded) {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    } else {
        RoundedCornerShape(8.dp)
    }
    Box(
        modifier = modifier
            .then(
                if (expanded) {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(imageRatio.coerceIn(0.45f, 3f))
                } else {
                    Modifier.size(104.dp)
                },
            )
            .clip(shape)
            .background(HarmonicTheme.colors.surfaceContainerHighest)
            .clickable(
                enabled = imageUrl != null,
                onClick = onClick,
            ),
    ) {
        if (loading) {
            LinkPreviewShimmer(Modifier.fillMaxSize())
        }
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .networkHeader("User-Agent", NetworkComponent.USER_AGENT)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(
                    if (expanded) {
                        R.string.link_summary_collapse_image
                    } else {
                        R.string.link_summary_expand_image
                    },
                ),
                modifier = Modifier.fillMaxSize(),
                contentScale = if (expanded) ContentScale.Fit else ContentScale.Crop,
                onSuccess = { success ->
                    val drawable = success.result.image.asDrawable(context.resources)
                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                        onImageRatio(drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight)
                    }
                },
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
    fontFamily: FontFamily,
    metaSize: Float,
    titleSize: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = favicon,
                fallback = painterResource(R.drawable.ic_public),
                error = painterResource(R.drawable.ic_public),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
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
    val retryingDescription = stringResource(R.string.link_summary_retrying)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_cloud_off),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = HarmonicTheme.colors.drawable,
        )
        Text(
            text = stringResource(
                if (offline) R.string.link_summary_offline_title else R.string.link_summary_error_title,
            ),
            modifier = Modifier.padding(top = 12.dp),
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
                    LoadingIndicator(
                        modifier = Modifier
                            .size(32.dp)
                            .semantics {
                                contentDescription = retryingDescription
                            },
                    )
                } else {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.height(48.dp),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_refresh),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.link_summary_retry),
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
private fun ImageOnlyPreviewCard(state: CommentLinkPreviewOverlayState.Image) {
    val context = LocalContext.current
    var imageRatio by remember(state.imageUrl, state.sourceBounds) {
        mutableFloatStateOf(
            state.sourceBounds?.let { bounds ->
                if (bounds.height > 0f) bounds.width / bounds.height else 16f / 9f
            } ?: (16f / 9f),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(state.imageUrl)
                .networkHeader("User-Agent", NetworkComponent.USER_AGENT)
                .build(),
            contentDescription = state.description,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(imageRatio.coerceIn(0.35f, 4f)),
            contentScale = ContentScale.Fit,
            onSuccess = { success ->
                val drawable = success.result.image.asDrawable(context.resources)
                if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                    imageRatio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight
                }
            },
        )
    }
}

@Composable
private fun LinkPreviewShimmer(modifier: Modifier = Modifier) {
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

private data class ReferenceSummaryUiState(
    val loading: Boolean = false,
    val showFallback: Boolean = false,
    val result: LinkSummaryLoader.Result? = null,
    val error: String? = null,
    val retrying: Boolean = false,
)

private fun isRetryableReferenceError(message: String): Boolean =
    !message.startsWith("This link contains ")

private fun referenceErrorMessage(context: android.content.Context, loaderMessage: String): String {
    if (!Utils.isNetworkAvailable(context)) {
        return context.getString(R.string.link_summary_offline_message)
    }
    return if (
        loaderMessage.startsWith("The page returned HTTP ") ||
        loaderMessage.startsWith("This link contains ") ||
        loaderMessage.startsWith("This link does not use ") ||
        loaderMessage.startsWith("The page is too large ")
    ) {
        loaderMessage
    } else {
        context.getString(R.string.link_summary_error_message)
    }
}

private fun firstNotBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }.orEmpty()

@Preview(name = "Reference link preview", device = Devices.PHONE, showBackground = true)
@Composable
private fun ReferencePreviewCardPreview() {
    HarmonicTheme {
        Surface(
            modifier = Modifier.padding(20.dp),
            shape = RoundedCornerShape(28.dp),
            color = HarmonicTheme.colors.surfaceContainerHigh,
        ) {
            ReferenceCardContent(
                url = "https://example.com/article",
                fallbackTitle = "An article linked from a comment",
                summary = ReferenceSummaryUiState(showFallback = true),
                preferredFont = "productsans",
                commentTextSize = SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE,
                faviconProvider = SettingsUtils.FAVICON_PROVIDER_GOOGLE,
                onOpen = {},
                onRetry = {},
            )
        }
    }
}
