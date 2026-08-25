package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLaunchStateStoreTest {
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
