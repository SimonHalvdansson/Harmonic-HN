package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.ui.common.consumeAllPointerGestures
import com.simon.harmonichackernews.ui.common.predictiveBackVisualProgress
import com.simon.harmonichackernews.ui.common.rememberGraphicsLayerSnapshot
import com.simon.harmonichackernews.ui.common.shouldUpdateRestingTargetGeometry
import androidx.compose.material3.ButtonDefaults
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.CommentMenuAction
import com.simon.harmonichackernews.ui.content.htmlAnnotatedString
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.AgePolicy
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun CommentActionOverlay(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
    hasAccount: Boolean,
    bookmarksEnabled: Boolean,
    textStyle: TextStyle,
    onOpenLink: (String) -> Unit,
) {
    val state = controller.commentActionOverlay ?: return
    val comment = state.comment
    val cardColor = if (settings.cardStyle) {
        HarmonicTheme.colors.surfaceContainerHigh
    } else {
        HarmonicTheme.colors.background
    }
    val source = state.sourceGeometry?.takeIf {
        it.contentLayer?.let { layer ->
            !layer.isReleased && layer.size.width > 0 && layer.size.height > 0
        } == true
    }
    val density = LocalDensity.current
    val transformProgress = remember(comment.id) { Animatable(0f) }
    var rootBounds by remember(comment.id) { mutableStateOf(Rect.Zero) }
    var targetContainer by remember(comment.id) { mutableStateOf<Rect?>(null) }
    var targetUserBounds by remember(comment.id) { mutableStateOf<Rect?>(null) }
    var targetBodyBounds by remember(comment.id) { mutableStateOf<Rect?>(null) }
    var targetSupplementaryBounds by remember(comment.id) { mutableStateOf<Rect?>(null) }
    var targetUserLayer by remember(comment.id) { mutableStateOf<GraphicsLayer?>(null) }
    var targetBodyLayer by remember(comment.id) { mutableStateOf<GraphicsLayer?>(null) }
    var targetSupplementaryLayer by remember(comment.id) { mutableStateOf<GraphicsLayer?>(null) }
    var overlayActive by remember(comment.id) { mutableStateOf(false) }
    var hideTargetContent by remember(comment.id) { mutableStateOf(source != null) }
    var drawOverlayShadows by remember(comment.id) { mutableStateOf(false) }
    var openingStarted by remember(comment.id) { mutableStateOf(false) }
    var openingCompleted by remember(comment.id) { mutableStateOf(false) }
    var closingStarted by remember(comment.id) { mutableStateOf(false) }
    val dismissRequest = controller.commentActionDismissRequest
    val updateRestingTargetGeometry = shouldUpdateRestingTargetGeometry(
        predictiveBackProgress = controller.commentActionPredictiveBackProgress,
        dismissRequestVersion = dismissRequest,
    )
    val snapshotRefreshKey = if (dismissRequest != 0 && !openingCompleted) 0 else dismissRequest
    val sourceCapture = rememberGraphicsLayerSnapshot(source?.contentLayer, 0)
    val targetUserCapture = rememberGraphicsLayerSnapshot(targetUserLayer, snapshotRefreshKey)
    val targetBodyCapture = rememberGraphicsLayerSnapshot(targetBodyLayer, snapshotRefreshKey)
    val targetSupplementaryCapture = rememberGraphicsLayerSnapshot(
        targetSupplementaryLayer,
        snapshotRefreshKey,
    )
    val snapshotsUnavailable = sourceCapture.isUnavailable(0) ||
        targetUserCapture.isUnavailable(snapshotRefreshKey) ||
        targetBodyCapture.isUnavailable(snapshotRefreshKey) ||
        targetSupplementaryCapture.isUnavailable(snapshotRefreshKey)
    val transitionSource = source?.takeUnless { snapshotsUnavailable }
    val snapshotsReady = transitionSource != null &&
        sourceCapture.isCurrent(0) &&
        targetContainer != null &&
        targetUserBounds != null &&
        targetBodyBounds != null &&
        targetSupplementaryBounds != null &&
        targetUserCapture.isCurrent(snapshotRefreshKey) &&
        targetBodyCapture.isCurrent(snapshotRefreshKey) &&
        targetSupplementaryCapture.isCurrent(snapshotRefreshKey)

    LaunchedEffect(
        comment.id,
        targetContainer,
        snapshotsReady,
        snapshotsUnavailable,
        dismissRequest,
    ) {
        if (targetContainer == null || dismissRequest != 0 || openingStarted) return@LaunchedEffect
        if (transitionSource != null && !snapshotsReady) return@LaunchedEffect
        openingStarted = true
        if (transitionSource != null) {
            // Cover the still-live row before suppression, eliminating the opening handoff flash.
            overlayActive = true
            withFrameNanos { }
            controller.setCommentActionSourceCovered(true)
            drawOverlayShadows = true
            withFrameNanos { }
        } else {
            // Restored dialogs have no source layer, but suppression must still not precede draw.
            hideTargetContent = false
            withFrameNanos { }
            controller.setCommentActionSourceCovered(true)
        }
        transformProgress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        if (transitionSource != null) {
            hideTargetContent = false
            drawOverlayShadows = false
            withFrameNanos { }
            overlayActive = false
        }
        openingCompleted = true
    }
    LaunchedEffect(dismissRequest, snapshotsReady, snapshotsUnavailable, targetContainer) {
        if (dismissRequest == 0 || closingStarted || targetContainer == null) {
            return@LaunchedEffect
        }
        if (transitionSource != null && !snapshotsReady) return@LaunchedEffect
        closingStarted = true
        if (transitionSource != null) {
            // Put an identical progress-one overlay over the dialog before hiding live content.
            overlayActive = true
            withFrameNanos { }
            hideTargetContent = true
            drawOverlayShadows = true
            withFrameNanos { }
        }
        transformProgress.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        controller.setCommentActionSourceCovered(false)
        drawOverlayShadows = false
        withFrameNanos { }
        controller.completeCommentActionDismiss()
    }
    LaunchedEffect(dismissRequest) {
        if (dismissRequest == 0) return@LaunchedEffect
        delay(CommentActionDismissFallbackDelayMillis)
        if (
            !closingStarted &&
            controller.commentActionDismissRequest == dismissRequest
        ) {
            closingStarted = true
            controller.setCommentActionSourceCovered(false)
            controller.completeCommentActionDismiss()
        }
    }

    val progress = transformProgress.value
    val predictiveVisualProgress = predictiveBackVisualProgress(
        predictiveBackProgress = controller.commentActionPredictiveBackProgress,
        transformProgress = progress,
    )
    val backDirection = if (controller.commentActionPredictiveBackEdge == 1) -1f else 1f
    val backScale = 1f - 0.1f * predictiveVisualProgress
    val backPivotFractionX = if (backDirection > 0f) 0f else 1f
    val backTranslationX =
        with(density) { 56.dp.toPx() } * predictiveVisualProgress * backDirection
    val backTranslationY = with(density) { 18.dp.toPx() } * predictiveVisualProgress
    val shape = RoundedCornerShape(HarmonicDimens.compose_comment_action_corner_radius)
    val sharedTransition = CommentActionSharedTransitionState(
        progress = progress,
        active = overlayActive,
        hideTargetContent = hideTargetContent,
        drawOverlayShadows = drawOverlayShadows,
        source = transitionSource,
        sourceSnapshot = sourceCapture.image,
        targetContainer = targetContainer,
        predictiveBackProgress = controller.commentActionPredictiveBackProgress,
        predictiveBackEdge = controller.commentActionPredictiveBackEdge,
        rootBounds = rootBounds,
        targetBounds = { element ->
            when (element) {
                CommentActionTargetElement.User -> targetUserBounds
                CommentActionTargetElement.Body -> targetBodyBounds
                CommentActionTargetElement.Supplementary -> targetSupplementaryBounds
            }
        },
        targetSnapshot = { element ->
            when (element) {
                CommentActionTargetElement.User -> targetUserCapture.image
                CommentActionTargetElement.Body -> targetBodyCapture.image
                CommentActionTargetElement.Supplementary -> targetSupplementaryCapture.image
            }
        },
        updateTargetBounds = { element, bounds ->
            if (updateRestingTargetGeometry) {
                when (element) {
                    CommentActionTargetElement.User -> if (targetUserBounds != bounds) {
                        targetUserBounds = bounds
                    }
                    CommentActionTargetElement.Body -> if (targetBodyBounds != bounds) {
                        targetBodyBounds = bounds
                    }
                    CommentActionTargetElement.Supplementary ->
                        if (targetSupplementaryBounds != bounds) {
                            targetSupplementaryBounds = bounds
                        }
                }
            }
        },
        updateTargetLayer = { element, layer ->
            when (element) {
                CommentActionTargetElement.User -> if (targetUserLayer !== layer) {
                    targetUserLayer = layer
                }
                CommentActionTargetElement.Body -> if (targetBodyLayer !== layer) {
                    targetBodyLayer = layer
                }
                CommentActionTargetElement.Supplementary ->
                    if (targetSupplementaryLayer !== layer) {
                        targetSupplementaryLayer = layer
                    }
            }
        },
    )
    val modalGestures = Modifier.consumeAllPointerGestures()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootBounds = it.boundsInWindow() },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = 0.32f * progress * (1f - 0.55f * predictiveVisualProgress),
                    ),
                )
                .then(modalGestures)
                .clickable(
                    enabled = dismissRequest == 0,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = controller::requestDismissCommentActions,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    horizontal = HarmonicDimens.compose_comment_action_screen_padding_horizontal,
                    vertical = HarmonicDimens.compose_comment_action_screen_padding_vertical,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val fallbackPresentation = if (source == null) {
                Modifier.graphicsLayer {
                    val fallbackScale = 0.96f + 0.04f * progress
                    scaleX = fallbackScale * backScale
                    scaleY = fallbackScale * backScale
                    alpha = progress
                    translationX = backTranslationX
                    translationY = backTranslationY
                    transformOrigin = TransformOrigin(
                        backPivotFractionX,
                        0.5f,
                    )
                }
            } else if (predictiveVisualProgress > 0f) {
                Modifier.graphicsLayer {
                    scaleX = backScale
                    scaleY = backScale
                    translationX = backTranslationX
                    translationY = backTranslationY
                    transformOrigin = TransformOrigin(
                        backPivotFractionX,
                        0.5f,
                    )
                }
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .widthIn(
                        max = if (settings.isTablet) {
                            HarmonicDimens.compose_comment_action_tablet_max_width
                        } else {
                            HarmonicDimens.compose_comment_action_max_width
                        },
                    )
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        if (updateRestingTargetGeometry) {
                            targetContainer = it.boundsInWindow()
                        }
                    }
                    .then(fallbackPresentation)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                CompositionLocalProvider(
                    LocalCommentActionSharedTransition provides
                        sharedTransition.takeIf { source != null },
                ) {
                    CommentActionContainerBackground(cardColor)
                    Box(Modifier.fillMaxWidth().clip(shape)) {
                        CommentActionCardContent(
                            controller = controller,
                            settings = settings,
                            comment = comment,
                            hasAccount = hasAccount,
                            bookmarksEnabled = bookmarksEnabled,
                            cardColor = cardColor,
                            textStyle = textStyle,
                            onOpenLink = onOpenLink,
                        )
                    }
                }
            }
        }
        CommentActionTransitionOverlay(sharedTransition, cardColor)
    }
}

