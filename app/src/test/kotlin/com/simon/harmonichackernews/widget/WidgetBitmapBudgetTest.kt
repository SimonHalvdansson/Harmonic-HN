package com.simon.harmonichackernews.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.simon.harmonichackernews.settings.StoryPreviewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetBitmapBudgetTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun twentyFourStoriesFitSmallDisplayBitmapBudget() {
        val count = 24
        val budget = widgetBitmapBudget(480, 800, count * 2)
        val bytes = (1..count).sumOf {
            val image = Bitmap.createBitmap(360, 216, Bitmap.Config.ARGB_8888)
                .forWidgetImage(widgetPreviewImageSize(StoryPreviewMode.MEDIUM, 3f), budget)!!
            val favicon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                .roundedWidgetFavicon(RuntimeEnvironment.getApplication())
                .withinWidgetBitmapBudget(budget)!!
            image.allocationByteCount.toLong() + favicon.allocationByteCount
        }
        assertTrue("Bitmaps use $bytes bytes", bytes <= 3L * 480 * 800)
        assertTrue(bytes < 6L * 480 * 800)
    }

    @Test
    fun smallImagesAreNotUpscaled() {
        val image = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        assertSame(image, image.withinWidgetBitmapBudget(24_000))
        val preview = image.forWidgetImage(widgetPreviewImageSize(StoryPreviewMode.MEDIUM, 3f), 24_000)!!
        assertTrue(preview.width <= 16 && preview.height <= 16)
    }

    @Test
    fun previewSizesMatchLayoutAtDifferentDensities() {
        assertEquals(WidgetImageSize(120, 72), widgetPreviewImageSize(StoryPreviewMode.MEDIUM, 1f))
        assertEquals(WidgetImageSize(180, 108), widgetPreviewImageSize(StoryPreviewMode.MEDIUM, 1.5f))
        assertEquals(WidgetImageSize(360, 216), widgetPreviewImageSize(StoryPreviewMode.MEDIUM, 3f))
        assertEquals(WidgetImageSize(72, 52), widgetPreviewImageSize(StoryPreviewMode.SMALL, 1f))
        assertEquals(WidgetImageSize(216, 156), widgetPreviewImageSize(StoryPreviewMode.SMALL, 3f))
        assertEquals(WidgetImageSize(36, 36), widgetFaviconImageSize(3f))
        assertEquals(widgetTintImageSize, widgetPreviewImageSize(StoryPreviewMode.OFF, 3f))
    }

    @Test
    fun highDensityCacheImagesRetainDisplayResolution() {
        val file = temporaryFolder.newFile()
        file.outputStream().use {
            Bitmap.createBitmap(720, 432, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        for (mode in listOf(StoryPreviewMode.SMALL, StoryPreviewMode.MEDIUM)) {
            val target = widgetPreviewImageSize(mode, 3f)
            val image = decodeWidgetBitmap(file.absolutePath, target, 2_000_000)!!
            assertEquals(target.widthPx, image.width)
            assertEquals(target.heightPx, image.height)
        }
    }

    @Test
    fun previewsCropToDisplayAspectRatioWithoutStretching() {
        val source = Bitmap.createBitmap(600, 216, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            for (x in 120 until 480) for (y in 0 until height) setPixel(x, y, Color.BLUE)
        }
        val image = source.forWidgetImage(widgetPreviewImageSize(StoryPreviewMode.MEDIUM, 3f), 2_000_000)!!
        assertEquals(360, image.width)
        assertEquals(216, image.height)
        assertEquals(Color.BLUE, image.getPixel(0, 0))
        assertEquals(Color.BLUE, image.getPixel(359, 215))
    }

    @Test
    fun highDensityCacheImagesStillRespectSmallBudget() {
        val file = temporaryFolder.newFile()
        file.outputStream().use {
            Bitmap.createBitmap(720, 432, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val target = widgetPreviewImageSize(StoryPreviewMode.MEDIUM, 3f)
        val image = decodeWidgetBitmap(file.absolutePath, target, 4_096)!!
        assertTrue(image.allocationByteCount <= 4_096)
        assertTrue(image.width < target.widthPx && image.height < target.heightPx)
        assertNull(decodeWidgetBitmap(file.absolutePath, target, 3))
    }

    @Test
    fun insufficientBudgetOmitsImages() {
        assertNull(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).withinWidgetBitmapBudget(3))
    }

    @Test
    @Config(qualifiers = "xxxhdpi")
    fun roundedFaviconsStillRespectBudgetAtHighDensity() {
        val favicon = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
            .roundedWidgetFavicon(RuntimeEnvironment.getApplication())
            .withinWidgetBitmapBudget(256)!!
        assertTrue(favicon.allocationByteCount <= 256)
    }

    @Test
    fun oldWideAndTallCacheImagesAreSampledBeforeRendering() {
        for ((width, height) in listOf(4096 to 16, 16 to 4096)) {
            val file = temporaryFolder.newFile()
            file.outputStream().use {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.RED)
                }.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val target = widgetPreviewImageSize(StoryPreviewMode.MEDIUM, 3f)
            val image = decodeWidgetBitmap(file.absolutePath, target, 4_096)!!
            assertTrue(image.width <= target.widthPx && image.height <= target.heightPx)
            assertTrue(image.allocationByteCount <= 4_096)
            assertEquals(Color.RED, image.getPixel(0, 0))
        }
    }

    @Test
    fun transparentCacheImagesKeepTheirAlpha() {
        val file = temporaryFolder.newFile()
        file.outputStream().use {
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.TRANSPARENT)
            }.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertEquals(Color.TRANSPARENT, decodeWidgetBitmap(file.absolutePath, widgetTintImageSize, 24_000)!!.getPixel(0, 0))
    }

    @Test
    fun missingOrInvalidCacheImagesAreOptional() {
        assertNull(decodeWidgetBitmap(temporaryFolder.newFile().absolutePath, widgetTintImageSize, 24_000))
    }
}
