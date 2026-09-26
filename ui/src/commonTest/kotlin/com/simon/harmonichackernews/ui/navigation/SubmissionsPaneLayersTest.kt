package com.simon.harmonichackernews.ui.navigation

import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.navigation.StoryDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubmissionsPaneLayersTest {
    @Test fun settingsAboveSubmissionsStoryKeepsTheActualBackTarget() {
        val navigation = submissionsWithStory().apply { openSettings("debug") }
        for (tablet in listOf(false, true)) {
            val plan = mainNavigationScenePlan(navigation.state.value, tablet)
            assertEquals(MainDestination.SETTINGS, plan.current.entry.destination)
            val parent = plan.surfaces[plan.surfaces.lastIndex - 1]
            assertEquals(if (tablet) MainDestination.SUBMISSIONS else MainDestination.STORY, parent.entry.destination)
            if (tablet) assertEquals(2, parent.detail?.storyId)
        }
    }

    @Test fun submissionsOpenedDirectlyFromSettingsKeepsSettingsAboveAnOlderStory() {
        val navigation = MainNavigationStore().apply {
            openStory(StoryDestination(1))
            openSettings("debug")
            openSubmissions("alice")
        }
        for (tablet in listOf(false, true)) {
            val plan = mainNavigationScenePlan(navigation.state.value, tablet)
            assertEquals(listOf(MainDestination.SETTINGS, MainDestination.SUBMISSIONS),
                plan.surfaces.takeLast(2).map { it.entry.destination })
        }
    }

    @Test fun phoneStoryCoversSubmissionsButTabletStorySharesItsSurface() {
        val navigation = submissionsWithStory()
        val phone = mainNavigationScenePlan(navigation.state.value, false)
        assertEquals(MainDestination.STORY, phone.current.entry.destination)
        assertEquals(MainDestination.SUBMISSIONS, phone.surfaces[phone.surfaces.lastIndex - 1].entry.destination)
        val tablet = mainNavigationScenePlan(navigation.state.value, true)
        assertEquals(MainDestination.SUBMISSIONS, tablet.current.entry.destination)
        assertEquals(2, tablet.current.detail?.storyId)
        assertEquals(1, tablet.surfaces.first().detail?.storyId)
    }

    @Test fun linkedStoryRemainsInSubmissionsDetailPaneAndPopsToPriorSelection() {
        val navigation = submissionsWithStory()
        val parent = mainNavigationScenePlan(navigation.state.value, true)
        navigation.openLinkedStory(StoryDestination(3))
        val linked = mainNavigationScenePlan(navigation.state.value, true)
        assertEquals(parent.surfaces.map { it.key }, linked.surfaces.map { it.key })
        assertEquals(3, linked.current.detail?.storyId)
        navigation.detailRemovedFromBackStack()
        assertEquals(parent, mainNavigationScenePlan(navigation.state.value, true))
    }

    @Test fun backFromDetailLeavesSubmissionsAboveUnderlyingStory() {
        val navigation = submissionsWithStory()
        navigation.detailRemovedFromBackStack()
        val plan = mainNavigationScenePlan(navigation.state.value, true)
        assertEquals(MainDestination.SUBMISSIONS, plan.current.entry.destination)
        assertNull(plan.current.detail)
        assertEquals(1, plan.surfaces.first().detail?.storyId)
    }

    private fun submissionsWithStory() = MainNavigationStore().apply {
        openStory(StoryDestination(1))
        openSubmissions("alice")
        openStory(StoryDestination(2))
    }
}
