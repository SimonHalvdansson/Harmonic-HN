package com.simon.harmonichackernews.platform

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class AndroidClipboardServiceTest {
    @Test
    fun backgroundConstructionThenMainThreadCopy() {
        val context = RuntimeEnvironment.getApplication()
        val construction = FutureTask { AndroidClipboardService(context) }
        Thread(construction).start()
        val service = construction.get(5, TimeUnit.SECONDS)

        service.copy("Comment", "Copied from Hacker News")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("Comment", clipboard.primaryClip!!.description.label.toString())
        assertEquals("Copied from Hacker News", clipboard.primaryClip!!.getItemAt(0).text.toString())
    }
}
