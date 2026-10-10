package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_visibility_off
import com.simon.harmonichackernews.ui.theme.pageBackground
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SwipeToHideStory(
    enabled: Boolean,
    onHide: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    // Dismissal is transient. Saving this state would restore a dismissed offset after Undo.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it * 0.4f }) }
    SwipeToDismissBox(
        state = state,
        modifier = Modifier.fillMaxWidth().semantics {
            customActions = listOf(CustomAccessibilityAction("Hide post") { onHide(); true })
        },
        onDismiss = { onHide() },
        backgroundContent = {
            if (state.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.pageBackground),
                    contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                        Alignment.CenterStart
                    } else Alignment.CenterEnd,
                ) {
                    Row(
                        Modifier.padding(horizontal = 24.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.large)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(Res.drawable.ic_visibility_off), null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.size(8.dp))
                        Text("Hide", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        },
    ) {
        // The row paints its own surface. Keep its outer padding transparent while it moves.
        Box(Modifier.fillMaxWidth()) { content() }
    }
}
