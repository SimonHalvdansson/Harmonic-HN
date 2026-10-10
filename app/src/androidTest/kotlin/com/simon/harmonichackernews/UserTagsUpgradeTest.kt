package com.simon.harmonichackernews

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.settings.UserTagCodec
import com.simon.harmonichackernews.settings.UserTagKeys
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserTagsUpgradeTest {
    @Test
    fun applicationReadsAndEditsTagsInTheLegacyContentStore() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.harmonicAppComposition
        val content = app.host.appDataStore
        val settings = app.host.settingsStore
        val originalTags = content.getString(UserTagKeys.TAGS)
        val originalSettingsTags = settings.getString(UserTagKeys.TAGS)
        try {
            // 3.1 wrote this JSON into GLOBAL_SHARED_PREFERENCES_KEY, not default preferences.
            content.putString(UserTagKeys.TAGS, """{"UpgradeTestUser":"From 3.1"}""")
            assertEquals("From 3.1", app.userTags.tagFor("upgradetestuser"))
            assertEquals(mapOf("UpgradeTestUser" to "From 3.1"), app.userTags.tags(false))

            app.userTags.setTag("UpgradeTestUser", "Edited after upgrade")
            assertEquals(
                "Edited after upgrade",
                UserTagCodec.tagFor(content.getString(UserTagKeys.TAGS), "UpgradeTestUser"),
            )
            assertEquals(originalSettingsTags, settings.getString(UserTagKeys.TAGS))

            app.userTags.setTag("UpgradeTestUser", "")
            assertEquals(emptyMap<String, String>(), app.userTags.tags())
        } finally {
            content.putString(UserTagKeys.TAGS, originalTags)
            settings.putString(UserTagKeys.TAGS, originalSettingsTags)
        }
    }
}
