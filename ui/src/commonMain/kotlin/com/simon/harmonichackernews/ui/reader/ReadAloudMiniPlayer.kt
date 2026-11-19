package com.simon.harmonichackernews.ui.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.CapturedAnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.presentation.ReadAloudState
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_pause
import com.simon.harmonichackernews.resources.ic_play_arrow
import com.simon.harmonichackernews.resources.ic_stop
import com.simon.harmonichackernews.ui.theme.cardBackground
import org.jetbrains.compose.resources.painterResource

/** Remains reachable after leaving the article. Keep composed so stopping can animate out. */
@Composable
fun ReadAloudMiniPlayer(
    state: ReadAloudState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onAction: (String) -> Unit,
) {
    val visible = enabled && state.count > 0
    CapturedAnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(animationSpec = tween(300), expandFrom = Alignment.Bottom),
        exit = shrinkVertically(animationSpec = tween(240), shrinkTowards = Alignment.Bottom),
    ) {
        // No alpha on this surface: native browser content must never show through the player.
        Surface(color = MaterialTheme.colorScheme.cardBackground) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            state.title.ifBlank { "Read aloud" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        AnimatedContent(
                            targetState = state.error ?: if (state.playing) "Reading aloud" else "Paused",
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "Reading status",
                        ) { status ->
                            Text(
                                status,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val progress by animateFloatAsState(
                            targetValue = (state.index.toFloat() / state.count.coerceAtLeast(1)).coerceIn(0f, 1f),
                            animationSpec = tween(300),
                            label = "Reading progress",
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp))
                                .semantics { contentDescription = "Section ${state.index + 1} of ${state.count}" },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                    val buttonColor by animateColorAsState(
                        if (state.playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        tween(200), label = "Playback button background",
                    )
                    val iconColor by animateColorAsState(
                        if (state.playing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        tween(200), label = "Playback button foreground",
                    )
                    FilledIconButton(
                        onClick = { onAction(if (state.playing) "pause" else "play") },
                        enabled = visible,
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = buttonColor,
                            contentColor = iconColor,
                            disabledContainerColor = buttonColor,
                            disabledContentColor = iconColor,
                        ),
                    ) {
                        AnimatedContent(
                            targetState = state.playing,
                            transitionSpec = {
                                (fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.75f)) togetherWith
                                    (fadeOut(tween(100)) + scaleOut(tween(160), targetScale = 0.75f))
                            },
                            label = "Play pause icon",
                        ) { playing ->
                            Icon(
                                painterResource(if (playing) Res.drawable.ic_pause else Res.drawable.ic_play_arrow),
                                contentDescription = if (playing) "Pause reading" else "Resume reading",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    IconButton(onClick = { onAction("stop") }, enabled = visible, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(Res.drawable.ic_stop), "Stop reading", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
