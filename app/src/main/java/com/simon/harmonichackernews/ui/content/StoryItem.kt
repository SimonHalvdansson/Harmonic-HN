package com.simon.harmonichackernews.ui.content

import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.roundToInt

private const val ContentAnimationDuration = 220
private val ContentMotionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

data class StoryItemUiModel(
    val index: String,
    val title: String,
    val summary: String,
    val points: Int,
    val domain: String,
    val domainWithoutTopLevel: String,
    val age: String,
    val commentCount: Int,
    val faviconRes: Int,
    val previewImageRes: Int?,
    val faviconUrl: String? = null,
    val previewImageUrl: String? = null,
    val faviconTintArgb: Int? = null,
    val previewImageTintArgb: Int? = null,
)

data class StoryItemStyle(
    val previewImageMode: String,
    val borderlessLargeImage: Boolean,
    val compact: Boolean,
    val showSummary: Boolean,
    val showFavicon: Boolean,
    val showPoints: Boolean,
    val compactPoints: Boolean,
    val includeTopLevelDomain: Boolean,
    val showCommentCount: Boolean,
    val showIndex: Boolean,
    val commentsOnLeft: Boolean,
    val tintCard: Boolean,
    val cardStyle: Boolean,
    val useHotnessIcon: Boolean,
    val preferredFont: String,
    val textSize: Float,
    val dimmed: Boolean = false,
    val paletteTintConfigKey: String = SettingsUtils.PALETTE_TINT_DEFAULT,
)

val SettingsStoryPreviewModel = StoryItemUiModel(
    index = "3.",
    title = "Algorithm breaks speed limit for solving linear equations",
    summary = "A faster method uses a new approach to solve large linear systems more efficiently.",
    points = 53,
    domain = "science.org",
    domainWithoutTopLevel = "science",
    age = "2h",
    commentCount = 18,
    faviconRes = R.drawable.quanta,
    previewImageRes = R.drawable.web_preview,
)