@Composable
private fun CommentActionCardContent(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
    comment: PortableCommentItem,
    hasAccount: Boolean,
    bookmarksEnabled: Boolean,
    cardColor: Color,
    textStyle: TextStyle,
    onOpenLink: (String) -> Unit,
) {
    val bookmarked = remember(controller.contentVersion, comment.id, bookmarksEnabled) {
        bookmarksEnabled && controller.isBookmarked(comment.id)
    }
    val favorited = remember(controller.contentVersion, comment.id) {
        controller.isFavorited(comment.id)
    }
    val upvoted = remember(controller.contentVersion, comment.id) {
        controller.isUpvoted(comment.id, isComment = true)
    }
    val downvoted = !upvoted && comment.id in controller.commentActionDownvotedIds
    val voteLoading = controller.commentActionVoteLoadingId == comment.id
    val favoriteLoading = controller.commentActionFavoriteLoadingId == comment.id
    val canReply = hasAccount && !AgePolicy.isOlderThanTwoWeeks(comment.time)
    val typography = rememberContentTypography(settings.font, settings.preferredTextSize)
    val commentTextSize = settings.preferredTextSize - 1f
    val userLabel = buildString {
        append(comment.by?.takeIf(String::isNotBlank) ?: "Unknown user")
        if (comment.by == controller.story.by) append(" (OP)")
    }
    val linkListener = remember(onOpenLink) {
        LinkInteractionListener { link ->
            val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
            onOpenLink(url)
        }
    }
    val linkColor = HarmonicTheme.colors.link
    val body = remember(comment.expandedAnchorText, linkColor, linkListener) {
        htmlAnnotatedString(comment.expandedAnchorText.orEmpty(), linkColor, linkListener)
    }
    val bodyScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(HarmonicDimens.compose_comment_action_card_padding),
    ) {
        CommentActionTarget(CommentActionTargetElement.User) {
            Button(
                onClick = {
                    controller.dismissCommentActionsThen(
                        comment,
                        CommentMenuAction.USER,
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = HarmonicTheme.colors.overlayButton,
                    contentColor = Color.White,
                ),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.height(40.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_account_circle), contentDescription = null)
                Text(
                    userLabel,
                    modifier = Modifier.padding(start = 8.dp),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        CommentActionTarget(
            element = CommentActionTargetElement.Body,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = HarmonicDimens.compose_comment_action_text_max_height),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(bodyScrollState)
                            .padding(start = 6.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
                    ) {
                        Text(
                            text = body,
                            color = HarmonicTheme.colors.storyNormal,
                            fontFamily = typography.family,
                            fontSize = commentTextSize.sp,
                            lineHeight = (commentTextSize * 1.34f).sp,
                            style = textStyle,
                        )
                    }
                    CommentActionTextScrollDecorations(
                        state = bodyScrollState,
                        containerColor = cardColor,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }

        CommentActionTarget(
            element = CommentActionTargetElement.Supplementary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = HarmonicTheme.colors.commentDivider.copy(alpha = 0.45f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasAccount) {
                        CommentActionIcon(
                            icon = if (upvoted) Res.drawable.ic_thumb_up_filled else Res.drawable.ic_thumb_up,
                            description = if (upvoted) "Upvoted" else "Vote up",
                            loading = voteLoading &&
                                controller.commentActionVoteLoadingAction ==
                                CommentMenuAction.UPVOTE,
                            enabled = !voteLoading,
                        ) {
                            controller.listener.onCommentAction(
                                comment,
                                CommentMenuAction.UPVOTE,
                            )
                        }
                        CommentActionIcon(
                            icon = if (downvoted) Res.drawable.ic_thumb_down_filled else Res.drawable.ic_thumb_down,
                            description = if (downvoted) "Downvoted" else "Vote down",
                            loading = voteLoading &&
                                controller.commentActionVoteLoadingAction ==
                                CommentMenuAction.DOWNVOTE,
                            enabled = !voteLoading,
                        ) {
                            controller.listener.onCommentAction(
                                comment,
                                CommentMenuAction.DOWNVOTE,
                            )
                        }
                        CommentActionIcon(
                            icon = Res.drawable.ic_thumbs_up_down_unvote,
                            description = "Unvote",
                            loading = voteLoading &&
                                controller.commentActionVoteLoadingAction ==
                                CommentMenuAction.UNVOTE,
                            enabled = !voteLoading,
                        ) {
                            controller.listener.onCommentAction(
                                comment,
                                CommentMenuAction.UNVOTE,
                            )
                        }
                    }
                    if (bookmarksEnabled) {
                        CommentActionIcon(
                            icon = if (bookmarked) Res.drawable.ic_bookmark_filled else Res.drawable.ic_bookmark,
                            description = if (bookmarked) "Remove bookmark" else "Bookmark",
                        ) {
                            controller.listener.onCommentAction(
                                comment,
                                CommentMenuAction.BOOKMARK,
                            )
                        }
                    }
                    if (hasAccount) {
                        CommentActionIcon(
                            icon = if (favorited) Res.drawable.ic_star_filled else Res.drawable.ic_star,
                            description = if (favorited) "Remove favorite" else "Favorite",
                            loading = favoriteLoading,
                            enabled = !favoriteLoading,
                        ) {
                            controller.listener.onCommentAction(
                                comment,
                                CommentMenuAction.FAVORITE,
                            )
                        }
                    }
                    CommentActionIcon(Res.drawable.ic_content_copy, "Copy text") {
                        controller.listener.onCommentAction(
                            comment,
                            CommentMenuAction.COPY,
                        )
                    }
                    CommentActionIcon(Res.drawable.ic_share, "Share link") {
                        controller.listener.onCommentAction(
                            comment,
                            CommentMenuAction.SHARE,
                        )
                    }
                }

                if (canReply) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            controller.dismissCommentActionsThen(
                                comment,
                                CommentMenuAction.REPLY,
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HarmonicTheme.colors.overlayButton,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(painterResource(Res.drawable.ic_reply), contentDescription = null)
                        Text(
                            "Reply",
                            modifier = Modifier.padding(start = 8.dp),
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
private fun CommentActionTextScrollDecorations(
    state: ScrollState,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    val maxValue = state.maxValue
    val viewportSize = state.viewportSize
    if (maxValue <= 0 || viewportSize <= 0) return

    val thumbColor = HarmonicTheme.colors.storyDisabled.copy(alpha = 0.55f)
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        val widthPx = with(density) { 3.dp.toPx() }
        val endPaddingPx = with(density) { 1.dp.toPx() }
        val verticalPaddingPx = with(density) { 8.dp.toPx() }
        val minimumHeightPx = with(density) { 24.dp.toPx() }
        val fadeLengthPx = with(density) {
            HarmonicDimens.compose_comment_action_text_fade_length.toPx()
        }.coerceAtMost(size.height / 2f)
        val topFadeStrength = (state.value / fadeLengthPx).coerceIn(0f, 1f)
        val bottomFadeStrength = ((maxValue - state.value) / fadeLengthPx).coerceIn(0f, 1f)

        if (topFadeStrength > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        containerColor.copy(alpha = containerColor.alpha * topFadeStrength),
                        containerColor.copy(alpha = 0f),
                    ),
                    startY = 0f,
                    endY = fadeLengthPx,
                ),
                size = androidx.compose.ui.geometry.Size(size.width, fadeLengthPx),
            )
        }
        if (bottomFadeStrength > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        containerColor.copy(alpha = 0f),
                        containerColor.copy(alpha = containerColor.alpha * bottomFadeStrength),
                    ),
                    startY = size.height - fadeLengthPx,
                    endY = size.height,
                ),
                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fadeLengthPx),
                size = androidx.compose.ui.geometry.Size(size.width, fadeLengthPx),
            )
        }

        val trackHeight = (size.height - verticalPaddingPx * 2f).coerceAtLeast(0f)
        val contentHeight = viewportSize + maxValue
        val visibleFraction = viewportSize.toFloat() / contentHeight
        val thumbHeight = (trackHeight * visibleFraction)
            .coerceIn(minimumHeightPx.coerceAtMost(trackHeight), trackHeight)
        val scrollFraction = state.value.toFloat() / maxValue
        val top = verticalPaddingPx + (trackHeight - thumbHeight) * scrollFraction
        drawRoundRect(
            color = thumbColor,
            topLeft = androidx.compose.ui.geometry.Offset(
                size.width - widthPx - endPaddingPx,
                top,
            ),
            size = androidx.compose.ui.geometry.Size(widthPx, thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(widthPx / 2f),
        )
    }
}

