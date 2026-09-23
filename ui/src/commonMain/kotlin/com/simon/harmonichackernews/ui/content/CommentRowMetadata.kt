package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.settings.UserAvatarOptions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

/** Preserve the legacy white label when readable, including custom and dynamic accent colors. */
internal fun commentCountContentColor(background: Color): Color =
    if (1.05f / (background.luminance() + 0.05f) >= 4.5f) Color.White else Color.Black

@Composable
internal fun CommentMeta(
    author: String,
    avatarsEnabled: Boolean,
    avatarOptions: UserAvatarOptions,
    age: String,
    byOp: Boolean,
    byUser: Boolean,
    userTag: String?,
    hiddenPreview: String?,
    subtreeReplyCount: Int?,
    showHiddenReplyCount: Boolean,
    emphasized: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    animateChanges: Boolean,
    isNew: Boolean = false,
) {
    val colors = HarmonicTheme.colors
    val metaColor = when {
        byUser -> colors.accent
        byOp -> colors.link
        emphasized -> colors.contentPrimary
        else -> colors.mutedText
    }
    val metaRadius by animateDpAsState(
        if (emphasized) 12.dp else 0.dp,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "comment meta radius",
    )
    val metaHorizontalPadding by animateDpAsState(
        if (emphasized) 7.dp else 0.dp,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "comment meta horizontal padding",
    )
    val metaVerticalPadding by animateDpAsState(
        if (emphasized) 2.dp else 0.dp,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "comment meta vertical padding",
    )
    val metaBackground by animateColorAsState(
        colors.surfaceContainerHighest.copy(alpha = if (emphasized) 1f else 0f),
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "comment meta background",
    )
    val metaBorderAlpha by animateFloatAsState(
        if (emphasized) 1f else 0f,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "comment meta border",
    )
    val hiddenReplyCountAlpha by animateFloatAsState(
        if (showHiddenReplyCount) 1f else 0f,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "hidden reply count",
    )
    val metaShape = RoundedCornerShape(metaRadius)
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = avatarsEnabled,
            transitionSpec = {
                (fadeIn(if (animateChanges) contentTween() else snap()) togetherWith
                    fadeOut(if (animateChanges) contentTween() else snap())).using(
                    SizeTransform(clip = true) { _, _ -> if (animateChanges) contentTween() else snap() },
                )
            },
            contentAlignment = Alignment.CenterStart,
            label = "comment user avatar",
        ) { showAvatars ->
            if (!showAvatars) {
                Box(Modifier.size(0.dp))
            } else {
                UserAvatar(author, Modifier.padding(end = 6.dp).size(22.dp), avatarOptions)
            }
        }
        Row(
            modifier = Modifier
                .clip(metaShape)
                .background(metaBackground)
                .border(1.dp, colors.commentDivider.copy(alpha = metaBorderAlpha), metaShape)
                .padding(horizontal = metaHorizontalPadding, vertical = metaVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(author, color = metaColor, fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (byOp) {
                Text(
                    "OP",
                    modifier = Modifier.padding(start = 3.dp).height(14.dp).clip(RoundedCornerShape(3.dp))
                        .background(metaColor.copy(alpha = 0.14f)).padding(horizontal = 3.dp),
                    color = metaColor,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    style = compactCommentTextStyle,
                )
            }
            Text(age, modifier = Modifier.padding(start = 4.dp), color = metaColor, fontFamily = fontFamily, fontSize = 13.sp)
            if (!userTag.isNullOrBlank()) {
                Text(
                    " • $userTag",
                    color = metaColor,
                    fontFamily = fontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        if (hiddenPreview != null) {
            Text(
                hiddenPreview,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                color = colors.mutedText,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Box(Modifier.weight(1f))
        }
        AnimatedVisibility(
            visible = isNew,
            modifier = Modifier.offset(y = (-2).dp),
            enter = fadeIn(if (animateChanges) tween(120) else snap()),
            exit = fadeOut(if (animateChanges) tween(120) else snap()),
        ) {
            Box(
                Modifier.size(6.dp)
                    .background(colors.accent, RoundedCornerShape(50))
                    .semantics { contentDescription = "New comment" },
            )
        }
        val replyCount: @Composable () -> Unit = {
            Text(
                "+${subtreeReplyCount ?: 0}",
                modifier = Modifier
                    .padding(start = if (isNew) 6.dp else 0.dp)
                    .graphicsLayer(alpha = hiddenReplyCountAlpha)
                    .clip(RoundedCornerShape(7.dp))
                    .background(colors.commentCountIndicator)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .then(
                        if (showHiddenReplyCount) Modifier else Modifier.clearAndSetSemantics { },
                    ),
                color = commentCountContentColor(colors.commentCountIndicator),
                fontFamily = fontFamily,
                fontSize = 12.sp,
                style = compactCommentTextStyle,
            )
        }
        if (isNew) {
            AnimatedVisibility(
                // Move the fade layer itself. Offsetting its child draws above the layer's
                // offscreen buffer and clips the pill while alpha is below one.
                modifier = Modifier.offset(y = (-2).dp),
                visible = showHiddenReplyCount && subtreeReplyCount != null,
                enter = fadeIn(contentTween()) + expandHorizontally(contentTween(), expandFrom = Alignment.End, clip = true),
                exit = fadeOut(contentTween()) + shrinkHorizontally(contentTween(), shrinkTowards = Alignment.End, clip = true),
            ) { replyCount() }
        } else if (subtreeReplyCount != null) {
            // Older comments retain the original fixed-width, opacity-only count animation.
            Box(Modifier.offset(y = (-2).dp)) { replyCount() }
        }
    }
}
