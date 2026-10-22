package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.awaitAndroidWebViewStartup
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.ui.common.CaptchaDialogContent
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptchaStartupTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val challenge = HackerNewsCaptchaChallenge(
        actionUrl = "https://news.ycombinator.com/login",
        siteKey = "test-site-key",
        formFields = emptyList(),
        useCookies = false,
    )

    @Test
    fun dialogStaysResponsiveUntilStartupCompletesThenCreatesTheBrowser() {
        val startup = CompletableDeferred<Unit>()
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CaptchaDialogContent(challenge, {}, {}, startWebView = {
                    startup.await()
                    awaitAndroidWebViewStartup(it)
                })
            }
        }
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Hacker News CAPTCHA").assertDoesNotExist()
        compose.onNodeWithText(UNAVAILABLE).assertDoesNotExist()
        startup.complete(Unit)
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("Hacker News CAPTCHA").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Continue").assertIsEnabled()
    }

    @Test
    fun dismissingDuringStartupCancelsBrowserCreation() {
        val startup = CompletableDeferred<Unit>()
        val completions = AtomicInteger()
        var visible by mutableStateOf(true)
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                if (visible) CaptchaDialogContent(challenge, { visible = false }, {}, startWebView = {
                    startup.await()
                    completions.incrementAndGet()
                    awaitAndroidWebViewStartup(it)
                })
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        startup.complete(Unit)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Hacker News CAPTCHA").assertDoesNotExist()
        assertEquals(0, completions.get())
    }

    @Test
    fun startupFailureShowsTheErrorAndKeepsContinueDisabled() {
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CaptchaDialogContent(challenge, {}, {}, startWebView = {
                    throw IllegalStateException("Test WebView provider unavailable")
                })
            }
        }
        compose.onNodeWithText(UNAVAILABLE).assertExists()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Hacker News CAPTCHA").assertDoesNotExist()
    }

    private companion object {
        const val UNAVAILABLE = "The CAPTCHA browser is unavailable. Check Android System WebView and try again."
    }
}
