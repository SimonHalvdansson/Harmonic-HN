package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentsSettingsCapabilityTest {
    @Test fun portableHostsHideUnsupportedVolumeKeysWhileAndroidKeepsThem() = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("CommentsSettingsCapabilityTest")
        try {
            for (portable in listOf(true, false)) {
                val scene = ImageComposeScene(600, 4000) {
                    HarmonicTheme(HarmonicThemeCatalog.scheme("dynamic", false).colorScheme, false) {
                        CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                            if (portable) PortableSettingsDetail(
                                section = SettingsSection.Comments, app = bootstrap.app, scene = bootstrap.scene,
                                singlePane = true, onBack = {}, onNavigate = { _, _ -> },
                                appIcon = ColorPainter(Color.Gray), debugPlatformVersion = "test",
                                debugNotificationsMessage = "test", aiSettings = {}, dataSettings = {},
                                readerPreview = { _, _ -> },
                            ) else CommentsSettingsRoute(
                                repository = bootstrap.app.settings, showNavigation = true, onBack = {}, onSpacingRequested = {},
                                onThreadDepthRequested = {}, onQuoteHighlightingRequested = {}, onUserAvatarsRequested = {},
                            )
                        }
                    }
                }
                try {
                    repeat(10) { scene.render(it * 16_666_667L).close() }
                    fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
                    val text = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
                        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                    assertTrue("Navigation" in text, "The navigation settings must be rendered")
                    assertEquals(!portable, "Volume buttons for navigation" in text)
                } finally { scene.close() }
            }
        } finally { bootstrap.close() }
    }
}
