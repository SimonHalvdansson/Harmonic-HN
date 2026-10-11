package com.simon.harmonichackernews.ios

import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.navigation.StoryRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class IosBackNavigationTest {
    @Test
    fun backPreviewUsesTheVisibleDestinationAboveRetainedHistory() {
        val navigation = MainNavigationStore()
        fun target() = iosBackVisualTarget(navigation.state.value, null, null, false)
        navigation.openSettings("debug")
        assertEquals(IosBackVisualTarget.Settings, target())
        navigation.openStory(StoryRoute(42))
        assertEquals(IosBackVisualTarget.Story, target())
        navigation.openSubmissions("tester")
        assertEquals(IosBackVisualTarget.Submissions, target())
        navigation.openStory(StoryRoute(43))
        assertEquals(IosBackVisualTarget.Story, target())
        navigation.detailRemovedFromBackStack()
        assertEquals(IosBackVisualTarget.Submissions, target())
        navigation.closeSubmissions()
        assertEquals(IosBackVisualTarget.Story, target())
    }
}
