package com.simon.harmonichackernews.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopWebToolbarLayoutTest {
    @Test
    fun narrowAndWideToolbarsKeepOneRowWhileSwitchingTabs() = SwingUtilities.invokeAndWait {
        for (width in listOf(320, 360, 440, 590, 800)) {
            val article = mutableStateOf(true)
            var height = 0
            val session = DesktopCommentsWebViewSession("https://example.com/article")
            val scene = ImageComposeScene(width, 200, Density(1f)) {
                val palette = HarmonicThemeCatalog.resolve("material_light", false)
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    DesktopWebViewToolbar(
                        showWebsite = article.value,
                        storyTitle = "A long story title that cannot fit into a narrow pane",
                        session = session,
                        onShowWebsite = {}, onShowComments = {}, onOpenExternal = {},
                        modifier = Modifier.onSizeChanged { height = it.height },
                    )
                }
            }
            try {
                var time = 0L
                for (showArticle in listOf(true, false, true)) {
                    article.value = showArticle
                    repeat(20) {
                        scene.render(time).close()
                        time += 16_000_000L
                        assertEquals(64, height, "Toolbar width=$width, article=$showArticle")
                    }
                }
            } finally {
                scene.close()
            }
        }
    }
}
