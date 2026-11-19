package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import com.simon.harmonichackernews.ui.content.ContentTypography
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import com.simon.harmonichackernews.utils.HtmlTextUtils
import kotlin.math.roundToInt

@Composable
fun PollOptions(
    options: List<PollOptionUi>?,
    voteInFlightOptionId: Int?,
    onVote: (Int) -> Unit,
    typography: ContentTypography,
) {
    val items = options.orEmpty()
    val totalPoints = pollTotalPoints(items)
    AnimatedVisibility(
        visible = items.isNotEmpty(),
        enter = fadeIn(tween(180)) + expandVertically(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top,
        ),
        exit = fadeOut(tween(90)) + shrinkVertically(shrinkTowards = Alignment.Top),
        label = "poll options",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEachIndexed { index, option ->
                key(option.id) {
                    val top = if (index == 0) 16.dp else 4.dp
                    val bottom = if (index == items.lastIndex) 16.dp else 4.dp
                    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
                    AnimatedContent(
                        targetState = option,
                        modifier = Modifier.fillMaxWidth(),
                        // Point updates keep the row in place and animate its fill.
                        contentKey = { it.loaded to it.loadFailed },
                        transitionSpec = {
                            (fadeIn(tween(180, delayMillis = 80)) togetherWith fadeOut(tween(90))).using(
                                SizeTransform(clip = false) { _, _ ->
                                    tween(260, easing = FastOutSlowInEasing)
                                },
                            )
                        },
                        label = "poll option content",
                    ) { displayedOption ->
                        PollOptionRow(
                            option = displayedOption,
                            shape = shape,
                            totalPoints = totalPoints,
                            submitting = voteInFlightOptionId == displayedOption.id,
                            votingEnabled = voteInFlightOptionId == null,
                            typography = typography,
                            onVote = onVote,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PollOptionRow(
    option: PollOptionUi,
    shape: RoundedCornerShape,
    totalPoints: Long?,
    submitting: Boolean,
    votingEnabled: Boolean,
    typography: ContentTypography,
    onVote: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val fill = lerp(colors.primaryContainer, colors.primary, 0.1f)
    val label = remember(option.text) { HtmlTextUtils.plainText(option.text) }
    val share = pollPointShare(option.points, totalPoints)
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val animatedShare by animateFloatAsState(
        targetValue = if (revealed) share ?: 0f else 0f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "poll result share",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .drawBehind {
                if (option.loaded && share != null) {
                    val width = size.width * animatedShare
                    val start = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                    drawRect(fill, topLeft = Offset(start, 0f), size = Size(width, size.height))
                }
            }
            .clickable(
                enabled = option.loaded && votingEnabled,
                role = Role.Button,
                onClickLabel = "Vote for $label",
                onClick = { onVote(option.id) },
            )
            .semantics(mergeDescendants = true) {
                if (submitting) stateDescription = "Submitting vote"
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (option.loaded) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    fontFamily = typography.family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = typography.commentTextSize.sp,
                    lineHeight = (typography.commentTextSize * 1.3f).sp,
                    color = colors.onSurface,
                )
                Text(
                    pointCount(option.points.coerceAtLeast(0).toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = AppUiFontFamily,
                    color = colors.onSurfaceVariant,
                )
            }
            if (submitting) {
                HarmonicLoadingIndicator(Modifier.size(24.dp), color = colors.primary)
            } else if (share != null) {
                Text(
                    "${(share * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = AppUiFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }
        } else {
            if (!option.loadFailed) HarmonicLoadingIndicator(Modifier.size(24.dp))
            Text(
                if (option.loadFailed) "Unable to load this option" else "Loading option…",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = AppUiFontFamily,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

// A partial denominator would incorrectly show the first loaded option as 100%.
internal fun pollTotalPoints(options: List<PollOptionUi>): Long? =
    options.takeIf { it.isNotEmpty() && it.all(PollOptionUi::loaded) }
        ?.sumOf { it.points.coerceAtLeast(0).toLong() }

internal fun pollPointShare(points: Int, totalPoints: Long?): Float? = when {
    totalPoints == null -> null
    totalPoints <= 0L -> 0f
    else -> (points.coerceAtLeast(0).toFloat() / totalPoints).coerceIn(0f, 1f)
}

private fun pointCount(points: Long): String = "$points ${if (points == 1L) "point" else "points"}"

data class PollOptionUi(
    val id: Int,
    val loaded: Boolean,
    val loadFailed: Boolean,
    val text: String?,
    val points: Int,
)
