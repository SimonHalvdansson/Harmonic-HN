package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.StringListEditorDialog
import com.simon.harmonichackernews.ui.settings.BlockedUsersDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilterListEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun addingAndRemovingItemsAnimatesTheDialogListHeight() {
        val items = mutableStateOf(listOf("Alpha", "Beta"))
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                StringListEditorDialog(
                    title = "Filter by story title",
                    subtitle = "Matching ignores capitalization.",
                    inputLabel = "Word or phrase",
                    initialItems = items.value,
                    emptyMessage = "No filters",
                    parseInput = { listOf(it) },
                    onItemsChanged = { items.value = it },
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
        fun listHeight() = compose.onNodeWithTag("filter-list").fetchSemanticsNode().boundsInRoot.height
        val before = listHeight()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { items.value += "Gamma" }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(112)
            val growing = listHeight()
            compose.mainClock.advanceTimeBy(400)
            val grown = listHeight()
            assertTrue("An added row must grow through intermediate heights: $before, $growing, $grown", growing > before + 1f && growing < grown - 1f)

            compose.runOnIdle { items.value = listOf("Alpha") }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(112)
            val shrinking = listHeight()
            compose.mainClock.advanceTimeBy(400)
            val shrunk = listHeight()
            assertTrue("Removed rows must shrink through intermediate heights: $grown, $shrinking, $shrunk", shrinking < grown - 1f && shrinking > shrunk + 1f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun blockedUserProfileActionPreservesCapitalizationAndKeepsTheListOpen() {
        val profileUser = mutableStateOf<String?>(null)
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                BlockedUsersDialog(
                    users = listOf("MixedCase"),
                    legacyUsers = emptyList(),
                    onProfileRequested = { profileUser.value = it },
                    onLegacyReviewRequested = {},
                    onLegacyDiscardRequested = {},
                    nestedDialog = {
                        profileUser.value?.let { username ->
                            SettingsAlertDialog(
                                onDismissRequest = { profileUser.value = null },
                                text = { Text("Profile for $username") },
                                confirmButton = {},
                                showButtons = false,
                            )
                        }
                    },
                    onUsersChanged = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithContentDescription("View profile for MixedCase").performClick()
        assertEquals("MixedCase", profileUser.value)
        compose.onNodeWithText("Profile for MixedCase").assertExists()
        compose.onNodeWithContentDescription("Unblock MixedCase").assertExists()
    }
}
