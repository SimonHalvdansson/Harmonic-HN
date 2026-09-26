package com.simon.harmonichackernews.ui.navigation

import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.MainNavigationEntry
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.navigation.StoryDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainNavigationScenePlanTest {
    @Test fun phoneSurfacesFollowTheEntireHistoryIncludingRepeatedScreenTypes() {
        val navigation = nestedVisits()
        val plan = mainNavigationScenePlan(navigation.state.value, false)
        assertEquals(navigation.state.value.destinationStack, plan.surfaces.map { it.entry })
        assertEquals(plan.surfaces.size, plan.surfaces.map { it.key }.toSet().size)
        assertTrue(plan.surfaces.all { it.detail == null })
    }

    @Test fun nestedSubmissionsNeverAssignsAStoryToTwoScenes() {
        for (secondUser in listOf("reader", "another-reader")) {
            val navigation = nestedVisits(secondUser)
            val plan = mainNavigationScenePlan(navigation.state.value, true)
            assertEquals(listOf(MainDestination.STORIES, MainDestination.SETTINGS, MainDestination.STORY,
                MainDestination.SUBMISSIONS, MainDestination.SUBMISSIONS), plan.surfaces.map { it.entry.destination })
            assertEquals(listOf(null, null, null, 2, 3), plan.surfaces.map { it.detail?.storyId })
            assertEquals(1, (plan.surfaces[2].entry as MainNavigationEntry.Story).request.storyId)
            assertEquals(3, renderedStorySerials(plan).toSet().size)
            navigation.openSubmissions("third-reader")
            val deeper = mainNavigationScenePlan(navigation.state.value, true)
            assertEquals(plan.surfaces, deeper.surfaces.dropLast(1))
            assertEquals(null, deeper.current.detail)
            navigation.closeSubmissions()
            assertEquals(plan, mainNavigationScenePlan(navigation.state.value, true))
        }
    }

    @Test fun coveringAnyScreenPreservesItsIdentityAndLayout() {
        for (tablet in listOf(false, true)) {
            val navigation = MainNavigationStore()
            navigation.openStory(StoryDestination(1))
            val parent = mainNavigationScenePlan(navigation.state.value, tablet)
            navigation.openSettings("debug")
            navigation.openStory(StoryDestination(2))
            navigation.openSubmissions("reader")
            val covered = mainNavigationScenePlan(navigation.state.value, tablet)
            assertEquals(parent.surfaces, covered.surfaces.take(parent.surfaces.size))
            assertEquals(MainDestination.SUBMISSIONS, covered.current.entry.destination)
            assertEquals(MainDestination.STORY, covered.surfaces[covered.surfaces.lastIndex - 1].entry.destination)
        }
    }

    @Test fun debugStoryIsFullScreenOnBothWindowSizes() {
        for (tablet in listOf(false, true)) {
            val navigation = MainNavigationStore()
            navigation.openSettings("debug")
            navigation.openStory(StoryDestination(2))
            val plan = mainNavigationScenePlan(navigation.state.value, tablet)
            assertFalse(plan.storyUsesTwoPane)
            assertEquals(MainDestination.STORY, plan.current.entry.destination)
            assertEquals(MainDestination.SETTINGS, plan.surfaces[plan.surfaces.lastIndex - 1].entry.destination)
        }
    }

    @Test fun poppingAStoryRevealsTheActualParentWithOlderStoriesStillRetained() {
        for (tablet in listOf(false, true)) {
            val navigation = MainNavigationStore()
            navigation.openStory(StoryDestination(1))
            navigation.openSettings("debug")
            val parent = mainNavigationScenePlan(navigation.state.value, tablet)
            navigation.openStory(StoryDestination(2))
            val child = mainNavigationScenePlan(navigation.state.value, tablet)
            assertEquals(parent.surfaces, child.surfaces.dropLast(1))
            navigation.detailRemovedFromBackStack()
            assertEquals(parent, mainNavigationScenePlan(navigation.state.value, tablet))
        }
    }

    @Test fun linkedStoryKeepsTheOriginalTabletDetailInItsOwnPane() {
        val navigation = MainNavigationStore()
        navigation.openStory(StoryDestination(1))
        val original = mainNavigationScenePlan(navigation.state.value, true)
        assertTrue(original.storyUsesTwoPane)
        navigation.openLinkedStory(StoryDestination(2))
        val linked = mainNavigationScenePlan(navigation.state.value, true)
        assertEquals(original.current, linked.surfaces.first())
        assertFalse(linked.storyUsesTwoPane)
        assertEquals(2, renderedStorySerials(linked).distinct().size)
        navigation.detailRemovedFromBackStack()
        assertEquals(original, mainNavigationScenePlan(navigation.state.value, true))
    }

    @Test fun externalStoryIsTheRootOnPhoneAndTabletIncludingNestedVisits() {
        for (tablet in listOf(false, true)) {
            val navigation = MainNavigationStore()
            navigation.openStory(StoryDestination(1))
            val external = requireNotNull(navigation.state.value.storyRequest)
            val root = mainNavigationScenePlan(navigation.state.value, tablet, external.serial)
            assertEquals(1, root.surfaces.size)
            assertEquals(MainNavigationEntry.Story(external), root.current.entry)
            assertFalse(root.storyUsesTwoPane)
            navigation.openLinkedStory(StoryDestination(2))
            navigation.openSubmissions("reader")
            val nested = mainNavigationScenePlan(navigation.state.value, tablet, external.serial)
            assertEquals(root.current, nested.surfaces.first())
            assertTrue(nested.surfaces.none { it.entry == MainNavigationEntry.Stories })
            navigation.closeSubmissions()
            navigation.detailRemovedFromBackStack()
            assertEquals(root, mainNavigationScenePlan(navigation.state.value, tablet, external.serial))
        }
    }

    @Test fun restoringHistoryKeepsVisitKeysAndPaneOwnership() {
        val navigation = nestedVisits()
        val restored = MainNavigationStore(navigation.restoration())
        for (tablet in listOf(false, true)) {
            assertEquals(mainNavigationScenePlan(navigation.state.value, tablet),
                mainNavigationScenePlan(restored.state.value, tablet))
        }
    }

    private fun nestedVisits(secondUser: String = "reader") = MainNavigationStore().apply {
        openSettings("debug")
        openStory(StoryDestination(1))
        openSubmissions("reader")
        openStory(StoryDestination(2))
        openSubmissions(secondUser)
        openStory(StoryDestination(3))
    }

    private fun renderedStorySerials(plan: MainNavigationScenePlan) = plan.surfaces.mapNotNull {
        (it.entry as? MainNavigationEntry.Story)?.request?.serial ?: it.detail?.serial
    }
}
