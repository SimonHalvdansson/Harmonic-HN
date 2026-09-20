package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.UserAvatarOptions
import com.simon.harmonichackernews.settings.UserAvatarStyle
import com.simon.harmonichackernews.ui.content.CommentItemStyle
import com.simon.harmonichackernews.ui.settings.UserAvatarSettingsScreen
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserAvatarSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun examplesCycleRerollWithoutSavingAndRemainPinned() {
        val enabled = mutableStateOf(true)
        val original = UserAvatarOptions(setOf(UserAvatarStyle.ROBOT, UserAvatarStyle.LANDSCAPE))
        val options = mutableStateOf(original)
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        val dependencies = HarmonicUiDependencies(app, scene)
        try {
            compose.setContent {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies) {
                    HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                        UserAvatarSettingsScreen(
                            enabled = enabled.value, options = options.value,
                            previewStyle = CommentItemStyle(
                                displayStyle = DisplayStyle.STANDARD, textSize = 14f,
                                collectLinks = false, emphasizeMeta = false,
                                depthIndicatorMode = "none", showDivider = false,
                                preferredFont = "default", animateChanges = false,
                            ),
                            onEnabledChanged = { enabled.value = it },
                            onOptionsChanged = { options.value = it }, onBack = {},
                        )
                    }
                }
            }
            compose.mainClock.autoAdvance = false
            fun previewText(row: Int): List<String> {
                fun text(node: SemanticsNode): List<String> =
                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                        node.children.flatMap { text(it) }
                return text(compose.onNodeWithTag("avatar-comment-preview-$row").fetchSemanticsNode())
            }
            val initial = (0..2).map { previewText(it) }
            compose.mainClock.advanceTimeBy(1_800)
            assertEquals(initial, (0..2).map { previewText(it) })
            compose.mainClock.advanceTimeBy(250)
            assertNotEquals(initial[0], previewText(0))
            assertEquals(initial[1], previewText(1))
            assertEquals(initial[2], previewText(2))
            compose.mainClock.advanceTimeBy(667)
            assertNotEquals(initial[1], previewText(1))
            assertEquals(initial[2], previewText(2))
            compose.mainClock.advanceTimeBy(667)
            assertNotEquals(initial[2], previewText(2))

            compose.mainClock.autoAdvance = true
            compose.onNodeWithText("Reroll examples").performScrollTo()
            compose.mainClock.autoAdvance = false
            val beforeReroll = (0..2).map { previewText(it) }
            compose.onNodeWithText("Reroll examples").performClick()
            compose.mainClock.advanceTimeBy(350)
            assertNotEquals(beforeReroll, (0..2).map { previewText(it) })
            assertEquals(original, options.value)

            compose.mainClock.autoAdvance = true
            compose.onNodeWithText("Generic").performScrollTo()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText("Generic").performClick()
            compose.mainClock.advanceTimeBy(100)
            assertTrue(options.value.generic)
            // Outgoing examples remain composed during the collapse, then disappear.
            compose.onNodeWithText("Mosaic").assertExists()
            compose.mainClock.advanceTimeBy(250)
            compose.onNodeWithText("Mosaic").assertDoesNotExist()
            compose.onNodeWithText("Reroll examples").assertDoesNotExist()
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText("Colors").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Muted").assertIsNotEnabled()
            compose.onNodeWithText("Expressive").performScrollTo().performClick()
            compose.mainClock.advanceTimeBy(350)
            assertEquals(original, options.value)

            compose.mainClock.autoAdvance = true
            compose.onNode(hasScrollAction()).performScrollToIndex(3)
            compose.onNodeWithTag("avatar-comment-preview").assertIsDisplayed()
            compose.onNodeWithText("Show user profile images").assertIsNotDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
            scene.close()
        }
    }
}
