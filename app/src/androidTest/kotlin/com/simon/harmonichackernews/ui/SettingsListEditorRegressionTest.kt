package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.settings.StringListEditorDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsListEditorRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun legacyDuplicatesCanBeDisplayedRemovedAndEditedWithoutDuplicateKeys() {
        var savedItems: List<String>? = null
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                StringListEditorDialog(
                    title = "Filter by story title",
                    subtitle = "Hide stories containing these words",
                    inputLabel = "Words",
                    initialItems = listOf("AI", "ai", "Example.com", "example.com"),
                    emptyMessage = "No filters",
                    parseInput = { input -> input.split(',').map(String::trim).filter(String::isNotEmpty) },
                    onItemsChanged = { savedItems = it },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("AI").assertIsDisplayed()
        compose.onNodeWithText("Example.com").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, savedItems) }
        compose.onNodeWithContentDescription("Remove AI").performClick()
        compose.runOnIdle { assertEquals(listOf("Example.com"), savedItems) }

        compose.onNode(hasSetTextAction()).performTextInput("EXAMPLE.COM,Music,MUSIC")
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Music").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("Example.com", "Music"), savedItems) }
    }
}
