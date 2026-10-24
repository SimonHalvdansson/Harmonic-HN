package com.simon.harmonichackernews.ui.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.simon.harmonichackernews.settings.SplitRatioPreferences

/** Browser adjustments belong to one opening of one story, never to the saved split preference. */
class BrowserPaneState(val standalone: Boolean = false) {
    var owner: Any? by mutableStateOf(null)
    var integrated by mutableStateOf(false)
    var opening by mutableStateOf(false)
    var sideBySide by mutableStateOf(false)
    var canShowSideBySide by mutableStateOf(false)
    var sheetExpansion by mutableFloatStateOf(1f)
    var logicalExpansion by mutableFloatStateOf(1f)
    var showSideBySide: () -> Unit = {}
    var restoreOnBack by mutableStateOf(false)
    var restoreBackActive by mutableStateOf(false)
    var restoreBackProgress by mutableFloatStateOf(0f)
    var restoreFromBack: () -> Unit = {}
    var backSlideActive by mutableStateOf(false)
    var backSlideProgress: () -> Float = { 0f }
    var backSlideDistance: () -> Float = { 0f }
    var splitPosition: (() -> Float)? = null
}

internal class BrowserSplitSession {
    var openingRatio by mutableStateOf<Float?>(null)
        private set
    var ratio by mutableFloatStateOf(0.5f)
        private set
    var reachedBrowser by mutableStateOf(false)
        private set
    var restoreRatio by mutableFloatStateOf(0.5f)
        private set

    fun update(savedRatio: Float, integrated: Boolean, opening: Boolean, expansion: Float) {
        if (!integrated || (!opening && expansion >= 0.999f)) {
            openingRatio = null
            reachedBrowser = false
        } else {
            if (openingRatio == null) {
                openingRatio = savedRatio
                ratio = savedRatio
                restoreRatio = savedRatio
            }
            if (expansion <= 0.001f) reachedBrowser = true
        }
    }

    fun adjust(value: Float, rememberForBack: Boolean = true) {
        if (value.isFinite()) ratio = value.coerceIn(0f, SplitRatioPreferences.Maximum)
        if (rememberForBack && ratio >= SplitRatioPreferences.Minimum) restoreRatio = ratio
    }

    fun settle() {
        // The area below the normal minimum is a reveal/collapse region, not a tiny list pane.
        if (ratio < SplitRatioPreferences.Minimum) {
            ratio = if (ratio < SplitRatioPreferences.Minimum / 2) 0f else SplitRatioPreferences.Minimum
        }
        if (ratio >= SplitRatioPreferences.Minimum) restoreRatio = ratio
    }

    fun target(savedRatio: Float, standalone: Boolean, sideBySide: Boolean, visualExpansion: Float): Float {
        val returning = reachedBrowser && visualExpansion > 0.001f
        return when {
            standalone && (!sideBySide || returning) -> 0f
            returning -> openingRatio ?: savedRatio
            openingRatio != null -> ratio
            else -> savedRatio
        }
    }
}

internal data class BrowserPaneControls(val restoreVisible: Boolean = false, val restore: () -> Unit = {})
val LocalBrowserPaneState = compositionLocalOf<BrowserPaneState?> { null }
internal val LocalBrowserPaneControls = compositionLocalOf { BrowserPaneControls() }
