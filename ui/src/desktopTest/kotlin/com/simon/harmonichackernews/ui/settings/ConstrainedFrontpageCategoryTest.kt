package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.StoriesHeaderPill
import com.simon.harmonichackernews.ui.stories.FrontpageHeaderControls
import com.simon.harmonichackernews.ui.stories.StoriesHeaderMenus
import com.simon.harmonichackernews.ui.stories.StoryTypeChips
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

class ConstrainedFrontpageCategoryTest {
    @Test
    fun frontpageNamesAndSiblingActionsFitNarrowWindowsAtDoubleTextSize() = SwingUtilities.invokeAndWait {
        for (width in listOf(208, 320, 600)) for (scale in listOf(1f, 2f)) {
            val list = LazyListState()
            var selected = 0
            var removed = 0
            val harness = Harness(width, 800, scale) {
                ManageFrontpagesSettingsScreen(
                    frontpages = listOf(StoryType.BEST_COMMENTS), defaultLabel = StoryType.BEST_COMMENTS.label,
                    available = emptyList(), onBack = {}, onDefaultSelected = { selected++ },
                    onOrderChanged = {}, onReset = {}, onRemove = { removed++ }, onAdd = {}, listState = list,
                )
            }
            try {
                harness.frames()
                assertTrue(harness.layouts(StoryType.BEST_COMMENTS.label).isNotEmpty())
                harness.layouts(StoryType.BEST_COMMENTS.label).forEach {
                    assertTextContentFits(it, "A default badge must not crowd out the name at $width / $scale×")
                }
                val choice = harness.nodes().single {
                    it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton
                }
                harness.touchTarget(choice)
                assertTrue(choice.config[SemanticsActions.OnClick].action!!.invoke())
                assertEquals(1, selected)
                for (description in listOf("Remove Best Comments", "About Best Comments", "Drag to reorder Best Comments")) {
                    harness.touchTarget(harness.description(description))
                }
                assertTrue(harness.description("Remove Best Comments").config[SemanticsActions.OnClick].action!!.invoke())
                assertEquals(1, removed)
                assertTrue(harness.description("Drag to reorder Best Comments")
                    .config[SemanticsActions.CustomActions].isNotEmpty())
                harness.evidence("frontpages-$width-${scale}x")
            } finally { harness.close() }

            val scroll = ScrollState(0)
            var dismissed = 0
            val title = "An unusually long frontpage name that must remain readable"
            val info = Harness(width, 240, scale) {
                FrontpageInfoContent(StoryType.BEST_COMMENTS, title,
                    "This frontpage shows useful comments and discussions. ".repeat(5), { dismissed++ }, scroll)
            }
            try {
                info.frames()
                assertTrue(info.layouts(title).isNotEmpty())
                info.layouts(title).forEach { assertTextContentFits(it) }
                scroll.dispatchRawDelta(100_000f)
                info.frames()
                val ok = info.action("OK")
                info.touchTarget(ok)
                assertTrue(ok.boundsInRoot.top >= 0f && ok.boundsInRoot.bottom <= 240f)
                assertTrue(ok.config[SemanticsActions.OnClick].action!!.invoke())
                assertEquals(1, dismissed, "The dismiss action must remain reachable in a short window")
            } finally { info.close() }
        }
    }

