package com.simon.harmonichackernews

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.AppDestinationCodec
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.ui.navigation.MainLaunchIntentRouter
import com.simon.harmonichackernews.ui.navigation.MainNavigationController
import com.simon.harmonichackernews.widget.widgetStoryIntent
import com.simon.harmonichackernews.widget.widgetStoriesIntent
import com.simon.harmonichackernews.navigation.MainDestination
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the intent supplied to Glance's activity action against the real launch decoder. */
@RunWith(AndroidJUnit4::class)
class WidgetLaunchRoutingTest {
    @Test
    fun widgetHeaderReturnsToStoriesFromAnExistingSettingsTask() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val scene = context.harmonicAppComposition.createScene()
            val navigation = MainNavigationController(scene)
            try {
                navigation.openSettings("appearance")
                assertEquals(MainDestination.SETTINGS, navigation.navigationState.state.value.currentDestination)
                assertTrue(MainLaunchIntentRouter(navigation).route(widgetStoriesIntent(context)))
                assertEquals(MainDestination.STORIES, navigation.navigationState.state.value.currentDestination)
            } finally {
                navigation.onDestroy()
                scene.close()
            }
        }
    }

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
                    val click = widgetStoryIntent(context, story.toDestination(showWebsite = isLink))
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
                val first = widgetStoryIntent(context, StoryDestination(storyId = 1))
                val second = widgetStoryIntent(context, StoryDestination(storyId = 2))
                assertFalse("Each widget row needs its own pending-intent identity", first.filterEquals(second))
                val website = widgetStoryIntent(context, StoryDestination(storyId = 1, showWebsite = true))
                assertFalse("A story's link and comments must have distinct actions", first.filterEquals(website))
            } finally {
                navigation.onDestroy()
                scene.close()
            }
        }
    }
}
