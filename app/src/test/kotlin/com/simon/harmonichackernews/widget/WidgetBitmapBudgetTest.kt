package com.simon.harmonichackernews.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
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
            val image = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                .withinWidgetBitmapBudget(budget)!!
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
            val image = decodeWidgetBitmap(file.absolutePath, 4_096)!!
            assertTrue(image.width <= 160 && image.height <= 160)
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
        assertEquals(Color.TRANSPARENT, decodeWidgetBitmap(file.absolutePath, 24_000)!!.getPixel(0, 0))
    }

    @Test
    fun missingOrInvalidCacheImagesAreOptional() {
        assertNull(decodeWidgetBitmap(temporaryFolder.newFile().absolutePath, 24_000))
    }
}
