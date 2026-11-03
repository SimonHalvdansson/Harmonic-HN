@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import android.view.View
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.app.AppMetadata
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicHostConfiguration
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.NetworkGraphEnvironment
import com.simon.harmonichackernews.network.NetworkGraphFactory
import com.simon.harmonichackernews.platform.StoredHistoryStore
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.settings.ArticleCategoryHistoryPicker
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryHistoryRestorationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun querySelectionAndScrollRestoreAfterLoadingTheSecondHistoryPage() {
        val history = StoredHistoryStore(InMemoryKeyValueStore())
        val settings = InMemoryKeyValueStore()
        val host = HarmonicHostConfiguration.inMemory(
            metadata = AppMetadata(debug = true), currentMinutesFromMidnight = { 0 },
            systemDark = { false }, settingsStore = settings, settingsChanges = settings.changes,
        )
        runBlocking {
            for (id in 1..60) {
                history.recordHistory(id, (100L - id) * 1_000)
                assertTrue(host.storyCacheRepository.storeStory(
                    id,
                    """{"id":$id,"type":"story","title":"Fixture history ${id.toString().padStart(2, '0')}","author":"fixture","points":12,"created_at_i":123,"url":"https://fixture.example/$id","children":[]}""",
                    cachedAtMillis = 1_000,
                ))
                assertTrue(host.storyCacheRepository.hydrateStory(Story().also { it.id = id }))
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = NoNetworkEngine()
        val network = NetworkGraphFactory.create(NetworkGraphEnvironment(
            scope = scope, userAgent = "CategoryHistoryRestorationTest", engine = { engine },
        ))
        val production = (compose.activity.application as HarmonicApplication).composition
        val app = HarmonicAppComposition(network, production.platform.copy(history = history), host)
        val scene = app.createScene()
        val categoryId = app.articleCategoryTraining.create("Fixture category")
        val restoration = StateRestorationTester(compose)
        try {
            restoration.setContent {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(app, scene)) {
                    HarmonicTheme(palette.colorScheme, palette.dark) {
                        ArticleCategoryHistoryPicker(categoryId, "Fixture category", emptySet(), onDismiss = {})
                    }
                }
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Fixture history 01").fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag("category-history-query").performTextReplacement("Fixture")
            compose.onNodeWithTag("category-history-query").performImeAction()
            lateinit var dialogDecor: View
            compose.runOnUiThread {
                dialogDecor = WindowInspector.getGlobalWindowViews().single { it !== compose.activity.window.decorView }
            }
            compose.waitUntil(5_000) {
                ViewCompat.getRootWindowInsets(dialogDecor)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom == 0
            }
            compose.onNodeWithText("Fixture history 02").performScrollTo().performClick()
            compose.onNodeWithText("Add (1)").assertIsEnabled()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
            compose.onNodeWithText("Load older stories").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Load older stories").fetchSemanticsNodes().isEmpty() &&
                    compose.onAllNodesWithText("Loading history…").fetchSemanticsNodes().isEmpty()
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
            val before = compose.onNodeWithText("Fixture history 41").fetchSemanticsNode().boundsInWindow.top
            restoration.emulateSavedInstanceStateRestore()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Fixture history 41").fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag("category-history-query").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Fixture")),
            )
            compose.onNodeWithText("Add (1)").assertIsEnabled()
            val after = compose.onNodeWithText("Fixture history 41").fetchSemanticsNode().boundsInWindow.top
            assertEquals(before, after, 4f * compose.activity.resources.displayMetrics.density)
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
            compose.onNodeWithText("Fixture history 02").assertIsOn()
            assertEquals("Every fixture must come from the production cache path", 0, engine.requests)
        } finally {
            scene.close()
            network.close()
            scope.cancel()
            history.close()
        }
    }

    @OptIn(InternalAPI::class)
    private class NoNetworkEngine : HttpClientEngineBase("category-history-cache-only") {
        override val config = HttpClientEngineConfig()
        override val supportedCapabilities = setOf(HttpTimeoutCapability)
        @Volatile var requests = 0
        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            requests++
            error("Unexpected network request in a cache-only history fixture")
        }
    }
}