@Composable
private fun RowScope.CommentActionIcon(
    icon: DrawableResource,
    description: String,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = CommentActionVisual(icon, description, loading),
            transitionSpec = {
                (
                    fadeIn(
                        tween(
                            durationMillis = CommentActionIconSwapInDurationMillis,
                            delayMillis = CommentActionIconSwapOutDurationMillis,
                        ),
                    ) + scaleIn(
                        animationSpec = tween(
                            durationMillis = CommentActionIconSwapInDurationMillis,
                            delayMillis = CommentActionIconSwapOutDurationMillis,
                        ),
                        initialScale = CommentActionIconSwapMinScale,
                    )
                ).togetherWith(
                    fadeOut(tween(CommentActionIconSwapOutDurationMillis)) + scaleOut(
                        animationSpec = tween(CommentActionIconSwapOutDurationMillis),
                        targetScale = CommentActionIconSwapMinScale,
                    ),
                )
            },
            contentAlignment = Alignment.Center,
            label = "comment action icon",
        ) { visual ->
            if (visual.loading) {
                HarmonicLoadingIndicator(Modifier.size(28.dp))
            } else {
                CommentsTooltip(visual.description) {
                    IconButton(onClick = onClick, enabled = enabled) {
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

private const val CommentActionIconSwapOutDurationMillis = 90
private const val CommentActionIconSwapInDurationMillis = 150
private const val CommentActionIconSwapMinScale = 0.72f
private const val CommentActionDismissFallbackDelayMillis = 460L

private data class CommentActionVisual(
    val icon: DrawableResource,
    val description: String,
    val loading: Boolean,
)
