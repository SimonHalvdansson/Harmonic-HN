package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.settings.StoriesHeaderPill
import com.simon.harmonichackernews.settings.UserAvatarOptions
import com.simon.harmonichackernews.settings.UserAvatarStyle
import com.simon.harmonichackernews.settings.UserTagColor
import com.simon.harmonichackernews.ui.content.userTagColor
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

class ConstrainedIdentitySettingsTest {
    private val username = "VeryLongUsername_0123456789_ABCDEFGHIJKLMN"
    private val tag = "Maintainer of accessibility and multiplatform rendering ".repeat(3).trim()

    @Test
    fun longTagPreviewWrapsAndEveryColorKeepsAFullTouchTarget() = SwingUtilities.invokeAndWait {
        for (width in listOf(208, 280, 480)) for (scale in listOf(1f, 1.5f, 2f)) {
            for (dark in listOf(false, true)) {
                val selected = mutableStateOf(UserTagColor.Theme)
                val palette = HarmonicThemeCatalog.scheme("dynamic", dark)
                var accentLuminance = 0f
                val harness = Harness(width, 1_600, scale, dark) {
                    accentLuminance = userTagColor(selected.value).luminance()
                    Column {
                        UserTagColorChoices(selected.value, true) { selected.value = it }
                        UserTagPreview(username, tag, selected.value)
                    }
                }
                try {
                    harness.frames()
                    val previewText = "$username • $tag"
                    val text = harness.layouts(previewText)
                    assertTrue(text.isNotEmpty(), "The preview must retain the full username and tag")
                    text.forEach {
                        assertTextContentFits(it, "Preview at $width dp / $scale×")
                        assertTrue(it.lineCount > 1)
                    }
                    val colors = harness.nodes().filter {
                        it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton
                    }
                    assertEquals(UserTagColor.entries.size, colors.size)
                    colors.forEachIndexed { index, node ->
                        harness.assertTouchTarget(node)
                        assertEquals(listOf("${UserTagColor.entries[index].label} tag color"),
                            node.config[SemanticsProperties.ContentDescription])
                    }
                    colors.zipWithNext().forEach { (a, b) ->
                        assertFalse(a.boundsInRoot.overlaps(b.boundsInRoot), "Color hit targets must not overlap")
                    }
                    UserTagColor.entries.forEach { choice ->
                        val option = harness.nodes().single {
                            it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf("${choice.label} tag color")
                        }
                        assertTrue(option.config[SemanticsActions.OnClick].action!!.invoke())
                        harness.frames()
                        assertEquals(choice, selected.value)
                        val background = palette.colorScheme.surfaceContainerHighest.luminance()
                        val contrast = (maxOf(accentLuminance, background) + .05f) /
                            (minOf(accentLuminance, background) + .05f)
                        assertTrue(contrast >= 4.5f, "$choice preview contrast must remain readable: $contrast")
                    }
                    harness.evidence("tag-preview-$width-${scale}x-${if (dark) "dark" else "light"}")
                } finally { harness.close() }
            }
        }
    }

    @Test
    fun segmentedChoicesWrapWithoutClippingTextOrShrinkingTouchTargets() = SwingUtilities.invokeAndWait {
        for (width in listOf(208, 280, 480)) for (scale in listOf(1f, 1.5f, 2f)) {
            val selected = mutableStateOf(StoriesHeaderPill.SECONDARY)
            val harness = Harness(width, 1_600, scale, false) {
                Column {
                    SegmentedSetting(
                        title = "Header pill",
                        options = StoriesHeaderPill.entries.map { it to it.label },
                        selected = selected.value,
                        optionWeights = mapOf(StoriesHeaderPill.OFF to .65f, StoriesHeaderPill.SECONDARY to 1.2f),
                        onSelected = { selected.value = it },
                    )
                    SurfaceEffectSelector(com.simon.harmonichackernews.settings.SurfaceEffectMode.Solid) {}
                }
            }
            try {
                harness.frames()
                val choices = harness.nodes().filter {
                    it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton
                }
                assertEquals(7, choices.size)
                choices.forEach(harness::assertTouchTarget)
                for (label in StoriesHeaderPill.entries.map { it.label } + listOf("Solid", "Blur", "Glass")) {
                    val layouts = harness.layouts(label)
                    assertTrue(layouts.isNotEmpty(), "$label must provide its text layout")
                    layouts.forEach { assertTextContentFits(it, "$label at $width dp / $scale×") }
                }
                StoriesHeaderPill.entries.forEach { pill ->
                    val option = harness.action(pill.label)
                    assertTrue(option.config[SemanticsActions.OnClick].action!!.invoke())
                    harness.frames()
                    assertEquals(pill, selected.value)
                }
                harness.evidence("segmented-$width-${scale}x")
            } finally { harness.close() }
        }
    }

