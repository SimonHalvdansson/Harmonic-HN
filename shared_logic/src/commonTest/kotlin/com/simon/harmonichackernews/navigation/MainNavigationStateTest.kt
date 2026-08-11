package com.simon.harmonichackernews.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainNavigationStateTest {
    @Test
    fun storyDestinationRoundTripsAllHeaderAndPreviewState() {
        val source = StoryDestination(
            storyId = 42,
            title = "Title",
            author = "author",
            url = "https://example.com",
            previewImageUrl = "https://example.com/image.png",
            previewImageTintColorLoaded = true,
            previewImageTintColor = 123,
            childIds = listOf(1, 2),
            pollOptionIds = listOf(3, 4),
            descendantCount = 8,
            score = 9,
            isLink = true,
        )

        val story = source.toStory()

        assertEquals(42, story.id)
        assertEquals("Title", story.title)
        assertEquals("author", story.by)
        assertTrue(story.loaded)
        assertTrue(story.previewImageUrlLoaded)
        assertEquals(123, story.previewImageTintColor)
        assertEquals(listOf(1, 2), story.kids?.toList())
        assertEquals(listOf(3, 4), story.pollOptions?.toList())
        assertEquals(8, story.descendants)
        assertEquals(9, story.score)
        assertTrue(story.isLink)
    }

    @Test
    fun closingAStoryOpenedFromSettingsRestoresTheSettingsSection() {
        val state = MainNavigationState()
        state.openSettings("appearance")
        state.openStory(StoryDestination(storyId = 42))

        assertNull(state.settingsRequest)
        assertTrue(state.storyOpenedFromSettings)

        state.detailRemovedFromBackStack()

        assertNull(state.storyRequest)
        assertEquals("appearance", state.settingsRequest?.initialSectionRoute)
        assertFalse(state.storyOpenedFromSettings)
    }

    @Test
    fun restoredRequestsContinueWithIncreasingSerials() {
        val state = MainNavigationState(
            MainNavigationRestoration(
                storyDestination = StoryDestination(storyId = 1),
                storyRequestSerial = 4,
                settingsOpen = true,
                settingsRequestSerial = 7,
                settingsSectionRoute = "data",
            ),
        )

        assertEquals(4, state.storyRequest?.serial)
        assertEquals(7, state.settingsRequest?.serial)

        state.openStory(StoryDestination(storyId = 2))
        state.openSettings("comments")

        assertEquals(5, state.storyRequest?.serial)
        assertEquals(8, state.settingsRequest?.serial)
    }

    @Test
    fun settingsRestartRequestIsConsumedExactlyOnce() {
        val state = MainNavigationState()
        state.openSettings(null)
        state.onSettingsThemeChanged()

        assertTrue(state.consumeSettingsRestartRequest())
        assertFalse(state.consumeSettingsRestartRequest())
        assertEquals(1, state.settingsThemeRevision)
    }

    @Test
    fun openingEditorDismissesCompetingMainSurfaces() {
        val state = MainNavigationState()
        state.openSettings("stories")
        state.openSubmissions("simon")
        state.openCoulombGas()

        state.openEditor(EditorDestination(EditorType.POST))

        assertNull(state.settingsRequest)
        assertNull(state.submissionsRequest)
        assertFalse(state.coulombGasVisible)
        assertEquals(EditorType.POST, state.editorRequest?.destination?.type)
    }
}
