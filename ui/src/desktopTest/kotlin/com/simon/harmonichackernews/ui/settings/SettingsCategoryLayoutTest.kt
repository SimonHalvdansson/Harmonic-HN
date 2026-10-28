package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.pageBackground
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsCategoryLayoutTest {
    @Test
    fun lazyRowsPreserveCategoryPixelsIncludingCornersAndDividers() = SwingUtilities.invokeAndWait {
        val palette = HarmonicThemeCatalog.scheme("violet", false)
        fun render(lazy: Boolean): IntArray {
            val scene = ImageComposeScene(360, 300, Density(1f)) {
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    Box(Modifier.fillMaxSize().background(palette.colorScheme.pageBackground)) {
                        if (lazy) LazyColumn {
                            settingsCategory("Layout") {
                                repeat(3) { row { Box(Modifier.fillMaxWidth().height(60.dp).background(Color.White)) } }
                            }
                        } else Column {
                            SettingsCategory("Layout") {
                                repeat(3) { index ->
                                    if (index > 0) SettingsDivider()
                                    Box(Modifier.fillMaxWidth().height(60.dp).background(Color.White))
                                }
                            }
                        }
                    }
                }
            }
            try {
                repeat(4) { scene.render(it * 16_666_667L).close() }
                return scene.render(100_000_000).use { image ->
                    IntArray(360 * 300).also { image.toComposeImageBitmap().readPixels(it) }
                }
            } finally { scene.close() }
        }
        assertContentEquals(render(false), render(true))
    }

    @Test
    fun offscreenRowsAreDeferredAndUseCurrentPreferencesWhenScrolledIntoView() = SwingUtilities.invokeAndWait {
        val state = LazyListState()
        val preference = mutableStateOf("before")
        val composed = mutableMapOf<Int, String>()
        val scene = ImageComposeScene(360, 300, Density(1f)) {
            LazyColumn(state = state) {
                settingsCategory("Comments") {
                    repeat(100) { index -> row {
                        val value = preference.value
                        SideEffect { composed[index] = value }
                        Box(Modifier.fillMaxWidth().height(60.dp))
                    } }
                }
            }
        }
        try {
            var time = 0L
            fun settle() = repeat(8) { scene.render(time.also { time += 16_666_667 }).close() }
            settle()
            assertTrue(composed.size < 15, "Construct only the viewport and its prefetch window")
            assertFalse(99 in composed)
            preference.value = "after"
            state.requestScrollToItem(100)
            settle()
            assertEquals("after", composed[99])
        } finally { scene.close() }
    }
}
