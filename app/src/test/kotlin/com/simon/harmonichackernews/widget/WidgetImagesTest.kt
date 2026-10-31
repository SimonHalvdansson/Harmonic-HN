package com.simon.harmonichackernews.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.ui.theme.cardBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetImagesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val previewSize = WidgetImageSize(120, 72)
    private val faviconSize = WidgetImageSize(12, 12)
    private val entry = WidgetEntry(StoryDestination(42), "preview", "preview", "favicon")
    private val budget = 24_000L

    @Test
    fun displayFlagsSkipUnusedAssetsAndShareThePreviewWithTint() {
        for (showPreview in listOf(false, true)) {
            for (tint in listOf(false, true)) {
                for (showFavicon in listOf(false, true)) {
                    val reads = mutableListOf<Pair<String, WidgetImageSize>>()
                    val images = prepareWidgetImages(
                        context, entry, previewSize, faviconSize, budget, showPreview, tint, showFavicon,
                        decode = { path, size, bytes ->
                            assertEquals(budget, bytes)
                            reads += path to size
                            bitmap(size)
                        },
                    )
                    val expected = buildList {
                        if (showPreview || tint) add("preview" to previewSize)
                        if (showFavicon) add("favicon" to faviconSize)
                    }
                    assertEquals("preview=$showPreview tint=$tint favicon=$showFavicon", expected, reads)
                    assertEquals(showPreview, images.image != null)
                    assertEquals(tint, images.palette != null)
                    assertEquals(showFavicon, images.favicon != null)
                }
            }
        }
    }

    @Test
    fun tintStillUsesItsSourceWhenTheFaviconAndPreviewAreHidden() {
        val reads = mutableListOf<Pair<String, WidgetImageSize>>()
        val images = prepareWidgetImages(
            context, entry.copy(tintPath = "favicon"), previewSize, faviconSize, budget,
            showPreview = false, tint = true, showFavicon = false,
            decode = { path, size, _ ->
                reads += path to size
                bitmap(size)
            },
        )
        assertEquals(listOf("favicon" to widgetTintImageSize), reads)
        assertNull(images.image)
        assertNotNull(images.palette)
        assertNull(images.favicon)
    }

    @Test
    fun tintOnlyPreviewUsesTheExistingOffModeCropAndDoesNotPublishAnImage() {
        val reads = mutableListOf<WidgetImageSize>()
        val images = prepareWidgetImages(
            context, entry, widgetTintImageSize, faviconSize, budget,
            showPreview = false, tint = true, showFavicon = false,
            decode = { _, size, _ ->
                reads += size
                bitmap(size)
            },
        )
        assertEquals(listOf(widgetTintImageSize), reads)
        assertNotNull(images.palette)
        assertNull(images.image)
    }

    @Test
    fun unavailableSharedPreviewIsNotDecodedAgainForTint() {
        var reads = 0
        val images = prepareWidgetImages(
            context, entry, previewSize, faviconSize, budget,
            showPreview = true, tint = true, showFavicon = false,
            decode = { _, _, _ -> reads++; null },
        )
        assertEquals(1, reads)
        assertNull(images.image)
        assertNull(images.palette)
    }

    @Test
    fun themesAndTintSettingsReusePreparedImagesAndPalette() {
        var reads = 0
        val images = prepareWidgetImages(
            context, entry, previewSize, faviconSize, budget,
            showPreview = true, tint = true, showFavicon = true,
            decode = { _, size, _ -> reads++; bitmap(size) },
        )
        val day = lightColorScheme()
        val night = darkColorScheme()
        val colors = WidgetColors(day, night)
        val initial = images.visual(colors, PaletteTintPreferences.DOMINANT)
        val changedTheme = images.visual(WidgetColors(night, day), PaletteTintPreferences.DOMINANT)
        val noStrength = PaletteTintPreferences.configKey(
            PaletteTintPreferences.DOMINANT, 0, PaletteTintPreferences.DEFAULT_COLORFULNESS, 0,
        )
        val changedSettings = images.visual(colors, noStrength)

        assertEquals(2, reads)
        assertNotEquals(initial.tint, initial.nightTint)
        assertEquals(initial.nightTint, changedTheme.tint)
        assertEquals(initial.tint, changedTheme.nightTint)
        assertEquals(day.cardBackground.toArgb(), changedSettings.tint)
        assertEquals(night.cardBackground.toArgb(), changedSettings.nightTint)
        assertNotEquals(initial.tint, changedSettings.tint)
        assertSame(initial.image, changedTheme.image)
        assertSame(initial.favicon, changedSettings.favicon)
    }

    private fun bitmap(size: WidgetImageSize): Bitmap =
        Bitmap.createBitmap(size.widthPx, size.heightPx, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
}
