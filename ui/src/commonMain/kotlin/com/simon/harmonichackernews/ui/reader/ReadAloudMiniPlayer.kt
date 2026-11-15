package com.simon.harmonichackernews.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.presentation.ReadAloudState

/** Remains reachable after the article is closed while desktop audio keeps playing. */
@Composable
fun ReadAloudMiniPlayer(state: ReadAloudState, onAction: (String) -> Unit) {
    if (state.count == 0) return
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                Text(state.error ?: "${if (state.playing) "Reading" else "Paused"} · ${state.index + 1} / ${state.count}",
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onAction(if (state.playing) "pause" else "play") }) { Text(if (state.playing) "Pause" else "Resume") }
            TextButton(onClick = { onAction("stop") }) { Text("Stop") }
        }
    }
}
