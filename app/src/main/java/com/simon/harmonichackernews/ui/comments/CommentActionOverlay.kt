package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import kotlin.math.max

private const val TRANSFORM_DURATION_MS = 280
private const val PREDICTIVE_BACK_MAX_TRANSLATION_X_DP = 56f
private const val PREDICTIVE_BACK_MAX_TRANSLATION_Y_DP = 18f

/**
 * Compose replacement for `comment_action_overlay.xml`. The card is laid out at its final size,
 * then transformed from the long-pressed comment bounds so the enter and return animations keep
 * the container-transform feel without a parallel View hierarchy.
 */
@Composable
internal fun CommentActionOverlay(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
) {
    val state = controller.commentActionOverlay ?: return
    val comment = state.comment
    val density = LocalDensity.current
    val transformProgress = remember(comment.id) { Animatable(0f) }
    var targetBounds by remember(comment.id) { mutableStateOf<Rect?>(null) }
    val dismissRequest = controller.commentActionDismissRequest

    LaunchedEffect(comment.id, targetBounds) {
        if (targetBounds != null && dismissRequest == 0) {
            transformProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(TRANSFORM_DURATION_MS, easing = FastOutSlowInEasing),
            )
        }
    }
    LaunchedEffect(dismissRequest) {
        if (dismissRequest <= 0) return@LaunchedEffect
        transformProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(TRANSFORM_DURATION_MS, easing = FastOutSlowInEasing),
        )
        controller.completeCommentActionDismiss()
    }

    val progress = transformProgress.value
    val predictiveProgress = controller.commentActionPredictiveBackProgress
    val predictiveEased = 1f - (1f - predictiveProgress) * (1f - predictiveProgress)
    val target = targetBounds
    val source = state.sourceBounds
    val startScaleX = if (target != null && source != null && target.width > 0f) {
        (source.width / target.width).coerceIn(0.08f, 1.15f)
    } else {
        0.96f
    }
    val startScaleY = if (target != null && source != null && target.height > 0f) {
        (source.height / target.height).coerceIn(0.08f, 1.15f)
    } else {
        0.96f
    }
    val startTranslationX = if (target != null && source != null) {
        source.center.x - target.center.x
    } else {
        0f
    }
    val startTranslationY = if (target != null && source != null) {
        source.center.y - target.center.y
    } else {
        0f
    }
    val backDirection = if (controller.commentActionPredictiveBackEdge == 1) -1f else 1f
    val backTranslationX = with(density) {
        PREDICTIVE_BACK_MAX_TRANSLATION_X_DP.dp.toPx()
    } * predictiveEased * backDirection
    val backTranslationY = with(density) {
        PREDICTIVE_BACK_MAX_TRANSLATION_Y_DP.dp.toPx()
    } * predictiveEased
    val cardRadius = dimensionResource(R.dimen.compose_comment_action_corner_radius)
    val cardColor = if (settings.cardStyle) {
        HarmonicTheme.colors.surfaceContainerHigh
    } else {
        HarmonicTheme.colors.background
    }

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
                onClick = controller::requestDismissCommentActions,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    horizontal = dimensionResource(
                        R.dimen.compose_comment_action_screen_padding_horizontal,
                    ),
                    vertical = dimensionResource(
                        R.dimen.compose_comment_action_screen_padding_vertical,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(
                        max = dimensionResource(
                            if (settings.isTablet) {
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
                        alpha = if (source == null) progress else max(0.7f, progress)
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (backDirection > 0f) 0f else 1f,
                            pivotFractionY = 0.5f,
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(cardRadius),
                color = cardColor,
                shadowElevation = 8.dp,
            ) {
                CommentActionCardContent(
                    controller = controller,
                    settings = settings,
                    comment = comment,
                )
            }
        }
    }
}

