package com.simon.harmonichackernews

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.settings.UserTagCodec
import com.simon.harmonichackernews.settings.UserTagColor
import com.simon.harmonichackernews.settings.UserTagKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserTagsUpgradeTest {
    @Test
    fun applicationPreservesStoredLegacySpellingsAndKeepsExactEditsSeparate() {
        val app = runBlocking { InstrumentationRegistry.getInstrumentation().targetContext.awaitHarmonicAppComposition() }
        val content = app.host.appDataStore
        val settings = app.host.settingsStore
        val stringKeys = listOf(UserTagKeys.TAGS, UserTagKeys.COLORS, UserTagKeys.EXACT_TAGS, UserTagKeys.EXACT_COLORS)
        val originalContent = stringKeys.associateWith { content.getString(it) }
        val originalSettings = stringKeys.associateWith { settings.getString(it) }
        val hadReviewed = content.contains(UserTagKeys.REVIEWED_LEGACY_TAGS)
        val originalReviewed = content.getStringSet(UserTagKeys.REVIEWED_LEGACY_TAGS)
        try {
            // Old releases used the global content store. Keep every stored spelling active.
            val rawTags = """{"UpgradeTestUser":"From 3.1.2","upgradetestuser":"Case lost before 3.1.2"}"""
            val rawColors = """{"UpgradeTestUser":"purple","upgradetestuser":"red"}"""
            content.update {
                putString(UserTagKeys.TAGS, rawTags)
                putString(UserTagKeys.COLORS, rawColors)
                remove(UserTagKeys.EXACT_TAGS)
                remove(UserTagKeys.EXACT_COLORS)
                remove(UserTagKeys.REVIEWED_LEGACY_TAGS)
            }
            assertEquals("From 3.1.2", app.userTags.tagFor("UpgradeTestUser"))
            assertEquals("Case lost before 3.1.2", app.userTags.tagFor("upgradetestuser"))
            assertEquals(mapOf("UpgradeTestUser" to "From 3.1.2", "upgradetestuser" to "Case lost before 3.1.2"), app.userTags.tags())

            assertTrue(app.userTags.setTag("UpgradeTestUser", "Edited after upgrade"))
            assertEquals("Edited after upgrade", UserTagCodec.tagFor(content.getString(UserTagKeys.EXACT_TAGS), "UpgradeTestUser"))
            assertEquals(UserTagColor.Purple, app.userTags.colorFor("UpgradeTestUser"))
            assertEquals("Case lost before 3.1.2", app.userTags.tagFor("upgradetestuser"))
            assertEquals(UserTagColor.Red, app.userTags.colorFor("upgradetestuser"))
            assertTrue(app.userTags.setTag("upgradetestuser", "Exact lowercase override", UserTagColor.Theme))
            assertEquals("Exact lowercase override", app.userTags.tagFor("upgradetestuser"))
            assertEquals(UserTagColor.Theme, app.userTags.colorFor("upgradetestuser"))
            assertEquals("", app.userTags.tagFor("UPGRADETestUser"))
            assertEquals(rawTags, content.getString(UserTagKeys.TAGS))
            assertEquals(rawColors, content.getString(UserTagKeys.COLORS))
            stringKeys.forEach { key -> assertEquals(originalSettings[key], settings.getString(key)) }

            assertTrue(app.userTags.setTag("UpgradeTestUser", ""))
            assertEquals(mapOf("upgradetestuser" to "Exact lowercase override"), app.userTags.tags())
            assertTrue(content.getStringSet(UserTagKeys.REVIEWED_LEGACY_TAGS).contains("UpgradeTestUser"))
            assertTrue(app.userTags.setTag("upgradetestuser", ""))
            assertEquals(emptyMap<String, String>(), app.userTags.tags())
            assertEquals(rawTags, content.getString(UserTagKeys.TAGS))
            assertEquals(rawColors, content.getString(UserTagKeys.COLORS))
        } finally {
            content.update {
                originalContent.forEach { (key, value) -> putString(key, value) }
                if (hadReviewed) putStringSet(UserTagKeys.REVIEWED_LEGACY_TAGS, originalReviewed)
                else remove(UserTagKeys.REVIEWED_LEGACY_TAGS)
            }
        }
    }
}
