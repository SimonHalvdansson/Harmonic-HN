package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.data.LinkPreviewType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class AppSettingsRepositoryTest {
    @Test
    fun depthIndicatorSwitchRestoresColorsAcrossRepositoryRecreation() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow())
        repository.setCommentDepthIndicatorMode(CommentDepthPreferences.AUTHOR)
        repository.setCommentBoolean(CommentBooleanPreference.CONTINUOUS_DEPTH_INDICATORS, true)
        repository.setCommentDepthIndicatorsEnabled(false)
        repository.setCommentDepthIndicatorsEnabled(false)
        assertEquals(CommentDepthPreferences.NONE, repository.snapshot().comments.depthIndicatorMode)

        val restored = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow())
        restored.setCommentDepthIndicatorsEnabled(true)
        assertEquals(CommentDepthPreferences.AUTHOR, restored.snapshot().comments.depthIndicatorMode)
        assertTrue(restored.snapshot().comments.continuousDepthIndicators)
    }

    @Test
    fun depthIndicatorSwitchSupportsLegacyPreferences() {
        val disabledStore = TestKeyValueStore(
            mapOf(UserPreferenceKeys.COMMENT_DEPTH_INDICATORS to CommentDepthPreferences.NONE),
        )
        val disabled = AppSettingsRepository(disabledStore, kotlinx.coroutines.flow.emptyFlow())
        assertEquals(CommentDepthPreferences.NONE, disabled.snapshot().comments.depthIndicatorMode)
        disabled.setCommentDepthIndicatorsEnabled(true)
        assertEquals(CommentDepthPreferences.THEME_DEFAULT, disabled.snapshot().comments.depthIndicatorMode)

        val monochromeStore = TestKeyValueStore(mapOf(UserPreferenceKeys.MONOCHROME_COMMENT_DEPTH to true))
        val monochrome = AppSettingsRepository(monochromeStore, kotlinx.coroutines.flow.emptyFlow())
        monochrome.setCommentDepthIndicatorsEnabled(false)
        monochrome.setCommentDepthIndicatorsEnabled(true)
        assertEquals(CommentDepthPreferences.MONOCHROME, monochrome.snapshot().comments.depthIndicatorMode)
    }

    @Test
    fun storyListSelectorDefaultsToDropdownAndPersistsAcrossReaders() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow())
        assertEquals(StoryListSelector.DROPDOWN, repository.snapshot().story.listSelector)

        repository.setStoryListSelector(StoryListSelector.CHIPS)
        val restored = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow()).snapshot().story
        assertEquals(StoryListSelector.CHIPS, restored.listSelector)
        assertEquals(
            StoryListSelector.CHIPS,
            com.simon.harmonichackernews.presentation.StoryDisplaySettings.from(restored).listSelector,
        )
        assertFalse(restored.compactHeader)

        repository.setStoryListSelector(StoryListSelector.DROPDOWN)
        assertEquals(StoryListSelector.DROPDOWN, repository.snapshot().story.listSelector)
    }

    @Test
    fun unknownStoryListSelectorFallsBackToDropdown() {
        val store = TestKeyValueStore(mapOf(UserPreferenceKeys.STORY_LIST_SELECTOR to "future-mode"))
        val repository = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow())
        assertEquals(StoryListSelector.DROPDOWN, repository.snapshot().story.listSelector)
    }

    @Test
    fun commentAppearanceDefaultsAndPersistedChoices() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow())
        assertEquals(false, repository.snapshot().comments.userAvatarsEnabled)
        assertEquals(CommentIndicatorThickness.STANDARD, repository.snapshot().comments.indicatorThickness)
        repository.setUserAvatarsEnabled(true)
        repository.setCommentIndicatorThickness(CommentIndicatorThickness.WIDE)
        repository.setCommentBoolean(CommentBooleanPreference.ROUNDED_DEPTH_INDICATORS, true)
        repository.setCommentBoolean(CommentBooleanPreference.CONTINUOUS_DEPTH_INDICATORS, true)
        repository.setCommentDepthIndicatorMode(CommentDepthPreferences.AUTHOR)
        val restored = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow()).snapshot().comments
        assertEquals(true, restored.userAvatarsEnabled)
        assertEquals(CommentIndicatorThickness.WIDE, restored.indicatorThickness)
        assertTrue(restored.roundedDepthIndicators)
        assertTrue(restored.continuousDepthIndicators)
        val display = com.simon.harmonichackernews.adapters.CommentDisplaySettings.from(
            restored, showInvert = false, isTablet = false, hasAccountDetails = false, canProvideSummary = false,
        )
        assertFalse(display.continuousDepthIndicators)
        repository.setUserAvatarsEnabled(false)
        store.putString(UserPreferenceKeys.COMMENT_INDICATOR_THICKNESS, "invalid")
        assertEquals(false, repository.snapshot().comments.userAvatarsEnabled)
        assertEquals(CommentIndicatorThickness.STANDARD, repository.snapshot().comments.indicatorThickness)
    }

    @Test
    fun updatesEmitInitialAndChangedTypedSnapshots() = runTest {
        val store = TestKeyValueStore()
        val changes = flow {
            emit(Unit)
            store.putBoolean(UserPreferenceKeys.SHOW_POINTS, false)
            emit(Unit)
        }
        val repository = AppSettingsRepository(store, changes)

        val snapshots = repository.updates.take(2).toList()

        assertTrue(snapshots.first().story.showPoints)
        assertFalse(snapshots.last().story.showPoints)
    }

    @Test
    fun editorAndReaderShareTheExistingPreferenceSchema() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow())

        repository.setStoryBoolean(StoryBooleanPreference.COMPACT_VIEW, true)
        repository.setLinkPreviewEnabled(LinkPreviewType.TWITTER_X, true)
        repository.setAppearanceBoolean(AppearanceBooleanPreference.COMPACT_HEADER, true)
        repository.setStoryBoolean(StoryBooleanPreference.HIDE_JOBS, true)
        repository.setCommentBoolean(CommentBooleanPreference.HIDE_DELAYED_COMMENTS, true)

        val snapshot = repository.snapshot()
        assertTrue(snapshot.story.compactView)
        assertTrue(LinkPreviewType.TWITTER_X in snapshot.reading.enabledLinkPreviews)
        assertTrue(snapshot.story.compactHeader)
        assertTrue(snapshot.story.hideJobs)
        assertTrue(snapshot.comments.hideDelayedComments)
        assertEquals(true, store.getBoolean(UserPreferenceKeys.COMPACT_VIEW, false))
    }
}
