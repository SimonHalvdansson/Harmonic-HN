package com.simon.harmonichackernews.ui

import android.webkit.WebView
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.MutableIntState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.AndroidCommentsCoordinator
import com.simon.harmonichackernews.AndroidCommentsWebViewController
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.StoryNavigationSeed
import com.simon.harmonichackernews.settings.AppearanceBooleanPreference
import com.simon.harmonichackernews.settings.ReadingBooleanPreference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production Navigation3 panes and back dispatcher with a real, locally populated WebView. */
@RunWith(AndroidJUnit4::class)
class SideBySideNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun interceptedFullScreenStoryCanSplitThenReturnToFullScreen() = verifyStandaloneSplit(Entry.External)

    @Test fun nestedStoryCanSplitWithoutLosingItsParent() = verifyStandaloneSplit(Entry.Nested)

    private fun verifyStandaloneSplit(entry: Entry) = withStory(entry) { coordinator, _ ->
        val navigation = compose.activity.navigationController
        val request = requireNotNull(navigation.navigationState.state.value.storyRequest)
        val stack = navigation.navigationState.state.value.destinationStack
        val rootWidth = compose.activity.window.decorView.width.toFloat()
        val pane = compose.onNodeWithTag("comments-story-pane-${request.serial}")
        assertTrue(pane.fetchSemanticsNode().boundsInRoot.width < rootWidth * 0.85f)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(requireNotNull(coordinator.composeUiController).sideBySideActive)
            assertEquals(entry == Entry.External, navigation.isExternalStoryEntry)
            assertEquals(stack, navigation.navigationState.state.value.destinationStack)
            assertEquals(request, navigation.navigationState.state.value.storyRequest)
            assertFalse(compose.activity.isFinishing)
        }
        assertEquals(rootWidth, pane.fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    @Test fun submissionStoryKeepsItsListOwnerAfterLeavingSplit() = withStory(Entry.Submissions) { coordinator, _ ->
        val navigation = compose.activity.navigationController
        val submission = navigation.navigationState.state.value.submissionsRequest
        val story = navigation.navigationState.state.value.storyRequest
        assertNotNull(submission)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(requireNotNull(coordinator.composeUiController).sideBySideActive)
            assertEquals(submission, navigation.navigationState.state.value.submissionsRequest)
            assertEquals(story, navigation.navigationState.state.value.storyRequest)
        }
    }

    @Test fun systemPredictiveBackCancelsThenReturnsToCommentsWithoutClosingTheStory() = withStory { coordinator, browser ->
        val controller = requireNotNull(coordinator.composeUiController)
        val navigation = compose.activity.navigationController
        val request = navigation.navigationState.state.value.storyRequest
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            browser.scrollTo(0, 400)
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 700f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(350f, 700f, 0.7f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(controller.predictiveBackActive)
            assertTrue(controller.sideBySideActive)
            dispatcher.dispatchOnBackCancelled()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(controller.sideBySideActive)
            assertEquals(request, navigation.navigationState.state.value.storyRequest)
            assertEquals(400, browser.scrollY)
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 700f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(350f, 700f, 0.7f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(controller.sideBySideActive)
            assertTrue(controller.isSheetExpanded())
            assertEquals(request, navigation.navigationState.state.value.storyRequest)
            assertEquals(400, browser.scrollY)
        }
    }

    @Test fun browserHistoryIsUsedUnlessGoBackToCommentsIsEnabled() = withStory { coordinator, browser ->
        val settings = compose.activity.harmonicAppComposition.settings
        val controller = requireNotNull(coordinator.composeUiController)
        compose.runOnIdle { loadPage(browser, "second") }
        compose.waitUntil(10_000) { compose.runOnIdle { browser.title == "second" && browser.canGoBack() } }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) { compose.runOnIdle { browser.title == "first" } }
        compose.runOnIdle {
            assertTrue(controller.sideBySideActive)
            loadPage(browser, "third")
        }
        compose.waitUntil(10_000) { compose.runOnIdle { browser.title == "third" && browser.canGoBack() } }
        compose.runOnIdle {
            settings.setReadingBoolean(ReadingBooleanPreference.CLOSE_WEB_VIEW_ON_BACK, true)
        }
        compose.waitForIdle()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(controller.sideBySideActive)
            assertTrue(controller.isSheetExpanded())
            assertEquals("third", browser.title)
        }
    }

    @Test fun disablingTheSettingWhileSplitIsActiveRejoinsTheComments() = withStory { coordinator, _ ->
        val controller = requireNotNull(coordinator.composeUiController)
        compose.runOnIdle {
            compose.activity.harmonicAppComposition.settings
                .setAppearanceBoolean(AppearanceBooleanPreference.SIDE_BY_SIDE_ENABLED, false)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(controller.sideBySideAvailable)
            assertFalse(controller.sideBySideActive)
            assertTrue(controller.isSheetExpanded())
        }
    }

    private enum class Entry { Feed, External, Nested, Submissions }

    private fun withStory(entry: Entry = Entry.Feed, test: (AndroidCommentsCoordinator, WebView) -> Unit) {
        val activity = compose.activity
        val navigation = activity.navigationController
        assumeTrue("Requires a two-pane emulator", navigation.isAdaptiveTwoPane())
        val settings = activity.harmonicAppComposition.settings
        val originalSettings = settings.snapshot()
        val originalNavigation = navigation.navigationState.restoration()
        val externalEntry = navigation.field<MutableIntState>("externalStorySerial\$delegate")
        val originalExternalSerial = externalEntry.intValue
        // A synthetic identity keeps live HN responses and existing user caches from replacing
        // the seeded link (the Debug sample may resolve to a comment once networking returns).
        val id = Int.MAX_VALUE - entry.ordinal
        try {
            compose.runOnIdle {
                settings.setReadingBoolean(ReadingBooleanPreference.INTEGRATED_WEB_VIEW, true)
                settings.setReadingBoolean(ReadingBooleanPreference.CLOSE_WEB_VIEW_ON_BACK, false)
                settings.setAppearanceBoolean(AppearanceBooleanPreference.SIDE_BY_SIDE_ENABLED, true)
                navigation.navigationState.returnToStories()
                navigation.dismissWelcomeDialog()
                navigation.dismissChangelogDialog()
                val destination = StoryDestination(id, seed = StoryNavigationSeed(
                    StorySnapshot(id, title = "Reading beside the discussion", url = "https://example.invalid/side-by-side"),
                    isLink = true,
                ))
                if (entry == Entry.Submissions) {
                    navigation.openSubmissions("pg")
                    navigation.openSubmissionStory(destination)
                } else {
                    navigation.navigationState.openStory(destination)
                    if (entry == Entry.External) navigation.markExternalStoryEntry()
                    if (entry == Entry.Nested) navigation.navigationState.openLinkedStory(destination)
                }
            }
            compose.waitUntil(10_000) { compose.runOnIdle {
                val active = navigation.getCommentsCoordinator()
                active?.sessionKey == navigation.navigationState.state.value.storyRequest?.serial &&
                    active?.composeUiController?.sideBySideAvailable == true
            } }
            val coordinator = compose.runOnIdle { requireNotNull(navigation.getCommentsCoordinator()) }
            val controller = requireNotNull(coordinator.composeUiController)
            if (entry == Entry.External || entry == Entry.Nested) {
                val serial = requireNotNull(navigation.navigationState.state.value.storyRequest).serial
                assertEquals(compose.activity.window.decorView.width.toFloat(),
                    compose.onNodeWithTag("comments-story-pane-$serial").fetchSemanticsNode().boundsInRoot.width, 1f)
            }
            compose.runOnIdle { controller.toggleSideBySide() }
            compose.waitForIdle()
            val webController = coordinator.field<Any>("viewSession").field<AndroidCommentsWebViewController>("webViewController")
            compose.waitUntil(10_000) { compose.runOnIdle { webController.hasWebView() } }
            val browser = compose.runOnIdle { webController.field<WebView>("webView") }
            compose.runOnIdle { loadPage(browser, "first") }
            compose.waitUntil(10_000) { compose.runOnIdle { browser.title == "first" } }
            compose.runOnIdle {
                // This is the test's new browser instance, never an existing user tab.
                browser.clearHistory()
            }
            compose.waitForIdle()
            test(coordinator, browser)
        } finally {
            compose.runOnIdle {
                navigation.navigationState.restore(originalNavigation)
                externalEntry.intValue = originalExternalSerial
                settings.setAppearanceBoolean(AppearanceBooleanPreference.SIDE_BY_SIDE_ENABLED, originalSettings.appearance.sideBySideEnabled)
                settings.setReadingBoolean(ReadingBooleanPreference.INTEGRATED_WEB_VIEW, originalSettings.reading.integratedWebView)
                settings.setReadingBoolean(ReadingBooleanPreference.CLOSE_WEB_VIEW_ON_BACK, originalSettings.reading.closeWebViewOnBack)
            }
        }
    }

    private fun loadPage(browser: WebView, title: String) {
        browser.loadDataWithBaseURL("https://example.invalid/$title", """
            <html><head><title>$title</title><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="padding:24px;font:20px Georgia;background:#fff;color:#222">
            <h1>Reading beside the discussion</h1>
            ${(1..35).joinToString("") { "<p>Paragraph $it. The article stays in place while comments move between panes.</p>" }}
            </body></html>
        """.trimIndent(), "text/html", "UTF-8", "https://example.invalid/$title")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.field(name: String): T = javaClass.getDeclaredField(name).let {
        it.isAccessible = true
        it.get(this) as T
    }
}
