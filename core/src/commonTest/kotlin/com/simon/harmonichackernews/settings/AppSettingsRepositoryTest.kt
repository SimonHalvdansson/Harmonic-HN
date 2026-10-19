package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

class AppSettingsRepositoryTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun scopedObserversShareSnapshotsAndResumeWithFreshSettings() = runTest {
        val store = InMemoryKeyValueStore()
        val delegate = StoredUserSettings(store, store.changes, { null }, false, false)
        var projections = 0
        val reader = object : UserSettings by delegate {
            override val story: StoryPreferences get() = delegate.story.also { projections++ }
        }
        val repository = AppSettingsRepository(reader, StoredSettingsMutator(store), backgroundScope)
        assertEquals(0, projections)
        val first = mutableListOf<AppSettings>()
        val second = mutableListOf<AppSettings>()
        val a = backgroundScope.launch { repository.updates.toList(first) }
        val b = backgroundScope.launch { repository.updates.toList(second) }
        runCurrent()
        assertEquals(1, projections)
        repository.setStoryBoolean(StoryBooleanPreference.SHOW_POINTS, false)
        runCurrent()
        assertEquals(2, projections)
        assertEquals(first, second)
        assertFalse(first.last().story.showPoints)
        a.cancel()
        b.cancel()
        runCurrent()
        repository.setStoryBoolean(StoryBooleanPreference.SHOW_POINTS, true)
        runCurrent()
        assertEquals(2, projections)
        val resumed = mutableListOf<AppSettings>()
        backgroundScope.launch { repository.updates.toList(resumed) }
        runCurrent()
        assertEquals(3, projections)
        assertEquals(1, resumed.size)
        assertTrue(resumed.single().story.showPoints)
    }

    @Test
    fun hideDeadCommentsDefaultsOnAndPersistsIndependentlyOfDelayedComments() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow())
        assertTrue(repository.snapshot().comments.hideDeadComments)
        assertFalse(repository.snapshot().comments.hideDelayedComments)

        repository.setCommentBoolean(CommentBooleanPreference.HIDE_DEAD_COMMENTS, false)
        repository.setCommentBoolean(CommentBooleanPreference.HIDE_DELAYED_COMMENTS, true)
        val restored = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow())
        assertFalse(restored.snapshot().comments.hideDeadComments)
        assertTrue(restored.snapshot().comments.hideDelayedComments)

        restored.setCommentBoolean(CommentBooleanPreference.HIDE_DEAD_COMMENTS, true)
        assertTrue(AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow()).snapshot().comments.hideDeadComments)
    }

    @Test
    fun unknownQuoteAppearanceFallsBackToMaterialAndBoth() {
        val repository = AppSettingsRepository(
            TestKeyValueStore(mapOf(
                UserPreferenceKeys.QUOTE_HIGHLIGHT_COLOR to "unknown",
                UserPreferenceKeys.QUOTE_HIGHLIGHT_STYLE to "unknown",
            )),
            kotlinx.coroutines.flow.emptyFlow(),
        )
        assertEquals(QuoteHighlightColor.MATERIAL, repository.snapshot().comments.quoteHighlightColor)
        assertEquals(QuoteHighlightStyle.BOTH, repository.snapshot().comments.quoteHighlightStyle)
    }

    @Test
    fun quoteHighlightingDefaultsOffAndPersistsInDisplaySettings() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow())
        assertFalse(repository.snapshot().comments.highlightQuotes)
        assertEquals(QuoteHighlightColor.MATERIAL, repository.snapshot().comments.quoteHighlightColor)
        assertEquals(QuoteSpacing.COMFORTABLE, repository.snapshot().comments.quoteSpacing)
        repository.setQuoteSpacing(QuoteSpacing.COMPACT)
        repository.setQuoteHighlightColor(QuoteHighlightColor.NEUTRAL)
        repository.setQuoteHighlightStyle(QuoteHighlightStyle.SIDE_LINE)
        repository.setCommentBoolean(CommentBooleanPreference.HIGHLIGHT_QUOTES, false)
        val restored = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow()).snapshot().comments
        assertFalse(restored.highlightQuotes)
        assertEquals(QuoteSpacing.COMPACT, restored.quoteSpacing)
        assertEquals(QuoteHighlightColor.NEUTRAL, restored.quoteHighlightColor)
        assertEquals(QuoteHighlightStyle.SIDE_LINE, restored.quoteHighlightStyle)
        assertFalse(CommentDisplaySettings.from(
            restored, showInvert = false, isTablet = false,
            hasAccountDetails = false, canProvideSummary = false,
        ).highlightQuotes)
        repository.setCommentBoolean(CommentBooleanPreference.HIGHLIGHT_QUOTES, true)
        assertTrue(repository.snapshot().comments.highlightQuotes)
    }

    @Test
    fun sideBySideDefaultsOnAndPersistsWithoutChangingBrowserPreferences() {
        val store = TestKeyValueStore()
        val repository = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow())
        assertTrue(repository.snapshot().appearance.sideBySideEnabled)
        repository.setAppearanceBoolean(AppearanceBooleanPreference.SIDE_BY_SIDE_ENABLED, false)
        val restored = AppSettingsRepository(store, kotlinx.coroutines.flow.emptyFlow())
        assertFalse(restored.snapshot().appearance.sideBySideEnabled)
        assertTrue(restored.snapshot().reading.integratedWebView)
        restored.setReadingBoolean(ReadingBooleanPreference.INTEGRATED_WEB_VIEW, false)
        restored.setAppearanceBoolean(AppearanceBooleanPreference.SIDE_BY_SIDE_ENABLED, true)
        assertTrue(restored.snapshot().appearance.sideBySideEnabled)
        assertFalse(restored.snapshot().reading.integratedWebView)
    }

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
            StoryDisplaySettings.from(restored).listSelector,
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
        assertEquals(true, repository.snapshot().comments.userAvatarsEnabled)
        assertTrue(repository.snapshot().comments.roundedDepthIndicators)
        assertEquals(setOf(UserAvatarStyle.MOSAIC), repository.snapshot().comments.userAvatarOptions.styles)
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
        val display = CommentDisplaySettings.from(
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
