package com.simon.harmonichackernews.ui.navigation

import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.navigation.StoryDestination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubmissionsPaneLayersTest {
    @Test
    fun submissionsAboveDebugStoryKeepsSettingsBelowItsBackTarget() {
        val navigation = MainNavigationStore().apply {
            openSettings("debug")
            openStory(StoryDestination(1))
            openSubmissions("alice")
        }
        val layers = mainDestinationLayerState(navigation.state.value, submissionsInTwoPane = false)
        assertTrue(layers.settingsBehindStory)
        assertTrue(layers.settingsSemanticsHidden)
        assertFalse(layers.submissionsBehindStory)
        assertTrue(layers.submissionsCoversBase)
        navigation.closeSubmissions()
        assertTrue(mainDestinationLayerState(navigation.state.value, false).settingsBehindStory)
        navigation.detailRemovedFromBackStack()
        assertFalse(mainDestinationLayerState(navigation.state.value, false).settingsBehindStory)
    }

    @Test
    fun settingsAboveSubmissionsStoryKeepsSubmissionsBelowItsBackTarget() {
        val navigation = submissionsWithStory().apply { openSettings("debug") }
        val layers = mainDestinationLayerState(navigation.state.value, submissionsInTwoPane = false)
        assertTrue(layers.submissionsBehindStory)
        assertTrue(layers.submissionsSemanticsHidden)
        assertFalse(layers.settingsBehindStory)
        assertTrue(layers.settingsCoversBase)
    }

    @Test
    fun submissionsOpenedDirectlyFromSettingsKeepsSettingsAboveAnOlderStory() {
        val navigation = MainNavigationStore().apply {
            openStory(StoryDestination(1))
            openSettings("debug")
            openSubmissions("alice")
        }
        val layers = mainDestinationLayerState(navigation.state.value, submissionsInTwoPane = false)
        assertFalse(layers.settingsBehindStory)
        assertFalse(layers.submissionsBehindStory)
    }

    @Test
    fun phoneStoryStillCoversSubmissions() {
        val navigation = submissionsWithStory()
        val layers = mainDestinationLayerState(navigation.state.value, submissionsInTwoPane = false)
        assertTrue(layers.submissionsBehindStory)
        assertTrue(layers.submissionsSemanticsHidden)
        assertFalse(layers.submissionsCoversBase)
    }

    @Test
    fun twoPaneStoryKeepsSubmissionsInteractiveAndHidesMainFeed() {
        val navigation = submissionsWithStory()
        val layers = mainDestinationLayerState(navigation.state.value, submissionsInTwoPane = true)
        assertTrue(layers.submissionsVisible)
        assertTrue(layers.submissionsCoversBase)
        assertTrue(layers.baseSemanticsHidden)
        assertFalse(layers.submissionsBehindStory)
        assertFalse(layers.submissionsSemanticsHidden)
    }

    @Test
    fun linkedStoryRemainsInSubmissionsDetailPane() {
        val navigation = submissionsWithStory()
        navigation.openLinkedStory(StoryDestination(3))
        val layers = mainDestinationLayerState(navigation.state.value, submissionsInTwoPane = true)
        assertFalse(layers.submissionsBehindStory)
        assertFalse(layers.submissionsSemanticsHidden)
        assertTrue(layers.baseSemanticsHidden)
    }

    @Test
    fun backFromDetailLeavesSubmissionsAboveUnderlyingStory() {
        val navigation = submissionsWithStory()
        navigation.detailRemovedFromBackStack()
        val layers = mainDestinationLayerState(navigation.state.value, submissionsInTwoPane = true)
        assertTrue(layers.submissionsVisible)
        assertTrue(layers.submissionsCoversBase)
        assertFalse(layers.submissionsSemanticsHidden)
        assertFalse(layers.submissionsBehindStory)
    }

    private fun submissionsWithStory() = MainNavigationStore().apply {
        openStory(StoryDestination(1))
        openSubmissions("alice")
        openStory(StoryDestination(2))
    }
}
