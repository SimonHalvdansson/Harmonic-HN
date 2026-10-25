package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.settings.SettingsPage
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreviewResizeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun editingAnOffscreenTallPreviewKeepsItHiddenThroughoutTheUpdate() {
        val version = mutableIntStateOf(0)
        val height = mutableStateOf(500.dp)
        lateinit var list: LazyListState
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                Box(Modifier.size(360.dp, 300.dp)) {
                    list = rememberLazyListState()
                    SettingsPage("Preview", false, {}, showTopAppBar = false,
                        listState = list, contentVersion = version.intValue,
                        pinnedContent = {
                            Box(Modifier.fillMaxWidth().height(height.value).testTag("preview"))
                        },
                    ) { items(30) { Text("Setting $it", Modifier.height(64.dp)) } }
                }
            }
        }
        compose.runOnIdle { runBlocking { list.scrollToItem(12) } }
        compose.onNodeWithTag("preview").assertIsNotDisplayed()
        val originalIndex = list.firstVisibleItemIndex
        val originalOffset = list.firstVisibleItemScrollOffset
        compose.mainClock.autoAdvance = false
        repeat(6) { edit ->
            compose.runOnIdle {
                version.intValue++
                height.value = if (edit % 2 == 0) 600.dp else 500.dp
            }
            repeat(8) {
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithTag("preview").assertIsNotDisplayed()
            }
            compose.runOnIdle {
                assertEquals(originalIndex, list.firstVisibleItemIndex)
                assertEquals(originalOffset, list.firstVisibleItemScrollOffset)
            }
        }
    }

    @Test
    fun previewsUnpinAndRepinWhenThePaneOrTextSizeChangesWhileScrolled() {
        val width = mutableStateOf(360.dp)
        val height = mutableStateOf(680.dp)
        val fontScale = mutableFloatStateOf(1f)
        lateinit var list: LazyListState
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.floatValue)) {
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    Box(Modifier.size(width.value, height.value)) {
                        list = rememberLazyListState()
                        SettingsPage(
                            title = "Preview resize", showNavigation = false, onBack = {}, listState = list,
                            pinnedContent = {
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    // Model a preview whose text wraps when a split pane gets narrower.
                                    val previewHeight = (if (maxWidth < 280.dp) 440.dp else 220.dp) *
                                        LocalDensity.current.fontScale
                                    Box(Modifier.fillMaxWidth().height(previewHeight).testTag("preview"))
                                }
                            },
                        ) {
                            items(30) { Text("Setting $it", Modifier.height(64.dp)) }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { runBlocking { list.scrollToItem(12) } }
        compose.onNodeWithTag("preview").assertIsDisplayed()
        compose.runOnIdle { height.value = 360.dp }
        compose.onNodeWithTag("preview").assertIsNotDisplayed()
        compose.runOnIdle { height.value = 680.dp }
        compose.onNodeWithTag("preview").assertIsDisplayed()

        compose.runOnIdle { width.value = 240.dp }
        compose.onNodeWithTag("preview").assertIsNotDisplayed()
        // The non-pinned preview is now offscreen. Widening must remeasure it and pin it again.
        compose.runOnIdle { width.value = 360.dp }
        compose.onNodeWithTag("preview").assertIsDisplayed()
        compose.runOnIdle { fontScale.floatValue = 2f }
        compose.onNodeWithTag("preview").assertIsNotDisplayed()
        compose.runOnIdle { fontScale.floatValue = 1f }
        compose.onNodeWithTag("preview").assertIsDisplayed()
        compose.runOnIdle { assertEquals(12, list.firstVisibleItemIndex) }
    }
}
