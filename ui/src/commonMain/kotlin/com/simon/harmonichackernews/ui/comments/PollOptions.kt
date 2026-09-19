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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import com.simon.harmonichackernews.ui.content.ContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Poll",
                    modifier = Modifier.weight(1f).semantics { heading() },
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = HarmonicTheme.colors.textPrimary,
                )
                Text(
                    when {
                        totalPoints != null -> pointCount(totalPoints)
                        items.any { !it.loaded && !it.loadFailed } -> "Loading results…"
                        else -> "Partial results"
                    },
                    fontFamily = ProductSansFontFamily,
                    fontSize = 12.sp,
                    color = HarmonicTheme.colors.textSecondary,
                )
            }
            items.forEach { option ->
                key(option.id) {
                    AnimatedContent(
                        targetState = option,
                        modifier = Modifier.fillMaxWidth(),
                        // Point updates retain the card and animate only its result bar.
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
                        PollOptionCard(
                            option = displayedOption,
                            totalPoints = totalPoints,
                            submitting = voteInFlightOptionId == displayedOption.id,
                            votingEnabled = voteInFlightOptionId == null,
                            typography = typography,
                            onVote = onVote,
                        )
                    }
                }
            }
            Text(
                if (voteInFlightOptionId != null) "Submitting vote…" else "Tap an option to vote",
                modifier = Modifier.padding(start = 2.dp, top = 2.dp),
                fontFamily = ProductSansFontFamily,
                fontSize = 12.sp,
                color = HarmonicTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PollOptionCard(
    option: PollOptionUi,
    totalPoints: Long?,
    submitting: Boolean,
    votingEnabled: Boolean,
    typography: ContentTypography,
    onVote: (Int) -> Unit,
) {
    val colors = HarmonicTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val label = remember(option.text) { HtmlTextUtils.plainText(option.text) }
    val share = pollPointShare(option.points, totalPoints)
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val animatedShare by animateFloatAsState(
        targetValue = if (revealed) share ?: 0f else 0f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "poll result share",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .border(1.dp, if (submitting) colors.accent else colors.commentDivider, shape)
            .clickable(
                enabled = option.loaded && votingEnabled,
                role = Role.Button,
                onClickLabel = "Vote for $label",
                onClick = { onVote(option.id) },
            )
            .semantics(mergeDescendants = true) {
                if (submitting) stateDescription = "Submitting vote"
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (option.loaded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        label,
                        fontFamily = typography.family,
                        fontWeight = FontWeight.Bold,
                        fontSize = typography.commentTextSize.sp,
                        color = colors.textPrimary,
                    )
                    Text(
                        pointCount(option.points.coerceAtLeast(0).toLong()),
                        fontFamily = ProductSansFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                    )
                }
                if (submitting) {
                    HarmonicLoadingIndicator(Modifier.size(24.dp), color = colors.accent)
                } else if (share != null) {
                    Text(
                        "${(share * 100).roundToInt()}%",
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.textPrimary,
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textSecondary.copy(alpha = 0.12f)),
            ) {
                Box(
                    Modifier.fillMaxWidth(animatedShare).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.accent),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!option.loadFailed) HarmonicLoadingIndicator(Modifier.size(24.dp))
                Text(
                    if (option.loadFailed) "Unable to load this option" else "Loading option…",
                    fontFamily = ProductSansFontFamily,
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                )
            }
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
