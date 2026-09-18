package com.simon.harmonichackernews

import android.app.PendingIntent
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.os.Parcel
import android.view.View
import android.widget.ListView
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.AppDestinationCodec
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.ui.navigation.MainLaunchIntentRouter
import com.simon.harmonichackernews.ui.navigation.MainNavigationController
import com.simon.harmonichackernews.widget.StoriesRemoteViewsFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real widget row, framework click dispatch, and Android launch decoder. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class WidgetLaunchRoutingTest {
    @Test
    fun widgetClickKeepsSerializedAndLegacyWebsiteDestinationsInAgreement() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val scene = context.harmonicAppComposition.createScene()
            val navigation = MainNavigationController(scene)
            val router = MainLaunchIntentRouter(navigation)
            try {
                for (isLink in listOf(true, false)) {
                    val story = Story("Widget destination", 990000016, true, false).apply {
                        this.isLink = isLink
                        url = if (isLink) "https://example.com/widget" else null
                        by = "widget-test"
                        text = if (isLink) null else "A text-only story"
                    }
                    val click = clickFactoryRow(context, story)
                    val encoded = AppDestinationCodec.decode(
                        click.getStringExtra(AppDestinationCodec.ANDROID_PAYLOAD_EXTRA),
                    ) as StoryDestination
                    assertEquals(isLink, encoded.showWebsite)
                    assertEquals(isLink, click.getBooleanExtra(CommentsContract.EXTRA_SHOW_WEBSITE, false))
                    assertTrue(router.route(click))
                    val destination = requireNotNull(navigation.navigationState.state.value.storyRequest).destination
                    assertEquals(encoded, destination)
                    assertEquals(story.id, destination.storyId)
                    assertEquals(story.title, destination.seed?.story?.title)
                    assertEquals(story.url, destination.seed?.story?.url)
                }
            } finally {
                navigation.onDestroy()
                scene.close()
            }
        }
    }

    private fun clickFactoryRow(context: Context, story: Story): Intent {
        val factory = StoriesRemoteViewsFactory(context, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Seed only the factory's fetched data, so this test doesn't depend on a live HN feed.
        // The production factory still creates and encodes the complete RemoteViews click action.
        val field = StoriesRemoteViewsFactory::class.java.getDeclaredField("stories").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        (field.get(factory) as MutableList<Story>).add(story)
        val capture = ClickCaptureContext(context)
        val pending = PendingIntent.getActivity(
            context,
            990000016,
            Intent(context, MainActivity::class.java).setAction("widget-routing-instrumentation"),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_stories).apply {
                setPendingIntentTemplate(R.id.widget_stories_list, pending)
                setRemoteAdapter(
                    R.id.widget_stories_list,
                    RemoteViews.RemoteCollectionItems.Builder()
                        .addItem(story.id.toLong(), factory.getViewAt(0))
                        .build(),
                )
            }
            // Widget actions cross Binder before a launcher inflates them.
            val parcel = Parcel.obtain()
            val transported = try {
                views.writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                RemoteViews.CREATOR.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }
            val host = AppWidgetHostView(capture)
            val root = transported.apply(capture, host)
            host.addView(root)
            host.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY),
            )
            host.layout(0, 0, 1080, 1600)
            val list = root.findViewById<ListView>(R.id.widget_stories_list)
            assertEquals(1, list.adapter.count)
            val row = list.getChildAt(0)
            assertNotNull("The framework must inflate the factory's widget row", row)
            assertTrue(list.performItemClick(row, 0, story.id.toLong()))
            return requireNotNull(capture.fillInIntent) { "Widget click must dispatch its fill-in intent" }
        } finally {
            pending.cancel()
            factory.onDestroy()
        }
    }

    private class ClickCaptureContext(base: Context) : ContextWrapper(base) {
        var fillInIntent: Intent? = null

        override fun startIntentSender(
            intent: IntentSender,
            fillInIntent: Intent?,
            flagsMask: Int,
            flagsValues: Int,
            extraFlags: Int,
            options: Bundle?,
        ) {
            // Capture exactly what the framework sends, without launching another activity.
            this.fillInIntent = fillInIntent?.let(::Intent)
        }
    }
}
