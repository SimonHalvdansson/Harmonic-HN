@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.SurfaceEffectMode
import com.simon.harmonichackernews.settings.SurfaceEffectPreferences
import com.simon.harmonichackernews.ui.common.HazeHost
import com.simon.harmonichackernews.ui.common.LocalHazePreferences
import com.simon.harmonichackernews.ui.common.currentSharedHazeState
import com.simon.harmonichackernews.ui.common.sharedHazeDialogBackground
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.content.CommentRowStyle
import com.simon.harmonichackernews.ui.content.SettingsCommentPreviewModel
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationStack
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Clock-matched captures cover native children, sampled edges, text and backdrop effects. */
@RunWith(AndroidJUnit4::class)
class ActivityNavigationRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun solidSurfacesPreserveOpeningNestedOpeningAndBack() = exercise(SurfaceEffectMode.Solid)
    @Test fun frostedSurfacesPreserveOpeningNestedOpeningAndBack() = exercise(SurfaceEffectMode.Frosted)
    @Test fun browserSurfacesPreserveOpeningNestedOpeningAndBack() = exercise(SurfaceEffectMode.Frosted, browser = true)

    private fun exercise(mode: SurfaceEffectMode, browser: Boolean = false) {
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        var entries by mutableStateOf(emptyList<Int>())
        val nativeViews = mutableMapOf<Int, View>()
        val browsers = mutableMapOf<Int, WebView>()
        try {
            if (browser) {
                val loaded = AtomicInteger()
                compose.runOnUiThread {
                    for (id in 1..2) {
                        browsers[id] = WebView(compose.activity).apply {
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    loaded.incrementAndGet()
                                }
                            }
                            loadDataWithBaseURL(null,
                                "<html><body style='margin:0;background:#ffa040'>Native browser surface $id</body></html>",
                                "text/html", "UTF-8", null)
                        }
                    }
                }
                compose.waitUntil(10_000) { loaded.get() == 2 }
            }
            compose.setContent {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                CompositionLocalProvider(
                    LocalHarmonicUiDependencies provides HarmonicUiDependencies(app, scene),
                    LocalHazePreferences provides SurfaceEffectPreferences(mode = mode),
                ) {
                    HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                        Box(Modifier.fillMaxWidth().height(600.dp).testTag("transition-comparison")) {
                            ActivityNavigationStack(
                                entries = entries,
                                entryKey = { it },
                                root = {
                                    Column(Modifier.fillMaxSize().background(Color(0xffd5e5f5))) {
                                        Text("Stories")
                                        repeat(12) { Text("Story $it", Modifier.padding(16.dp)) }
                                    }
                                },
                                content = { id ->
                                    HazeHost {
                                        val haze = currentSharedHazeState()
                                        Box(Modifier.fillMaxSize().background(Color.White)) {
                                            Column(Modifier.fillMaxSize().sharedHazeSource(haze)) {
                                                AndroidView(
                                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                                    factory = { context ->
                                                        (browsers[id] ?: TextView(context).apply {
                                                            text = "Native browser surface $id"
                                                            setBackgroundColor(0xffffa040.toInt())
                                                            setTextColor(android.graphics.Color.BLACK)
                                                        }).also { nativeViews[id] = it }
                                                    },
                                                )
                                                Text("Discussion $id", Modifier.padding(16.dp))
                                                repeat(4) {
                                                    CommentRow(SettingsCommentPreviewModel, rowStyle)
                                                }
                                            }
                                            Box(Modifier.padding(top = 240.dp, start = 16.dp)
                                                .size(240.dp, 64.dp)
                                                .sharedHazeDialogBackground(Color.White, RoundedCornerShape(24.dp))) {
                                                Text("Floating controls", Modifier.padding(16.dp))
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            fun frames(label: String) {
                var elapsed = 0L
                for (target in listOf(32L, 64L, 96L, 160L, 256L, 560L)) {
                    compose.mainClock.advanceTimeBy(target - elapsed)
                    elapsed = target
                    capture("${if (browser) "Browser" else mode.name}-$label-$target")
                }
            }
            compose.runOnUiThread { entries = listOf(1) }
            frames("open")
            assertNativeSurfaceVisible()
            val first = nativeViews.getValue(1)
            compose.runOnUiThread { entries = listOf(1, 2) }
            frames("nested")
            assertNativeSurfaceVisible()
            compose.runOnUiThread { entries = listOf(1) }
            frames("back")
            assertNativeSurfaceVisible()
            assertTrue("Returning must retain the original native child", nativeViews.getValue(1) === first)
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnUiThread { browsers.values.forEach(WebView::destroy) }
            scene.close()
        }
    }

    private fun assertNativeSurfaceVisible() {
        val pixels = compose.onNodeWithTag("transition-comparison").captureToImage().toPixelMap()
        val pixel = pixels[pixels.width - 10, 10]
        assertEquals(1f, pixel.red, 0.01f)
        assertEquals(160f / 255f, pixel.green, 0.01f)
        assertEquals(64f / 255f, pixel.blue, 0.01f)
    }

    private fun capture(name: String) {
        val folder = InstrumentationRegistry.getArguments().getString("transitionComparisonDir") ?: return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "transition-comparison/$folder/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = compose.onNodeWithTag("transition-comparison").captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val rowStyle = CommentRowStyle(
        displayStyle = DisplayStyle.RAISED, textSize = 14f, collectLinks = true,
        emphasizeMeta = false, depthIndicatorMode = "threads", showDivider = false,
        preferredFont = "default",
    )
}
