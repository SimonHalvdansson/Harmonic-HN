@file:Suppress("RestrictedApi")

package com.simon.harmonichackernews.ui.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.ui.session.CommentsViewModel
import com.simon.harmonichackernews.ui.session.HarmonicSceneViewModel
import com.simon.harmonichackernews.ui.session.SubmissionsViewModel
import com.simon.harmonichackernews.presentation.SubmissionsIntent
import com.simon.harmonichackernews.presentation.SubmissionsScrollRestoration
import com.simon.harmonichackernews.ui.settings.SettingsNavigationViewModel
import com.simon.harmonichackernews.ui.settings.SettingsSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class NavigationSavedStateTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun submissionsRestoreTheLatestScrollForBothNewAndRetainedHosts() {
        Dispatchers.setMain(StandardTestDispatcher())
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("submissions-state-test")
        val first = bootstrap.app.createScene()
        val second = bootstrap.app.createScene()
        val models = ViewModelStore()
        try {
            first.navigation.openSubmissions("alice")
            val request = requireNotNull(first.navigation.state.value.submissionsRequest)
            val handle = SavedStateHandle()
            val original = SubmissionsViewModel(first, request, handle)
            models.put("original", original)
            original.store.accept(SubmissionsIntent.RecordScrollPosition(8, -23, true))
            val restored = SubmissionsViewModel(second, request, restoredHandle(handle))
            models.put("restored", restored)
            assertEquals(SubmissionsScrollRestoration(8, -23, true), restored.scrollRestoration())
            restored.store.accept(SubmissionsIntent.RecordScrollPosition(15, -7, true))
            assertEquals(SubmissionsScrollRestoration(15, -7, true), restored.scrollRestoration())
        } finally {
            models.clear()
            first.close()
            second.close()
            bootstrap.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun newViewModelRestoresTheWholeNavigationStackWithoutRetainingTheOldScene() {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("saved-state-test")
        val models = ViewModelStore()
        try {
            val handle = SavedStateHandle()
            val original = HarmonicSceneViewModel(bootstrap.app, handle)
            models.put("original", original)
            original.scene.navigation.apply {
                openSettings("debug")
                openStory(StoryRoute(42, showWebsite = true, scrollToCommentId = 99))
                openSubmissions("alice")
                openEditor(EditorDestination(EditorType.COMMENT_REPLY, itemId = 99))
            }
            val expected = original.scene.navigation.restoration()
            val restored = HarmonicSceneViewModel(bootstrap.app, restoredHandle(handle))
            models.put("restored", restored)
            assertNotSame(original.scene, restored.scene)
            assertEquals(expected, restored.scene.navigation.restoration())
            assertNull(expected.storyDestination, "Only routes belong in lifecycle state")
            restored.scene.navigation.closeEditor()
            restored.scene.navigation.closeSubmissions()
            assertEquals(42, restored.scene.navigation.state.value.storyRequest?.storyId)
            restored.scene.navigation.detailRemovedFromBackStack()
            assertEquals("debug", restored.scene.navigation.state.value.settingsRequest?.initialSectionRoute)
            assertEquals(expected, original.scene.navigation.restoration(), "Windows must remain independent")
        } finally { models.clear(); bootstrap.close() }
    }

    @Test
    fun settingsListAndNestedParentSurviveSavedStateSerialization() {
        val handle = SavedStateHandle()
        val original = SettingsNavigationViewModel(handle, null, false)
        original.navigation.navigateTo(SettingsSection.Debug)
        original.navigation.navigateTo(SettingsSection.About, preserveCurrentDetail = true)
        val restored = SettingsNavigationViewModel(restoredHandle(handle), null, false)
        assertEquals(original.navigation.savedRoutes(), restored.navigation.savedRoutes())
        assertTrue(restored.navigation.navigateBack())
        assertEquals(SettingsSection.Debug, restored.navigation.state.value.selectedSection)
        assertTrue(restored.navigation.navigateBack())
        val emptyHandle = SavedStateHandle()
        val list = SettingsNavigationViewModel(emptyHandle, SettingsSection.Debug, false)
        assertTrue(list.navigation.navigateBack())
        assertFalse(SettingsNavigationViewModel(restoredHandle(emptyHandle), SettingsSection.Debug, false)
            .navigation.state.value.canNavigateBackWithinSettings)
        assertTrue(list.navigation.state.value.detailStack.isEmpty())
    }

    @Test
    fun commentsReadingPositionAndCollapsedThreadsRestoreInANewScene() {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("comments-state-test")
        val first = bootstrap.app.createScene()
        val second = bootstrap.app.createScene()
        try {
            first.navigation.openStory(StoryRoute(42))
            val request = requireNotNull(first.navigation.state.value.storyRequest)
            val handle = SavedStateHandle()
            val original = CommentsViewModel(first, request, handle)
            original.session.scrollProgress.apply {
                initialized = true
                topCommentId = 99
                topCommentOffset = -23
                collapsedIDs += setOf(51, 52)
            }
            val restored = CommentsViewModel(second, request, restoredHandle(handle))
            assertNotSame(original.session, restored.session)
            assertEquals(99, restored.session.scrollProgress.topCommentId)
            assertEquals(-23, restored.session.scrollProgress.topCommentOffset)
            assertEquals(setOf(51, 52), restored.session.scrollProgress.collapsedIDs)
            assertTrue(restored.session.scrollProgress.initialized)
        } finally { first.close(); second.close(); bootstrap.close() }
    }

    // Exercise the same SavedStateHandle provider used by the lifecycle registry, without reusing
    // its ViewModelStore. This simulates process reconstruction rather than a retained rotation.
    private fun restoredHandle(handle: SavedStateHandle) = SavedStateHandle.createHandle(
        restoredState = handle.savedStateProvider().saveState(), defaultState = null,
    )
}
