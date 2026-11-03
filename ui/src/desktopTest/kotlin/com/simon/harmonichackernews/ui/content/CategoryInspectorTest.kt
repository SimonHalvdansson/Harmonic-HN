package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.categorization.ArticleCategoryInputSource
import com.simon.harmonichackernews.categorization.ArticleCategoryResult
import com.simon.harmonichackernews.ui.settings.sampleCategoryInspection
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.io.File
import javax.swing.SwingUtilities
import org.jetbrains.skia.EncodedImageFormat
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CategoryInspectorTest {
    @Test
    fun invalidatedScoresDisappearWhileCutoffNamesAndRemovedStateStayCurrent() = SwingUtilities.invokeAndWait {
        val sample = sampleCategoryInspection()
        val result = mutableStateOf<ArticleCategoryResult?>(sample.result)
        val preferences = mutableStateOf(sample.preferences)
        val available = mutableStateOf(true)
        val scene = ImageComposeScene(360, 680, Density(1f)) {
            MaterialTheme {
                CategoryInspectorContent("ai", preferences.value.name(preferences.value.categories.first { it.id == "ai" }),
                    sample.title, result.value, preferences.value, sample = false, canEdit = available.value,
                    onEditExamples = {}, onDismiss = {})
            }
        }
        var time = 0L
        fun frames() = repeat(35) { scene.render(time.also { time += 16_666_667L }).close() }
        fun descriptions() = nodes(scene).flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        fun text() = nodes(scene).flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
        try {
            frames()
            assertTrue("Similarity, 42 out of 100" in descriptions())
            result.value = null
            preferences.value = preferences.value.copy(cutoff = .31f, names = mapOf("ai" to "Language models"))
            frames()
            assertTrue("Similarity, not available" in descriptions())
            assertTrue("Your cutoff, 31 out of 100" in descriptions())
            assertTrue(descriptions().none { "similarity 34" in it })
            assertTrue("Language models" in text())
            assertTrue("This match needs to be recalculated." in text())
            available.value = false
            frames()
            assertTrue("This category was removed." in text())
            val edit = nodes(scene).single { it.config.getOrNull(SemanticsProperties.TestTag) == "category-inspector-edit-examples" }
            assertNotNull(edit.config.getOrNull(SemanticsProperties.Disabled))
        } finally { scene.close() }
    }

    @Test
    fun scoresProvenanceAndActionsRemainReadableInNarrowLargeFontLayouts() = SwingUtilities.invokeAndWait {
        val evidence = System.getenv("HARMONIC_CATEGORY_INSPECTOR_EVIDENCE_DIR")?.let { File(it).apply { mkdirs() } }
        for (width in listOf(280, 380)) for (height in listOf(240, 520)) for (fontScale in listOf(1f, 2f))
            for (dark in listOf(false, true)) for (source in ArticleCategoryInputSource.entries) {
                val sample = sampleCategoryInspection(source)
                val preferences = sample.preferences.copy(names = mapOf("ai" to "Artificial intelligence and ML research"))
                var edited = 0
                var dismissed = 0
                val scene = ImageComposeScene(width, height, Density(1f, fontScale)) {
                    val palette = HarmonicThemeCatalog.scheme("dynamic", dark)
                    HarmonicTheme(palette.colorScheme, palette.dark) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            CategoryInspectorContent("ai", preferences.name(preferences.categories.first { it.id == "ai" }),
                                sample.title, sample.result, preferences, sample = false, canEdit = true,
                                onEditExamples = { edited++ }, onDismiss = { dismissed++ })
                        }
                    }
                }
                var time = 0L
                fun frames(count: Int = 35) = repeat(count) { scene.render(time.also { time += 16_666_667L }).close() }
                try {
                    frames()
                    val nodes = nodes(scene)
                    fun described(value: String) = nodes.single {
                        it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(value) == true
                    }
                    described("Similarity, 42 out of 100")
                    described("Your cutoff, 24 out of 100")
                    described("Hardware, similarity 34 out of 100")
                    described("Programming, similarity 30 out of 100")
                    assertTrue(nodes.any {
                        it.config.getOrNull(SemanticsProperties.Text)?.any { text ->
                            text.text == if (source == ArticleCategoryInputSource.TITLE_ONLY)
                                "Based on the title only" else "Based on the title and preview text"
                        } == true
                    })
                    val close = described("Close category inspector")
                    val closeTarget = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "category-inspector-close" }
                    assertTrue(closeTarget.touchBoundsInRoot.width >= 56 && closeTarget.touchBoundsInRoot.height >= 56,
                        "The close action must keep its enlarged 56dp touch target")
                    assertTrue(close.boundsInRoot.top >= 0 && close.boundsInRoot.bottom <= height,
                        "Close must remain visible at ${width}dp and ${fontScale}x font")
                    val edit = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "category-inspector-edit-examples" }
                    assertTrue(edit.boundsInRoot.top >= close.boundsInRoot.bottom && edit.boundsInRoot.bottom <= height,
                        "The editor action must stay visible while the middle content scrolls")
                    val scroll = nodes.single { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                    val viewport = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "category-inspector-scroll-content" }
                    assertTrue(viewport.boundsInRoot.height >= 48,
                        "The inspector body needs a usable scroll viewport, including at 2x in short windows")
                    val textTargets = nodes.filter {
                        it.config.getOrNull(SemanticsProperties.Heading) != null ||
                            it.config.getOrNull(SemanticsProperties.TestTag) == "category-inspector-provenance"
                    }
                    val inspected = mutableSetOf<Int>()
                    fun verifyVisibleText() {
                        for (node in nodes(scene).filter { candidate ->
                            textTargets.any { it.id == candidate.id } && candidate.boundsInRoot.height > 0
                        }) {
                            val layouts = mutableListOf<TextLayoutResult>()
                            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                            for (layout in layouts) {
                                // Skia can retain the parent width in MultiParagraph after Text
                                // shrinks to its intrinsic width. Compare actual line ink, since
                                // didOverflowWidth then reports overflow even for a short label.
                                assertTrue(!layout.didOverflowHeight && (0 until layout.lineCount).all { line ->
                                    layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f
                                }, "Text must wrap at ${width}x$height / ${fontScale}x: ${layout.layoutInput.text.text}; size=${layout.size}")
                            }
                            if (layouts.isNotEmpty()) inspected += node.id
                        }
                    }
                    verifyVisibleText()
                    if (fontScale == 2f) assertTrue(
                        assertNotNull(scroll.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)).maxValue() > 0f,
                        "Large text must scroll rather than clip")
                    val scrollAction = assertNotNull(scroll.config.getOrNull(SemanticsActions.ScrollBy)?.action)
                    val step = viewport.boundsInRoot.height * .75f
                    val maximum = assertNotNull(scroll.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)).maxValue()
                    repeat(ceil(maximum / step).toInt() + 2) {
                        scrollAction.invoke(0f, step)
                        frames(25)
                        verifyVisibleText()
                    }
                    assertEquals(textTargets.map { it.id }.toSet(), inspected,
                        "Every heading and provenance line must be reachable by scrolling")
                    assertNotNull(nodes.first { it.boundsInRoot.contains(edit.boundsInRoot.center) &&
                        it.config.getOrNull(SemanticsActions.OnClick) != null }
                        .config.getOrNull(SemanticsActions.OnClick)?.action).invoke()
                    assertEquals(1, edited)
                    assertNotNull(nodes.first { it.boundsInRoot.contains(close.boundsInRoot.center) &&
                        it.config.getOrNull(SemanticsActions.OnClick) != null }
                        .config.getOrNull(SemanticsActions.OnClick)?.action).invoke()
                    assertEquals(1, dismissed)
                    if (evidence != null) scene.render(time).use { image ->
                        image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                            File(evidence, "category-inspector-${width}x$height-${fontScale}x-${source.name.lowercase()}-${if (dark) "dark" else "light"}.png")
                                .writeBytes(data.bytes)
                        }
                    }
                } finally { scene.close() }
            }
    }

    @Test
    fun chipTapAndAccessibleLongPressConsumeTheStoryGestureAndKeepAccessibleTouchBounds() = SwingUtilities.invokeAndWait {
        var storyClicks = 0
        var storyLongClicks = 0
        var inspections = 0
        var captured = false
        val scene = ImageComposeScene(240, 160, Density(1f)) {
            MaterialTheme {
                Box(Modifier.fillMaxSize().combinedClickable(onClick = { storyClicks++ }, onLongClick = { storyLongClicks++ })
                    .padding(24.dp)) {
                    InspectableCategoryChip("AI", "ai", 42,
                        StoryCategoryColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer),
                        covered = false,
                        onOpen = { bounds, layer -> inspections++; captured = bounds != null && layer != null })
                }
            }
        }
        var time = 0L
        fun frames(count: Int) = repeat(count) { scene.render(time.also { time += 16_666_667L }).close() }
        try {
            frames(5)
            val chip = nodes(scene).single { it.config.getOrNull(SemanticsProperties.TestTag) == "category-label-ai" }
            assertTrue(chip.boundsInRoot.height < 32, "The chip should not reserve a 48dp-tall layout row")
            assertTrue(chip.touchBoundsInRoot.width >= 48 && chip.touchBoundsInRoot.height >= 48,
                "Compact inspector chips must retain expanded touch bounds")
            assertEquals(listOf("AI category, similarity 42 out of 100"),
                chip.config.getOrNull(SemanticsProperties.ContentDescription))
            scene.sendPointerEvent(PointerEventType.Press, chip.boundsInRoot.center, timeMillis = time / 1_000_000, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, chip.boundsInRoot.center, timeMillis = time / 1_000_000 + 1, type = PointerType.Touch)
            frames(12)
            assertEquals(1, inspections)
            assertTrue(captured, "A clicked chip must supply its visual bounds and source layer to the transform")
            assertEquals(0, storyClicks)
            assertEquals(0, storyLongClicks)
            assertNotNull(chip.config.getOrNull(SemanticsActions.OnLongClick)?.action).invoke()
            frames(12)
            assertEquals(2, inspections)
            assertEquals(0, storyClicks)
            assertEquals(0, storyLongClicks)
        } finally { scene.close() }
    }

    @Test
    fun storiesKeepCategoryLabelsCompactAndTapsBelongToTheStory() = SwingUtilities.invokeAndWait {
        var storyClicks = 0
        val sample = sampleCategoryInspection()
        val bootstrap = com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap.inMemory("CategoryFeedTest")
        val scene = ImageComposeScene(360, 200, Density(1f)) {
            MaterialTheme {
                StoryRow(
                    model = SettingsStoryPreviewModel.copy(categoryLabels = sample.labels, categoryInspection = sample),
                    style = com.simon.harmonichackernews.presentation.StoryDisplaySettings.from(
                        bootstrap.app.userSettings.story).toStoryRowStyle(
                            StoryRowStyleContext(53, 18, false)),
                    animateChanges = false, listItem = true,
                    onLinkClick = { storyClicks++ },
                )
            }
        }
        var time = 0L
        fun frames() = repeat(12) { scene.render(time.also { time += 16_666_667L }).close() }
        try {
            frames()
            val labels = nodes(scene).filter {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "AI" } == true
            }
            val label = labels.single()
            assertTrue(label.boundsInRoot.height < 32, "Stories labels must retain their compact typography")
            assertTrue(nodes(scene).none {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("category-label-") == true
            }, "Stories must not expose inspector actions")
            scene.sendPointerEvent(PointerEventType.Press, label.boundsInRoot.center, timeMillis = time / 1_000_000, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, label.boundsInRoot.center, timeMillis = time / 1_000_000 + 1, type = PointerType.Touch)
            frames()
            assertEquals(1, storyClicks, "A label tap follows the story rather than opening an inspector")
            System.getenv("HARMONIC_CATEGORY_INSPECTOR_EVIDENCE_DIR")?.let { directory ->
                val output = File(directory).apply { mkdirs() }
                scene.render(time).use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                        File(output, "category-story-compact.png").writeBytes(data.bytes)
                    }
                }
            }
        } finally { scene.close(); bootstrap.close() }
    }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> {
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        return scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }
    }
}
