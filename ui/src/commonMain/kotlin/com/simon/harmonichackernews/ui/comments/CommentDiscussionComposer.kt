package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_arrow_upward
import com.simon.harmonichackernews.resources.ic_stop
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.painterResource

/** Floating input bar; the host reserves its measured height in the conversation's scroll content. */
@Composable
internal fun CommentDiscussionComposer(
    draft: String,
    onDraftChanged: (String) -> Unit,
    hasTurns: Boolean,
    running: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = enabled && !running && draft.isNotBlank()
    val actionEnabled = enabled && (running || draft.isNotBlank())
    val fieldShape = RoundedCornerShape(32.dp)
    val fieldColor = HarmonicTheme.colors.surfaceContainerHigh
    val accent = MaterialTheme.colorScheme.primary
    val actionBackground by animateColorAsState(
        if (actionEnabled) accent else accent.copy(alpha = 0.25f),
        tween(140), label = "Chat action background",
    )
    val actionForeground by animateColorAsState(
        if (actionEnabled) MaterialTheme.colorScheme.onPrimary
            else HarmonicTheme.colors.contentPrimary.copy(alpha = 0.38f),
        tween(140), label = "Chat action foreground",
    )
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(3.dp, fieldShape)
            .background(fieldColor, fieldShape)
            .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        TextField(
            value = draft,
            onValueChange = onDraftChanged,
            enabled = enabled,
            placeholder = { Text(if (hasTurns) "Ask a follow-up…" else "Ask a question…") },
            modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                if ((event.key == Key.Enter || event.key == Key.NumPadEnter) && !event.isShiftPressed) {
                    if (event.type == KeyEventType.KeyDown && canSend) onSend()
                    true
                } else false
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            shape = fieldShape,
            maxLines = 5,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedTextColor = HarmonicTheme.colors.contentPrimary,
                unfocusedTextColor = HarmonicTheme.colors.contentPrimary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = accent,
            ),
        )
        FilledIconButton(
            enabled = actionEnabled,
            onClick = { if (running) onStop() else onSend() },
            modifier = Modifier.padding(bottom = 4.dp).size(48.dp).semantics {
                contentDescription = if (running) "Stop response" else "Send question"
            },
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = actionBackground,
                contentColor = actionForeground,
                disabledContainerColor = actionBackground,
                disabledContentColor = actionForeground,
            ),
        ) {
            Crossfade(running, animationSpec = tween(120), label = "Chat action icon") { showStop ->
                Icon(
                    painterResource(if (showStop) Res.drawable.ic_stop else Res.drawable.ic_arrow_upward),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
