package com.simon.harmonichackernews.ui

import android.Manifest
import android.graphics.Bitmap
import android.animation.ValueAnimator
import android.app.UiAutomation
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Choreographer
import android.view.PixelCopy
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.settings.UserTagColor
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.app.AppMetadata
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicHostConfiguration
import com.simon.harmonichackernews.network.NetworkGraphEnvironment
import com.simon.harmonichackernews.network.NetworkGraphFactory
import com.simon.harmonichackernews.ui.content.userTagColor
import com.simon.harmonichackernews.ui.settings.AndroidUserSettingsDialog
import com.simon.harmonichackernews.ui.settings.SynchronizedSettingsDialogs
import com.simon.harmonichackernews.ui.settings.UserDialogUiState
import com.simon.harmonichackernews.ui.settings.UserInfoUi
import com.simon.harmonichackernews.ui.settings.UserSettingsDialog
import com.simon.harmonichackernews.ui.settings.UserTagDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.InternalAPI
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserProfileDialogsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun inferredBioLinksRequireOptInAndDisappearAgain() {
        val about = "Builds https://example.com and writes at www.example.org."
        showProfile(about)
        fun links() = compose.onNodeWithText(about, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single()
            .getLinkAnnotations(0, about.length)

        assertTrue(links().isEmpty())
        compose.onNodeWithText("Clickable links").performClick()
        assertEquals(listOf("https://example.com", "https://www.example.org"), links().map {
            (it.item as androidx.compose.ui.text.LinkAnnotation.Url).url
        })
        compose.onNodeWithText("Plain text").performClick()
        assertTrue(links().isEmpty())
    }

    @Test
    fun bioWithoutPlausibleLinksDoesNotOfferTheToggle() {
        showProfile("Writes Main.kt and Node.js. Email alice@example.com.")
        compose.onNodeWithText("Clickable links").assertDoesNotExist()
    }

    @Test
    fun blockingRequiresInlineConfirmationAndCanBeCancelled() {
        var blocks = 0
        showProfile("Hello there", onBlock = { blocks++ })
        compose.onNodeWithText("Block", substring = false).performClick()
        compose.onNodeWithText("Block Alice?").assertIsDisplayed()
        assertEquals(0, blocks)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Hello there").assertIsDisplayed()
        assertEquals(0, blocks)
        compose.onNodeWithText("Block", substring = false).performClick()
        compose.onNodeWithText("Block user", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, blocks) }
    }

    @Test
    fun nestedTagDialogDimsProfileAndKeepsItsStateThroughKeyboardAndColorChanges() {
        var tagOpen by mutableStateOf(false)
        var profileWindow: Window? = null
        var saved: Pair<String, UserTagColor>? = null
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                SynchronizedSettingsDialogs {
                    UserSettingsDialog(
                        requestedUserName = "Alice",
                        state = profile("https://example.com"),
                        tag = "",
                        blocked = false,
                        ownProfile = false,
                        onDismiss = {}, onRetry = {}, onOpenSubmissions = {},
                        onEditTag = { tagOpen = true }, onToggleBlocked = {}, onReport = {},
                        nestedDialog = {
                            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
                            SideEffect { profileWindow = window }
                            if (tagOpen) UserTagDialog(
                                currentTag = "",
                                userName = "Alice",
                                onSave = { tag, color -> saved = tag to color; tagOpen = false },
                                onDismiss = { tagOpen = false },
                            )
                        },
                    )
                }
            }
        }
        compose.onNodeWithText("Clickable links").performClick()
        compose.waitForIdle()
        val before = brightestPixelRed(requireNotNull(profileWindow))
        compose.onNodeWithText("Set tag").performClick()
        compose.onNode(hasSetTextAction()).assertIsFocused().performTextInput("maintainer")
        compose.onNodeWithContentDescription("Purple tag color").performClick()
        compose.onNodeWithText("Color").assertIsDisplayed()
        compose.onNodeWithText("Alice • maintainer").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        val dimmed = brightestPixelRed(requireNotNull(profileWindow))
        assertTrue("The underlying profile must be dimmed: $dimmed vs $before", dimmed < before * .9f)
        compose.onNodeWithText("Set", substring = false).performClick()
        compose.waitUntil(5_000) { saved != null }
        compose.runOnIdle { assertEquals("maintainer" to UserTagColor.Purple, saved) }
        compose.onNodeWithText("Plain text").assertIsDisplayed()
        compose.waitForIdle()
        // GPU readback can round a fully restored surface by one channel value.
        assertTrue(abs(before - brightestPixelRed(requireNotNull(profileWindow))) <= 2)
    }

    @Test
    fun savingOnlyTheColorImmediatelyUpdatesTheProductionProfileLabel() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val network = NetworkGraphFactory.create(NetworkGraphEnvironment(
            scope = scope,
            userAgent = "UserProfileDialogsTest",
            engine = { ProfileFixtureEngine() },
        ))
        val settings = InMemoryKeyValueStore()
        val production = (compose.activity.application as HarmonicApplication).composition
        val app = HarmonicAppComposition(
            network = network,
            platform = production.platform,
            host = HarmonicHostConfiguration.inMemory(
                metadata = AppMetadata(debug = true),
                currentMinutesFromMidnight = { 0 },
                systemDark = { false },
                settingsStore = settings,
                settingsChanges = settings.changes,
            ),
        )
        val scene = app.createScene()
        app.userTags.setTag("TagColorFixture", "Maintainer", UserTagColor.Purple)
        var expectedPurple = Color.Unspecified
        var expectedGreen = Color.Unspecified
        try {
            // Surface fixture/transport errors directly and start the real profile with cached data.
            runBlocking { withTimeout(5_000) { network.userProfiles.load("TagColorFixture") } }
            compose.setContent {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(app, scene)) {
                    HarmonicTheme(palette.colorScheme, palette.dark) {
                        expectedPurple = userTagColor(UserTagColor.Purple)
                        expectedGreen = userTagColor(UserTagColor.Green)
                        AndroidUserSettingsDialog("TagColorFixture", onDismiss = {}, onTagChanged = {})
                    }
                }
            }
            val label = "Set tag (Maintainer)"
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().size == 1
            }
            fun renderedLabelColor(): Color {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                return layouts.single().layoutInput.style.color
            }
            assertEquals(expectedPurple, renderedLabelColor())
            compose.onNodeWithText(label).performClick()
            compose.onNodeWithText("Color").assertIsDisplayed()
            compose.onNodeWithContentDescription("Green tag color").performClick()
            compose.onNodeWithText("Set", substring = false).performClick()
            compose.waitUntil(5_000) { app.userTags.colorFor("TagColorFixture") == UserTagColor.Green }
            compose.onNodeWithText(label).assertIsDisplayed()
            assertEquals("Maintainer", app.userTags.tagFor("TagColorFixture"))
            assertEquals(expectedGreen, renderedLabelColor())
        } finally {
            scene.close()
            network.close()
            scope.cancel()
        }
    }

    /** Runs the production profile/session without network access or persistent user data. */
    @OptIn(InternalAPI::class)
    private class ProfileFixtureEngine : HttpClientEngineBase("profile-fixture") {
        override val config = HttpClientEngineConfig()
        override val supportedCapabilities = setOf(HttpTimeoutCapability)

        override suspend fun execute(data: HttpRequestData) = HttpResponseData(
            HttpStatusCode.OK,
            GMTDate(),
            headersOf(HttpHeaders.ContentType, "application/json"),
            HttpProtocolVersion.HTTP_1_1,
            ByteReadChannel("""{"id":"TagColorFixture","created":1577836800,"karma":42,"about":"Hello there","submitted":[1]}"""),
            callContext(),
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun setAndCancelKeepTheProfileMovingWithTheKeyboardUntilItHasClosed() = withSlowSystemAnimations {
        var tagOpen by mutableStateOf(false)
        var profileWindow: Window? = null
        var callbackImeBottom: Int? = null
        var latestAnimatedImeBottom = 0
        var saved = 0
        var cancelled = 0
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                SynchronizedSettingsDialogs {
                    UserSettingsDialog(
                        requestedUserName = "Alice", state = profile("Hello there"), tag = "",
                        blocked = false, ownProfile = false,
                        onDismiss = {}, onRetry = {}, onOpenSubmissions = {},
                        onEditTag = { tagOpen = true }, onToggleBlocked = {}, onReport = {},
                        nestedDialog = {
                            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
                            SideEffect { profileWindow = window }
                            if (tagOpen) UserTagDialog(
                                currentTag = "", userName = "Alice",
                                onSave = { _, _ ->
                                    callbackImeBottom = latestAnimatedImeBottom
                                    saved++
                                    tagOpen = false
                                },
                                onDismiss = {
                                    callbackImeBottom = latestAnimatedImeBottom
                                    cancelled++
                                    tagOpen = false
                                },
                            )
                        },
                    )
                }
            }
        }
        // Keep this retained node rather than polling Compose test APIs while native IME frames run.
        val profileTitle = compose.onNodeWithText("Alice", useUnmergedTree = true).fetchSemanticsNode()
        val fullScreenTop = stableTitleTop(profileTitle)
        val density = compose.activity.resources.displayMetrics.density
        val animationsEnabled = ValueAnimator.areAnimatorsEnabled() &&
            Settings.Global.getFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f &&
            Settings.Global.getFloat(compose.activity.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) > 0f

        for (action in listOf("Set", "Cancel")) {
            compose.onNodeWithText("Set tag").performClick()
            compose.onNode(hasSetTextAction()).assertIsFocused().performTextInput("maintainer")
            lateinit var tagDecor: View
            compose.runOnUiThread {
                tagDecor = WindowInspector.getGlobalWindowViews().single {
                    it !== compose.activity.window.decorView && it !== requireNotNull(profileWindow).decorView
                }
            }
            compose.waitUntil(5_000) {
                ViewCompat.getRootWindowInsets(tagDecor)?.let {
                    it.isVisible(WindowInsetsCompat.Type.ime()) && it.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0
                } == true
            }
            val keyboardOpenTop = stableTitleTop(profileTitle)
            val movement = fullScreenTop - keyboardOpenTop
            assertTrue("Opening the real IME must move the retained profile", movement > 12 * density)
            val samples = mutableListOf<ImeFrame>()
            lateinit var layoutListener: ViewTreeObserver.OnPreDrawListener
            val profileDecor = requireNotNull(profileWindow).decorView
            compose.runOnUiThread {
                latestAnimatedImeBottom = requireNotNull(ViewCompat.getRootWindowInsets(tagDecor))
                    .getInsets(WindowInsetsCompat.Type.ime()).bottom
                callbackImeBottom = null
                samples += ImeFrame(latestAnimatedImeBottom, profileTitle.boundsInWindow.top)
                ViewCompat.setWindowInsetsAnimationCallback(tagDecor,
                    object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                        override fun onProgress(
                            insets: WindowInsetsCompat,
                            runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                        ): WindowInsetsCompat {
                            latestAnimatedImeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                            return insets
                        }
                    },
                )
                // Read after layout so each top position corresponds to the live animated inset.
                layoutListener = ViewTreeObserver.OnPreDrawListener {
                    samples += ImeFrame(latestAnimatedImeBottom, profileTitle.boundsInWindow.top)
                    true
                }
                profileDecor.viewTreeObserver.addOnPreDrawListener(layoutListener)
            }
            try {
                compose.onNodeWithText(action, substring = false).performClick()
                compose.waitUntil(5_000) { callbackImeBottom != null }
                compose.waitForIdle()
                assertEquals("$action must retain the tag window until the animated IME inset is zero", 0, callbackImeBottom)
                lateinit var frames: List<ImeFrame>
                compose.runOnUiThread { frames = samples.toList() }
                val startingInset = frames.first().imeBottom
                val protectedFrames = frames.filter { it.imeBottom > startingInset / 2 }
                assertTrue(protectedFrames.isNotEmpty())
                assertTrue(
                    "The profile must not jump to its full-screen center while the keyboard is still on screen: $frames",
                    protectedFrames.all { it.profileTop < fullScreenTop - movement * .15f },
                )
                if (animationsEnabled) {
                    assertTrue(
                        "$action must include intermediate keyboard insets: $frames",
                        frames.map { it.imeBottom }.filter { it in 1 until startingInset }.distinct().size >= 2,
                    )
                    assertTrue(
                        "$action must move the profile through intermediate positions: $frames",
                        frames.map { it.profileTop.roundToInt() }
                            .filter { it > keyboardOpenTop + density && it < fullScreenTop - density }
                            .distinct().size >= 2,
                    )
                }
                assertEquals(fullScreenTop, stableTitleTop(profileTitle), 4f * density)
            } finally {
                compose.runOnUiThread {
                    profileDecor.viewTreeObserver.removeOnPreDrawListener(layoutListener)
                    ViewCompat.setWindowInsetsAnimationCallback(tagDecor, null)
                }
            }
        }
        compose.runOnIdle { assertEquals(1, saved); assertEquals(1, cancelled) }
    }

    /** Give native IME frames time to render on a loaded emulator, restoring exact prior settings. */
    private fun withSlowSystemAnimations(block: () -> Unit) {
        val resolver = compose.activity.contentResolver
        val keys = listOf(
            Settings.Global.ANIMATOR_DURATION_SCALE,
            Settings.Global.WINDOW_ANIMATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
        )
        // A null value is an absent setting; retain it rather than replacing it with a default.
        val original = keys.associateWith { Settings.Global.getString(resolver, it) }
        val automation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(UiAutomation.FLAG_DONT_USE_ACCESSIBILITY)
        automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
        try {
            keys.forEach { key ->
                assertTrue("Could not slow $key", Settings.Global.putString(resolver, key, "5.0"))
            }
            compose.waitUntil(5_000) {
                ValueAnimator.areAnimatorsEnabled() &&
                    keys.all { Settings.Global.getString(resolver, it) == "5.0" }
            }
            block()
        } finally {
            try {
                // Attempt every restoration even if one provider write fails.
                val restored = original.map { (key, value) ->
                    runCatching { Settings.Global.putString(resolver, key, value) }.getOrDefault(false)
                }
                assertTrue("Could not restore animation settings", restored.all { it })
            } finally {
                automation.dropShellPermissionIdentity()
            }
        }
    }

    private data class ImeFrame(val imeBottom: Int, val profileTop: Float)

    /** Wait for native insets/layout animation to settle without advancing or replacing its clock. */
    private fun stableTitleTop(node: SemanticsNode): Float {
        val done = CountDownLatch(1)
        var last = Float.NaN
        var stableFrames = 0
        lateinit var frameCallback: Choreographer.FrameCallback
        compose.runOnUiThread {
            frameCallback = Choreographer.FrameCallback {
                val current = node.boundsInWindow.top
                stableFrames = if (abs(last - current) < .1f) stableFrames + 1 else 0
                last = current
                if (stableFrames >= 8) done.countDown() else
                    Choreographer.getInstance().postFrameCallback(frameCallback)
            }
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
        try {
            assertTrue("Profile layout did not settle", done.await(8, TimeUnit.SECONDS))
        } finally {
            compose.runOnUiThread { Choreographer.getInstance().removeFrameCallback(frameCallback) }
        }
        return last
    }

    private fun showProfile(about: String, onBlock: (String) -> Unit = {}) {
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                UserSettingsDialog(
                    requestedUserName = "Alice", state = profile(about), tag = "",
                    blocked = false, ownProfile = false,
                    onDismiss = {}, onRetry = {}, onOpenSubmissions = {}, onEditTag = {},
                    onToggleBlocked = onBlock, onReport = {},
                )
            }
        }
    }

    private fun profile(about: String) = UserDialogUiState.Loaded(
        UserInfoUi("Alice", "42 karma · joined January 2020", about, true),
    )

    /** PixelCopy excludes the foreground tag window; the brightest profile pixels are its surface. */
    private fun brightestPixelRed(window: Window): Int {
        val decor = window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1)
        var result = -1
        PixelCopy.request(window, bitmap, { result = it; done.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result)
        var maximum = 0
        // Skip system bars: their icon color deliberately remains independent of dialog dimming.
        for (y in bitmap.height / 5 until bitmap.height * 4 / 5 step 4) {
            for (x in bitmap.width / 8 until bitmap.width * 7 / 8 step 4) {
                maximum = maxOf(maximum, android.graphics.Color.red(bitmap.getPixel(x, y)))
            }
        }
        bitmap.recycle()
        return maximum
    }
}
