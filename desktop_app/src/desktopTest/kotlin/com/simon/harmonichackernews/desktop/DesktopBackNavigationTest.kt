package com.simon.harmonichackernews.desktop

import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.StoryRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopBackNavigationTest {
    @Test
    fun escapePopsTheVisibleStoryAboveRetainedSettingsAndSubmissions() {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("BackTest")
        try {
            val scene = bootstrap.scene
            val navigation = scene.navigation
            navigation.openSettings("debug")
            navigation.openStory(StoryRoute(42))
            navigation.openSubmissions("tester")
            navigation.openStory(StoryRoute(43))
            for (expected in listOf(
                MainDestination.SUBMISSIONS,
                MainDestination.STORY,
                MainDestination.SETTINGS,
                MainDestination.STORIES,
            )) {
                assertTrue(handleDesktopBack(navigation.state.value, scene, null, null, null, {}))
                assertEquals(expected, navigation.state.value.currentDestination)
            }
        } finally {
            bootstrap.close()
        }
    }
}
