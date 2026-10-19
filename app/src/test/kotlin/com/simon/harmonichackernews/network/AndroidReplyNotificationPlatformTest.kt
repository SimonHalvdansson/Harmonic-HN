package com.simon.harmonichackernews.network

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class AndroidReplyNotificationPlatformTest {
    @Test
    fun notificationDeliveryRunsOffTheCallerAndCompletesBeforeReturning() = runBlocking {
        val owner = Thread.currentThread()
        val accesses = CopyOnWriteArrayList<Thread>()
        val base = RuntimeEnvironment.getApplication()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? {
                if (name == Context.NOTIFICATION_SERVICE) accesses += Thread.currentThread()
                return super.getSystemService(name)
            }
        }
        val platform = AndroidReplyNotificationPlatform(context)
        platform.prepareNotifications()
        accesses.clear()

        platform.publish(ReplyNotificationPresentation.present(
            (1..100).map { HackerNewsReply(it, 200, "alice", "Reply $it") },
        ))

        assertTrue(accesses.isNotEmpty())
        assertTrue(accesses.all { it !== owner })
        val manager = base.getSystemService(NotificationManager::class.java)
        val notifications = shadowOf(manager).allNotifications
        assertEquals(6, notifications.size)
        assertEquals("100 new replies", shadowOf(manager)
            .getNotification(ReplyNotificationPresentation.SUMMARY_NOTIFICATION_ID)
            .extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notifications.all { it.getLargeIcon() != null })
    }
}
