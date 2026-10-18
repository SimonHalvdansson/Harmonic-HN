@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.settings.QuoteHighlightColor
import com.simon.harmonichackernews.settings.QuoteHighlightStyle
import com.simon.harmonichackernews.settings.QuoteSpacing
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.ui.content.CommentBodyText
import com.simon.harmonichackernews.ui.settings.QuoteHighlightingSettingsRoute
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class QuoteToggleAnimationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun comfortableSettingsPreviewHasNoHeightJump() = checkSettingsPreview(QuoteSpacing.COMFORTABLE)

    @Test fun compactSettingsPreviewHasNoHeightJump() = checkSettingsPreview(QuoteSpacing.COMPACT)

    @Test fun productSansSettingsPreviewHasNoHeightJump() =
        checkSettingsPreview(QuoteSpacing.COMPACT, font = "productsans")

    private fun checkSettingsPreview(spacing: QuoteSpacing, font: String = "googlesansflexrounded") {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        repository.setQuoteSpacing(spacing)
        repository.setFont(font)
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        val dependencies = HarmonicUiDependencies(app, scene)
        compose.setContent {
            CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies) {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    Box(Modifier.width(360.dp)) {
                        QuoteHighlightingSettingsRoute(repository, onBack = {})
                    }
                }
            }
        }
        try {
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            fun position() = compose.onNodeWithText("Appearance").fetchSemanticsNode().boundsInRoot.top
            fun toggle(): List<Float> {
                val positions = mutableListOf(position())
                compose.onNodeWithText("Highlight quotes").performClick()
                repeat(30) {
                    compose.mainClock.advanceTimeByFrame()
                    positions += position()
                }
                return positions
            }
            val entering = toggle()
            val leaving = toggle()
            fun assertSmooth(positions: List<Float>) {
                val total = abs(positions.last() - positions.first())
                val largestStep = positions.zipWithNext { a, b -> abs(b - a) }.max()
                val message = "$font $spacing preview jumps: $positions"
                // Catch both the switch into segmented text and its removal after the fade,
                // not just an intermediate height followed by the correct final height.
                assertTrue(message, largestStep <= total * 0.25f + 2f)
                val low = minOf(positions.first(), positions.last()) - 1f
                val high = maxOf(positions.first(), positions.last()) + 1f
                assertTrue(message, positions.all { it in low..high })
                if (spacing == QuoteSpacing.COMPACT) {
                    assertTrue("Compact spacing changes height", total > 10f)
                    assertTrue("Compact spacing has intermediate frames", positions.distinct().size > 5)
                } else {
                    assertEquals("Comfortable spacing preserves height", 0f, total, 1f)
                }
            }
            assertSmooth(entering)
            assertSmooth(leaving)
            assertEquals("Restores original preview height", entering.first(), leaving.last(), 1f)
        } finally {
            scene.close()
        }
    }

    @Test fun toggleFadesAndAnimatesSpacingBeforeReturningToOneTextNode() {
        val enabled = mutableStateOf(false)
        compose.setContent {
            MaterialTheme(lightColorScheme(primary = Color.Blue, onSurface = Color.Black)) {
                Box(Modifier.width(320.dp).testTag("body").background(Color.White).padding(10.dp)) {
                    CommentBodyText(
                        highlightQuotes = enabled.value,
                        quoteSpacing = QuoteSpacing.COMPACT,
                        quoteHighlightColor = QuoteHighlightColor.MATERIAL,
                        quoteHighlightStyle = QuoteHighlightStyle.BOTH,
                        html = "&gt; Quote<p>Reply",
                        searchTerm = "", markedColor = Color.Red, fontFamily = FontFamily.SansSerif,
                        fontSize = 20f, animateSearchMatches = false,
                        onLinkClick = {}, onLinkLongClick = { _, _, _ -> },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        fun height() = compose.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot.height
        fun railRed(): Float {
            val pixels = compose.onNodeWithTag("body").captureToImage().toPixelMap()
            val density = compose.density.density
            return pixels[(6 * density).roundToInt(), (16 * density).roundToInt()].red
        }
        compose.onNodeWithText("> Quote\n\nReply", useUnmergedTree = true).assertExists()
        val offHeight = height()
        assertEquals(1f, railRed(), 0.02f)

        compose.runOnIdle { enabled.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        val enteringRed = railRed()
        val enteringHeight = height()
        compose.mainClock.advanceTimeBy(400)
        val onRed = railRed()
        val onHeight = height()
        compose.onNodeWithText("Quote", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Reply", useUnmergedTree = true).assertExists()
        assertTrue("Rail fades in: $onRed < $enteringRed < 1", onRed < enteringRed && enteringRed < 0.99f)
        assertTrue("Spacing animates in", onHeight < enteringHeight && enteringHeight < offHeight)

        compose.runOnIdle { enabled.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithText("> Quote", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Reply", useUnmergedTree = true).assertExists()
        val leavingRed = railRed()
        val leavingHeight = height()
        assertTrue("Rail fades out", leavingRed > onRed && leavingRed < 0.99f)
        assertTrue("Spacing animates out", leavingHeight > onHeight && leavingHeight < offHeight)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("> Quote\n\nReply", useUnmergedTree = true).assertExists()
        assertEquals("No height jump when dropping the segmented layout", offHeight, height(), 1f)
        assertEquals(1f, railRed(), 0.02f)

        // Reverse a transition before it settles, then turn it off while entering again.
        compose.runOnIdle { enabled.value = true }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { enabled.value = false }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { enabled.value = true }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(onRed, railRed(), 0.02f)
        assertEquals(onHeight, height(), 1f)
        compose.runOnIdle { enabled.value = false }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("> Quote\n\nReply", useUnmergedTree = true).assertExists()
    }
}
