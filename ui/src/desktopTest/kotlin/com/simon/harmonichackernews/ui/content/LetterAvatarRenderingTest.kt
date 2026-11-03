package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.settings.UserAvatarColors
import com.simon.harmonichackernews.settings.UserAvatarOptions
import com.simon.harmonichackernews.settings.UserAvatarShape
import com.simon.harmonichackernews.settings.UserAvatarStyle
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LetterAvatarRenderingTest {
    @Test
    fun coloredInitialFitsSmallAvatarsWithoutGrowingWithTheTextSizeSetting() = SwingUtilities.invokeAndWait {
        for (size in listOf(24, 64)) for (colors in UserAvatarColors.entries) {
            var regularInk: Int? = null
            for (fontScale in listOf(1f, 2f)) {
                val scene = ImageComposeScene(size, size, Density(1f, fontScale)) {
                    UserAvatar(
                        author = "simon", modifier = Modifier.size(size.dp),
                        options = UserAvatarOptions(
                            styles = setOf(UserAvatarStyle.LETTERS),
                            shape = UserAvatarShape.SQUARE, colors = colors,
                        ),
                    )
                }
                try {
                    repeat(5) { scene.render(it * 16_000_000L).close() }
                    scene.render(100_000_000L).use { image ->
                        val pixels = image.toComposeImageBitmap().toPixelMap()
                        val ink = buildList {
                            for (y in 0 until size) for (x in 0 until size) {
                                if (pixels[x, y].luminance() < .45f) add(x to y)
                            }
                        }
                        assertTrue(ink.size > size * size / 15, "An initial must be visible at $size dp")
                        assertTrue(ink.all { (x, y) -> x in 2 until size - 2 && y in 2 until size - 2 },
                            "The initial must remain inside its frame at font scale $fontScale")
                        regularInk?.let { assertEquals(it, ink.size, "Font scale must not distort the avatar") }
                        regularInk = ink.size
                        val background = pixels[1, 1]
                        if (colors == UserAvatarColors.MONOCHROME) {
                            assertEquals(background.red, background.green, .001f)
                            assertEquals(background.green, background.blue, .001f)
                        } else {
                            assertTrue(maxOf(background.red, background.green, background.blue) -
                                minOf(background.red, background.green, background.blue) > .01f,
                                "Letter avatars must retain their username color")
                        }
                    }
                } finally {
                    scene.close()
                }
            }
        }
    }
}
