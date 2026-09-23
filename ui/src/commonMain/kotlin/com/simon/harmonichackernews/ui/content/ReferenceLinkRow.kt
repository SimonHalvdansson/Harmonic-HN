package com.simon.harmonichackernews.ui.content


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.ui.common.captureSharedTransformSourceContent
import com.simon.harmonichackernews.ui.common.onSecondaryClick
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ReferenceLinkRow(
    marker: String,
    label: String,
    typography: ContentTypography,
    expandedUrl: String? = null,
    faviconUrl: String? = null,
    topPadding: Dp = 4.dp,
    modifier: Modifier = Modifier,
    suppressed: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (Rect, GraphicsLayer?) -> Unit,
) {
    val colors = HarmonicTheme.colors
    val resolvedExpandedUrl = expandedUrl?.takeIf { hasReferenceLinkTitle(label, it) }
    val faviconSize by animateDpAsState(
        targetValue = if (resolvedExpandedUrl != null) 21.dp else 17.dp,
        animationSpec = contentTween(),
        label = "collected link favicon size",
    )
    var bounds by remember(label) { mutableStateOf(Rect.Zero) }
    var sourceContentLayer by remember(label) { mutableStateOf<GraphicsLayer?>(null) }
    Box(modifier.fillMaxWidth().padding(top = topPadding)) {
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
                    indication = ripple(color = colors.mutedText.copy(alpha = 0.35f)),
                    onClick = onClick,
                    onLongClick = { onLongClick(bounds, sourceContentLayer) },
                )
                .onSecondaryClick { onLongClick(bounds, sourceContentLayer) }
                .captureSharedTransformSourceContent { sourceContentLayer = it }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val faviconFallback = painterResource(Res.drawable.ic_public)
            if (faviconUrl == null) {
                Icon(
                    painter = faviconFallback,
                    contentDescription = null,
                    tint = colors.iconTint,
                    modifier = Modifier.padding(end = 8.dp).size(faviconSize),
                )
            } else {
                var faviconLoaded by remember(faviconUrl) { mutableStateOf(false) }
                AsyncImage(
                    model = faviconUrl,
                    contentDescription = null,
                    placeholder = faviconFallback,
                    error = faviconFallback,
                    fallback = faviconFallback,
                    colorFilter = if (faviconLoaded) null else ColorFilter.tint(colors.iconTint),
                    onLoading = { faviconLoaded = false },
                    onSuccess = { faviconLoaded = true },
                    onError = { faviconLoaded = false },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(faviconSize)
                        .clip(RoundedCornerShape(faviconSize * (3f / 17f))),
                )
            }
            SharedTransitionLayout(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = label to resolvedExpandedUrl,
                    contentAlignment = Alignment.TopStart,
                    transitionSpec = {
                        (fadeIn(contentTween()) + slideInVertically(contentTween()) { it / 5 })
                            .togetherWith(fadeOut(tween(120)))
                            .using(SizeTransform(clip = false) { _, _ -> contentTween() })
                    },
                    label = "collected link layout",
                ) { (title, url) ->
                    val visibilityScope = this
                    val titleSize = typography.referenceLabelSize + if (url != null) 0.5f else 0f
                    val titleLineHeight = titleSize + if (url != null) 0f else 3f
                    Column(verticalArrangement = Arrangement.spacedBy(if (url != null) (-1).dp else 0.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (marker.isNotBlank()) Text(
                                marker,
                                modifier = Modifier.padding(end = 6.dp).alignByBaseline()
                                    .sharedElement(
                                        sharedContentState = rememberSharedContentState("marker"),
                                        animatedVisibilityScope = visibilityScope,
                                        boundsTransform = BoundsTransform { _, _ -> contentTween() },
                                    ),
                                color = colors.mutedText,
                                fontFamily = typography.family,
                                fontWeight = FontWeight.Bold,
                                fontSize = titleSize.sp,
                                lineHeight = titleLineHeight.sp,
                            )
                            Text(
                                title,
                                modifier = Modifier.weight(1f).alignByBaseline()
                                    .wrapContentWidth(Alignment.Start)
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState(
                                            (if (url == null) "url" else "title") to title,
                                        ),
                                        animatedVisibilityScope = visibilityScope,
                                        enter = fadeIn(contentTween()),
                                        exit = fadeOut(contentTween()),
                                        boundsTransform = BoundsTransform { _, _ -> contentTween() },
                                    ),
                                color = if (url != null) lerp(colors.mutedText, colors.contentPrimary, 0.55f) else colors.contentPrimary,
                                fontFamily = typography.family,
                                fontSize = titleSize.sp,
                                lineHeight = titleLineHeight.sp,
                                fontWeight = if (url != null) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (url != null) Text(
                            url,
                            modifier = Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState("url" to url),
                                animatedVisibilityScope = visibilityScope,
                                enter = fadeIn(contentTween()),
                                exit = fadeOut(contentTween()),
                                boundsTransform = BoundsTransform { _, _ -> contentTween() },
                            ),
                            color = colors.mutedText,
                            fontFamily = typography.family,
                            fontSize = (typography.referenceMarkerSize - 2f).sp,
                            lineHeight = (typography.referenceMarkerSize - 0.5f).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
