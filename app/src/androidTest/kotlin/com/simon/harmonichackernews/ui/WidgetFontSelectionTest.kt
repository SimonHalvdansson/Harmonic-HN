package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.widget.WidgetConfigScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetFontSelectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun frontpageMenuHighlightsSelectionAndSliderSavesIntermediateCounts() {
        var saved: WidgetConfiguration? = null
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                WidgetConfigScreen(WidgetConfiguration(), listOf(StoryType.TOP_STORIES, StoryType.BEST_STORIES, StoryType.UNSLOP),
                    onConfirm = { saved = it }, onBack = {})
            }
        }
        compose.onNodeWithText("Frontpage").performClick()
        compose.onAllNodesWithText("Top Stories").filter(isSelected()).assertCountEquals(1)
        compose.onNodeWithText("Best Stories").performClick()
        compose.onNodeWithText("Frontpage").performClick()
        compose.onAllNodesWithText("Best Stories").filter(isSelected()).assertCountEquals(1)
        compose.onNodeWithText("unslop.news").performClick()
        val slider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        val info = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(12f, info.current, 0f)
        assertEquals(8f..24f, info.range)
        assertEquals(15, info.steps)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(13f) }
        compose.onNodeWithText("Add widget").performClick()
        compose.runOnIdle {
            assertEquals(13, requireNotNull(saved).visibleStoryCount)
            assertEquals(StoryType.UNSLOP, requireNotNull(saved).storyType)
        }
    }

    @Test
    fun availableHeadlineIsSelectedByDefaultAndThePreviewFollowsTheSavedChoice() {
        val headline = mutableStateOf<FontFamily?>(FontFamily.Serif)
        var saved: WidgetConfiguration? = null
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                WidgetConfigScreen(WidgetConfiguration(), listOf(StoryType.TOP_STORIES),
                    onConfirm = { saved = it }, onBack = {}, headlineFontFamily = headline.value)
            }
        }
        compose.onNodeWithText("Device headline").performScrollTo().assertIsDisplayed()
        assertPreviewFamily(FontFamily.Serif)
        compose.onNodeWithText("Default").performClick()
        assertPreviewFamily(FontFamily.SansSerif)
        compose.onNodeWithText("Add widget").performClick()
        compose.runOnIdle { assertFalse(requireNotNull(saved).useHeadlineFont) }
        compose.runOnUiThread { headline.value = null }
        compose.onNodeWithText("Device headline").assertDoesNotExist()
        compose.onNodeWithText("Default").assertDoesNotExist()
    }

    private fun assertPreviewFamily(expected: FontFamily) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Algorithm breaks speed limit for solving linear equations", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(expected, layouts.single().layoutInput.style.fontFamily)
    }
}
