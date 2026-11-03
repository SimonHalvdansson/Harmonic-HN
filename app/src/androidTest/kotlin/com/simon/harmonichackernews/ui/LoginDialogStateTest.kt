package com.simon.harmonichackernews.ui

import android.view.KeyEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.network.HackerNewsActionRepository
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.HackerNewsAuthenticatedSession
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.network.HackerNewsCaptchaFormField
import com.simon.harmonichackernews.network.HackerNewsCredentials
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.network.HackerNewsWebRepository
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.platform.HackerNewsAccountState
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import com.simon.harmonichackernews.presentation.LoginWorkflow
import com.simon.harmonichackernews.ui.common.LoginDialog
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.SettingsDialogTextButton
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginDialogStateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun credentialsAndInformationChildSurviveRecreationAndBackClosesOnlyTheChild() {
        val fixture = Fixture()
        var dismissals = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            Theme { LoginDialog({ dismissals++ }, fixture.workflow, {}, {}, captchaDialog = { _, _, _ -> }) }
        }
        enterDraft()
        compose.onNodeWithText("How does login work?").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("OK").fetchSemanticsNodes().size == 1 }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("OK").assertIsDisplayed()
        assertDraft()
        Espresso.pressBack()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("OK").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("login-dialog").assertIsDisplayed()
        assertDraft()
        assertEquals(0, dismissals)
        assertEquals(0, fixture.actions.loginAttempts)
    }

    @Test
    fun backDuringLoginKeepsTheBusyDialogOpaqueAndDoesNotCancelTheAttempt() {
        val fixture = Fixture()
        var dismissals = 0
        compose.setContent {
            Theme { LoginDialog({ dismissals++ }, fixture.workflow, {}, {}, captchaDialog = { _, _, _ -> }) }
        }
        enterDraft()
        compose.onNodeWithText("Log in", substring = false).performClick()
        compose.waitUntil(5_000) { fixture.actions.loginAttempts == 1 }
        compose.onNodeWithText("Logging in…").assertIsDisplayed()
        val beforeBounds = compose.onNodeWithTag("login-dialog").fetchSemanticsNode().boundsInWindow
        val beforePixels = compose.onNodeWithTag("login-dialog").captureToImage().toPixelMap()
        val before = beforePixels[beforePixels.width / 2, 12]
        Espresso.pressBack()
        compose.waitForIdle()
        val afterBounds = compose.onNodeWithTag("login-dialog").fetchSemanticsNode().boundsInWindow
        val afterPixels = compose.onNodeWithTag("login-dialog").captureToImage().toPixelMap()
        val after = afterPixels[afterPixels.width / 2, 12]
        assertEquals(beforeBounds, afterBounds)
        assertEquals(before.red, after.red, .01f)
        assertEquals(before.green, after.green, .01f)
        assertEquals(before.blue, after.blue, .01f)
        assertEquals(0, dismissals)
        assertFalse(fixture.actions.loginCancelled)
        fixture.actions.loginResult.complete(HackerNewsActionResult.Failure("Fixture rejection"))
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Login failed. Check your username and password, then try again.")
                .fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Login failed. Check your username and password, then try again.").assertIsDisplayed()
        assertDraft()
    }

    @Test
    fun pendingCaptchaSurvivesRecreationAndCancelsWithoutClosingTheLoginDraft() {
        val fixture = Fixture()
        val challenge = HackerNewsCaptchaChallenge(
            actionUrl = "https://news.ycombinator.com/login",
            siteKey = "fixture-site-key",
            formFields = listOf(
                HackerNewsCaptchaFormField("acct", "AliceCase"),
                HackerNewsCaptchaFormField("pw", "temporary-draft-password"),
                HackerNewsCaptchaFormField("goto", "news"),
            ),
            useCookies = true,
        )
        fixture.actions.loginResult.complete(HackerNewsActionResult.Captcha(challenge))
        var observed: HackerNewsCaptchaChallenge? = null
        var captchaWindow: Window? = null
        var dismissals = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            Theme {
                LoginDialog({ dismissals++ }, fixture.workflow, {}, {}, captchaDialog = { current, cancel, _ ->
                    observed = current
                    SettingsAlertDialog(
                        onDismissRequest = cancel,
                        text = {
                            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
                            SideEffect { captchaWindow = window }
                            Text("Fixture CAPTCHA")
                        },
                        confirmButton = { SettingsDialogTextButton(onClick = cancel) { Text("Cancel CAPTCHA") } },
                    )
                })
            }
        }
        enterDraft()
        compose.onNodeWithText("Log in", substring = false).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Fixture CAPTCHA").fetchSemanticsNodes().size == 1 }
        assertEquals(challenge, observed)
        observed = null
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Fixture CAPTCHA").assertIsDisplayed()
        assertNotNull(observed)
        assertEquals(challenge, observed)
        compose.waitUntil(5_000) { captchaWindow?.decorView?.hasWindowFocus() == true }
        // Espresso can select the unfocused activity root under a restored stack of dialogs.
        // Inject the actual key only after the restored CAPTCHA window owns focus.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Fixture CAPTCHA").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("login-dialog").assertIsDisplayed()
        assertDraft()
        assertEquals(0, dismissals)
        assertEquals(1, fixture.actions.loginAttempts)
    }

    private fun enterDraft() {
        compose.onNodeWithTag("login-username").performTextReplacement("AliceCase")
        compose.onNodeWithTag("login-password").performTextReplacement("temporary-draft-password")
        compose.onNodeWithContentDescription("Show password").performClick()
    }

    private fun assertDraft() {
        compose.onNodeWithTag("login-username").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("AliceCase")))
        compose.onNodeWithTag("login-password").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("temporary-draft-password")))
        compose.onNodeWithContentDescription("Hide password").assertExists()
    }

    private class Fixture {
        val accounts = object : ObservableHackerNewsAccountRepository {
            override val accountState = MutableStateFlow<HackerNewsAccountState>(HackerNewsAccountState.LoggedOut)
            override suspend fun saveAccount(account: HackerNewsAccount): Boolean {
                accountState.value = HackerNewsAccountState.LoggedIn(account)
                return true
            }
            override suspend fun clearAccount(): Boolean {
                accountState.value = HackerNewsAccountState.LoggedOut
                return true
            }
        }
        val actions = Actions()
        private val session = object : HackerNewsAuthenticatedSession {
            override val actions: HackerNewsActionRepository get() = this@Fixture.actions
            override val authenticatedWeb: HackerNewsWebRepository get() = error("Not used by login")
            override val publicWeb: HackerNewsWebRepository get() = error("Not used by login")
            override fun reset() = Unit
        }
        val workflow = LoginWorkflow(accounts, HackerNewsUserService(session, accounts))
    }

    private class Actions : HackerNewsActionRepository {
        val loginResult = CompletableDeferred<HackerNewsActionResult>()
        @Volatile var loginAttempts = 0
        @Volatile var loginCancelled = false
        override suspend fun login(credentials: HackerNewsCredentials): HackerNewsActionResult {
            loginAttempts++
            return try { loginResult.await() } catch (cancelled: CancellationException) {
                loginCancelled = true
                throw cancelled
            }
        }
        override suspend fun continueLoginWithCaptcha(challenge: HackerNewsCaptchaChallenge, captchaResponse: String): HackerNewsActionResult = error("Not used")
        override suspend fun vote(credentials: HackerNewsCredentials, itemId: String, direction: String): HackerNewsActionResult = error("Not used")
        override suspend fun comment(credentials: HackerNewsCredentials, itemId: String, text: String): HackerNewsActionResult = error("Not used")
        override suspend fun submit(credentials: HackerNewsCredentials, title: String, text: String, url: String): HackerNewsActionResult = error("Not used")
        override suspend fun submitAfterLoginCaptcha(challenge: HackerNewsCaptchaChallenge, captchaResponse: String, title: String, text: String, url: String): HackerNewsActionResult = error("Not used")
        override suspend fun continueCaptchaAction(challenge: HackerNewsCaptchaChallenge, captchaResponse: String): HackerNewsActionResult = error("Not used")
        override suspend fun setFavorite(credentials: HackerNewsCredentials, itemId: Int, favorite: Boolean): HackerNewsActionResult = error("Not used")
        override suspend fun continueFavoriteCaptcha(challenge: HackerNewsCaptchaChallenge, captchaResponse: String, itemId: Int, favorite: Boolean): HackerNewsActionResult = error("Not used")
    }

    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        val palette = HarmonicThemeCatalog.resolve("light", false)
        HarmonicTheme(palette.colorScheme, palette.dark, content = content)
    }
}
