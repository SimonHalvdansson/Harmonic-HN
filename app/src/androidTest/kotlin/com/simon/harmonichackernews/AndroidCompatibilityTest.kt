package com.simon.harmonichackernews

import android.app.NotificationManager
import android.app.job.JobScheduler
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.network.AndroidReplyNotificationPlatform
import com.simon.harmonichackernews.network.ReplyNotificationSchedule
import com.simon.harmonichackernews.platform.accountOrNull
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.utils.AndroidKeystoreSecretStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on the supported API matrix; host JVM tests cannot exercise these platform services. */
@RunWith(AndroidJUnit4::class)
class AndroidCompatibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun everySettingsScreenOpensAndReturnsToStories() {
        val navigation = compose.activity.navigationController
        compose.runOnIdle {
            navigation.dismissWelcomeDialog()
            navigation.dismissChangelogDialog()
        }
        for (section in SettingsSection.entries) {
            // Notifications deliberately returns to the Settings list while logged out.
            val title = if (section == SettingsSection.Notifications &&
                compose.activity.harmonicAppComposition.platform.accounts.accountState.value.accountOrNull == null
            ) "Settings" else runBlocking { getString(section.titleResource) }
            try {
                compose.runOnIdle { navigation.openSettings(section.route) }
                compose.waitForIdle()
                compose.waitUntil(10_000) {
                    val headings = compose.onAllNodesWithText(title, substring = false)
                    headings.fetchSemanticsNodes().indices.any { headings[it].isDisplayed() }
                }
                assertFalse("Opening ${section.route} must retain the activity", compose.activity.isFinishing)
            } catch (error: Throwable) {
                throw AssertionError("Settings ${section.route}: ${compose.onRoot().printToString()}", error)
            } finally {
                compose.runOnIdle { navigation.closeSettings() }
                compose.waitForIdle()
            }
        }
    }

    @Test
    fun keystoreSecretSurvivesReopeningReplacementAndReset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "compatibility-secret-${UUID.randomUUID()}"
        fun store() = AndroidKeystoreSecretStore(context, name, "secret", name)
        val secret = "Compatibility secret \u00e5\u00e4\u00f6".encodeToByteArray()
        val replacement = ByteArray(1024) { it.toByte() }
        try {
            assertNull(store().read())
            assertTrue(store().write(secret))
            assertArrayEquals(secret, store().read())
            assertTrue(store().write(replacement))
            assertArrayEquals(replacement, store().read())
            assertTrue(store().reset())
            assertNull(store().read())
            assertFalse(store().legacyMigrationAllowed)
            assertTrue(store().write(secret))
            assertArrayEquals(secret, store().read())
        } finally {
            store().reset()
            context.deleteSharedPreferences(name)
        }
    }

    @Test
    fun replyNotificationsCanCreateTheirChannelAndScheduleBackgroundChecks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val platform = AndroidReplyNotificationPlatform(context)
        val hadChannel = manager.getNotificationChannel(AndroidReplyNotificationPlatform.CHANNEL_ID) != null
        val previous = scheduler.allPendingJobs.firstOrNull {
            it.service.className == "com.simon.harmonichackernews.network.ReplyNotificationJobService"
        }
        try {
            platform.prepareNotifications()
            assertNotNull(manager.getNotificationChannel(AndroidReplyNotificationPlatform.CHANNEL_ID))
            platform.scheduleChecks(ReplyNotificationSchedule())
            val job = scheduler.allPendingJobs.single {
                it.service.className == "com.simon.harmonichackernews.network.ReplyNotificationJobService"
            }
            assertTrue(job.isPersisted)
            assertTrue(job.isPeriodic)
            assertEquals(ReplyNotificationSchedule().intervalMillis, job.intervalMillis)
            platform.cancelChecks()
            assertNull(scheduler.getPendingJob(job.id))
        } finally {
            platform.cancelChecks()
            previous?.let { scheduler.schedule(it) }
            if (!hadChannel) manager.deleteNotificationChannel(AndroidReplyNotificationPlatform.CHANNEL_ID)
        }
    }
}
