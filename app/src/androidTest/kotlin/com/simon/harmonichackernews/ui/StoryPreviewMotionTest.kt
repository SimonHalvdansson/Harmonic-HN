package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemStyle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real settings preview and its bundled image, with no network or timing dependency. */
@RunWith(AndroidJUnit4::class)
class StoryPreviewMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bodyAndCommentRailUseTheSameSingleLongPressHapticForEveryImageMode() {
        val mode = mutableStateOf(StoryPreviewMode.MEDIUM)
        var previews = 0
        var comments = 0
        val haptics = mutableListOf<HapticFeedbackType>()
        val feedback = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                haptics += hapticFeedbackType
            }
        }
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            CompositionLocalProvider(LocalHapticFeedback provides feedback) {
                HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                    StoryItem(
                        model = SettingsStoryPreviewModel,
                        style = previewStyle(mode.value),
                        onCommentClick = { comments++ },
                        onLinkLongClick = { previews++ },
                    )
                }
            }
        }
        for ((index, imageMode) in StoryPreviewMode.entries.withIndex()) {
            compose.runOnIdle { mode.value = imageMode; haptics.clear() }
            compose.waitForIdle()
            compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("Open comments") {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label == "Open comments"
            }).onFirst().performTouchInput { longClick() }
            compose.waitForIdle()
            assertEquals(listOf(HapticFeedbackType.LongPress), haptics)
            compose.runOnIdle { haptics.clear() }
            compose.onNodeWithText(SettingsStoryPreviewModel.title).performTouchInput { longClick() }
            compose.waitForIdle()
            assertEquals("Body and rail must produce one identical pulse in $imageMode", listOf(HapticFeedbackType.LongPress), haptics)
            assertEquals((index + 1) * 2, previews)
            assertEquals(0, comments)
        }
    }

    @Test
    fun smallToLargeKeepsTheEntireStoryInsideItsCardOnEveryFrame() =
        assertLargePreviewMotion(StoryPreviewMode.SMALL)

    @Test
    fun mediumToLargeKeepsTheEntireStoryInsideItsCardOnEveryFrame() =
        assertLargePreviewMotion(StoryPreviewMode.MEDIUM)

    @Test
    fun mediumToSmallMovesImageDuringPillFade() {
        val mode = mutableStateOf(StoryPreviewMode.MEDIUM)
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                Box(Modifier.width(360.dp)) {
                    StoryItem(
                        model = SettingsStoryPreviewModel,
                        style = previewStyle(mode.value),
                        onCommentClick = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        // Follow the medium image's owning surface, which remains inspectable while exiting.
        // The incoming small-mode comments rail has the same click label during the transition.
        val image = compose.onAllNodesWithTag(
            "story-medium-preview-image",
            useUnmergedTree = true,
        )
        val before = image.fetchSemanticsNodes().single().boundsInRoot
        compose.runOnUiThread { mode.value = StoryPreviewMode.SMALL }
        // Image motion must begin within the outgoing badges' 75ms fade.
        val widths = mutableListOf<Float>()
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            val bounds = image.fetchSemanticsNodes().single().boundsInRoot
            widths.add(bounds.width)
        }
        assertTrue("Image must start shrinking during the fade", widths.any { it < before.width - 1f })
        repeat(24) {
            compose.mainClock.advanceTimeByFrame()
            image.fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.width?.let(widths::add)
        }
        assertTrue(
            "Image bounds must shrink through multiple intermediate widths",
            widths.filter { it < before.width - 1f }.map { it.toInt() }.distinct().size >= 3,
        )
        assertTrue("The medium image must leave after its transition", image.fetchSemanticsNodes().isEmpty())

        compose.runOnUiThread { mode.value = StoryPreviewMode.MEDIUM }
        repeat(24) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnUiThread { mode.value = StoryPreviewMode.SMALL }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnUiThread { mode.value = StoryPreviewMode.MEDIUM }
        repeat(24) { compose.mainClock.advanceTimeByFrame() }
        val restored = image.fetchSemanticsNodes().single().boundsInRoot
        assertEquals("Reversing during the fade restores image position", before.left, restored.left, 0.5f)
        assertEquals("Reversing during the fade restores image width", before.width, restored.width, 0.5f)
    }

    private fun assertLargePreviewMotion(initialMode: StoryPreviewMode) {
        val mode = mutableStateOf(initialMode)
        val model = SettingsStoryPreviewModel.copy(
            title = "A preview with a title that wraps across multiple lines",
            summary = "The story summary stays visible below the expanding image.",
        )
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                Box(Modifier.width(360.dp)) {
                    StoryItem(
                        model = model,
                        style = previewStyle(mode.value),
                        modifier = Modifier.testTag("story-preview"),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val story = compose.onNodeWithTag("story-preview")
        val textNodes = listOf(
            compose.onNodeWithText(model.title, useUnmergedTree = true),
            compose.onNodeWithText(model.summary, useUnmergedTree = true),
            compose.onNodeWithText(model.domain, substring = true, useUnmergedTree = true),
        )
        val initialBounds = story.getUnclippedBoundsInRoot()
        val before = initialBounds.bottom.value - initialBounds.top.value
        compose.runOnUiThread { mode.value = StoryPreviewMode.LARGE }
        val heights = (1..24).map { frame ->
            compose.mainClock.advanceTimeByFrame()
            val storyBounds = story.getUnclippedBoundsInRoot()
            // The public StoryItem includes 10dp outer + 4dp inner padding below its card.
            // Use unclipped text bounds: clipped semantics could hide the very regression
            // under test by reporting only the portion still visible inside the card.
            val cardBottom = storyBounds.bottom.value - 14f
            textNodes.forEach { text ->
                val bounds = text.getUnclippedBoundsInRoot()
                assertTrue(
                    "$initialMode -> LARGE frame $frame: text bottom ${bounds.bottom} " +
                        "must remain inside card bottom ${cardBottom}dp",
                    bounds.bottom.value <= cardBottom + 0.5f,
                )
            }
            storyBounds.bottom.value - storyBounds.top.value
        }
        val after = heights.last()
        assertTrue("Large preview must add image space", after > before + 80f)
        assertTrue(
            "The card must grow through intermediate sizes",
            heights.any { it > before + 1f && it < after - 1f },
        )
    }

    private fun previewStyle(mode: StoryPreviewMode) = StoryItemStyle(
        previewImageMode = mode,
        borderlessLargeImage = true,
        compact = false,
        showSummary = true,
        showFavicon = false,
        showPoints = true,
        compactPoints = false,
        includeTopLevelDomain = true,
        showCommentCount = true,
        showIndex = false,
        commentsOnLeft = false,
        tintCard = false,
        displayStyle = DisplayStyle.STANDARD,
        useHotnessIcon = false,
        preferredFont = "default",
        textSize = 16f,
    )
}
