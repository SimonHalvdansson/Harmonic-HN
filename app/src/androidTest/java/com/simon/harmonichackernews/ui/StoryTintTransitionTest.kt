package com.simon.harmonichackernews.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemStyle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual rendered row while image discovery and palette delivery arrive separately. */
@RunWith(AndroidJUnit4::class)
@OptIn(coil3.annotation.DelicateCoilApi::class)
class StoryTintTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun faviconTintSurvivesPendingPreviewThenChangesDirectlyToImageTint() {
        val context = compose.activity
        val originalLoader = SingletonImageLoader.get(context)
        val imageReady = CompletableDeferred<Unit>()
        val extractedTint = CompletableDeferred<Int>()
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
        }
        val pendingLoader = ImageLoader.Builder(context)
            .components {
                add(Interceptor { chain ->
                    imageReady.await()
                    SuccessResult(bitmap.asImage(), chain.request, DataSource.NETWORK)
                })
            }
            .build()
        SingletonImageLoader.setUnsafe(pendingLoader)
        try {
            val faviconTint = 0xffbbddbb.toInt()
            val baseColor = 0xffeeeeee.toInt()
            val model = mutableStateOf(SettingsStoryPreviewModel.copy(
                previewImageFallback = null,
                faviconTintArgb = faviconTint,
                tintFallbackArgb = baseColor,
            ))
            val style = mutableStateOf(StoryItemStyle(
                previewImageMode = StoryPreviewMode.SMALL,
                borderlessLargeImage = false,
                compact = false,
                showSummary = false,
                showFavicon = true,
                showPoints = true,
                compactPoints = false,
                includeTopLevelDomain = true,
                showCommentCount = true,
                showIndex = true,
                commentsOnLeft = false,
                tintCard = true,
                displayStyle = DisplayStyle.RAISED,
                useHotnessIcon = false,
                preferredFont = "default",
                textSize = 16f,
            ))
            compose.setContent {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                    StoryItem(
                        model.value, style.value, Modifier.testTag("story"), listItem = true,
                        onPreviewTintExtracted = { extractedTint.complete(it) },
                    )
                }
            }
            assertCardColor("favicon only", faviconTint)
            compose.runOnIdle {
                model.value = model.value.copy(previewImageUrl = "https://tint.test/preview.png")
            }
            assertCardColor("preview discovered but palette pending", faviconTint)
            imageReady.complete(Unit)
            compose.waitUntil(timeoutMillis = 5_000) { extractedTint.isCompleted }
            val imageTint = kotlinx.coroutines.runBlocking { extractedTint.await() }
            org.junit.Assert.assertNotEquals(baseColor, imageTint)
            org.junit.Assert.assertNotEquals(faviconTint, imageTint)
            assertCardColor("decoded preview palette ready", imageTint)
            compose.runOnIdle { model.value = model.value.copy(previewImageLoadFailed = true) }
            assertCardColor("failed preview", faviconTint)
            compose.runOnIdle {
                model.value = model.value.copy(previewImageLoadFailed = false)
                style.value = style.value.copy(previewImageMode = StoryPreviewMode.OFF)
            }
            assertCardColor("previews disabled", faviconTint)
        } finally {
            SingletonImageLoader.setUnsafe(originalLoader)
            pendingLoader.shutdown()
        }
    }

    private fun assertCardColor(stage: String, expected: Int) {
        val pixels = compose.onNodeWithTag("story").captureToImage().toPixelMap()
        // The background occupies the majority of the row; ignore text, icons and rounded edges.
        val counts = mutableMapOf<Int, Int>()
        for (y in 0 until pixels.height step 3) {
            for (x in 0 until pixels.width step 3) {
                val color = pixels[x, y].toArgb()
                counts[color] = (counts[color] ?: 0) + 1
            }
        }
        assertEquals(stage, expected, counts.maxBy { it.value }.key)
    }
}
