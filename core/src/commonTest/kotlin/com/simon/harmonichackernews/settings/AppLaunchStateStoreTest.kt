package com.simon.harmonichackernews.settings

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
    fun versionUpgradeIsConsumedOnlyForNewerVersions() {
        val store = AppLaunchStateStore(TestKeyValueStore())

        assertTrue(store.consumeVersionUpgrade(12))
        assertFalse(store.consumeVersionUpgrade(12))
        assertFalse(store.consumeVersionUpgrade(11))
        assertTrue(store.consumeVersionUpgrade(13))
    }

    @Test
    fun welcomeTakesPriorityWhileStillConsumingTheCurrentVersion() {
        val store = AppLaunchStateStore(TestKeyValueStore())

        assertEquals(
            AppLaunchDialog.WELCOME,
            store.consumeLaunchDialog(currentVersion = 12, showChangelog = true),
        )
        store.markWelcomeDialogShown()
        assertEquals(
            AppLaunchDialog.NONE,
            store.consumeLaunchDialog(currentVersion = 12, showChangelog = true),
        )
        assertEquals(
            AppLaunchDialog.CHANGELOG,
            store.consumeLaunchDialog(currentVersion = 13, showChangelog = true),
        )
    }
}
