package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.simon.harmonichackernews.palette.HarmonicPaletteExtractor
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.hacker_news_favicon
import com.simon.harmonichackernews.ui.content.toPainterPaletteSample
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

class HeaderPreviewPaletteTest {
    @Test fun bundledHnFaviconMatchesBakedSwatches() = SwingUtilities.invokeAndWait {
        val scene = ImageComposeScene(96, 96, Density(1f)) {
            Image(painterResource(Res.drawable.hacker_news_favicon), null, Modifier.fillMaxSize())
        }
        try {
            scene.render(0L).use { image ->
                val pixels = IntArray(96 * 96)
                image.toComposeImageBitmap().readPixels(pixels)
                val palette = HarmonicPaletteExtractor().extract(pixels).toPreviewTintPalette()
                assertEquals(HeaderPreviewSample.TEXT_POST.tintPalette, palette,
                    "Re-bake the swatches after changing the HN favicon")
            }
        } finally { scene.close() }
    }

    @Test fun bundledImagePalettesMatchBakedSwatches() = runBlocking {
        for ((name, preview) in listOf(
            "web_preview" to HeaderPreviewSample.LINK_WITH_IMAGE,
            "github_preview" to HeaderPreviewSample.LINK_PREVIEW,
        )) {
            val image = Res.readBytes("drawable/$name.webp").decodeToImageBitmap()
            val sample = BitmapPainter(image).toPainterPaletteSample(Density(1f), LayoutDirection.Ltr)
            val pixels = IntArray(sample.width * sample.height)
            sample.readPixels(pixels)
            val palette = HarmonicPaletteExtractor().extract(pixels).toPreviewTintPalette()
            assertEquals(preview.tintPalette, palette, "Re-bake the swatches after changing $name")
        }
    }
}
