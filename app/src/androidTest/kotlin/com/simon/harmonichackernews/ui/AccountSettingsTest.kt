package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.WindowCompat
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.network.*
import com.simon.harmonichackernews.presentation.NotificationsSettingsState
import com.simon.harmonichackernews.presentation.DebugAccountIdentity
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.platform.HackerNewsAccountState
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.settings.AccountSettingsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import com.simon.harmonichackernews.ui.settings.AccountSettingsScreen
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.harmonicThemePalette
import com.simon.harmonichackernews.settings.ThemeSelection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses production UI with deterministic account activity; never changes the device's HN login. */
@RunWith(AndroidJUnit4::class)
class AccountSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = 1_800_000_000_000L
    private val first = HackerNewsReply(101, 20, "alice", "I tried this approach too. Keeping the state in one place made it much easier to follow.",
        time = (now / 1000 - 480).toInt(), storyId = 10, storyTitle = "Building small tools that last")
    private val second = HackerNewsReply(102, 10, "jules", "Thanks for sharing this. The examples make the trade-offs really clear.",
        time = (now / 1000 - 1800).toInt(), storyId = 10, storyTitle = "Building small tools that last", kind = AccountActivityKind.SubmissionComment)
    private val state = mutableStateOf(NotificationsSettingsState(enabled = true, supported = true,
        activity = AccountActivitySnapshot(initialized = true, checkpoint = 102, lastCheckedMillis = now - 120_000,
            entries = listOf(AccountActivityEntry(first), AccountActivityEntry(second, read = true)))))
    private var checked = false
    private var opened = 0
    private var login = false
    private var logout = false
    private var systemSettings = false
    private var scene: com.simon.harmonichackernews.app.HarmonicSceneComposition? = null

    @After fun closeScene() { scene?.close() }

    private fun show(username: String? = "simon", blocked: Boolean = false, dark: Boolean = false) {
        WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene().also { this.scene = it }
        compose.setContent {
            CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(app, scene)) {
                val palette = harmonicThemePalette(compose.activity, ThemeSelection.forScheme("dynamic", dark))
                HarmonicTheme(palette.colorScheme, dark) {
                    AccountSettingsScreen(username, state.value, busy = state.value.operation != null, deliveryAllowed = !blocked,
                        showNavigation = true, nowMillis = now, onBack = {}, onLogin = { login = true },
                        onLogout = { logout = true }, onEnabledChanged = { state.value = state.value.copy(enabled = it) },
                        onCheckNow = { checked = true; state.value = state.value.copy(activity = state.value.activity.copy(lastCheckedMillis = now + 500)) },
                        onFrequencyChanged = { state.value = state.value.copy(checkFrequency = it) },
                        onPreferencesChanged = { state.value = state.value.copy(activity = state.value.activity.copy(alerts = it)) },
                        onOpenSystemSettings = { systemSettings = true }, onOpenReply = { opened = it.reply.id },
                        onMarkAllRead = { state.value = state.value.copy(activity = state.value.activity.copy(entries = state.value.activity.entries.map { it.copy(read = true) })) },
                        onMute = { id, title, muted ->
                            state.value = state.value.copy(activity = state.value.activity.copy(mutedDiscussions =
                                if (muted) listOf(MutedDiscussion(id, title)) else emptyList()))
                        })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun list() = compose.onNode(hasScrollAction() and !hasAnyAncestor(hasScrollAction()))
    private fun click(text: String) {
        list().performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }

    @Test fun activityCanBeReadFilteredMutedAndUnmuted() {
        show()
        compose.onNodeWithText("Last checked 2m ago").assertIsDisplayed()
        compose.onNodeWithText("Check now").performClick()
        assertTrue(checked)
        compose.onNodeWithText("Last checked just now").assertIsDisplayed()
        compose.onNodeWithTag("account-avatar-101", useUnmergedTree = true).assertWidthIsEqualTo(28.dp)
        click("Unread")
        compose.onNodeWithText("jules").assertDoesNotExist()
        click("alice")
        assertEquals(101, opened)
        compose.onNodeWithContentDescription("Activity options for reply 101").performClick()
        compose.onNodeWithText("Mute discussion").performClick()
        assertEquals(listOf(10), state.value.activity.mutedDiscussions.map { it.id })
        click("Muted discussions")
        compose.onNodeWithText("Unmute").performClick()
        assertTrue(state.value.activity.mutedDiscussions.isEmpty())
        compose.onNodeWithText("Done").performClick()
        click("Mark all read")
        compose.onNodeWithText("You're all caught up").assertIsDisplayed()
        click("All")
        compose.onNodeWithText("alice").assertExists()
    }

    @Test fun alertTypesNetworkAndHoursAreIndependentControls() {
        show(blocked = true)
        click("Replies to your comments")
        assertFalse(state.value.activity.alerts.replies)
        assertTrue(state.value.activity.alerts.submissionComments)
        click("Comments on your submissions")
        assertFalse(state.value.activity.alerts.submissionComments)
        click("System notification settings")
        assertTrue(systemSettings)
        list().performScrollToNode(hasText("Checking hours"))
        compose.onNodeWithText("Checking hours").assertIsNotEnabled()
        click("Wi-Fi only")
        assertTrue(state.value.activity.alerts.wifiOnly)
        click("Limit checking hours")
        click("Checking hours")
        compose.onNodeWithText("From (HH:mm)").assertExists()
        compose.onNodeWithText("Until (HH:mm)").performTextReplacement("08:00")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Until (HH:mm)").performTextReplacement("07:30")
        compose.onNodeWithText("Save").performClick()
        assertEquals(480, state.value.activity.alerts.startMinute)
        assertEquals(450, state.value.activity.alerts.endMinute)
        click("Check automatically")
        assertFalse(state.value.enabled)
        list().performScrollToIndex(0)
        compose.onNodeWithText("Check now").assertIsEnabled()
        compose.onNodeWithText("Log out").performClick()
        assertTrue(logout)
    }

    @Test fun loggedOutAccountOffersLoginAndKeepsTheAccountTitle() {
        show(username = null)
        compose.onNodeWithText("Account").assertIsDisplayed()
        compose.onNodeWithText("Log in").performClick()
        assertTrue(login)
        compose.onNodeWithText("Check now").assertDoesNotExist()
    }

    @Test fun logoutIsAvailableWhileActivityIsLoading() {
        state.value = state.value.copy(operation = com.simon.harmonichackernews.presentation.NotificationSettingsOperation.Checking)
        show()
        compose.onNodeWithText("Check now").assertIsNotEnabled()
        compose.onNodeWithText("Log out").assertIsEnabled().performClick()
        assertTrue(logout)
    }

    @Test fun filteringAnimatesActivityHeightAndEmptyViews() {
        show()
        compose.mainClock.autoAdvance = false
        try {
            fun height() = compose.onNodeWithTag("account-activity-content").fetchSemanticsNode().size.height
            val allHeight = height()
            compose.onNodeWithText("Unread").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(130)
            val middleHeight = height()
            compose.mainClock.advanceTimeBy(1000)
            val unreadHeight = height()
            assertTrue("Height should interpolate instead of jumping", middleHeight in (unreadHeight + 1) until allHeight)
            compose.runOnIdle { state.value = state.value.copy(activity = state.value.activity.copy(entries = emptyList())) }
            compose.mainClock.advanceTimeBy(1000)
            val emptyUnreadHeight = height()
            compose.onNodeWithText("All").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(130)
            val emptyMiddleHeight = height()
            compose.mainClock.advanceTimeBy(1000)
            val emptyAllHeight = height()
            assertTrue(emptyMiddleHeight in (emptyUnreadHeight + 1) until emptyAllHeight)
            compose.onNodeWithText("No recent replies yet").assertIsDisplayed()
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun mutedStatusAnimatesRowHeightInBothDirectionsAndMenuAlignsWithMetadata() {
        show()
        val menu = compose.onNodeWithContentDescription("Activity options for reply 101").fetchSemanticsNode().boundsInRoot
        val author = compose.onNodeWithText("alice", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithText("Replied to your comment", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("Overflow aligns with the two-line metadata", (author.top + action.bottom) / 2, menu.center.y, author.height / 2)
        compose.mainClock.autoAdvance = false
        try {
            fun height() = compose.onNodeWithTag("account-activity-101").fetchSemanticsNode().size.height
            val unmuted = height()
            compose.runOnIdle { state.value = state.value.copy(activity = state.value.activity.copy(mutedDiscussions = listOf(MutedDiscussion(10, "Tools")))) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(100)
            val entering = height()
            compose.mainClock.advanceTimeBy(500)
            val muted = height()
            assertTrue("Muted status grows smoothly", entering in (unmuted + 1) until muted)
            compose.onNodeWithTag("account-muted-101", useUnmergedTree = true).assertExists()
            compose.runOnIdle { state.value = state.value.copy(activity = state.value.activity.copy(mutedDiscussions = emptyList())) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(100)
            assertTrue("Unmuting shrinks smoothly", height() in (unmuted + 1) until muted)
            compose.mainClock.advanceTimeBy(500)
            assertEquals(unmuted, height())
            compose.onNodeWithTag("account-muted-101", useUnmergedTree = true).assertDoesNotExist()
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun accountPreviewLogoutAndLoginRetainTheOutgoingSurfaceDuringTransitions() {
        // This vault and preview exist only in memory; the device's saved login is never used.
        val account = HackerNewsAccount("real-reader", "test-only")
        val accounts = object : ObservableHackerNewsAccountRepository {
            override val accountState = MutableStateFlow<HackerNewsAccountState>(HackerNewsAccountState.LoggedIn(account))
            override suspend fun saveAccount(account: HackerNewsAccount): Boolean {
                accountState.value = HackerNewsAccountState.LoggedIn(account)
                return true
            }
            override suspend fun clearAccount(): Boolean {
                accountState.value = HackerNewsAccountState.LoggedOut
                return true
            }
        }
        val preview = DebugAccountIdentity(InMemoryKeyValueStore(), enabled = true).apply { setUsername("preview-reader") }
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene().also { this.scene = it }
        compose.setContent {
            CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(app, scene)) {
                val palette = harmonicThemePalette(compose.activity, ThemeSelection.forScheme("dynamic", false))
                HarmonicTheme(palette.colorScheme, false) {
                    AccountSettingsRoute(accounts, notifications = null, showNavigation = true, onBack = {},
                        onLogin = { accounts.accountState.value = HackerNewsAccountState.LoggedIn(account) },
                        onOpenReply = {}, debugIdentity = preview)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            fun transition(action: String, outgoing: String, incoming: String) {
                compose.onNodeWithText(action).performClick()
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeBy(48)
                compose.onNodeWithText(outgoing).assertExists()
                compose.onNodeWithText(incoming).assertExists()
                compose.mainClock.advanceTimeBy(500)
                compose.onNodeWithText(outgoing).assertDoesNotExist()
                compose.onNodeWithText(incoming).assertIsDisplayed()
            }
            transition("Use real account", "preview-reader", "real-reader")
            assertEquals(account, accounts.currentAccount)
            transition("Log out", "real-reader", "Your Hacker News account")
            transition("Log in", "Your Hacker News account", "real-reader")
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun populatedAccountRendersInBothThemes() {
        val dark = InstrumentationRegistry.getArguments().getString("accountDark") == "true"
        val section = InstrumentationRegistry.getArguments().getString("accountPreviewSection")
        if (section == "background") state.value = state.value.copy(activity = state.value.activity.copy(alerts = AccountAlertPreferences(limitedHours = true)))
        show(dark = dark)
        compose.onNodeWithText("simon").assertIsDisplayed()
        compose.onNodeWithText("Check now").assertIsDisplayed()
        list().performScrollToNode(hasText("alice"))
        compose.onNodeWithText("alice").assertIsDisplayed()
        list().performScrollToIndex(0)
        if (section == "background") list().performScrollToNode(hasText("Check automatically"))
        if (section == "notifications") list().performScrollToNode(hasText("Replies to your comments"))
        if (section == "menu") {
            compose.onNodeWithContentDescription("Activity options for reply 101").performClick()
            compose.waitForIdle()
        }
        // Optional bounded pause lets Android CLI capture the production screen on an emulator.
        InstrumentationRegistry.getArguments().getString("accountPreviewMs")?.toLongOrNull()
            ?.coerceIn(0, 30_000)?.let(Thread::sleep)
    }
}