@Composable
private fun CommentActionCardContent(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
    comment: Comment,
) {
    val context = LocalContext.current
    val hasAccount = AccountUtils.hasAccountDetails(context)
    val bookmarksEnabled = SettingsUtils.shouldUseBookmarks(context)
    val bookmarked = remember(controller.contentVersion, comment.id) {
        bookmarksEnabled && Utils.isBookmarked(context, comment.id)
    }
    val favorited = remember(controller.contentVersion, comment.id) {
        Utils.isFavorited(context, comment.id)
    }
    val upvoted = remember(controller.contentVersion, comment.id) {
        Utils.isUpvoted(context, comment.id, true)
    }
    val downvoted = !upvoted && comment.id in controller.commentActionDownvotedIds
    val voteLoading = controller.commentActionVoteLoadingId == comment.id
    val favoriteLoading = controller.commentActionFavoriteLoadingId == comment.id
    val canReply = hasAccount && !Utils.timeInSecondsMoreThanTwoWeeksAgo(comment.time)
    val typography = rememberContentTypography(settings.font, settings.preferredTextSize)
    val userLabel = buildString {
        append(comment.by?.takeIf(String::isNotBlank) ?: "Unknown user")
        if (comment.by == controller.story.by) append(" (OP)")
    }
    val linkStyles = TextLinkStyles(
        style = androidx.compose.ui.text.SpanStyle(
            color = HarmonicTheme.colors.link,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val linkListener = LinkInteractionListener { link ->
        val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
        Utils.openLinkMaybeHN(context, url)
    }
    val body = remember(comment.text) {
        runCatching {
            AnnotatedString.fromHtml(
                comment.expandedAnchorText.orEmpty(),
                linkStyles,
                linkListener,
            )
        }.getOrElse { AnnotatedString(android.text.Html.fromHtml(comment.text.orEmpty(), 0).toString()) }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(dimensionResource(R.dimen.compose_comment_action_card_padding)),
    ) {
        Button(
            onClick = {
                controller.listener.onCommentAction(
                    comment,
                    CommentsComposeController.COMMENT_ACTION_USER,
                )
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = HarmonicTheme.colors.overlayButton,
                contentColor = Color.White,
            ),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.height(48.dp),
        ) {
            Icon(painterResource(R.drawable.ic_account_circle), contentDescription = null)
            Text(
                userLabel,
                modifier = Modifier.padding(start = 8.dp),
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }

        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = dimensionResource(R.dimen.compose_comment_action_text_max_height))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 6.dp, top = 14.dp, end = 6.dp, bottom = 14.dp),
            ) {
                Text(
                    text = body,
                    color = HarmonicTheme.colors.storyNormal,
                    fontFamily = typography.family,
                    fontSize = settings.preferredTextSize.sp,
                    lineHeight = (settings.preferredTextSize * 1.34f).sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)),
                )
            }
        }

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
                    icon = if (upvoted) R.drawable.ic_thumb_up_filled else R.drawable.ic_thumb_up,
                    description = if (upvoted) "Upvoted" else "Vote up",
                    loading = voteLoading &&
                        controller.commentActionVoteLoadingAction ==
                        CommentsComposeController.COMMENT_ACTION_UPVOTE,
                    enabled = !voteLoading,
                ) {
                    controller.listener.onCommentAction(
                        comment,
                        CommentsComposeController.COMMENT_ACTION_UPVOTE,
                    )
                }
                CommentActionIcon(
                    icon = if (downvoted) R.drawable.ic_thumb_down_filled else R.drawable.ic_thumb_down,
                    description = if (downvoted) "Downvoted" else "Vote down",
                    loading = voteLoading &&
                        controller.commentActionVoteLoadingAction ==
                        CommentsComposeController.COMMENT_ACTION_DOWNVOTE,
                    enabled = !voteLoading,
                ) {
                    controller.listener.onCommentAction(
                        comment,
                        CommentsComposeController.COMMENT_ACTION_DOWNVOTE,
                    )
                }
                CommentActionIcon(
                    icon = R.drawable.ic_thumbs_up_down_unvote,
                    description = "Unvote",
                    loading = voteLoading &&
                        controller.commentActionVoteLoadingAction ==
                        CommentsComposeController.COMMENT_ACTION_UNVOTE,
                    enabled = !voteLoading,
                ) {
                    controller.listener.onCommentAction(
                        comment,
                        CommentsComposeController.COMMENT_ACTION_UNVOTE,
                    )
                }
            }
            if (bookmarksEnabled) {
                CommentActionIcon(
                    icon = if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                    description = if (bookmarked) "Remove bookmark" else "Bookmark",
                ) {
                    controller.listener.onCommentAction(
                        comment,
                        CommentsComposeController.COMMENT_ACTION_BOOKMARK,
                    )
                }
            }
            if (hasAccount) {
                CommentActionIcon(
                    icon = if (favorited) R.drawable.ic_star_filled else R.drawable.ic_star,
                    description = if (favorited) "Remove favorite" else "Favorite",
                    loading = favoriteLoading,
                    enabled = !favoriteLoading,
                ) {
                    controller.listener.onCommentAction(
                        comment,
                        CommentsComposeController.COMMENT_ACTION_FAVORITE,
                    )
                }
            }
            CommentActionIcon(R.drawable.ic_content_copy, "Copy text") {
                controller.listener.onCommentAction(
                    comment,
                    CommentsComposeController.COMMENT_ACTION_COPY,
                )
            }
            CommentActionIcon(R.drawable.ic_share, "Share link") {
                controller.listener.onCommentAction(
                    comment,
                    CommentsComposeController.COMMENT_ACTION_SHARE,
                )
            }
        }

        if (canReply) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    controller.listener.onCommentAction(
                        comment,
                        CommentsComposeController.COMMENT_ACTION_REPLY,
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
                Icon(painterResource(R.drawable.ic_reply), contentDescription = null)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.CommentActionIcon(
    icon: Int,
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
            targetState = loading,
            transitionSpec = {
                (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.72f))
                    .togetherWith(fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.72f))
            },
            label = "comment action icon",
        ) { isLoading ->
            if (isLoading) {
                LoadingIndicator(Modifier.size(28.dp))
            } else {
                IconButton(onClick = onClick, enabled = enabled) {
                    Icon(
                        painterResource(icon),
                        contentDescription = description,
                        tint = HarmonicTheme.colors.drawable,
                    )
                }
            }
        }
    }
}

