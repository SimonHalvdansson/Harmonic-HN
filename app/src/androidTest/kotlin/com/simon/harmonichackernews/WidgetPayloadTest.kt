package com.simon.harmonichackernews

import android.content.Context
import android.graphics.Bitmap
import android.os.Parcel
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.layout.fillMaxSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.DebugBooleanPreference
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** The largest supported feed must fit comfortably in the launcher's shared Binder buffer. */
@RunWith(AndroidJUnit4::class)
class WidgetPayloadTest {
    @Test
    fun renderingErrorsAreActionableAndDetailsRequireOptIn() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val app = context.harmonicAppComposition
            val original = app.userSettings.debug.showWidgetDebugInfo
            try {
                for (showDetails in listOf(false, true)) {
                    app.settings.setDebugBoolean(DebugBooleanPreference.SHOW_WIDGET_DEBUG_INFO, showDetails)
                    val views = widgetErrorViews(context, 0, IllegalStateException("Example rendering failure"))
                    val message = views.apply(context, FrameLayout(context)) as TextView
                    assertTrue(message.isClickable)
                    assertTrue(message.text.contains("Tap to retry"))
                    if (showDetails) assertTrue(message.text.contains("IllegalStateException: Example rendering failure"))
                    else assertFalse(message.text.contains("Example rendering failure"))
                }
            } finally {
                app.settings.setDebugBoolean(DebugBooleanPreference.SHOW_WIDGET_DEBUG_INFO, original)
            }
        }
    }

    @Test
    fun twentyFourImageStoriesFitInLauncherTransaction() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entries = (1..24).map { index ->
            val story = Story("An example story with a reasonably long headline $index", index, true, false).apply {
                isLink = true
                url = "https://example.com/$index"
                score = 1234
                descendants = 234
            }
            WidgetEntry(story.toDestination(true), null, null, null)
        }
        // Separate allocations reproduce the real feed: identical references hide payload regressions.
        val visuals = entries.map {
            val image = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff428b90.toInt()) }
            val favicon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(0xffac4c30.toInt()) }
            WidgetVisual(image.forWidget(), 0xffdfeeed.toInt(), favicon.forWidget())
        }
        for (mode in listOf(StoryPreviewMode.OFF, StoryPreviewMode.SMALL, StoryPreviewMode.MEDIUM)) {
            val widget = object : GlanceAppWidget() {
                override suspend fun provideGlance(context: Context, id: GlanceId) {
                    provideContent {
                        LazyColumn(GlanceModifier.fillMaxSize()) {
                            itemsIndexed(entries, itemId = { _, entry -> entry.destination.storyId.toLong() }) { index, entry ->
                                WidgetStoryRow(context, entry, index,
                                    WidgetConfiguration(visibleStoryCount = 24, previewImageMode = mode, displayStyle = DisplayStyle.OUTLINED, tint = true),
                                    WidgetColors(HarmonicThemeCatalog.resolve("light", false).colors,
                                        HarmonicThemeCatalog.resolve("dark", true).colors), visuals[index])
                            }
                        }
                    }
                }
            }
            val views = widget.compose(context, size = DpSize(360.dp, 320.dp))
            val parcel = Parcel.obtain()
            try {
                views.writeToParcel(parcel, 0)
                android.util.Log.i("WidgetPayloadTest", "$mode widget parcel is ${parcel.dataSize()} bytes")
                assertTrue("$mode widget parcel is ${parcel.dataSize()} bytes", parcel.dataSize() < 400_000)
            } finally {
                parcel.recycle()
            }
        }
    }
}
