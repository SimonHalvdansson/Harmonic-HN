package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.settings.DomainFilterRule
import com.simon.harmonichackernews.settings.TitleFilterMatchMode
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

class ConstrainedFilterRulesTest {
    @Test
    fun titleEditorChoicesAndPreviewRemainReachableAtDoubleTextSize() = SwingUtilities.invokeAndWait {
        for (width in listOf(208, 280, 480)) for (scale in listOf(1f, 2f)) {
            val mode = mutableStateOf(TitleFilterMatchMode.WORDS_PHRASE)
            val harness = Harness(width, 240, scale) {
                TitleFilterRuleEditorContent("AI", mode.value, {}, { mode.value = it }, true, FocusRequester(), {})
            }
            try {
                harness.frames()
                for (label in listOf("Words / phrase", "Contains text")) {
                    val choice = harness.reveal(label, "title-filter-rule-content")
                    harness.assertTouchTarget(choice)
                    val layouts = harness.layouts(label)
                    assertTrue(layouts.isNotEmpty(), "Missing text layout for $label")
                    layouts.forEach { assertTextContentFits(it, "$label at $width / $scale×") }
                    assertTrue(choice.config[SemanticsActions.OnClick].action!!.invoke())
                    harness.frames()
                }
                assertEquals(TitleFilterMatchMode.CONTAINS_TEXT, mode.value)
                harness.scrollToBottom("title-filter-rule-content")
                harness.evidence("title-filter-$width-${scale}x")
            } finally { harness.close() }
        }
    }

    @Test
    fun longModeRowsGrowAndKeepTheirEditAndRemoveTargets() = SwingUtilities.invokeAndWait {
        for (width in listOf(208, 280, 480)) for (scale in listOf(1f, 2f)) {
            val mode = "Contains text · previous behavior"
            var edited = -1
            var removed = -1
            val harness = Harness(width, 1_600, scale) {
                FilterRuleListContent("Saved domain rules", listOf(FilterRuleRow("legacy", "a-long-domain.example", mode)),
                    "No rules", true, {}, { edited = it }, { removed = it })
            }
            try {
                harness.frames()
                val layouts = harness.layouts(mode)
                assertTrue(layouts.isNotEmpty())
                layouts.forEach { assertTextContentFits(it, "Rule mode at $width / $scale×") }
                val edit = harness.byDescription("Edit a-long-domain.example")
                val remove = harness.byDescription("Remove a-long-domain.example")
                harness.assertTouchTarget(edit)
                harness.assertTouchTarget(remove)
                assertTrue(edit.config[SemanticsActions.OnClick].action!!.invoke())
                assertTrue(remove.config[SemanticsActions.OnClick].action!!.invoke())
                assertEquals(0, edited)
                assertEquals(0, removed)
                harness.evidence("filter-list-$width-${scale}x")
            } finally { harness.close() }
        }
    }

    @Test
    fun domainEditorControlsRemainReachableInShortWindows() = SwingUtilities.invokeAndWait {
        for (width in listOf(208, 280, 480)) for (scale in listOf(1f, 2f)) {
            val harness = Harness(width, 240, scale) {
                DomainFilterRuleEditorContent("127.0.0.1", "127.0.0.1", DomainFilterRule("127.0.0.1", false),
                    true, false, {}, {}, true, FocusRequester(), {})
            }
            try {
                harness.frames()
                harness.scrollToBottom("domain-filter-rule-content")
                harness.reveal("Test a domain or URL", "domain-filter-rule-content")
                harness.evidence("domain-filter-$width-${scale}x")
            } finally { harness.close() }
        }
    }

    private class Harness(private val width: Int, private val height: Int, scale: Float, content: @Composable () -> Unit) {
        private var time = 0L
        private val scene = ImageComposeScene(width, height, Density(1f, scale)) {
            val palette = HarmonicThemeCatalog.scheme("dynamic", false)
            HarmonicTheme(palette.colorScheme, palette.dark, content = content)
        }
        fun frames(count: Int = 30) = repeat(count) { scene.render(time.also { time += 16_666_667L }).close() }
        fun nodes(unmerged: Boolean = false): List<SemanticsNode> {
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            return scene.semanticsOwners.flatMap { descendants(if (unmerged) it.unmergedRootSemanticsNode else it.rootSemanticsNode) }
        }
        fun layouts(text: String): List<TextLayoutResult> = nodes(unmerged = true).flatMap { node ->
            mutableListOf<TextLayoutResult>().also { node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(it) }
                .filter { it.layoutInput.text.text == text }
        }
        fun byDescription(text: String): SemanticsNode = nodes().first {
            it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(text)
        }
        private fun scroll(tag: String, amount: Float) {
            val node = nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
            node.config[SemanticsActions.ScrollBy].action!!.invoke(0f, amount)
            frames()
        }
        fun scrollToBottom(tag: String) = scroll(tag, 100_000f)
        fun reveal(label: String, tag: String): SemanticsNode {
            scroll(tag, -100_000f)
            repeat(50) {
                val action = nodes().firstOrNull {
                    it.config.getOrNull(SemanticsActions.OnClick) != null &&
                        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
                }
                if (action != null && action.touchBoundsInRoot.height >= 47.9f &&
                    action.boundsInRoot.top >= -.5f && action.boundsInRoot.bottom <= height + .5f) return action
                scroll(tag, 48f)
            }
            error("Unreachable $label at $width×$height")
        }
        fun assertTouchTarget(node: SemanticsNode) {
            assertTrue(node.touchBoundsInRoot.width >= 47.9f && node.touchBoundsInRoot.height >= 47.9f,
                "The expanded touch target must remain 48dp around the compact control: ${node.touchBoundsInRoot}")
            assertTrue(node.boundsInRoot.left >= -.5f && node.boundsInRoot.right <= width + .5f)
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
