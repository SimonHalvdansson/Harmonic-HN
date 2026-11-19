package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.app.AppMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLaunchStateStoreTest {
    @Test
    fun acceptingWelcomeQueuesHintUntilDismissedEvenAcrossRestarts() {
        val preferences = TestKeyValueStore()
        val store = AppLaunchStateStore(preferences)
        assertFalse(store.storiesMenuHintPending.value)

        store.completeWelcome()
        assertFalse(store.shouldShowWelcomeDialog)
        assertTrue(store.storiesMenuHintPending.value)

        val restarted = AppLaunchStateStore(preferences)
        assertTrue(restarted.storiesMenuHintPending.value)
        restarted.dismissStoriesMenuHint()
        assertFalse(restarted.storiesMenuHintPending.value)
        assertFalse(AppLaunchStateStore(preferences).storiesMenuHintPending.value)

        restarted.completeWelcome()
        assertFalse(restarted.storiesMenuHintPending.value, "Reopening Welcome must not offer the hint again")
    }

    @Test
    fun existingUsersAndWelcomeBackDismissalDoNotOfferHint() {
        val preferences = TestKeyValueStore()
        val store = AppLaunchStateStore(preferences)
        store.markWelcomeDialogShown()
        store.completeWelcome()
        assertFalse(store.storiesMenuHintPending.value)
        assertFalse(AppLaunchStateStore(preferences).storiesMenuHintPending.value)
    }

    @Test
    fun welcomeIsShownOnceAfterBeingMarked() {
        val store = AppLaunchStateStore(TestKeyValueStore())

        assertTrue(store.shouldShowWelcomeDialog)
        store.markWelcomeDialogShown()
        assertFalse(store.shouldShowWelcomeDialog)
    }

    @Test
    fun changelogAppearsOncePerMajorOrMinorUpgrade() {
        val preferences = TestKeyValueStore()
        val store = AppLaunchStateStore(preferences)
        fun launch(name: String, code: Int, enabled: Boolean = true) =
            store.consumeLaunchDialog(AppMetadata(versionName = name, versionCode = code), enabled)

        assertEquals(AppLaunchDialog.WELCOME, launch("3.2.7", 89))
        store.markWelcomeDialogShown()
        assertEquals(AppLaunchDialog.NONE, launch("3.2.7", 89))
        assertEquals(AppLaunchDialog.NONE, launch("3.2.8", 90))
        assertEquals(AppLaunchDialog.CHANGELOG, launch("3.3.1", 92))
        assertEquals(AppLaunchDialog.NONE, launch("3.2.8", 90))
        assertEquals(AppLaunchDialog.NONE, launch("3.3.1", 92))
        assertEquals(AppLaunchDialog.CHANGELOG, launch("4.0", 93))
        assertEquals(AppLaunchDialog.NONE, launch("4.0.1", 94))
        assertEquals(AppLaunchDialog.NONE, launch("4.1", 95, enabled = false))
        assertEquals(AppLaunchDialog.NONE, launch("4.1.1", 96))
    }

    @Test
    fun existingInstallUsesItsLastBuildNumberForTheFirstUpgrade() {
        fun upgrade(previousCode: Int, versionName: String): AppLaunchDialog {
            val preferences = TestKeyValueStore()
            preferences.putInt(AppLaunchPreferenceKeys.LAST_VERSION, previousCode)
            val store = AppLaunchStateStore(preferences)
            store.markWelcomeDialogShown()
            return store.consumeLaunchDialog(AppMetadata(versionName = versionName, versionCode = 90), true)
        }

        assertEquals(AppLaunchDialog.NONE, upgrade(89, "3.2.8"))
        assertEquals(AppLaunchDialog.NONE, upgrade(81, "3.2.8"))
        assertEquals(AppLaunchDialog.CHANGELOG, upgrade(80, "3.2.8"))
        assertEquals(AppLaunchDialog.CHANGELOG, upgrade(89, "3.3"))
    }
}
