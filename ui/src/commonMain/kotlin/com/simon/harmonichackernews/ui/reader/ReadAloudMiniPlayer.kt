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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.presentation.ReadAloudState
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_pause
import com.simon.harmonichackernews.resources.ic_play_arrow
import com.simon.harmonichackernews.resources.ic_volume_up
import com.simon.harmonichackernews.resources.ic_stop
import com.simon.harmonichackernews.ui.theme.cardBackground
import org.jetbrains.compose.resources.painterResource

/** Remains reachable after leaving the article. Keep composed so stopping can animate out. */
@Composable
fun ReadAloudMiniPlayer(
    state: ReadAloudState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onAction: (JsonObject) -> Unit,
) {
    val visible = enabled && state.count > 0
    var expanded by rememberSaveable(visible, state.url, state.title) { mutableStateOf(false) }
    fun command(action: String) = onAction(buildJsonObject { put("action", action) })
    fun seek(seconds: Int) = onAction(buildJsonObject { put("action", "seek"); put("seconds", seconds) })
    CapturedAnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(animationSpec = tween(300), expandFrom = Alignment.Bottom),
        exit = shrinkVertically(animationSpec = tween(240), shrinkTowards = Alignment.Bottom),
    ) {
        // No alpha on this surface: native browser content must never show through the player.
        Surface(color = MaterialTheme.colorScheme.cardBackground) {
            Column(Modifier.fillMaxWidth()) {
                val progress by animateFloatAsState(
                    (state.positionSeconds.toFloat() / state.durationSeconds.coerceAtLeast(1)).coerceIn(0f, 1f),
                    tween(240), label = "Reading progress",
                )
                LinearProgressIndicator(
                    progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant,
                    gapSize = 0.dp, drawStopIndicator = {},
                )
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = visible, role = Role.Button,
                        onClickLabel = if (expanded) "Collapse playback controls" else "Expand playback controls",
                        onClick = { expanded = !expanded })
                        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(painterResource(Res.drawable.ic_volume_up), null, Modifier.padding(9.dp).size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp, end = 4.dp)) {
                        Text(
                            state.title.ifBlank { "Read aloud" }, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            maxLines = 1, style = MaterialTheme.typography.titleSmall,
                        )
                        val status = if (state.playing) "Reading" else "Paused"
                        Text(
                            state.error ?: "$status · Sentence ${state.index + 1} of ${state.count}",
                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
                        onClick = { command(if (state.playing) "pause" else "play") },
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
                    IconButton(onClick = { command("stop") }, enabled = visible, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(Res.drawable.ic_stop), "Stop reading", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                androidx.compose.animation.AnimatedVisibility(expanded) {
                    SeekControls(state, ::seek)
                }
            }
        }
    }
}

@Composable
private fun SeekControls(state: ReadAloudState, seek: (Int) -> Unit) {
    val duration = state.durationSeconds.coerceAtLeast(1)
    var drag by remember(state.url) { mutableStateOf<Float?>(null) }
    val position = drag?.roundToInt() ?: state.positionSeconds
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) {
        Slider(
            value = (drag ?: position.toFloat()).coerceIn(0f, duration.toFloat()),
            onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { seek(it.roundToInt()) }; drag = null },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Seek reading position" },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${playbackTime(position)} / ~${playbackTime(duration)}",
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { seek((position - 15).coerceAtLeast(0)) }, enabled = position > 0,
                modifier = Modifier.semantics { contentDescription = "Back 15 seconds" }) { Text("−15 s") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { seek((position + 15).coerceAtMost(duration)) }, enabled = position < duration,
                modifier = Modifier.semantics { contentDescription = "Forward 15 seconds" }) { Text("+15 s") }
        }
    }
}

private fun playbackTime(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
