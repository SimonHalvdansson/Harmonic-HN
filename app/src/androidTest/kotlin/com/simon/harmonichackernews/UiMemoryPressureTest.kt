@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")

package com.simon.harmonichackernews

import android.content.ComponentCallbacks2
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.ui.content.CommentHtmlTextCache
import com.simon.harmonichackernews.ui.content.CommentRenderModelCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiMemoryPressureTest {
    @Test
    fun realPressureReclaimsCachesWhileOrdinaryBackgroundingKeepsThem() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as HarmonicApplication
        app.awaitStartup()
        val html = "&gt; A quoted <b>point</b><p><a href='https://example.com'>Reference</a>"
        instrumentation.runOnMainSync {
            val model = CommentRenderModelCache.get(991_223, html, false)
            val text = CommentHtmlTextCache.getPrepared(html)
            app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            assertSame(model, CommentRenderModelCache.get(991_223, html, false))
            assertSame(text, CommentHtmlTextCache.getPrepared(html))

            app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
            assertEquals(0, CommentRenderModelCache.entryCountForTest())
            assertEquals(0, CommentHtmlTextCache.entryCountForTest())
            val restored = CommentHtmlTextCache.getPrepared(html)
            assertNotSame(text, restored)
            assertEquals(text, restored)
            assertEquals("A quoted point\n\nReference", text.highlighted.text)

            app.onLowMemory()
            assertEquals(0, CommentHtmlTextCache.entryCountForTest())
        }
    }
}
