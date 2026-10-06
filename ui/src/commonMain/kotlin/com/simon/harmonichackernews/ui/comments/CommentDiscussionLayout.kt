package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun CommentDiscussionLayout(
    header: @Composable () -> Unit,
    composer: @Composable (Modifier) -> Unit,
    safeInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (composerHeight: Dp) -> Unit,
) {
    val density = LocalDensity.current
    var composerHeight by remember { mutableStateOf(80.dp) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(
        safeInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    )) {
        header()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The viewport reaches the bottom edge; only the floating controls avoid the
            // navigation bar/IME. Include that inset in the list's trailing content padding.
            content(composerHeight)
            composer(Modifier.align(Alignment.BottomCenter)
                .onSizeChanged { composerHeight = with(density) { it.height.toDp() } }
                .windowInsetsPadding(safeInsets.only(WindowInsetsSides.Bottom)))
        }
    }
}
