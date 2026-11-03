package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.settings.UserAvatarOptions
import com.simon.harmonichackernews.settings.UserTagColor
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class UserTagRenderingTest {
    @Test
    fun savedTagAccentsReachCommentTextAndStayReadableInBothThemes() = SwingUtilities.invokeAndWait {
        for (dark in listOf(false, true)) for (choice in UserTagColor.entries) {
            val scheme = if (dark) darkColorScheme() else lightColorScheme()
            var accent = Color.Unspecified
            val scene = ImageComposeScene(520, 48, Density(1f)) {
                HarmonicTheme(scheme, dark) {
                    accent = userTagColor(choice)
                    Box(Modifier.fillMaxSize().background(scheme.surface)) {
                        CommentMeta(
                            author = "alice", avatarsEnabled = false,
                            avatarOptions = UserAvatarOptions(), age = "1h", byOp = false,
                            byUser = false, userTag = "maintainer", hiddenPreview = null,
                            subtreeReplyCount = null, showHiddenReplyCount = false,
                            emphasized = false, fontFamily = FontFamily.SansSerif,
                            animateChanges = false, tagColor = choice,
                        )
                    }
                }
            }
            try {
                repeat(5) { scene.render(it * 16_666_667L).close() }
                scene.render(100_000_000L).use { image ->
                    val pixels = image.toComposeImageBitmap().toPixelMap()
                    var accentPixels = 0
                    for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                        val color = pixels[x, y]
                        if (abs(color.red - accent.red) < .04f &&
                            abs(color.green - accent.green) < .04f &&
                            abs(color.blue - accent.blue) < .04f
                        ) accentPixels++
                    }
                    assertTrue(accentPixels > 12, "$choice must color the actual tag text (dark=$dark)")
                }
                val lighter = maxOf(accent.luminance(), scheme.surface.luminance())
                val darker = minOf(accent.luminance(), scheme.surface.luminance())
                assertTrue((lighter + .05f) / (darker + .05f) >= 4.5f,
                    "$choice must remain readable against the comment surface (dark=$dark)")
            } finally {
                scene.close()
            }
        }
    }
}
