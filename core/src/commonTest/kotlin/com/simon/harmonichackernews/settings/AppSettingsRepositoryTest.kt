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
    fun commentAppearanceDefaultsAndPersistedChoices() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow())
        assertEquals(UserAvatarMode.NONE, repository.snapshot().comments.userAvatarMode)
        assertEquals(CommentIndicatorThickness.STANDARD, repository.snapshot().comments.indicatorThickness)
        repository.setUserAvatarMode(UserAvatarMode.GENERATED)
        repository.setCommentIndicatorThickness(CommentIndicatorThickness.WIDE)
        repository.setCommentBoolean(CommentBooleanPreference.ROUNDED_DEPTH_INDICATORS, true)
        repository.setCommentBoolean(CommentBooleanPreference.CONTINUOUS_DEPTH_INDICATORS, true)
        repository.setCommentDepthIndicatorMode(CommentDepthPreferences.AUTHOR)
        val restored = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow()).snapshot().comments
        assertEquals(UserAvatarMode.GENERATED, restored.userAvatarMode)
        assertEquals(CommentIndicatorThickness.WIDE, restored.indicatorThickness)
        assertTrue(restored.roundedDepthIndicators)
        assertTrue(restored.continuousDepthIndicators)
        val display = com.simon.harmonichackernews.adapters.CommentDisplaySettings.from(
            restored, showInvert = false, isTablet = false, hasAccountDetails = false, canProvideSummary = false,
        )
        assertFalse(display.continuousDepthIndicators)
        store.putString(UserPreferenceKeys.USER_AVATAR_MODE, "invalid")
        store.putString(UserPreferenceKeys.COMMENT_INDICATOR_THICKNESS, "invalid")
        assertEquals(UserAvatarMode.NONE, repository.snapshot().comments.userAvatarMode)
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
