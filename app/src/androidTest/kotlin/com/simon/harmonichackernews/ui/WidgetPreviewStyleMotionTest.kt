package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.ui.widget.WidgetPreviewStoryRow
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks rendered pixels, so a snapped border/shadow cannot pass as an animated state change. */
@RunWith(AndroidJUnit4::class)
class WidgetPreviewStyleMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyDisplayStyleTransitionHasAnIntermediateFrame() {
        val displayStyle = mutableStateOf(DisplayStyle.FLAT)
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                Box(Modifier.width(360.dp).background(palette.colors.background).testTag("preview")) {
                    WidgetPreviewStoryRow(
                        SettingsStoryPreviewModel.copy(previewImageFallback = null),
                        WidgetConfiguration(previewImageMode = StoryPreviewMode.OFF, displayStyle = displayStyle.value),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            val styles = listOf(DisplayStyle.FLAT, DisplayStyle.STANDARD, DisplayStyle.RAISED, DisplayStyle.OUTLINED)
            for (from in styles) for (to in styles.filter { it != from }) {
                compose.runOnUiThread { displayStyle.value = from }
                compose.mainClock.advanceTimeBy(400)
                val before = pixels()
                compose.runOnUiThread { displayStyle.value = to }
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeBy(64)
                val during = pixels()
                compose.mainClock.advanceTimeBy(400)
                val after = pixels()
                assertFalse("$from → $to should start changing", before.contentEquals(during))
                assertFalse("$from → $to must not snap to its final appearance", during.contentEquals(after))
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun pixels(): IntArray {
        val image = compose.onNodeWithTag("preview").captureToImage().toPixelMap()
        return IntArray(image.width * image.height) { index -> image[index % image.width, index / image.width].toArgb() }
    }
}
