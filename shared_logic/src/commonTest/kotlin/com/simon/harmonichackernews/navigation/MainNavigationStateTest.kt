package com.simon.harmonichackernews.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainNavigationStateTest {
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
