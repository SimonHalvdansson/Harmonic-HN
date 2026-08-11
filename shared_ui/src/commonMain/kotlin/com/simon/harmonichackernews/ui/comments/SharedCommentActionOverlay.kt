package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.ui.common.SharedTransformOverlay
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

    SharedTransformOverlay(
        contentKey = comment.id,
        sourceBounds = state.sourceBounds,
        dismissRequestVersion = controller.commentActionDismissRequest,
        predictiveBackProgress = controller.commentActionPredictiveBackProgress,
        predictiveBackEdge = controller.commentActionPredictiveBackEdge,
        maxWidth = if (settings.isTablet) {
            HarmonicDimens.compose_comment_action_tablet_max_width
        } else {
            HarmonicDimens.compose_comment_action_max_width
        },
        horizontalPadding = HarmonicDimens.compose_comment_action_screen_padding_horizontal,
        verticalPadding = HarmonicDimens.compose_comment_action_screen_padding_vertical,
        shape = RoundedCornerShape(HarmonicDimens.compose_comment_action_corner_radius),
        containerColor = cardColor,
        onDismissRequest = controller::requestDismissCommentActions,
        onDismissAnimationFinished = controller::completeCommentActionDismiss,
    ) {
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

@Composable
private fun CommentActionCardContent(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
    comment: Comment,
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
            Icon(painterResource(Res.drawable.ic_account_circle), contentDescription = null)
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
                        CommentsComposeController.COMMENT_ACTION_UPVOTE,
                    enabled = !voteLoading,
                ) {
                    controller.listener.onCommentAction(
                        comment,
                        CommentsComposeController.COMMENT_ACTION_UPVOTE,
                    )
                }
                CommentActionIcon(
                    icon = if (downvoted) Res.drawable.ic_thumb_down_filled else Res.drawable.ic_thumb_down,
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
                    icon = Res.drawable.ic_thumbs_up_down_unvote,
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
                    icon = if (bookmarked) Res.drawable.ic_bookmark_filled else Res.drawable.ic_bookmark,
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
                    icon = if (favorited) Res.drawable.ic_star_filled else Res.drawable.ic_star,
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
            CommentActionIcon(Res.drawable.ic_content_copy, "Copy text") {
                controller.listener.onCommentAction(
                    comment,
                    CommentsComposeController.COMMENT_ACTION_COPY,
                )
            }
            CommentActionIcon(Res.drawable.ic_share, "Share link") {
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
