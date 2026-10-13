package com.simon.harmonichackernews.desktop

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.simon.harmonichackernews.ui.settings.LocalSettingsListState

/** Layout clamping must not overwrite the reading position chosen by scrolling. */
internal class SettingsScrollHistory {
    private data class Anchor(val index: Int, val offset: Int)
    private val anchors = mutableMapOf<String, Anchor>()

    fun createState(page: String): LazyListState = anchors[page].let {
        LazyListState(it?.index ?: 0, it?.offset ?: 0)
    }

    fun record(page: String, state: LazyListState) {
        anchors[page] = Anchor(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
    }

    fun restore(page: String, state: LazyListState) {
        anchors[page]?.let { state.requestScrollToItem(it.index, it.offset) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun SettingsScrollPage(history: SettingsScrollHistory, layout: Any, page: String, content: @Composable () -> Unit) {
    val state = remember(history, layout, page) { history.createState(page) }
    LaunchedEffect(state, page) {
        var wasScrolling = false
        snapshotFlow { Triple(state.isScrollInProgress, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
            .collect { (scrolling, _, _) ->
                if (scrolling || wasScrolling) history.record(page, state)
                wasScrolling = scrolling
            }
    }
    val defaultFocusScroll = LocalBringIntoViewSpec.current
    var keyboardNavigation by remember { mutableStateOf(false) }
    val focusScroll = remember(defaultFocusScroll) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (keyboardNavigation) defaultFocusScroll.calculateScrollDistance(offset, size, containerSize) else 0f
        }
    }
    // Compose tries to bring the retained focused group into view when its viewport changes.
    // That is not a reading-position change. Keep normal focus scrolling for keyboard navigation.
    BoxWithConstraints(Modifier.fillMaxSize()
        .onSizeChanged { keyboardNavigation = false }
        .onPreviewKeyEvent { keyboardNavigation = true; false }) {
        LaunchedEffect(state, constraints.maxWidth, constraints.maxHeight) {
            if (!state.isScrollInProgress) history.restore(page, state)
        }
        CompositionLocalProvider(
            LocalSettingsListState provides state,
            LocalBringIntoViewSpec provides focusScroll,
            content = content,
        )
    }
}