@Preview(name = "Comment actions phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Comment actions tablet", device = Devices.TABLET, showBackground = true)
@Composable
private fun CommentActionCardPreview() {
    val comment = remember {
        Comment().apply {
            id = 123
            by = "alephnerd"
            text = "So has Australia, the EU, and the UK.<p>That said, being a signatory has limited impact as long as it isn't ratified.</p>"
            time = (System.currentTimeMillis() / 1000L).toInt()
        }
    }
    HarmonicTheme {
        Surface(color = HarmonicTheme.colors.surfaceContainerHigh) {
            Column(Modifier.padding(18.dp)) {
                Button(onClick = {}) {
                    Icon(painterResource(R.drawable.ic_account_circle), null)
                    Text("alephnerd", Modifier.padding(start = 8.dp))
                }
                Text(
                    android.text.Html.fromHtml(comment.text, 0).toString(),
                    Modifier.padding(vertical = 14.dp),
                    color = HarmonicTheme.colors.storyNormal,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                )
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(
                        R.drawable.ic_thumb_up,
                        R.drawable.ic_thumb_down,
                        R.drawable.ic_thumbs_up_down_unvote,
                        R.drawable.ic_bookmark,
                        R.drawable.ic_star,
                        R.drawable.ic_content_copy,
                        R.drawable.ic_share,
                    ).forEach { icon -> Icon(painterResource(icon), null, Modifier.padding(10.dp)) }
                }
            }
        }
    }
}
