package com.simon.harmonichackernews.ui.content

import androidx.compose.material3.MaterialTheme
import com.simon.harmonichackernews.settings.UserAvatarOptions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

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
    val colors = MaterialTheme.colorScheme
    val metaColor = when {
        byUser -> colors.primary
        byOp -> colors.primary
        emphasized -> colors.onSurface
        else -> colors.onSurfaceVariant
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
        if (emphasized) 1.5.dp else 0.dp,
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
    val hiddenReplyCountProgress by animateFloatAsState(
        if (showHiddenReplyCount) 1f else 0f,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "hidden reply count",
    )
    var hiddenReplyCountWidth by remember { mutableIntStateOf(0) }
    // Keep the pill whole while it fades. Release its space only after it is invisible;
    // the reverse transition reserves the full space before revealing it again.
    val replyCountWidthProgress = (hiddenReplyCountProgress * 2f).coerceIn(0f, 1f)
    val replyCountAlpha = if (isNew) {
        (hiddenReplyCountProgress * 2f - 1f).coerceIn(0f, 1f)
    } else hiddenReplyCountProgress
    val layoutDirection = LocalLayoutDirection.current
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
                .border(1.dp, colors.outlineVariant.copy(alpha = metaBorderAlpha), metaShape)
                .padding(horizontal = metaHorizontalPadding, vertical = metaVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(author, color = metaColor, fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (byOp) CommentAuthorBadge("OP", metaColor, fontFamily)
            if (byUser) CommentAuthorBadge("YOU", metaColor, fontFamily)
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
                color = colors.onSurfaceVariant,
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
            modifier = Modifier.offset(y = (-2).dp).graphicsLayer {
                if (isNew && subtreeReplyCount != null) {
                    // Row widths are whole pixels. Preserve the fractional travel on the
                    // dot itself so its last movement does not snap to a layout pixel.
                    val width = hiddenReplyCountWidth * replyCountWidthProgress
                    val rounding = width.roundToInt() - width
                    translationX = if (layoutDirection == LayoutDirection.Ltr) rounding else -rounding
                } else {
                    translationX = 0f
                }
            },
            enter = fadeIn(if (animateChanges) tween(120) else snap()),
            exit = fadeOut(if (animateChanges) tween(120) else snap()),
        ) {
            Box(
                Modifier.size(6.dp)
                    .background(colors.primary, RoundedCornerShape(50))
                    .semantics { contentDescription = "New comment" },
            )
        }
        val replyCount: @Composable () -> Unit = {
            Text(
                "+${subtreeReplyCount ?: 0}",
                modifier = Modifier
                    .padding(start = if (isNew) 6.dp else 0.dp)
                    .graphicsLayer { alpha = replyCountAlpha }
                    .clip(RoundedCornerShape(7.dp))
                    .background(colors.tertiary)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .then(
                        if (showHiddenReplyCount) Modifier else Modifier.clearAndSetSemantics { },
                    ),
                color = colors.onTertiary,
                fontFamily = fontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                style = compactCommentTextStyle,
            )
        }
        if (isNew && subtreeReplyCount != null) {
            // Keep the full pill measured, including its gap. A single progress value owns
            // opacity and occupied width; no visibility-content removal can move the dot
            // again at the end of the transition or when its direction is reversed.
            Layout(
                content = { replyCount() },
                modifier = Modifier.offset(y = (-2).dp),
            ) { measurables, constraints ->
                val pill = measurables.single().measure(constraints.copy(minWidth = 0))
                hiddenReplyCountWidth = pill.width
                val width = (pill.width * replyCountWidthProgress).roundToInt()
                layout(width, pill.height) {
                    pill.placeRelative(width - pill.width, 0)
                }
            }
        } else if (subtreeReplyCount != null) {
            // Older comments retain the original fixed-width, opacity-only count animation.
            Box(Modifier.offset(y = (-2).dp)) { replyCount() }
        }
    }
}

@Composable
internal fun CommentAuthorBadge(
    label: String,
    color: Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
) {
    Box(
        modifier = Modifier.padding(start = 3.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = color,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            style = compactCommentTextStyle,
        )
    }
}
