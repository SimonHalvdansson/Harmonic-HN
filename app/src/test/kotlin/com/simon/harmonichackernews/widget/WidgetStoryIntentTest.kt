package com.simon.harmonichackernews.widget

import android.app.Application
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.AppDestinationCodec
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetStoryIntentTest {
    @Test
    fun commentLaunchPreservesRankedKidsAndPollOptionsWhileOmittingStoryText() {
        val story = Story("Discussion", 42, true, false).apply {
            kids = intArrayOf(17, 9, 31)
            pollOptionIds = intArrayOf(45, 46)
            text = "Large story text"
        }
        val intent = widgetStoryIntent(RuntimeEnvironment.getApplication(), story.toDestination(false))
        val destination = AppDestinationCodec.decode(
            intent.getStringExtra(AppDestinationCodec.ANDROID_PAYLOAD_EXTRA),
        ) as StoryDestination
        assertEquals(listOf(17, 9, 31), destination.seed!!.story.childIds)
        assertEquals(listOf(45, 46), destination.seed!!.story.pollOptionIds)
        assertNull(destination.seed!!.story.text)
        assertEquals(42, destination.storyId)
        assertEquals(false, destination.showWebsite)
    }
}
