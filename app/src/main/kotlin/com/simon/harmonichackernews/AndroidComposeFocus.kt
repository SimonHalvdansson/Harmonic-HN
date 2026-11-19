package com.simon.harmonichackernews

import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi

/** Install before creating any Compose owners; keep the matching release R8 rule in sync. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun configureAndroidComposeFocus() {
    // A focused AndroidView being removed/moved can synchronously request root focus while
    // Compose applies changes. The bypass path searches lazy content and re-enters composition.
    // Use Compose's earlier focus path rather than disabling keyboard focus in WebViews.
    // Upstream: https://issuetracker.google.com/issues/469958424
    ComposeUiFlags.isBypassUnfocusableComposeViewEnabled = false
}
