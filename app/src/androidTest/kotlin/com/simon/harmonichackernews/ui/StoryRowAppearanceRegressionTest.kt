package com.simon.harmonichackernews.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryRow
import com.simon.harmonichackernews.ui.content.StoryRowStyle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Optional frame exports allow exact before/after comparison, including intermediate fades. */
@RunWith(AndroidJUnit4::class)
class StoryRowAppearanceRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tintAnimationsAcrossThemesStylesAndPreviewSizes() = assertAnimation(read = false)

    @Test
    fun readAnimationsAcrossThemesStylesAndPreviewSizes() = assertAnimation(read = true)

    @OptIn(coil3.annotation.DelicateCoilApi::class)
    @Test
    fun faviconArrivalPreservesCrossfadeInLightDarkAndReadRows() {
        data class Case(val theme: String, val dimmed: Boolean) {
            val url = "https://favicon.test/$theme-$dimmed.png"
        }
        val cases = listOf("light", "dark").flatMap { theme ->
            listOf(false, true).map { Case(theme, it) }
        }
        val gates = cases.associate { it.url to CompletableDeferred<Unit>() }
        val delivered = ConcurrentHashMap<String, Boolean>()
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.MAGENTA)
        }
        val originalLoader = SingletonImageLoader.get(compose.activity)
        val loader = ImageLoader.Builder(compose.activity)
            .components {
                add(Interceptor { chain ->
                    gates.getValue(chain.request.data.toString()).await()
                    SuccessResult(bitmap.asImage(), chain.request, DataSource.NETWORK)
                })
            }
            .eventListener(object : EventListener() {
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    delivered[request.data.toString()] = true
                }
            })
            .build()
        SingletonImageLoader.setUnsafe(loader)
        try {
            val current = mutableStateOf(cases.first())
            compose.setContent {
                val case = current.value
                val palette = HarmonicThemeCatalog.resolve(case.theme, case.theme == "dark")
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    key(case) {
                        Box(Modifier.width(360.dp).background(palette.colorScheme.pageBackground).testTag("story-frame")) {
                            StoryRow(
                                model = SettingsStoryPreviewModel.copy(
                                    faviconUrl = case.url,
                                    previewImageFallback = null,
                                ),
                                style = StoryRowStyle(
                                    previewImageMode = StoryPreviewMode.OFF,
                                    borderlessLargeImage = false,
                                    compact = false,
                                    showPreviewText = false,
                                    showFavicon = true,
                                    showPoints = true,
                                    compactPoints = false,
                                    includeTopLevelDomain = true,
                                    showCommentCount = true,
                                    showIndex = true,
                                    commentsOnLeft = false,
                                    tintCard = false,
                                    displayStyle = DisplayStyle.RAISED,
                                    useHotnessIcon = false,
                                    preferredFont = "default",
                                    textSize = 16f,
                                    dimmed = case.dimmed,
                                ),
                                listItem = true,
                                animateChanges = true,
                            )
                        }
                    }
                }
            }
            cases.forEach { case ->
                compose.mainClock.autoAdvance = true
                compose.runOnIdle { current.value = case }
                compose.waitForIdle()
                compose.mainClock.autoAdvance = false
                val prefix = "favicon-${case.theme}-${case.dimmed}"
                val before = saveFrame("$prefix-before")
                gates.getValue(case.url).complete(Unit)
                compose.waitUntil(timeoutMillis = 5_000) { delivered[case.url] == true }
                // Drain the request's main-thread success callback before sampling animation time.
                compose.runOnUiThread { }
                var previousTime = 0L
                val frames = listOf(16L, 80L, 160L, 256L).map { time ->
                    compose.mainClock.advanceTimeBy(time - previousTime)
                    previousTime = time
                    saveFrame("$prefix-$time")
                }
                assertFalse("$prefix must reveal the loaded image", before.sameAs(frames.last()))
                assertFalse("$prefix must retain the crossfade", before.sameAs(frames[1]))
                assertFalse("$prefix must retain intermediate alpha", frames[1].sameAs(frames.last()))
                compose.mainClock.advanceTimeBy(128)
                assertTrue("$prefix must settle", frames.last().sameAs(captureFrame()))
            }
        } finally {
            compose.mainClock.autoAdvance = true
            SingletonImageLoader.setUnsafe(originalLoader)
            loader.shutdown()
        }
    }

    private fun assertAnimation(read: Boolean) {
        data class Case(val theme: String, val display: DisplayStyle, val preview: StoryPreviewMode)
        val cases = listOf("light", "dark").flatMap { theme ->
            DisplayStyle.entries.flatMap { display ->
                StoryPreviewMode.entries.map { Case(theme, display, it) }
            }
        }
        val current = mutableStateOf(cases.first())
        val changed = mutableStateOf(false)
        compose.setContent {
            val case = current.value
            val palette = HarmonicThemeCatalog.resolve(case.theme, case.theme == "dark")
            HarmonicTheme(palette.colorScheme, palette.dark) {
                key(case) {
                    Box(Modifier.width(360.dp).background(palette.colorScheme.pageBackground).testTag("story-frame")) {
                        StoryRow(
                            model = SettingsStoryPreviewModel.copy(
                                previewImageTintArgb = if (!read && changed.value) 0xffc6d7ee.toInt() else 0xffe3d4bf.toInt(),
                                faviconTintArgb = if (!read && changed.value) 0xffc6d7ee.toInt() else 0xffe3d4bf.toInt(),
                                tintFallbackArgb = 0xffeeeeee.toInt(),
                            ),
                            style = StoryRowStyle(
                                previewImageMode = case.preview,
                                borderlessLargeImage = false,
                                compact = false,
                                showPreviewText = true,
                                showFavicon = true,
                                showPoints = true,
                                compactPoints = false,
                                includeTopLevelDomain = true,
                                showCommentCount = true,
                                showIndex = true,
                                commentsOnLeft = false,
                                tintCard = true,
                                displayStyle = case.display,
                                useHotnessIcon = false,
                                preferredFont = "default",
                                textSize = 16f,
                                dimmed = read && changed.value,
                            ),
                            listItem = true,
                            animateChanges = true,
                        )
                    }
                }
            }
        }
        cases.forEach { case ->
            compose.mainClock.autoAdvance = true
            compose.runOnIdle {
                changed.value = false
                current.value = case
            }
            compose.waitForIdle()
            val prefix = "${if (read) "read" else "tint"}-${case.theme}-${case.display}-${case.preview}"
            val before = saveFrame("$prefix-before")
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { changed.value = true }
            var previousTime = 0L
            val frames = mutableListOf<Bitmap>()
            for (time in listOf(16L, 80L, 160L, 256L)) {
                compose.mainClock.advanceTimeBy(time - previousTime)
                previousTime = time
                frames += saveFrame("$prefix-$time").also {
                    assertEquals("$prefix width during fade", before.width, it.width)
                    assertEquals("$prefix height during fade", before.height, it.height)
                }
            }
            assertFalse("$prefix must visibly animate", before.sameAs(frames[1]))
            assertFalse("$prefix must retain intermediate colors", frames[1].sameAs(frames.last()))
            compose.mainClock.advanceTimeBy(128)
            assertTrue("$prefix must settle", frames.last().sameAs(captureFrame()))
        }
        compose.mainClock.autoAdvance = true
    }

    private fun captureFrame(): Bitmap =
        compose.onNodeWithTag("story-frame").captureToImage().asAndroidBitmap()

    private fun saveFrame(name: String): Bitmap {
        val image = captureFrame()
        check(image.width > 0 && image.height > 0)
        val label = InstrumentationRegistry.getArguments().getString("storyComparisonDir") ?: return image
        val directory = File(compose.activity.getExternalFilesDir(null), "story-comparison/$label")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return image
    }
}
