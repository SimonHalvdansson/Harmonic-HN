package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

internal val LocalSettingsFocusSetting = staticCompositionLocalOf<String?> { null }
internal val LocalSettingsSearchFocus = staticCompositionLocalOf<SettingsSearchFocus?> { null }

/** Shared by ordinary, segmented, slider and main-toggle rows, including rows inside lazy cards. */
internal class SettingsSearchFocus(val title: String) {
    var row: LayoutCoordinates? = null
    var viewport: LayoutCoordinates? = null
    val highlight = Animatable(0f)

    fun matches(title: String?): Boolean = title?.substringBefore(" (") == this.title
}

@Composable
internal fun Modifier.settingsSearchTarget(title: String?): Modifier {
    val focus = LocalSettingsSearchFocus.current
    if (focus == null || !focus.matches(title)) return this
    val colors = HarmonicTheme.colors
    DisposableEffect(focus) { onDispose { focus.row = null } }
    return this.onGloballyPositioned { focus.row = it }.drawBehind {
        drawRect(colors.primary.copy(alpha = focus.highlight.value * 0.16f))
    }
}

@Composable
internal fun rememberSettingsSearchFocus(title: String?, list: LazyListState): SettingsSearchFocus? {
    val focus = remember(title) { title?.let(::SettingsSearchFocus) }
    var completed by rememberSaveable(title) { mutableStateOf(false) }
    LaunchedEffect(focus, list) {
        if (focus == null || completed) return@LaunchedEffect
        snapshotFlow { list.layoutInfo.totalItemsCount }.first { it > 0 }
        withFrameNanos { }
        // Rows register when composed. Scan lazy items only until the requested row exists;
        // this also handles grouped cards and avoids hardcoded indices that drift with features.
        val originalIndex = list.firstVisibleItemIndex
        val originalOffset = list.firstVisibleItemScrollOffset
        if (focus.row?.isAttached != true) list.scrollToItem(0)
        var lastIndex = -1
        while (focus.row?.isAttached != true) {
            withFrameNanos { }
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: break
            if (last == lastIndex || last >= list.layoutInfo.totalItemsCount - 1) break
            lastIndex = last
            list.scrollToItem(last + 1)
        }
        val row = focus.row
        val viewport = focus.viewport
        if (row?.isAttached != true || viewport?.isAttached != true) {
            // Conditional controls can disappear after a preference changes. Keep the page usable.
            list.scrollToItem(originalIndex, originalOffset)
            completed = true
            return@LaunchedEffect
        }
        val preview = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "settings-preview" }
        val pinnedHeight = preview?.takeIf {
            it.offset <= 0 && it.size <= viewport.size.height * 0.7f
        }?.let { it.offset + it.size }?.coerceAtLeast(0) ?: 0
        val targetCenter = (pinnedHeight + viewport.size.height) / 2f
        val rowCenter = viewport.localPositionOf(row, Offset.Zero).y + row.size.height / 2f
        list.animateScrollBy(rowCenter - targetCenter)
        completed = true
        delay(450)
        repeat(2) {
            focus.highlight.animateTo(1f, tween(250))
            focus.highlight.animateTo(0f, tween(550))
        }
    }
    return focus
}
