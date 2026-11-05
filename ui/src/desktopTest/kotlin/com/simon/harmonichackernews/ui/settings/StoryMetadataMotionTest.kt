package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.*
import javax.swing.SwingUtilities
import kotlin.test.*

class StoryMetadataMotionTest {
    @Test fun settingsKeepsTheOriginalMetadataSpanAndFaviconAnimations() = SwingUtilities.invokeAndWait {
        val app = DesktopHarmonicAppBootstrap.inMemory("StoryMetadataMotion")
        val model = SettingsStoryPreviewModel
        val base = StoryDisplaySettings.from(app.app.userSettings.story)
            .toStoryRowStyle(StoryRowStyleContext(53, 18, false))
            .copy(showPoints = true, showFavicon = true, compactPoints = false, includeTopLevelDomain = true)
        var style by mutableStateOf(base)
        val scene = ImageComposeScene(420, 400) {
            MaterialTheme { LookaheadScope {
                val visibility = rememberStorySettingsVisibility(model, style)
                val owner = remember(this) { StoryRowPlacementAnimation(this, visibility) }
                val rendered = visibility.rendered.style
                val typography = rememberContentTypography(rendered.preferredFont, rendered.textSize)
                Column {
                    // The pre-existing animateChanges=true path is the committed metadata
                    // animation: span scale/alpha plus AnimatedVisibility for the favicon.
                    StoryMeta(model, rendered, typography, 1f, 0, rendered.paletteTintConfigKey,
                        false, {}, true, Modifier.width(300.dp).testTag("reference"), wrapContent = true)
                    StoryRowMovingContent(owner, "metadata") {
                        StoryMeta(model, rendered, typography, 1f, 0, rendered.paletteTintConfigKey,
                            false, {}, false, Modifier.width(300.dp).testTag("settings"),
                            wrapContent = true, placementAnimation = owner)
                    }
                }
            } }
        }
        var time = 0L
        fun frame() { scene.render(time.also { time += 16_666_667L }).close() }
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        fun textNode(tag: String): SemanticsNode {
            val parent = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.single {
                it.config.getOrNull(SemanticsProperties.TestTag) == tag
            }
            return walk(parent).single { it.config.getOrNull(SemanticsProperties.Text) != null }
        }
        try {
            for (mode in StoryPreviewMode.entries) {
                val original = base.copy(previewImageMode = mode)
                style = original
                repeat(40) { frame() }
                val targets = listOf(original.copy(showPoints = false), original,
                    original.copy(showFavicon = false), original,
                    original.copy(compactPoints = true), original,
                    original.copy(includeTopLevelDomain = false), original)
                for (target in targets) {
                    style = target
                    repeat(32) { index ->
                        frame()
                        val reference = textNode("reference")
                        val settings = textNode("settings")
                        assertEquals(reference.config[SemanticsProperties.Text], settings.config[SemanticsProperties.Text],
                            "$mode frame $index: preserve the original fragment opacity and width")
                        assertEquals(reference.boundsInRoot.left, settings.boundsInRoot.left, 1f,
                            "$mode frame $index: favicon must not acquire an extra placement tween")
                        assertEquals(reference.boundsInRoot.width, settings.boundsInRoot.width, 1f)
                        // Domain and age must stay readable while the points/suffix changes.
                        val text = settings.config[SemanticsProperties.Text].single()
                        val domainStart = text.text.indexOf(model.domainWithoutTopLevel)
                        assertTrue(text.spanStyles.none { it.start <= domainStart && it.end > domainStart && it.item.color.alpha < 1f })
                    }
                }
            }
        } finally { scene.close(); app.close() }
    }
}