    @Test
    fun longStoryHeaderNamesKeepAccessibleLabelsAndFortyEightDpControls() = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("ConstrainedHeader")
        try {
            val settings = StoryDisplaySettings.from(bootstrap.app.userSettings.story).copy(headerPill = StoriesHeaderPill.PRIMARY)
            val title = "A very long frontpage name with many words and a complete accessible label"
            for (width in listOf(208, 320, 600)) for (scale in listOf(1f, 2f)) {
                var moreClicks = 0
                val harness = Harness(width, 180, scale) {
                    Column {
                        FrontpageHeaderControls(settings, title, listOf(title), 0, StoriesHeaderMenus(), {},
                            modifier = Modifier.padding(horizontal = 16.dp),
                            moreMenu = { _, _ ->
                                IconButton(onClick = { moreClicks++ }, modifier = Modifier.semantics { contentDescription = "More options" }) {
                                    Text("⋮")
                                }
                            })
                        StoryTypeChips(listOf(StoryType.BEST_COMMENTS.label), 0, FontFamily.SansSerif,
                            PaddingValues(0.dp), {})
                    }
                }
                try {
                    harness.frames(35)
                    val selectedTitle = harness.nodes().first {
                        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == title } == true &&
                            it.config.getOrNull(SemanticsActions.OnClick) != null
                    }
                    harness.touchTarget(selectedTitle)
                    harness.touchTarget(harness.description("More options"))
                    assertTrue(harness.description("More options").config[SemanticsActions.OnClick].action!!.invoke())
                    assertEquals(1, moreClicks)
                    val titleLayouts = harness.layouts(title)
                    assertTrue(titleLayouts.isNotEmpty())
                    titleLayouts.forEach {
                        // Single-line ellipsis also sets didOverflowHeight via didExceedMaxLines.
                        // The full name remains in semantics; check the actual painted height.
                        assertTrue(it.multiParagraph.height <= it.size.height + .5f,
                            "Header text must fit vertically at $width / $scale×")
                    }
                    val chip = harness.action(StoryType.BEST_COMMENTS.label)
                    harness.touchTarget(chip)
                    val chipLayouts = harness.layouts(StoryType.BEST_COMMENTS.label)
                    assertTrue(chipLayouts.isNotEmpty())
                    chipLayouts.forEach { assertTextContentFits(it, "Story chip at $width / $scale×") }
                    harness.evidence("long-header-$width-${scale}x")
                } finally { harness.close() }
            }
        } finally { bootstrap.close() }
    }

    @Test
    fun longCategoryNamesWrapAndExamplesCanBeSelectedWithoutTappingCanvasDots() = SwingUtilities.invokeAndWait {
        val categories = listOf(
            "long" to "Artificial intelligence, machine learning, and accessible on-device software",
            "science" to "Scientific discoveries and interdisciplinary engineering research",
            "space" to "Space",
        )
        for (width in listOf(208, 320, 600)) for (scale in listOf(1f, 2f)) {
            val selected = mutableStateOf<String?>(null)
            val harness = Harness(width, 1_600, scale) {
                CategoryExplorationLegend(categories, listOf(Color.Red, Color.Blue, Color.Green), selected.value) {
                    selected.value = it
                }
            }
            try {
                harness.frames()
                categories.forEach { (id, name) ->
                    val choice = harness.action(name)
                    harness.touchTarget(choice)
                    val layouts = harness.layouts(name)
                    assertTrue(layouts.isNotEmpty())
                    layouts.forEach { assertTextContentFits(it, "Category legend at $width / $scale×") }
                    assertTrue(choice.config[SemanticsActions.OnClick].action!!.invoke())
                    harness.frames()
                    assertEquals(id, selected.value)
                }
                harness.evidence("category-legend-$width-${scale}x")
            } finally { harness.close() }
        }
        val points = ArticleCategories.defaults.mapIndexed { index, category ->
            CategoryMapPoint(index, "An example headline about ${category.name}", category.id,
                ArticleCategories.defaults.take(3).mapIndexed { rank, match -> CategoryMapMatch(match.id, .8f - rank * .1f) })
        }
        val positions = points.indices.map { listOf((it % 4) / 3f, (it / 4) / ((points.size - 1) / 4).toFloat()) }
        val data = CategoryExplorationData(4, "test", "test", "title_only", points,
            listOf("umap", "tsne", "pca").map { CategoryProjection(it, positions) })
        val example = mutableStateOf<Int?>(null)
        val harness = Harness(280, 1_600, 2f) {
            CategoryExplorationPanel(data, false, "umap", categoryMapPalette(Color.White), null, example.value,
                {}, { example.value = it }, {})
        }
        try {
            harness.frames()
            for (label in listOf("Next example", "Previous example")) {
                val action = harness.action(label)
                harness.touchTarget(action)
                assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke())
                harness.frames(30)
            }
            assertEquals(points.last().id, example.value)
        } finally { harness.close() }
    }

    private class Harness(width: Int, private val height: Int, scale: Float, content: @Composable () -> Unit) {
        private val width = width
        private var time = 0L
        private val scene = ImageComposeScene(width, height, Density(1f, scale)) {
            val palette = HarmonicThemeCatalog.scheme("dynamic", false)
            HarmonicTheme(palette.colorScheme, palette.dark, content)
        }
        fun frames(count: Int = 8) = repeat(count) { scene.render(time.also { time += 16_666_667L }).close() }
        fun nodes(merged: Boolean = true): List<SemanticsNode> {
            fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
            return scene.semanticsOwners.flatMap { all(if (merged) it.rootSemanticsNode else it.unmergedRootSemanticsNode) }
        }
        fun description(value: String) = nodes().first {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(value) == true
        }
        fun action(label: String) = nodes().first {
            it.config.getOrNull(SemanticsActions.OnClick) != null &&
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
        }
        fun layouts(label: String) = nodes(false).flatMap { node ->
            mutableListOf<TextLayoutResult>().also {
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(it)
            }.filter { it.layoutInput.text.text == label }
        }
        fun touchTarget(node: SemanticsNode) {
            val bounds = node.touchBoundsInRoot
            assertTrue(bounds.width >= 47.9f && bounds.height >= 47.9f, "Target smaller than 48dp: $bounds")
            assertTrue(bounds.left >= -.5f && bounds.right <= width + .5f, "Target exceeds viewport: $bounds")
        }
        fun evidence(name: String) {
            val root = System.getenv("HARMONIC_ACCESSIBILITY_EVIDENCE_DIR")?.let(::File) ?: return
            root.mkdirs()
            scene.render(time.also { time += 16_666_667L }).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { File(root, "$name.png").writeBytes(it.bytes) }
            }
        }
        fun close() = scene.close()
    }
}