@Composable
fun StoryItem(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    modifier: Modifier = Modifier,
    listItem: Boolean = false,
    onLinkClick: (() -> Unit)? = null,
    onLinkLongClick: (() -> Unit)? = null,
    onCommentClick: (() -> Unit)? = null,
    onBoundsChanged: ((Rect) -> Unit)? = null,
) {
    val context = LocalContext.current
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(
        preferredFont = style.preferredFont,
        storyTextSize = style.textSize,
    )
    val animateChanges = !listItem
    val titleSize = storyFloat(
        targetValue = typography.storyTitleSize,
        animate = animateChanges,
        label = "story title size",
    )
    val summarySize = storyFloat(
        targetValue = typography.storySummarySize,
        animate = animateChanges,
        label = "story summary size",
    )
    val metaSize = storyFloat(
        targetValue = typography.storyMetaSize,
        animate = animateChanges,
        label = "story meta size",
    )
    val commentCountSize = storyFloat(
        targetValue = typography.storyCommentCountSize,
        animate = animateChanges,
        label = "story comment count size",
    )
    val cardProgress = storyFloat(
        targetValue = if (style.cardStyle) 1f else 0f,
        animate = animateChanges,
        label = "story card style",
    )
    val alignmentProgress = storyFloat(
        targetValue = if (style.commentsOnLeft) 1f else 0f,
        animate = animateChanges,
        label = "story comment alignment",
    )
    val clickedMediaAlpha = if (animateChanges) {
        animateFloatAsState(
            targetValue = if (style.dimmed) 0.6f else 1f,
            animationSpec = tween(180, easing = ContentMotionEasing),
            label = "clicked story media alpha",
        ).value
    } else {
        if (style.dimmed) 0.6f else 1f
    }
    val paletteKey = style.paletteTintConfigKey
    val faviconSource = model.faviconUrl ?: model.faviconRes
    val previewSource = model.previewImageUrl ?: model.previewImageRes
    var loadedFaviconTintColor by remember(
        faviconSource,
        paletteKey,
        model.faviconTintArgb,
    ) {
        androidx.compose.runtime.mutableStateOf(model.faviconTintArgb?.let(::Color))
    }
    var loadedPreviewTintColor by remember(
        previewSource,
        paletteKey,
        model.previewImageTintArgb,
    ) {
        androidx.compose.runtime.mutableStateOf(model.previewImageTintArgb?.let(::Color))
    }
    val baseTintColor = remember(context) {
        Color(PreviewImageTintUtils.getTintBaseColor(context))
    }
    val faviconResourceTintColor = remember(context, model.faviconRes, model.faviconUrl, paletteKey) {
        if (model.faviconUrl == null) {
            calculatePreviewTint(
                drawable = AppCompatResources.getDrawable(context, model.faviconRes),
                fallback = baseTintColor,
                context = context,
            )
        } else {
            null
        }
    }
    val previewResourceTintColor = remember(
        context,
        model.previewImageRes,
        model.previewImageUrl,
        paletteKey,
    ) {
        if (model.previewImageUrl == null) {
            model.previewImageRes?.let {
                calculatePreviewTint(
                    drawable = AppCompatResources.getDrawable(context, it),
                    fallback = baseTintColor,
                    context = context,
                )
            }
        } else {
            null
        }
    }
    val onPreviewDrawableLoaded: (Drawable) -> Unit = { drawable ->
        if (!listItem &&
            style.previewImageMode != SettingsUtils.STORY_PREVIEW_IMAGE_OFF &&
            model.previewImageTintArgb == null
        ) {
            loadedPreviewTintColor = calculatePreviewTint(drawable, baseTintColor, context)
        }
    }
    val onFaviconDrawableLoaded: (Drawable) -> Unit = { drawable ->
        if (!listItem && model.faviconTintArgb == null) {
            loadedFaviconTintColor = calculatePreviewTint(drawable, baseTintColor, context)
        }
    }
    val useFaviconTint = model.faviconUrl != null || model.faviconRes != R.drawable.ic_public
    val effectiveFaviconTint = if (useFaviconTint) {
        loadedFaviconTintColor ?: faviconResourceTintColor
    } else {
        null
    }
    val usePreviewTint = style.previewImageMode != SettingsUtils.STORY_PREVIEW_IMAGE_OFF &&
        previewSource != null
    val targetCardBackground = when {
        style.tintCard && usePreviewTint -> loadedPreviewTintColor
            ?: previewResourceTintColor
            ?: effectiveFaviconTint
            ?: baseTintColor
        style.tintCard -> effectiveFaviconTint ?: baseTintColor
        style.cardStyle -> colors.surfaceContainerHigh
        else -> colors.background
    }
    // Async preview/favicons arrive after the row itself. Keep this one inexpensive animation for
    // list items so their extracted tint blends in instead of flashing to the palette color.
    val cardBackground by androidx.compose.animation.animateColorAsState(
        targetValue = targetCardBackground,
        animationSpec = contentTween(),
        label = "story card tint",
    )
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (listItem) 0.dp else 8.dp,
                vertical = if (listItem) 0.dp else 10.dp,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        onBoundsChanged?.invoke(it.boundsInWindow())
                    }
                    .then(
                        if (cardProgress > 0f) {
                            Modifier.shadow(
                                elevation = 1.dp,
                                shape = shape,
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = cardProgress),
                                spotColor = Color.Black.copy(alpha = cardProgress),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clip(shape)
                    .background(cardBackground)
                    .border(1.dp, colors.commentDivider.copy(alpha = cardProgress), shape)
                    .combinedClickable(
                        enabled = onLinkClick != null || onLinkLongClick != null,
                        onClick = { onLinkClick?.invoke() },
                        onLongClick = onLinkLongClick,
                    ),
            ) {
                LargeStoryPreviewImage(
                    visible = style.previewImageMode == SettingsUtils.STORY_PREVIEW_IMAGE_LARGE,
                    borderless = style.borderlessLargeImage,
                    model = model.previewImageUrl ?: model.previewImageRes,
                    onDrawableLoaded = onPreviewDrawableLoaded,
                    alpha = clickedMediaAlpha,
                    animateChanges = animateChanges,
                )

                StoryBodyRow(
                    commentsOnLeftProgress = alignmentProgress,
                    content = {
                        StoryLinkContent(
                            model = model,
                            style = style,
                            typography = typography,
                            titleSize = titleSize,
                            summarySize = summarySize,
                            metaSize = metaSize,
                            alignmentProgress = alignmentProgress,
                            onPreviewDrawableLoaded = onPreviewDrawableLoaded,
                            onFaviconDrawableLoaded = onFaviconDrawableLoaded,
                            mediaAlpha = clickedMediaAlpha,
                            animateChanges = animateChanges,
                        )
                    },
                    commentRail = {
                        StoryCommentRail(
                            model = model,
                            style = style,
                            fontFamily = typography.family,
                            countTextSize = commentCountSize,
                            onClick = onCommentClick,
                            animateChanges = animateChanges,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LargeStoryPreviewImage(
    visible: Boolean,
    borderless: Boolean,
    model: Any?,
    onDrawableLoaded: (Drawable) -> Unit,
    alpha: Float,
    animateChanges: Boolean,
) {
    val context = LocalContext.current
    val softwareModel = remember(context, model) {
        ImageRequest.Builder(context).data(model).allowHardware(false).build()
    }
    val targetInset = if (borderless) 0.dp else 10.dp
    val targetBottomMargin = if (borderless) 0.dp else 2.dp
    val targetRadius = if (borderless) 0.dp else 6.dp
    val inset = if (animateChanges) {
        animateDpAsState(targetInset, contentTween(), label = "large story image inset").value
    } else {
        targetInset
    }
    val bottomMargin = if (animateChanges) {
        animateDpAsState(
            targetBottomMargin,
            contentTween(),
            label = "large story image bottom margin",
        ).value
    } else {
        targetBottomMargin
    }
    val radius = if (animateChanges) {
        animateDpAsState(targetRadius, contentTween(), label = "large story image corners").value
    } else {
        targetRadius
    }

    if (animateChanges) {
        AnimatedVisibility(
            visible = visible && model != null,
            enter = fadeIn(contentTween()) +
                expandVertically(contentTween(), expandFrom = Alignment.Top),
            exit = fadeOut(contentTween()) +
                shrinkVertically(contentTween(), shrinkTowards = Alignment.Top),
        ) {
            LargeStoryPreviewImageContent(
                model = softwareModel,
                inset = inset,
                bottomMargin = bottomMargin,
                radius = radius,
                alpha = alpha,
                onDrawableLoaded = onDrawableLoaded,
            )
        }
    } else if (visible && model != null) {
        LargeStoryPreviewImageContent(
            model = softwareModel,
            inset = inset,
            bottomMargin = bottomMargin,
            radius = radius,
            alpha = alpha,
            onDrawableLoaded = onDrawableLoaded,
        )
    }
}

@Composable
private fun LargeStoryPreviewImageContent(
    model: Any,
    inset: androidx.compose.ui.unit.Dp,
    bottomMargin: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
    alpha: Float,
    onDrawableLoaded: (Drawable) -> Unit,
) {
    var imageLoaded by remember(model) { androidx.compose.runtime.mutableStateOf(false) }
    val loadProgress by animateFloatAsState(
        targetValue = if (imageLoaded) 1f else 0f,
        animationSpec = tween(ContentAnimationDuration, easing = ContentMotionEasing),
        label = "large story image load",
    )
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, top = inset, end = inset, bottom = bottomMargin)
            .height(176.dp)
            .clip(RoundedCornerShape(radius))
            .renderTransform(
                alpha = alpha * loadProgress,
                scaleX = 0.98f + 0.02f * loadProgress,
                scaleY = 0.98f + 0.02f * loadProgress,
            ),
        contentScale = ContentScale.Crop,
        onSuccess = {
            imageLoaded = true
            onDrawableLoaded(it.result.drawable)
        },
        onError = { imageLoaded = true },
    )
}

@Composable
private fun StoryLinkContent(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    typography: ContentTypography,
    titleSize: Float,
    summarySize: Float,
    metaSize: Float,
    alignmentProgress: Float,
    onPreviewDrawableLoaded: (Drawable) -> Unit,
    onFaviconDrawableLoaded: (Drawable) -> Unit,
    mediaAlpha: Float,
    animateChanges: Boolean,
) {
    val startPadding = lerp(6f, 0f, alignmentProgress).dp
    val endPadding = lerp(0f, 12f, alignmentProgress).dp
    val textStartPadding = lerp(10f, 4f, alignmentProgress).dp
    val showSmallImage = style.previewImageMode == SettingsUtils.STORY_PREVIEW_IMAGE_SMALL &&
        (model.previewImageUrl != null || model.previewImageRes != null)
    val smallImageProgress = storyFloat(
        targetValue = if (showSmallImage) 1f else 0f,
        animate = animateChanges,
        label = "small story image",
    )
    val summaryProgress = storyFloat(
        targetValue = if (style.showSummary) 1f else 0f,
        animate = animateChanges,
        label = "story summary",
    )
    val metaProgress = storyFloat(
        targetValue = if (style.compact) 0f else 1f,
        animate = animateChanges,
        label = "story meta",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = startPadding,
                top = 10.dp,
                end = endPadding,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        val indexProgress = storyFloat(
            targetValue = if (style.showIndex) 1f else 0f,
            animate = animateChanges,
            label = "story index",
        )
        Box(
            modifier = Modifier
                .width((21f * indexProgress).dp)
                .then(
                    if (indexProgress > 0f) {
                        Modifier.renderTransform(alpha = indexProgress)
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (indexProgress > 0f) {
                val indexSize = with(LocalDensity.current) { 16.dp.toSp() }
                Text(
                    text = model.index,
                    modifier = Modifier
                        .requiredWidth(43.dp)
                        .offset(x = 3.dp),
                    color = if (style.dimmed) {
                        HarmonicTheme.colors.storyDisabled
                    } else {
                        HarmonicTheme.colors.storyNormal
                    },
                    fontFamily = typography.family,
                    fontSize = indexSize,
                    style = legacyTextStyle,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        StoryTextBlock(
            model = model,
            style = style,
            typography = typography,
            titleSize = titleSize,
            summarySize = summarySize,
            metaSize = metaSize,
            textStartPadding = textStartPadding,
            smallImageProgress = smallImageProgress,
            summaryProgress = summaryProgress,
            metaProgress = metaProgress,
            onPreviewDrawableLoaded = onPreviewDrawableLoaded,
            onFaviconDrawableLoaded = onFaviconDrawableLoaded,
            mediaAlpha = mediaAlpha,
            animateChanges = animateChanges,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StoryTextBlock(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    typography: ContentTypography,
    titleSize: Float,
    summarySize: Float,
    metaSize: Float,
    textStartPadding: androidx.compose.ui.unit.Dp,
    smallImageProgress: Float,
    summaryProgress: Float,
    metaProgress: Float,
    onPreviewDrawableLoaded: (Drawable) -> Unit,
    onFaviconDrawableLoaded: (Drawable) -> Unit,
    mediaAlpha: Float,
    animateChanges: Boolean,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val previewModel = model.previewImageUrl ?: model.previewImageRes
    var previewImageLoaded by remember(previewModel) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    val previewLoadProgress by animateFloatAsState(
        targetValue = if (previewImageLoaded) 1f else 0f,
        animationSpec = tween(ContentAnimationDuration, easing = ContentMotionEasing),
        label = "small story image load",
    )
    Layout(
        modifier = modifier,
        content = {
            Text(
                text = model.title,
                modifier = Modifier
                    .padding(start = textStartPadding, end = 2.dp),
                color = if (style.dimmed) {
                    HarmonicTheme.colors.storyDisabled
                } else {
                    HarmonicTheme.colors.storyNormal
                },
                fontFamily = typography.family,
                fontWeight = FontWeight.Bold,
                fontSize = titleSize.sp,
                style = if (animateChanges) animatedLegacyTextStyle else legacyTextStyle,
            )
            if (summaryProgress > 0f) {
                Text(
                    text = model.summary,
                    modifier = Modifier
                        .padding(start = textStartPadding, end = 2.dp)
                        .renderTransform(alpha = summaryProgress),
                    color = HarmonicTheme.colors.storyDisabled,
                    fontFamily = typography.family,
                    fontSize = summarySize.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = if (animateChanges) animatedLegacyTextStyle else legacyTextStyle,
                )
            } else {
                Box(Modifier.size(0.dp))
            }
            if (metaProgress > 0f) {
                StoryMeta(
                    model = model,
                    style = style,
                    typography = typography,
                    textSize = metaSize,
                    onFaviconDrawableLoaded = onFaviconDrawableLoaded,
                    mediaAlpha = mediaAlpha,
                    animateChanges = animateChanges,
                    modifier = Modifier
                        .padding(start = textStartPadding, end = 2.dp)
                        .renderTransform(alpha = metaProgress),
                )
            } else {
                Box(Modifier.size(0.dp))
            }
            if (previewModel != null && smallImageProgress > 0f) {
                val softwarePreviewModel = remember(context, previewModel) {
                    ImageRequest.Builder(context)
                        .data(previewModel)
                        .allowHardware(false)
                        .build()
                }
                AsyncImage(
                    model = softwarePreviewModel,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 72.dp, height = 52.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .renderTransform(
                            alpha = smallImageProgress * mediaAlpha * previewLoadProgress,
                            scaleX = 0.92f + 0.08f * smallImageProgress * previewLoadProgress,
                            scaleY = 0.92f + 0.08f * smallImageProgress * previewLoadProgress,
                        ),
                    contentScale = ContentScale.Crop,
                    onSuccess = {
                        previewImageLoaded = true
                        onPreviewDrawableLoaded(it.result.drawable)
                    },
                )
            } else {
                Box(Modifier.size(0.dp))
            }
        },
    ) { measurables, constraints ->
        val imageSlotWidth = with(density) { 82.dp.roundToPx() }
        val imageWidth = with(density) { 72.dp.roundToPx() }
        val imageHeight = with(density) { 52.dp.roundToPx() }
        val imageEnd = with(density) { 2.dp.roundToPx() }
        val reservedImageWidth = (imageSlotWidth * smallImageProgress).roundToInt()
        val textWidth = (constraints.maxWidth - reservedImageWidth).coerceAtLeast(0)
        val textConstraints = constraints.copy(
            minWidth = textWidth,
            maxWidth = textWidth,
            minHeight = 0,
        )
        val title = measurables[0].measure(textConstraints)
        val summary = measurables[1].measure(textConstraints)
        val meta = measurables[2].measure(textConstraints)
        val image = measurables[3].measure(Constraints.fixed(imageWidth, imageHeight))
        val twoDp = with(density) { 2.dp.roundToPx() }
        val threeDp = with(density) { 3.dp.roundToPx() }
        val effectiveImageHeight = (image.height * smallImageProgress).roundToInt()
        val effectiveMetaHeight = (meta.height * metaProgress).roundToInt()

        val offMetaY = title.height + twoDp
        val offHeight = maxOf(
            title.height + (twoDp * metaProgress).roundToInt() + effectiveMetaHeight,
            effectiveImageHeight,
        )
        val offImageY = ((offHeight - effectiveImageHeight) / 2).coerceAtLeast(0)

        val titleRowHeight = maxOf(title.height, effectiveImageHeight)
        val onSummaryY = titleRowHeight + threeDp
        val effectiveSummaryHeight = (summary.height * summaryProgress).roundToInt()
        val onMetaY = onSummaryY + effectiveSummaryHeight + threeDp
        val onHeight = onSummaryY + effectiveSummaryHeight +
            (threeDp * metaProgress).roundToInt() + effectiveMetaHeight
        val onImageY = ((titleRowHeight - effectiveImageHeight) / 2).coerceAtLeast(0)

        val summaryY = lerp(offMetaY.toFloat(), onSummaryY.toFloat(), summaryProgress).roundToInt()
        val metaY = lerp(offMetaY.toFloat(), onMetaY.toFloat(), summaryProgress).roundToInt()
        val imageY = lerp(offImageY.toFloat(), onImageY.toFloat(), summaryProgress).roundToInt()
        val height = lerp(offHeight.toFloat(), onHeight.toFloat(), summaryProgress)
            .roundToInt()
            .coerceAtLeast(title.height)

        layout(constraints.maxWidth, height) {
            title.placeRelative(0, 0)
            summary.placeRelative(0, summaryY)
            meta.placeRelative(0, metaY)
            image.placeRelative(constraints.maxWidth - image.width - imageEnd, imageY)
        }
    }
}

@Composable
private fun StoryMeta(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    typography: ContentTypography,
    textSize: Float,
    onFaviconDrawableLoaded: (Drawable) -> Unit,
    mediaAlpha: Float,
    animateChanges: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val faviconModel = model.faviconUrl ?: model.faviconRes
    val softwareFaviconModel = remember(context, faviconModel) {
        ImageRequest.Builder(context).data(faviconModel).allowHardware(false).build()
    }
    val metaText = remember(
        style.showPoints,
        style.compactPoints,
        style.includeTopLevelDomain,
        model,
    ) {
        buildString {
            if (style.showPoints) {
                append(if (style.compactPoints) "+${model.points}" else "${model.points} points")
                append(" • ")
            }
            append(if (style.includeTopLevelDomain) model.domain else model.domainWithoutTopLevel)
            append(" • ${model.age}")
        }
    }

    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (animateChanges) {
            AnimatedVisibility(
                visible = style.showFavicon,
                enter = fadeIn(contentTween()) + expandHorizontally(contentTween()),
                exit = fadeOut(contentTween()) + shrinkHorizontally(contentTween()),
            ) {
                StoryFavicon(
                    model = softwareFaviconModel,
                    fallbackRes = model.faviconRes,
                    mediaAlpha = mediaAlpha,
                    onDrawableLoaded = onFaviconDrawableLoaded,
                )
            }
        } else if (style.showFavicon) {
            StoryFavicon(
                model = softwareFaviconModel,
                fallbackRes = model.faviconRes,
                mediaAlpha = mediaAlpha,
                onDrawableLoaded = onFaviconDrawableLoaded,
            )
        }
        if (animateChanges) {
            AnimatedContent(
                targetState = metaText,
                transitionSpec = {
                    (fadeIn(contentTween()) + slideInVertically(contentTween()) { it / 3 })
                        .togetherWith(
                            fadeOut(contentTween()) +
                                slideOutVertically(contentTween()) { -it / 3 },
                        )
                        .using(
                            SizeTransform(
                                clip = false,
                                sizeAnimationSpec = { _, _ -> contentTween() },
                            ),
                        )
                },
                label = "story meta text",
            ) { text ->
                StoryMetaText(text, typography, textSize, animatedLegacyTextStyle)
            }
        } else {
            StoryMetaText(metaText, typography, textSize, legacyTextStyle)
        }
    }
}

@Composable
private fun StoryFavicon(
    model: Any,
    fallbackRes: Int,
    mediaAlpha: Float,
    onDrawableLoaded: (Drawable) -> Unit,
) {
    var imageLoaded by remember(model) { androidx.compose.runtime.mutableStateOf(false) }
    val loadProgress by animateFloatAsState(
        targetValue = if (imageLoaded) 1f else 0f,
        animationSpec = tween(ContentAnimationDuration, easing = ContentMotionEasing),
        label = "story favicon load",
    )
    AsyncImage(
        model = model,
        placeholder = painterResource(fallbackRes),
        fallback = painterResource(fallbackRes),
        error = painterResource(fallbackRes),
        contentDescription = null,
        modifier = Modifier
            .padding(end = 4.dp)
            .size(17.dp)
            .clip(RoundedCornerShape(3.dp))
            .renderTransform(alpha = mediaAlpha * loadProgress),
        onSuccess = {
            imageLoaded = true
            onDrawableLoaded(it.result.drawable)
        },
        onError = { imageLoaded = true },
    )
}

@Composable
private fun StoryMetaText(
    text: String,
    typography: ContentTypography,
    textSize: Float,
    textStyle: TextStyle,
) {
    Text(
        text = text,
        color = HarmonicTheme.colors.storyDisabled,
        fontFamily = typography.family,
        fontSize = textSize.sp,
        style = textStyle,
    )
}

@Composable
private fun StoryCommentRail(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    countTextSize: Float,
    onClick: (() -> Unit)?,
    animateChanges: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (animateChanges) {
            AnimatedContent(
                targetState = style.useHotnessIcon,
                transitionSpec = {
                    (fadeIn(contentTween()) +
                        expandVertically(contentTween(), expandFrom = Alignment.CenterVertically))
                        .togetherWith(
                            fadeOut(contentTween()) +
                                shrinkVertically(
                                    contentTween(),
                                    shrinkTowards = Alignment.CenterVertically,
                                ),
                        )
                },
                label = "story comments icon",
            ) { hot ->
                StoryCommentIcon(hot, style.dimmed)
            }
        } else {
            StoryCommentIcon(style.useHotnessIcon, style.dimmed)
        }
        val showCommentCount = style.showCommentCount && !style.compact
        if (animateChanges) {
            AnimatedVisibility(
                visible = showCommentCount,
                enter = fadeIn(contentTween()) + expandVertically(contentTween()),
                exit = fadeOut(contentTween()) + shrinkVertically(contentTween()),
            ) {
                StoryCommentCount(model, style, fontFamily, countTextSize, animatedLegacyTextStyle)
            }
        } else if (showCommentCount) {
            StoryCommentCount(model, style, fontFamily, countTextSize, legacyTextStyle)
        }
    }
}

@Composable
private fun StoryCommentIcon(hot: Boolean, dimmed: Boolean) {
    Icon(
        painter = painterResource(if (hot) R.drawable.ic_whatshot else R.drawable.ic_comment),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = HarmonicTheme.colors.drawable.let { drawable ->
            drawable.copy(alpha = drawable.alpha * if (dimmed) 0.6f else 1f)
        },
    )
}

@Composable
private fun StoryCommentCount(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    countTextSize: Float,
    textStyle: TextStyle,
) {
    Text(
        text = model.commentCount.toString(),
        modifier = Modifier.fillMaxWidth(),
        color = if (style.dimmed) {
            HarmonicTheme.colors.storyDisabled
        } else {
            HarmonicTheme.colors.storyNormal
        },
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = countTextSize.sp,
        style = textStyle,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun StoryBodyRow(
    commentsOnLeftProgress: Float,
    content: @Composable () -> Unit,
    commentRail: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Box { content() }
            Box { commentRail() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val railWidth = with(density) { 60.dp.roundToPx() }.coerceAtMost(width)
        val contentWidth = (width - railWidth).coerceAtLeast(0)
        val contentPlaceable = measurables[0].measure(
            Constraints(
                minWidth = contentWidth,
                maxWidth = contentWidth,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            ),
        )
        val railPlaceable = measurables[1].measure(
            Constraints.fixed(railWidth, contentPlaceable.height),
        )
        val contentX = (railWidth * commentsOnLeftProgress).roundToInt()
        val railX = ((width - railWidth) * (1f - commentsOnLeftProgress)).roundToInt()
        layout(width, contentPlaceable.height) {
            contentPlaceable.placeRelative(contentX, 0)
            railPlaceable.placeRelative(railX, 0)
        }
    }
}

private fun calculatePreviewTint(
    drawable: Drawable?,
    fallback: Color,
    context: android.content.Context,
): Color {
    if (drawable == null) return fallback
    return runCatching { Color(PreviewImageTintUtils.calculateCardTint(context, drawable)) }
        .getOrDefault(fallback)
}

@Composable
private fun storyFloat(
    targetValue: Float,
    animate: Boolean,
    label: String,
): Float = if (animate) {
    animateFloatAsState(
        targetValue = targetValue,
        animationSpec = contentTween(),
        label = label,
    ).value
} else {
    targetValue
}

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private fun Modifier.renderTransform(
    alpha: Float,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
): Modifier = if (alpha == 1f && scaleX == 1f && scaleY == 1f) {
    this
} else {
    graphicsLayer(
        alpha = alpha,
        scaleX = scaleX,
        scaleY = scaleY,
        transformOrigin = TransformOrigin.Center,
    )
}

internal fun <T> contentTween() = tween<T>(
    durationMillis = ContentAnimationDuration,
    easing = ContentMotionEasing,
)

private val legacyTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)

private val animatedLegacyTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
    textMotion = TextMotion.Animated,
)

@Preview(widthDp = 412, showBackground = true)
@Composable
private fun StoryItemPreview() {
    HarmonicTheme {
        StoryItem(
            model = SettingsStoryPreviewModel,
            style = StoryItemStyle(
                previewImageMode = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE,
                borderlessLargeImage = false,
                compact = false,
                showSummary = false,
                showFavicon = true,
                showPoints = true,
                compactPoints = false,
                includeTopLevelDomain = true,
                showCommentCount = true,
                showIndex = true,
                commentsOnLeft = false,
                tintCard = true,
                cardStyle = false,
                useHotnessIcon = false,
                preferredFont = "googlesansflexrounded",
                textSize = SettingsUtils.DEFAULT_STORY_TEXT_SIZE,
            ),
        )
    }
}
