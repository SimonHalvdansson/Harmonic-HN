package com.simon.harmonichackernews.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.settings.BlockedUsersDialog
import com.simon.harmonichackernews.ui.settings.LegacyUsernameReviewDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsernameIdentityDialogsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun profileAndRemoveActionsKeepCaseDistinct() {
        val users = mutableStateOf(listOf("Alice", "alice"))
        var opened: String? = null
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                BlockedUsersDialog(
                    users = users.value, legacyUsers = emptyList(),
                    onUsersChanged = { users.value = it },
                    onProfileRequested = { opened = it },
                    onLegacyReviewRequested = {}, onLegacyDiscardRequested = {}, onDismiss = {},
                )
            }
        }
        compose.onNodeWithContentDescription("View profile for Alice").performClick()
        assertEquals("Alice", opened)
        compose.onNodeWithContentDescription("View profile for alice").performClick()
        assertEquals("alice", opened)
        compose.onNodeWithContentDescription("Unblock Alice").performClick()
        compose.runOnIdle { assertEquals(listOf("alice"), users.value) }
        compose.onNodeWithContentDescription("View profile for alice").assertExists()
    }

    @Test
    fun legacyBlocksOfferReviewAndRemovalWithoutBindingAProfile() {
        var reviewed: String? = null
        var discarded: String? = null
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                BlockedUsersDialog(
                    users = emptyList(), legacyUsers = listOf("olduser"),
                    onUsersChanged = {}, onProfileRequested = {},
                    onLegacyReviewRequested = { reviewed = it },
                    onLegacyDiscardRequested = { discarded = it }, onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("1 needs review · Inactive").assertExists()
        compose.onNodeWithContentDescription("View profile for olduser").assertDoesNotExist()
        compose.onNodeWithContentDescription("Review saved block for olduser").performClick()
        assertEquals("olduser", reviewed)
        compose.onNodeWithContentDescription("Remove saved block for olduser").performClick()
        assertEquals("olduser", discarded)
    }

    @Test
    fun legacyConfirmationRequiresDeliberateInputAndRetainsConflicts() {
        var attempted: String? = null
        var dismissed = false
        val conflict = "That exact user already has a tag. Both saved tags have been kept."
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                LegacyUsernameReviewDialog(
                    title = "Confirm tagged user", savedUsername = "alice", savedTag = "Legacy label",
                    onResolve = { attempted = it; conflict }, onDismiss = { dismissed = true },
                )
            }
        }
        compose.onNodeWithText("Confirm username").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Alice")
        compose.onNodeWithText("Confirm username").performClick()
        compose.waitUntil(5_000) { attempted != null }
        assertEquals("Alice", attempted)
        assertFalse(dismissed)
        compose.onNodeWithText(conflict).assertExists()
    }

    @Test
    @Suppress("DEPRECATION")
    fun constrainedRowsGrowAtDoubleFontScaleAndKeepFullSizeActions() {
        val legacy = mutableStateOf(false)
        val username = "WWWWWWWWWWWWWWW"
        // Android Dialog installs a fresh platform density. Updating this Activity's resources
        // makes the native dialog itself use 200%; an outer composition local alone does not.
        // This changes only the test app's resource configuration, never the device setting.
        val resources = compose.activity.resources
        val originalConfiguration = Configuration(resources.configuration)
        compose.runOnUiThread {
            resources.updateConfiguration(Configuration(originalConfiguration).apply { fontScale = 2f }, resources.displayMetrics)
        }
        try {
            compose.setContent {
                val density = LocalDensity.current
                val palette = HarmonicThemeCatalog.resolve("light", false)
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    HarmonicTheme(palette.colorScheme, palette.dark) {
                        BlockedUsersDialog(
                            users = if (legacy.value) emptyList() else listOf(username),
                            legacyUsers = if (legacy.value) listOf(username) else emptyList(),
                            onUsersChanged = {}, onProfileRequested = {}, onLegacyReviewRequested = {},
                            onLegacyDiscardRequested = {}, onDismiss = {},
                            modifier = Modifier.widthIn(max = 280.dp),
                        )
                    }
                }
            }
            fun checkRow(prefix: String, action: String, minimumHeight: Int) {
                compose.onNodeWithTag("blocked-users-list").performScrollTo()
                compose.onNodeWithTag("blocked-users-list").performScrollToNode(hasTestTag("$prefix:$username"))
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(username).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertEquals("The actual native dialog must use 200% text", 2f, layout.layoutInput.density.fontScale, .001f)
                assertFalse("Username must wrap fully rather than clip", layout.didOverflowHeight)
                val row = compose.onNodeWithTag("$prefix:$username").getUnclippedBoundsInRoot()
                assertTrue("Row must grow: bounds=$row, fontScale=${layout.layoutInput.density.fontScale}, " +
                    "lines=${layout.lineCount}, textSize=${layout.size}", row.bottom - row.top > minimumHeight.dp)
                val actionNode = compose.onNodeWithContentDescription(action).assertHasClickAction()
                actionNode.performScrollTo()
                // IconButton paints a 40dp surface inside its minimum interactive area. Verify
                // the actual expanded hit region in pixels rather than the decorative surface.
                val target = actionNode.fetchSemanticsNode().touchBoundsInRoot
                val minimum = with(compose.density) { 48.dp.toPx() }
                assertTrue("Touch width must be at least 48dp: $target", target.width >= minimum - .5f)
                assertTrue("Touch height must be at least 48dp: $target", target.height >= minimum - .5f)
            }
            checkRow("blocked-active-row", "View profile for $username", 52)
            compose.runOnIdle { legacy.value = true }
            checkRow("blocked-legacy-row", "Review saved block for $username", 72)
        } finally {
            compose.runOnUiThread { resources.updateConfiguration(originalConfiguration, resources.displayMetrics) }
        }
    }
}
