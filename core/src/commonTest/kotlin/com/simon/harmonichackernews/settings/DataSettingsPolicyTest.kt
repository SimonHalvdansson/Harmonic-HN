package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.platform.CredentialIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class DataSettingsPolicyTest {
    @Test
    fun snapshotUsesTypedSettingsAndSanitizesCounts() {
        val store = TestKeyValueStore(
            mapOf(
                UserPreferenceKeys.BOOKMARKS_ENABLED to false,
                UserPreferenceKeys.SHOW_CHANGELOG to false,
            ),
        )
        val settings = AppSettingsRepository(store, emptyFlow()).snapshot()

        val result = DataSettingsPolicy.snapshot(
            settings,
            DataSettingsCounts(
                bookmarks = -1,
                history = 2,
                postCache = 3,
                tintCache = 4,
                aiModelsBytes = -5L,
            ),
            loggedIn = true,
        )

        assertFalse(result.bookmarksEnabled)
        assertFalse(result.showChangelog)
        assertEquals(0, result.bookmarkCount)
        assertEquals(2, result.historyCount)
        assertEquals(0L, result.aiModelBytes)
        assertTrue(result.loggedIn)
    }

    @Test
    fun actionValidationAndFormattingAreStable() {
        assertEquals(BookmarkExportDecision.Empty, DataSettingsPolicy.exportDecision(0))
        assertEquals(BookmarkExportDecision.Ready, DataSettingsPolicy.exportDecision(1))
        assertEquals("Cleared 1 entry", DataSettingsPolicy.clearedItemsMessage(1, "entry", "entries"))
        assertNull(DataSettingsPolicy.clearedItemsMessage(0, "entry", "entries"))
        assertEquals(
            "HarmonicBookmarks2026-8-12.txt",
            DataSettingsPolicy.bookmarksFilename(2026, 8, 12),
        )
    }

    @Test
    fun resetPreservesContentAndLoginWhileClearingSettingsAndAiKey() = runTest {
        val defaults = TestKeyValueStore(mapOf(UserPreferenceKeys.SHOW_POINTS to false))
        val global = TestKeyValueStore(
            mapOf(
                NighttimeScheduleKeys.FROM_HOUR to "22",
                UserTagKeys.TAGS to "preserved",
            ),
        )
        val credentials = TestCredentialStore(
            mapOf(
                CredentialIds.AI_SUMMARY_API_KEY to "secret",
                CredentialIds.HACKER_NEWS_USERNAME to "user",
            ),
        )

        val aiSettings = AiSummarySettingsRepository(defaults, credentials, emptyFlow())
        SettingsResetUseCase(defaults, global, aiSettings).execute()

        assertFalse(defaults.contains(UserPreferenceKeys.SHOW_POINTS))
        assertFalse(global.contains(NighttimeScheduleKeys.FROM_HOUR))
        assertEquals("preserved", global.getString(UserTagKeys.TAGS))
        assertNull(credentials.read(CredentialIds.AI_SUMMARY_API_KEY))
        assertEquals("user", credentials.read(CredentialIds.HACKER_NEWS_USERNAME))
    }
}
