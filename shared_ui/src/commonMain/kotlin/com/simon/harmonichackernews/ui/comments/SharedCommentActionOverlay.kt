package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import com.simon.harmonichackernews.ui.common.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun SharedCommentActionOverlay(
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
    val snapshotRefreshKey = if (dismissRequest != 0 && !openingCompleted) 0 else dismissRequest
    val sourceCapture = rememberCommentActionLayerSnapshot(source?.contentLayer, 0)
    val targetUserCapture = rememberCommentActionLayerSnapshot(targetUserLayer, snapshotRefreshKey)
    val targetBodyCapture = rememberCommentActionLayerSnapshot(targetBodyLayer, snapshotRefreshKey)
    val targetSupplementaryCapture = rememberCommentActionLayerSnapshot(
        targetSupplementaryLayer,
        snapshotRefreshKey,
    )
    val snapshotsReady = source != null &&
        sourceCapture.isCurrent(0) &&
        targetContainer != null &&
        targetUserBounds != null &&
        targetBodyBounds != null &&
        targetSupplementaryBounds != null &&
        targetUserCapture.isCurrent(snapshotRefreshKey) &&
        targetBodyCapture.isCurrent(snapshotRefreshKey) &&
        targetSupplementaryCapture.isCurrent(snapshotRefreshKey)

    LaunchedEffect(comment.id, targetContainer, snapshotsReady, dismissRequest) {
        if (targetContainer == null || dismissRequest != 0 || openingStarted) return@LaunchedEffect
        if (source != null && !snapshotsReady) return@LaunchedEffect
        openingStarted = true
        if (source != null) {
            // Cover the still-live row before suppression, eliminating the opening handoff flash.
            overlayActive = true
            withFrameNanos { }
            controller.setCommentActionSourceCovered(true)
            drawOverlayShadows = true
            withFrameNanos { }
        } else {
            // Restored dialogs have no source layer, but suppression must still not precede draw.
            withFrameNanos { }
            controller.setCommentActionSourceCovered(true)
        }
        transformProgress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        if (source != null) {
            hideTargetContent = false
            drawOverlayShadows = false
            withFrameNanos { }
            overlayActive = false
        }
        openingCompleted = true
    }
    LaunchedEffect(dismissRequest, snapshotsReady, targetContainer) {
        if (dismissRequest == 0 || closingStarted || targetContainer == null) {
            return@LaunchedEffect
        }
        if (source != null && !snapshotsReady) return@LaunchedEffect
        closingStarted = true
        if (source != null) {
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

    val progress = transformProgress.value
    val predictiveEased = controller.commentActionPredictiveBackProgress
        .coerceIn(0f, 1f)
        .let { 1f - (1f - it) * (1f - it) }
    val backDirection = if (controller.commentActionPredictiveBackEdge == 1) -1f else 1f
    val backTranslationX = with(density) { 56.dp.toPx() } * predictiveEased * backDirection
    val backTranslationY = with(density) { 18.dp.toPx() } * predictiveEased
    val shape = RoundedCornerShape(HarmonicDimens.compose_comment_action_corner_radius)
    val sharedTransition = CommentActionSharedTransitionState(
        progress = progress,
        active = overlayActive,
        hideTargetContent = hideTargetContent,
        drawOverlayShadows = drawOverlayShadows,
        source = source,
        sourceSnapshot = sourceCapture.image,
        targetContainer = targetContainer,
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
    val modalGestures = Modifier.consumeCommentActionGestures()

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
                        alpha = 0.32f * progress * (1f - 0.55f * predictiveEased),
                    ),
                )
                .then(modalGestures)
                .clickable(
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
                    val backScale = 1f - 0.1f * predictiveEased
                    scaleX = fallbackScale * backScale
                    scaleY = fallbackScale * backScale
                    alpha = progress
                    translationX = backTranslationX
                    translationY = backTranslationY
                    transformOrigin = TransformOrigin(
                        if (backDirection > 0f) 0f else 1f,
                        0.5f,
                    )
                }
            } else if (predictiveEased > 0f) {
                Modifier.graphicsLayer {
                    val backScale = 1f - 0.1f * predictiveEased
                    scaleX = backScale
                    scaleY = backScale
                    translationX = backTranslationX
                    translationY = backTranslationY
                    transformOrigin = TransformOrigin(
                        if (backDirection > 0f) 0f else 1f,
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
                    .onGloballyPositioned { targetContainer = it.boundsInWindow() }
                    .then(fallbackPresentation)
                    .then(modalGestures)
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

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(HarmonicDimens.compose_comment_action_card_padding),
    ) {
        CommentActionTarget(CommentActionTargetElement.User) {
            Button(
                onClick = {
                    controller.listener.onCommentAction(
                        comment,
                        CommentMenuAction.USER,
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = HarmonicTheme.colors.overlayButton,
                    contentColor = Color.White,
                ),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.height(48.dp),
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
                        .heightIn(max = HarmonicDimens.compose_comment_action_text_max_height)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 6.dp, top = 14.dp, end = 6.dp, bottom = 14.dp),
                ) {
                    Text(
                        text = body,
                        color = HarmonicTheme.colors.storyNormal,
                        fontFamily = typography.family,
                        fontSize = settings.preferredTextSize.sp,
                        lineHeight = (settings.preferredTextSize * 1.34f).sp,
                        style = textStyle,
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
                            controller.listener.onCommentAction(
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
                            .height(56.dp),
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
            transitionSpec = { fadeIn(tween(150)).togetherWith(fadeOut(tween(150))) },
            label = "comment action icon",
        ) { visual ->
            if (visual.loading) {
                HarmonicLoadingIndicator(Modifier.size(28.dp))
            } else {
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

private data class CommentActionVisual(
    val icon: DrawableResource,
    val description: String,
    val loading: Boolean,
)

@Composable
private fun rememberCommentActionLayerSnapshot(
    layer: GraphicsLayer?,
    refreshKey: Int,
): CommentActionLayerSnapshot {
    // Preserve the previous bitmap while a dismiss refresh is recorded. Clearing it first leaves
    // one empty frame when dismissal interrupts the opening animation.
    var snapshot by remember(layer) { mutableStateOf(CommentActionLayerSnapshot()) }
    LaunchedEffect(layer, refreshKey) {
        val currentLayer = layer ?: return@LaunchedEffect
        withFrameNanos { }
        if (!currentLayer.isReleased && currentLayer.size.width > 0 && currentLayer.size.height > 0) {
            snapshot = CommentActionLayerSnapshot(
                image = currentLayer.toImageBitmap(),
                refreshKey = refreshKey,
            )
        }
    }
    return snapshot
}

private data class CommentActionLayerSnapshot(
    val image: ImageBitmap? = null,
    val refreshKey: Int? = null,
) {
    fun isCurrent(key: Int): Boolean = image != null && refreshKey == key
}

private fun Modifier.consumeCommentActionGestures(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
