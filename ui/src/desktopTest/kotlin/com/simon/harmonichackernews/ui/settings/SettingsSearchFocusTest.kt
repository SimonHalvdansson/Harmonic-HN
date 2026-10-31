package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettingsSearchFocusTest {
    @Test fun findsAndCentersARowAfterManyLazyItems() = verify(grouped = false)
    @Test fun findsAndCentersARowInsideATallGroupedCard() = verify(grouped = true)

    private fun verify(grouped: Boolean) = SwingUtilities.invokeAndWait {
        lateinit var list: LazyListState
        var focus: SettingsSearchFocus? = null
        var version by mutableIntStateOf(0)
        val palette = HarmonicThemeCatalog.resolve("material_light", false)
        val scene = ImageComposeScene(400, 800) {
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalSettingsFocusSetting provides "Target") {
                    list = rememberLazyListState()
                    SettingsPage("Test", true, {}, listState = list, contentVersion = version) {
                        if (grouped) {
                            item {
                                SettingsCategory("Group") {
                                    repeat(30) { SettingRow("Before $it", icon = null, onClick = {}) }
                                    focus = LocalSettingsSearchFocus.current
                                    SettingRow("Target", icon = null, onClick = {})
                                    repeat(8) { SettingRow("After $it", icon = null, onClick = {}) }
                                }
                            }
                        } else {
                            items(60) { androidx.compose.foundation.layout.Spacer(Modifier.height(72.dp)) }
                            item {
                                focus = LocalSettingsSearchFocus.current
                                SettingRow("Target", icon = null, onClick = {})
                            }
                            items(8) { androidx.compose.foundation.layout.Spacer(Modifier.height(72.dp)) }
                        }
                    }
                }
            }
        }
        var time = 0L
        fun frames(count: Int = 150) {
            repeat(count) { scene.render(time.also { time += 16_666_667 }).close() }
        }
        try {
            frames()
            val target = assertNotNull(focus)
            val viewport = assertNotNull(target.viewport)
            val row = assertNotNull(target.row)
            assertTrue(row.isAttached)
            val center = viewport.localPositionOf(row, Offset.Zero).y + row.size.height / 2f
            assertTrue(center in (viewport.size.height * 0.35f)..(viewport.size.height * 0.65f),
                "Target center $center should be near the center of ${viewport.size.height}")
            list.requestScrollToItem(0)
            version++
            frames(20)
            assertEquals(0, list.firstVisibleItemIndex, "A preference update must not repeat the focus jump")
        } finally { scene.close() }
    }
}
