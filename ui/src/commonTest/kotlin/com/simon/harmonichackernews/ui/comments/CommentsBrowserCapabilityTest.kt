package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.CommentsSettingsState
import com.simon.harmonichackernews.presentation.CommentsState
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.StoredUserSettings
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommentsBrowserCapabilityTest {
    @Test
    fun unsupportedHostDisablesBrowserControlsWithoutChangingTheSavedPreference() {
        val settings = StoredUserSettings(InMemoryKeyValueStore(), emptyFlow())
        val feature = CommentsState(
            story = StoryListItemSnapshot(
                StorySnapshot(42, url = "https://example.com"),
                StoryPresentationSnapshot(isLink = true),
            ),
            settings = CommentsSettingsState(
                displaySettings = CommentDisplaySettings.from(
                    settings.comments, showInvert = false, isTablet = false,
                    hasAccountDetails = false, canProvideSummary = false,
                ),
                reading = settings.reading,
                integratedWebView = settings.reading.integratedWebView,
                smoothScroll = true,
                transparentStatusBar = false,
            ),
        )
        val platform = CommentsPlatformPresentation(
            adBlockActive = false, readerModeAvailable = false, readerModeEnabled = false,
            topInsetPx = 0, contentInsetLeftPx = 0, contentInsetRightPx = 0,
            integratedWebViewAvailable = false,
        )

        assertTrue(settings.reading.integratedWebView)
        assertFalse(assertNotNull(CommentsScreenStateFactory.create(feature, platform)).integratedWebView)
        assertTrue(settings.reading.integratedWebView)
        assertTrue(assertNotNull(CommentsScreenStateFactory.create(
            feature, platform.copy(integratedWebViewAvailable = true),
        )).integratedWebView)
    }
}