    @Test
    fun profileActionsRemainReachableInShortWindowsAndAtDoubleTextSize() = SwingUtilities.invokeAndWait {
        for ((width, height) in listOf(208 to 240, 280 to 640, 360 to 240, 600 to 640)) {
            for (scale in listOf(1f, 2f)) {
                val list = LazyListState()
                var submissions = 0
                var edits = 0
                var reports = 0
                var blocks = 0
                val harness = Harness(width, height, scale, false) {
                    UserProfileDialogContent(
                        userName = username,
                        state = UserDialogUiState.Loaded(UserInfoUi(username,
                            "123,456 karma since January 2000", "Writes at https://example.com", true)),
                        tag = tag,
                        userAvatarsEnabled = true,
                        userAvatarOptions = UserAvatarOptions(setOf(UserAvatarStyle.LETTERS)),
                        onOpenSubmissions = { submissions++ }, onEditTag = { edits++ },
                        onReport = { reports++ }, onToggleBlocked = { blocks++ }, listState = list,
                    )
                }
                try {
                    harness.frames()
                    val headings = harness.nodes().filter {
                        it.config.getOrNull(SemanticsProperties.Heading) != null
                    }
                    assertTrue(headings.any { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == username } == true
                    }, "The full username must be available as a screen-reader heading")
                    harness.layouts(username).forEach { assertTextContentFits(it, "Long profile title") }

                    fun reveal(label: String): SemanticsNode {
                        list.dispatchRawDelta(-100_000f)
                        harness.frames()
                        while (true) {
                            val node = harness.actionOrNull(label)
                            if (node != null && harness.isVisibleTarget(node)) return node
                            if (!list.canScrollForward || list.dispatchRawDelta(height * .2f) <= .01f) break
                            harness.frames()
                        }
                        error("Unreachable profile action $label at $width×$height / $scale×")
                    }
                    fun click(label: String) {
                        val action = reveal(label)
                        harness.assertTouchTarget(action)
                        assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke())
                        harness.frames(25)
                    }
                    click("Submissions")
                    click("Set tag ($tag)")
                    click("Report")
                    assertEquals(1, submissions)
                    assertEquals(1, edits)
                    assertEquals(1, reports)
                    click("Block")
                    assertEquals(0, blocks)
                    click("Cancel")
                    click("Block")
                    click("Block user")
                    assertEquals(1, blocks)
                    harness.evidence("profile-$width-$height-${scale}x")
                } finally { harness.close() }
            }
        }
    }

    private class Harness(
        private val width: Int,
        private val height: Int,
        scale: Float,
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        private var time = 0L
        private val scene = ImageComposeScene(width, height, Density(1f, scale)) {
            val palette = HarmonicThemeCatalog.scheme("dynamic", dark)
            HarmonicTheme(palette.colorScheme, palette.dark, content = content)
        }
        fun frames(count: Int = 5) = repeat(count) { scene.render(time.also { time += 16_666_667L }).close() }
        fun nodes(merged: Boolean = true): List<SemanticsNode> {
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            return scene.semanticsOwners.flatMap {
                descendants(if (merged) it.rootSemanticsNode else it.unmergedRootSemanticsNode)
            }
        }
        fun layouts(text: String): List<TextLayoutResult> = nodes(merged = false).flatMap { node ->
            mutableListOf<TextLayoutResult>().also {
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(it)
            }.filter { it.layoutInput.text.text == text }
        }
        fun actionOrNull(label: String): SemanticsNode? = nodes().firstOrNull {
            it.config.getOrNull(SemanticsActions.OnClick) != null &&
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
        }
        fun action(label: String): SemanticsNode = requireNotNull(actionOrNull(label)) { "Missing action: $label" }
        fun isVisibleTarget(node: SemanticsNode): Boolean {
            val bounds = node.touchBoundsInRoot
            return bounds.width >= 48f && bounds.height >= 48f &&
                bounds.top >= -.5f && bounds.bottom <= height + .5f
        }
        fun assertTouchTarget(node: SemanticsNode) {
            val bounds = node.touchBoundsInRoot
            assertTrue(bounds.width >= 47.9f && bounds.height >= 47.9f, "Touch target must be at least 48dp: $bounds")
            assertTrue(bounds.left >= -.5f && bounds.right <= width + .5f, "Action must fit the viewport: $bounds")
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
