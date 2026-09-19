package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.stories.StoriesRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoriesAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun searchHidesRetainedFeedSemanticsAndBackRestoresItsState() {
        val searching = mutableStateOf(false)
        val predictiveBack = mutableStateOf(false)
        compose.setContent {
            StoriesRoot(
                searching = searching.value,
                suppressSearchAutoFocus = false,
                predictiveBackActive = predictiveBack.value,
                predictiveBackProgress = if (predictiveBack.value) 0.5f else 0f,
                backgroundColor = Color.White,
                mainLayer = {
                    val clicks = remember { mutableStateOf(0) }
                    BasicText("Feed clicks: ${clicks.value}", Modifier.clickable { clicks.value++ })
                },
                searchLayer = { BasicText("Search results") },
            )
        }
        compose.onNodeWithText("Feed clicks: 0").performClick()
        compose.onNodeWithText("Feed clicks: 1").assertIsDisplayed()
        compose.onNodeWithText("Search results").assertDoesNotExist()

        compose.runOnIdle { searching.value = true }
        compose.onNodeWithText("Search results").assertIsDisplayed()
        compose.onNodeWithText("Feed clicks: 1").assertDoesNotExist()

        compose.runOnIdle { predictiveBack.value = true }
        compose.onNodeWithText("Search results").assertDoesNotExist()
        compose.onNodeWithText("Feed clicks: 1").assertDoesNotExist()

        compose.runOnIdle { predictiveBack.value = false }
        compose.onNodeWithText("Search results").assertIsDisplayed()

        compose.runOnIdle { searching.value = false }
        compose.onNodeWithText("Feed clicks: 1").assertIsDisplayed()
        compose.onNodeWithText("Search results").assertDoesNotExist()
    }
}
