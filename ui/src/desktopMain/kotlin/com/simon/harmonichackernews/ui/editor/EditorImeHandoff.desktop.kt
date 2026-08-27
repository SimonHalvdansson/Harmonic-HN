package com.simon.harmonichackernews.ui.editor

import androidx.compose.runtime.Composable

@Composable
internal actual fun KeepImeOpenDuringFieldHandoff(content: @Composable () -> Unit) {
    content()
}
