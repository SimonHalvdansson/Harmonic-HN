package com.simon.harmonichackernews.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.harmonicAppComposition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugAccountSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun publicAccountPreviewOpensTheInboxAndLeavesCredentialsUntouched() {
        val app = compose.activity.harmonicAppComposition
        assumeFalse("Keep any existing background subscription intact", app.replyNotifications?.isEnabled == true)
        val originalAccount = runBlocking { app.platform.accounts.awaitAccount() }
        val originalPreview = app.debugAccountIdentity.state.value.username
        compose.runOnIdle {
            compose.activity.navigationController.closeWelcome()
            compose.activity.navigationController.dismissChangelogDialog()
            compose.activity.navigationController.openSettings("debug")
        }
        try {
            val list = compose.onNode(hasScrollAction() and !hasAnyAncestor(hasScrollAction()))
            list.performScrollToNode(hasText("HN username"))
            compose.onNodeWithText("HN username").performTextReplacement("pg")
            compose.onNodeWithText("Use account").performScrollTo().performClick()
            compose.waitUntil { app.debugAccountIdentity.state.value.username == "pg" }
            compose.onNodeWithText("Open Account settings").performScrollTo().performClick()
            compose.onNodeWithText("pg").assertIsDisplayed()
            compose.onNodeWithText("Debug account · Public activity").assertIsDisplayed()
            assertEquals(originalAccount, app.platform.accounts.currentAccount)
            compose.onNodeWithText("Use real account").performClick()
            compose.waitUntil { app.debugAccountIdentity.state.value.username == null }
            assertEquals(originalAccount, app.platform.accounts.currentAccount)
        } finally {
            compose.runOnIdle {
                app.debugAccountIdentity.setUsername(originalPreview)
                compose.activity.navigationController.closeSettings()
            }
        }
    }
}
