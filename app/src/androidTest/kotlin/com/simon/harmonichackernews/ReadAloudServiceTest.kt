package com.simon.harmonichackernews

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadAloudServiceTest {
    @Test fun speechSurvivesBackgroundAndHonorsMediaControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ActivityScenario.launch(ComponentActivity::class.java).use { activity ->
            fun command(action: String) = instrumentation.runOnMainSync {
                AndroidReadAloud.command(context, buildJsonObject { put("action", action) })
            }
            fun await(message: String, predicate: () -> Boolean) {
                val deadline = System.nanoTime() + 30_000_000_000L
                while (!predicate()) {
                    assertNull(AndroidReadAloud.state.value.error)
                    assertTrue(message, System.nanoTime() < deadline)
                    Thread.sleep(50)
                }
            }
            try {
                activity.onActivity {
                    AndroidReadAloud.command(it, buildJsonObject {
                        put("action", "start"); put("title", "Read aloud verification")
                        put("url", "https://example.org/article")
                        put("text", "This is a sentence for the read aloud test. ".repeat(100))
                    })
                }
                await("Speech did not start") { AndroidReadAloud.state.value.index >= 1 }
                command("pause")
                val paused = AndroidReadAloud.state.value.index
                Thread.sleep(500)
                assertFalse(AndroidReadAloud.state.value.playing)
                assertEquals(paused, AndroidReadAloud.state.value.index)
                command("next")
                assertEquals(paused + 1, AndroidReadAloud.state.value.index)
                command("play")
                activity.moveToState(Lifecycle.State.CREATED)
                await("Speech stopped when activity backgrounded") { AndroidReadAloud.state.value.index > paused + 1 }
                val notification = context.getSystemService(android.app.NotificationManager::class.java)
                    .activeNotifications.first { it.notification.category == android.app.Notification.CATEGORY_TRANSPORT }.notification
                notification.actions[1].actionIntent.send()
                @Suppress("DEPRECATION")
                val token = notification.extras.getParcelable<android.media.session.MediaSession.Token>(android.app.Notification.EXTRA_MEDIA_SESSION)!!
                val media = android.media.session.MediaController(context, token)
                await("Notification pause was ignored") { !AndroidReadAloud.state.value.playing }
                media.transportControls.play()
                await("Notification resume was ignored") { AndroidReadAloud.state.value.playing }
                media.transportControls.stop()
                await("Service did not stop") { AndroidReadAloud.service == null }
                assertEquals(0, AndroidReadAloud.state.value.count)
            } finally { command("stop") }
        }
    }
}
