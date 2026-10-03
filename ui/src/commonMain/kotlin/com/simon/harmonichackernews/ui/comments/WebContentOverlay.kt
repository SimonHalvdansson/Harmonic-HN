package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.download_file
import com.simon.harmonichackernews.resources.ic_file_download
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** UI state only: loading settlement and download execution remain owned by the browser host. */
class WebContentOverlayState {
    var progress by mutableIntStateOf(0)
        private set
    var progressVisible by mutableStateOf(false)
        private set
    var loadId by mutableIntStateOf(0)
        private set
    var onDownload by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun beginLoad() {
        loadId++
        progress = 0
        progressVisible = true
        dismissDownload()
    }

    fun updateProgress(value: Int) { progress = value.coerceIn(0, 100) }
    fun showProgress() { progressVisible = true }
    fun finishLoad(completeProgress: Boolean) {
        if (completeProgress) progress = 100
        progressVisible = false
    }
    fun showDownload(action: () -> Unit) { onDownload = action }
    fun dismissDownload() { onDownload = null }
    fun reset() {
        loadId++
        progress = 0
        progressVisible = false
        dismissDownload()
    }
}

@Composable
fun WebContentOverlay(state: WebContentOverlayState, modifier: Modifier = Modifier) {
    // A new load gets a new animation even if completion and restart occur in the same frame.
    val progress = remember(state, state.loadId) { Animatable(0f) }
    val alpha = remember(state) { Animatable(0f) }
    LaunchedEffect(state, state.loadId, state.progress, state.progressVisible) {
        val target = state.progress / 100f
        if (!state.progressVisible || target <= progress.value) progress.snapTo(target)
        else progress.animateTo(target, tween(400))
    }
    LaunchedEffect(state, state.progressVisible) {
        alpha.animateTo(if (state.progressVisible) 1f else 0f, tween(50))
    }
    Box(modifier.fillMaxSize()) {
        state.onDownload?.let { onDownload ->
            OutlinedButton(
                onClick = onDownload,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HarmonicTheme.colors.contentPrimary),
                modifier = Modifier.align(Alignment.Center).testTag("webview_download"),
            ) {
                Icon(
                    painterResource(Res.drawable.ic_file_download),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.download_file))
            }
        }
        if (state.progressVisible || alpha.value > 0f) {
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxWidth().alpha(alpha.value).testTag("webview_progress"),
            )
        }
    }
}
