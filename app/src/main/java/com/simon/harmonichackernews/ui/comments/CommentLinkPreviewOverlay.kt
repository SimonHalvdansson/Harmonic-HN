package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.network.networkHeader
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.link_summary_collapse_image
import com.simon.harmonichackernews.resources.link_summary_expand_image
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.AndroidDisplay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CommentLinkPreviewOverlay(controller: CommentsComposeController) {
    val context = LocalContext.current
    SharedCommentLinkPreviewOverlay(
        controller = controller,
        tablet = controller.displaySettings?.isTablet == true || AndroidDisplay.isTablet(context.resources),
        referenceContent = { state -> ReferencePreviewCard(controller, state) },
        imageContent = ::ImageOnlyPreviewCard,
    )
}

@Composable
private fun ReferencePreviewCard(
    controller: CommentsComposeController,
    state: CommentLinkPreviewOverlayState.Reference,
) {
    val appComposition = LocalHarmonicUiDependencies.current
    val scope = rememberCoroutineScope()
    val runtime = remember(state.originalUrl, appComposition, scope) {
        appComposition.createReferenceLinkPreviewRuntime(scope)
    }
    val runtimeState by runtime.state.collectAsState()

    LaunchedEffect(runtime, state) {
        runtime.load(state.originalUrl, state.fallbackTitle, state.resolvedTitle)
    }
    DisposableEffect(runtime) {
        onDispose(runtime::dispose)
    }
    LaunchedEffect(runtimeState.url) {
        runtimeState.url.takeIf { it.isNotBlank() && it != state.originalUrl }?.let {
            controller.updateLinkPreviewVisibleUrl(state.originalUrl, it)
        }
    }

    val summary = ReferenceSummaryUiState(
        loading = runtimeState.loading,
        showFallback = runtimeState.showFallback,
        result = runtimeState.summary,
        error = runtimeState.error,
        retrying = runtimeState.retrying,
    )
    val currentUrl = runtimeState.url.takeIf(String::isNotBlank)
        ?: controller.linkPreviewVisibleUrl
        ?: state.originalUrl
    val userSettings = appComposition.userSettings
    val preferredFont = controller.displaySettings?.font ?: userSettings.story.font
    val commentTextSize = controller.displaySettings?.preferredTextSize
        ?: userSettings.comments.textSize
    val faviconProvider = controller.displaySettings?.faviconProvider
        ?: userSettings.story.faviconProvider
    val favicon = remember(currentUrl, faviconProvider) {
        runCatching { FaviconUrlBuilder.faviconUrl(currentUrl, faviconProvider) }.getOrNull()
    }
    SharedReferenceCardContent(
        url = currentUrl,
        fallbackTitle = state.fallbackTitle,
        summary = summary,
        preferredFont = preferredFont,
        commentTextSize = commentTextSize,
        favicon = favicon,
        offline = runtimeState.offline,
        textStyle = linkPreviewTextStyle,
        referenceImage = { imageUrl, loading, expanded, ratio, onRatio, onClick, modifier ->
            ReferencePreviewImage(
                imageUrl = imageUrl,
                loading = loading,
                expanded = expanded,
                imageRatio = ratio,
                onImageRatio = onRatio,
                onClick = onClick,
                modifier = modifier,
            )
        },
        onOpen = { appComposition.links.open(currentUrl) },
        onRetry = {
            runtime.retry(state.originalUrl, state.fallbackTitle, state.resolvedTitle)
        },
    )
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
    val appComposition = LocalHarmonicUiDependencies.current
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
            .clickable(enabled = imageUrl != null, onClick = onClick),
    ) {
        if (loading) SharedLinkPreviewShimmer(Modifier.fillMaxSize())
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .networkHeader("User-Agent", appComposition.network.userAgent)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(
                    if (expanded) {
                        Res.string.link_summary_collapse_image
                    } else {
                        Res.string.link_summary_expand_image
                    },
                ),
                modifier = Modifier.fillMaxSize(),
                contentScale = if (expanded) ContentScale.Fit else ContentScale.Crop,
                onSuccess = { success ->
                    val image = success.result.image
                    if (image.width > 0 && image.height > 0) {
                        onImageRatio(image.width.toFloat() / image.height)
                    }
                },
            )
        }
    }
}

@Composable
private fun ImageOnlyPreviewCard(state: CommentLinkPreviewOverlayState.Image) {
    val context = LocalContext.current
    val appComposition = LocalHarmonicUiDependencies.current
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
                .networkHeader("User-Agent", appComposition.network.userAgent)
                .build(),
            contentDescription = state.description,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(imageRatio.coerceIn(0.35f, 4f)),
            contentScale = ContentScale.Fit,
            onSuccess = { success ->
                val image = success.result.image
                if (image.width > 0 && image.height > 0) {
                    imageRatio = image.width.toFloat() / image.height
                }
            },
        )
    }
}

private val linkPreviewTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
