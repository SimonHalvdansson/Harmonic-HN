package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.StoryTypeMenuPolicy
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.StoryListSelector
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class StoriesHeaderPreviewTest {
    @Test
    fun selectorAndCompactnessAnimateInsideAFixedCenteredPreview() = SwingUtilities.invokeAndWait {
        for (width in listOf(320, 640)) for (dark in listOf(false, true)) {
            val bootstrap = DesktopHarmonicAppBootstrap.inMemory("StoriesHeaderPreviewTest")
            val settings = mutableStateOf(StoryDisplaySettings.from(bootstrap.app.userSettings.story))
            val scene = ImageComposeScene(width, 840, Density(1f)) {
                val palette = HarmonicThemeCatalog.scheme("dynamic", dark)
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    CompositionLocalProvider(LocalHarmonicUiDependencies provides
                        HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                        StoriesHeaderSettingsScreen(
                            settings.value, StoryTypeMenuPolicy.baseFrontpages,
                            onSelectorChanged = { settings.value = settings.value.copy(listSelector = it) },
                            onCompactHeaderChanged = { settings.value = settings.value.copy(compactHeader = it) },
                            onBack = {},
                        )
                    }
                }
            }
            var time = 0L
            fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
            fun allNodes() = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
            fun taggedBounds(tag: String) = allNodes().single {
                it.config.getOrNull(SemanticsProperties.TestTag) == tag
            }.boundsInRoot
            var frameBounds: Rect? = null
            var previousHeight: Float? = null
            var storyId: Int? = null
            fun preview(selector: StoryListSelector, compact: Boolean): Rect {
                settings.value = settings.value.copy(listSelector = selector, compactHeader = compact)
                val animatedHeights = mutableListOf<Int>()
                val storyPositions = mutableListOf<Int>()
                repeat(45) {
                    scene.render(time.also { time += 16_666_667L }).close()
                    val bounds = taggedBounds("stories-header-preview")
                    frameBounds?.let { expected ->
                        assertEquals(expected.height, bounds.height, 1f, "The preview bounds must stay fixed throughout the animation")
                    }
                    animatedHeights += taggedBounds("stories-header-preview-content").height.toInt()
                    val liveContent = allNodes().single {
                        it.config.getOrNull(SemanticsProperties.TestTag) == "stories-header-preview-content"
                    }
                    val liveNodes = nodes(liveContent)
                    val story = liveNodes.single {
                        it.config.getOrNull(SemanticsProperties.TestTag) == "stories-header-preview-story"
                    }
                    // Dropdown semantics merge the title when clickable; chips expose the text itself.
                    // Both modes must keep exactly one visible heading, including during transitions.
                    liveNodes.single {
                        it.config.getOrNull(SemanticsProperties.Heading) != null &&
                            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Top Stories" } == true
                    }
                    storyId?.let { assertEquals(it, story.id, "Keep the same post throughout the layout change") }
                    storyId = story.id
                    storyPositions += story.boundsInRoot.top.toInt()
                }
                val bounds = taggedBounds("stories-header-preview")
                val content = taggedBounds("stories-header-preview-content")
                assertEquals(0f, bounds.left, 1f)
                assertEquals(width.toFloat(), bounds.right, 1f)
                if (selector == StoryListSelector.CHIPS) {
                    val chips = nodes(allNodes().single {
                        it.config.getOrNull(SemanticsProperties.TestTag) == "stories-header-preview-content"
                    }).single {
                        it.config.getOrNull(SemanticsProperties.TestTag) == "stories-header-preview-chips"
                    }.boundsInRoot
                    assertEquals(bounds.right, chips.right, 1f, "Chips must reach the page edge")
                }
                assertEquals(bounds.center.y, content.center.y, 1f, "Center the selected layout inside the largest preview")
                val compactTitle = allNodes().first {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Compact header" } == true
                }.boundsInRoot
                assertTrue(compactTitle.top >= bounds.bottom && compactTitle.bottom <= 840,
                    "Controls must remain visible below the preview at width $width")
                previousHeight?.let { before ->
                    assertTrue(animatedHeights.filter {
                        it > minOf(before, content.height) + 1 && it < maxOf(before, content.height) - 1
                    }.distinct().size >= 3, "Layout changes must pass through intermediate sizes")
                    assertTrue(storyPositions.distinct().size >= 4, "The retained post must move smoothly with the header")
                }
                frameBounds = bounds
                previousHeight = content.height
                return content
            }
            try {
                val dropdown = preview(StoryListSelector.DROPDOWN, false)
                val compactDropdown = preview(StoryListSelector.DROPDOWN, true)
                val compactChips = preview(StoryListSelector.CHIPS, true)
                val chips = preview(StoryListSelector.CHIPS, false)
                assertTrue(dropdown.height > compactDropdown.height + 15)
                assertTrue(compactChips.height > compactDropdown.height + 30)
                assertTrue(chips.height > compactChips.height + 15)
                val returnedDropdown = preview(StoryListSelector.DROPDOWN, false)
                assertEquals(dropdown.height, returnedDropdown.height, 1f)
            } finally {
                scene.close()
                bootstrap.close()
            }
        }
    }
}
