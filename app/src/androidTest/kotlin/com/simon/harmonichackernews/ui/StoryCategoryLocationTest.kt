package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.categorization.ArticleCategorySettings
import com.simon.harmonichackernews.settings.*
import com.simon.harmonichackernews.ui.content.*
import com.simon.harmonichackernews.ui.settings.StoriesSettingsRoute
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production settings and rows, with isolated stores and a bundled preview image. */
@RunWith(AndroidJUnit4::class)
class StoryCategoryLocationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun categorizationControlsAvailabilityWithoutLosingTheSavedLocation() {
        val store = InMemoryKeyValueStore()
        val settings = AppSettingsRepository(store, store.changes)
        val categories = ArticleCategorySettings(store, store.changes)
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                StoriesSettingsRoute(
                    repository = settings, categorySettings = categories,
                    previewModel = SettingsStoryPreviewModel, faviconIcon = ColorPainter(Color.Gray),
                    showNavigation = true, onBack = {}, onPlatformEffect = {},
                    onManageFrontpages = {}, onPaletteTintRequested = {}, faviconDialog = { _, _, _, _ -> },
                )
            }
        }
        compose.onNodeWithText("Under title").assertIsNotEnabled()
        compose.onNodeWithText("Metadata").assertIsNotEnabled()
        compose.onNodeWithText("Metadata").assertHeightIsEqualTo(40.dp)
        compose.runOnIdle { categories.setEnabled(true) }
        compose.onNodeWithText("Metadata").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(CategoryTagLocation.METADATA, settings.snapshot().story.categoryTagLocation) }
        compose.onNodeWithTag("story-details", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { categories.setEnabled(false) }
        compose.onNodeWithText("Metadata").assertIsNotEnabled().assertIsSelected()
        compose.onNodeWithTag("story-details", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { categories.setEnabled(true) }
        compose.onNodeWithText("Metadata").assertIsEnabled().assertIsSelected()
        compose.onNodeWithTag("story-details", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun narrowLargeTextKeepsTaggedAuthorAndMetadataBelowTheImage() {
        val mode = mutableStateOf(StoryPreviewMode.SMALL)
        val location = mutableStateOf(CategoryTagLocation.METADATA)
        compose.setContent {
            val density = LocalDensity.current
            val palette = HarmonicThemeCatalog.resolve("light", false)
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    Box(Modifier.width(280.dp)) {
                        StoryRow(
                            SettingsStoryPreviewModel.copy(
                                title = "A useful new tool", categoryLabels = listOf("AI", "Science"),
                                authorName = "LongTaggedUsername", authorTag = "A long personal tag for this author",
                                authorTagColor = UserTagColor.Purple,
                            ),
                            StoryRowStyle(
                                previewImageMode = mode.value, categoryTagLocation = location.value,
                                borderlessLargeImage = false, compact = false, showPreviewText = false,
                                showFavicon = false, showPoints = true, compactPoints = false,
                                includeTopLevelDomain = true, showCommentCount = true, showIndex = true,
                                commentsOnLeft = false, tintCard = false, displayStyle = DisplayStyle.STANDARD,
                                useHotnessIcon = false, preferredFont = "devicedefault", textSize = 16f,
                            ),
                            animateChanges = false,
                        )
                    }
                }
            }
        }
        for (previewMode in listOf(StoryPreviewMode.SMALL, StoryPreviewMode.MEDIUM)) {
            compose.runOnIdle { mode.value = previewMode }
            val image = compose.onNodeWithTag(
                if (previewMode == StoryPreviewMode.SMALL) "story-small-preview" else "story-medium-preview",
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            val details = compose.onNodeWithTag("story-details", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("Metadata must follow the entire image", image.bottom <= details.top + 1f)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("story-author-tag", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("The long author tag must wrap", layouts.single().didOverflowWidth || layouts.single().didOverflowHeight)
        }
        compose.runOnIdle { location.value = CategoryTagLocation.TITLE }
        compose.onNodeWithTag("story-details", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("story-author-tag", useUnmergedTree = true).assertExists()
    }
}
