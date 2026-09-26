package com.simon.harmonichackernews.ui.navigation

import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.navigation.StoryDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainNavigationScenePlanTest {
    @Test fun nestedSubmissionsNeverAssignsAStoryToTwoScenes() {
        for (secondUser in listOf("reader", "another-reader")) {
            val navigation = MainNavigationStore()
            navigation.openSettings("debug")
            navigation.openStory(StoryDestination(1))
            navigation.openSubmissions("reader")
            navigation.openStory(StoryDestination(2))
            val original = mainNavigationScenePlan(navigation.state.value, true)
            navigation.openSubmissions(secondUser)
            val nested = mainNavigationScenePlan(navigation.state.value, true)
            assertEquals(original.fullScreenStories, nested.fullScreenStories)
            assertEquals(listOf(2, null), nested.submissionsScenes.map { it.storyRequest?.storyId })
            assertEquals(null, nested.submissionsStoryRequest)
            navigation.openStory(StoryDestination(3))
            navigation.openSubmissions("third-reader")
            val deeper = mainNavigationScenePlan(navigation.state.value, true)
            assertEquals(listOf(2, 3, null), deeper.submissionsScenes.map { it.storyRequest?.storyId })
            assertEquals(listOf(1), deeper.fullScreenStories.map { it.storyId })
            navigation.closeSubmissions()
            navigation.detailRemovedFromBackStack()
            navigation.closeSubmissions()
            assertEquals(original, mainNavigationScenePlan(navigation.state.value, true))
        }
    }

    @Test fun coveringSettingsDoesNotHideItsStoriesParent() {
        for (tablet in listOf(false, true)) {
            val navigation = MainNavigationStore()
            navigation.openSettings(null)
            val plan = mainNavigationScenePlan(navigation.state.value, tablet)
            assertTrue(plan.showsStoriesRoot)
            assertEquals(tablet, plan.baseUsesTwoPane)
            assertTrue(plan.fullScreenStories.isEmpty())
        }
    }

    @Test fun coveringDestinationsKeepTheStoryRunAndItsOrigin() {
        val navigation = MainNavigationStore()
        navigation.openStory(StoryDestination(1))
        val parent = mainNavigationScenePlan(navigation.state.value, false)
        navigation.openSettings(null)
        assertEquals(parent, mainNavigationScenePlan(navigation.state.value, false))
        navigation.openSubmissions("reader")
        assertEquals(parent, mainNavigationScenePlan(navigation.state.value, false))
    }

    @Test fun debugStoryUsesAFullScreenSurfaceOnBothWindowSizes() {
        for (tablet in listOf(false, true)) {
            val navigation = MainNavigationStore()
            navigation.openSettings("debug")
            navigation.openStory(StoryDestination(2))
            val plan = mainNavigationScenePlan(navigation.state.value, tablet)
            assertFalse(plan.showsStoriesRoot)
            assertFalse(plan.baseUsesTwoPane)
            assertEquals(listOf(2), plan.fullScreenStories.map { it.storyId })
            assertEquals(tablet, plan.animateInitialStory)
        }
    }

    @Test fun tabletPopDoesNotSubstituteAnOlderPaneStoryForTheExitingFullScreenStory() {
        val navigation = MainNavigationStore()
        navigation.openStory(StoryDestination(1))
        navigation.openSettings("debug")
        navigation.openStory(StoryDestination(2))
        navigation.detailRemovedFromBackStack()
        val plan = mainNavigationScenePlan(navigation.state.value, true)
        assertTrue(plan.baseUsesTwoPane)
        assertEquals(1, plan.baseStoryRequest?.storyId)
        assertTrue(plan.fullScreenStories.isEmpty())
    }

    @Test fun tabletSubmissionsKeepsItsOwnDetailAndTheUnderlyingMainDetail() {
        val navigation = MainNavigationStore()
        navigation.openStory(StoryDestination(1))
        navigation.openSubmissions("reader")
        navigation.openStory(StoryDestination(2))
        val plan = mainNavigationScenePlan(navigation.state.value, true)
        assertTrue(plan.submissionsInTwoPane)
        assertTrue(plan.baseUsesTwoPane)
        assertEquals(1, plan.baseStoryRequest?.storyId)
        assertEquals(2, plan.submissionsStoryRequest?.storyId)
        assertTrue(plan.fullScreenStories.isEmpty())
    }
    @Test fun tabletSubmissionsRetainsTheFullScreenDebugStoryRun() {
        val navigation = MainNavigationStore()
        navigation.openSettings("debug")
        navigation.openStory(StoryDestination(1))
        navigation.openLinkedStory(StoryDestination(2))
        val original = mainNavigationScenePlan(navigation.state.value, true)
        navigation.openSubmissions("reader")
        val covered = mainNavigationScenePlan(navigation.state.value, true)
        assertFalse(covered.baseUsesTwoPane)
        assertFalse(covered.showsStoriesRoot)
        assertEquals(original.fullScreenStories, covered.fullScreenStories)
        assertEquals(original.baseStoryRequest, covered.baseStoryRequest)

        navigation.openStory(StoryDestination(3))
        val withDetail = mainNavigationScenePlan(navigation.state.value, true)
        assertFalse(withDetail.baseUsesTwoPane)
        assertTrue(withDetail.storyUsesTwoPane)
        assertEquals(original.fullScreenStories, withDetail.fullScreenStories)
        assertEquals(3, withDetail.submissionsStoryRequest?.storyId)
        navigation.detailRemovedFromBackStack()
        navigation.closeSubmissions()
        assertEquals(original, mainNavigationScenePlan(navigation.state.value, true))
    }

}
