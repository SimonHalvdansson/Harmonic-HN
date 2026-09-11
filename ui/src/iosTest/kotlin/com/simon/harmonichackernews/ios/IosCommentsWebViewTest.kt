package com.simon.harmonichackernews.ios

import com.simon.harmonichackernews.presentation.WebPreloadEnvironment
import com.simon.harmonichackernews.settings.WebViewPreloadMode
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationTransitionDurationMillis
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.UIKit.UIUserInterfaceStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalForeignApi::class, ExperimentalCoroutinesApi::class)
class IosCommentsWebViewTest {
    private val url = "http://127.0.0.1:9/article"

    @Test
    fun unopenedBrowserStaysUnallocatedThroughAppearanceAndNavigationUpdates() {
        val browser = IosCommentsWebView(url)
        try {
            assertNull(browser.view)
            browser.updateAppearance(dark = true, matchTheme = true)
            assertEquals(url, browser.currentUrl())
            assertFalse(browser.canGoBack())
            browser.goBack()
            browser.toggleInversion()
            assertNull(browser.view)
        } finally {
            browser.dispose()
        }
        assertNull(browser.view)
    }

    @Test
    fun explicitLoadCreatesOneBrowserWithThePreviouslySelectedAppearance() {
        val browser = IosCommentsWebView(url)
        try {
            browser.updateAppearance(dark = true, matchTheme = true)
            browser.ensureLoaded()
            val view = assertNotNull(browser.view)
            assertEquals(UIUserInterfaceStyle.UIUserInterfaceStyleDark, view.overrideUserInterfaceStyle)
            browser.ensureLoaded()
            assertSame(view, browser.view)
            browser.reload()
            assertSame(view, browser.view)
            browser.updateAppearance(dark = false, matchTheme = true)
            assertEquals(UIUserInterfaceStyle.UIUserInterfaceStyleLight, view.overrideUserInterfaceStyle)
        } finally {
            browser.dispose()
        }
        assertNull(browser.view)
    }

    @Test
    fun invalidUrlsAndDisposalDoNotCreateABrowser() {
        val invalid = IosCommentsWebView("javascript:alert(1)")
        invalid.ensureLoaded()
        assertFalse(invalid.load("file:///private/test.html"))
        assertNull(invalid.view)
        invalid.dispose()

        val disposed = IosCommentsWebView(url)
        disposed.dispose()
        disposed.ensureLoaded()
        disposed.reload()
        assertFalse(disposed.load(url))
        assertNull(disposed.view)
    }

    @Test
    fun preloadingWaitsForFirstDrawAndTheOpeningTransition() = runTest {
        val browser = IosCommentsWebView(url)
        val firstDraw = CompletableDeferred<Unit>()
        try {
            launch {
                browser.preloadAfterOpening(firstDraw, WebViewPreloadMode.ALWAYS.storedValue, 0) {
                    WebPreloadEnvironment(unmeteredConnection = false, batteryPercent = 100)
                }
            }
            advanceUntilIdle()
            assertNull(browser.view)
            firstDraw.complete(Unit)
            runCurrent()
            advanceTimeBy(ActivityNavigationTransitionDurationMillis.toLong() - 1)
            runCurrent()
            assertNull(browser.view)
            advanceTimeBy(1)
            runCurrent()
            assertNotNull(browser.view)
        } finally {
            browser.dispose()
        }
    }

    @Test
    fun preloadPolicyRespectsNeverWifiAndBatteryAtLoadTime() = runTest {
        val cases = listOf(
            Triple(WebViewPreloadMode.NEVER, WebPreloadEnvironment(true, 100), false),
            Triple(WebViewPreloadMode.WIFI_ONLY, WebPreloadEnvironment(false, 100), false),
            Triple(WebViewPreloadMode.ALWAYS, WebPreloadEnvironment(true, 10), false),
            Triple(WebViewPreloadMode.WIFI_ONLY, WebPreloadEnvironment(true, 100), true),
        )
        for ((mode, environment, expected) in cases) {
            val browser = IosCommentsWebView(url)
            try {
                launch {
                    browser.preloadAfterOpening(CompletableDeferred(Unit), mode.storedValue, 20) {
                        environment
                    }
                }
                advanceUntilIdle()
                assertEquals(expected, browser.view != null, mode.name)
            } finally {
                browser.dispose()
            }
        }
    }

    @Test
    fun leavingDuringTheTransitionCancelsPreloading() = runTest {
        val browser = IosCommentsWebView(url)
        val job = launch {
            browser.preloadAfterOpening(CompletableDeferred(Unit), WebViewPreloadMode.ALWAYS.storedValue, 0) {
                WebPreloadEnvironment(true, 100)
            }
        }
        runCurrent()
        job.cancel()
        browser.dispose()
        advanceUntilIdle()
        assertNull(browser.view)
    }
}
