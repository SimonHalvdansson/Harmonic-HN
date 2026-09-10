package com.simon.harmonichackernews.ui.navigation

import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.navigation.StoryDestination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubmissionsPaneLayersTest {
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
