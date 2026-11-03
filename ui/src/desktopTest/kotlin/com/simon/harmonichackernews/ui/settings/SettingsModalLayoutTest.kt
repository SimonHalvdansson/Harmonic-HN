@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfoImpl
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.settings.UserTagColor
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsModalLayoutTest {
    @Test
    fun dialogActionsWrapWholeButtonsBeforeSqueezingTheirLabels() = SwingUtilities.invokeAndWait {
        for (scale in listOf(1f, 2f)) for (longLabel in listOf(false, true)) {
            val confirm = if (longLabel) "Apply selected examples" else "Save"
            val scene = ImageComposeScene(382, 800, Density(1f, scale)) {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    SettingsAlertDialog(
                        onDismissRequest = {},
                        title = { SettingsDialogTitle("Category name") },
                        neutralButton = { SettingsDialogTextButton(onClick = {}) { Text("Reset") } },
                        dismissButton = { SettingsDialogTextButton(onClick = {}) { Text("Cancel") } },
                        confirmButton = { SettingsDialogTextButton(onClick = {}) { Text(confirm) } },
                    )
                }
            }
            try {
                repeat(12) { scene.render(it * 16_666_667L).close() }
                for (label in listOf("Reset", "Cancel", confirm)) {
                    val button = scene.nodes().single {
                        it.config.getOrNull(SemanticsActions.OnClick) != null &&
                            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
                    }
                    val bounds = button.boundsInWindow
                    val touchBounds = button.touchBoundsInRoot
                    assertTrue(touchBounds.width >= 48 && touchBounds.height >= 48,
                        "$label must retain its touch target at ${scale}x: $touchBounds")
                    assertTrue(bounds.left >= 35 && bounds.right <= 347,
                        "$label must fit inside the dialog at ${scale}x: $bounds")
                    val layouts = mutableListOf<TextLayoutResult>()
                    button.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
                    val layout = layouts.single { it.layoutInput.text.text == label }
                    assertTextContentFits(layout, "$label at ${scale}x")
                    if (!label.contains(' ')) {
                        assertEquals(1, layout.lineCount, "$label must move to a new row as a whole action at ${scale}x")
                    } else {
                        repeat(layout.lineCount) { line ->
                            val lineText = label.substring(layout.getLineStart(line), layout.getLineEnd(line)).trim()
                            assertTrue(lineText.length > 1, "A long action must not leave a one-character line: $lineText")
                        }
                    }
                }
            } finally { scene.close() }
        }
    }

    @Test
    fun shortLargeTextTagDialogCanRevealTheEditorColorsPreviewAndActions() = SwingUtilities.invokeAndWait {
        lateinit var windowInfo: WindowInfoImpl
        val scene = ImageComposeScene(320, 800, Density(1f, 2f)) {
            windowInfo = LocalWindowInfo.current as WindowInfoImpl
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                UserTagDialog(
                    currentTag = "Maintainer", currentColor = UserTagColor.Green,
                    userName = "AUsernameWithMixedCase", onSave = { _, _ -> }, onDismiss = {},
                )
            }
        }
        var time = 0L
        fun frames(count: Int = 8) { repeat(count) { scene.render(time.also { time += 16_666_667L }).close() } }
        fun tagged(tag: String) = scene.nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun includes(node: SemanticsNode, id: Int): Boolean = node.id == id || node.children.any { includes(it, id) }
        fun visible(node: SemanticsNode): Boolean {
            val bounds = node.boundsInWindow
            return bounds.width > 0 && bounds.height >= 40 && bounds.top >= 0 && bounds.bottom <= 240
        }
        try {
            frames()
            val editor = scene.nodes().single {
                it.config.getOrNull(SemanticsProperties.EditableText)?.text == "Maintainer"
            }
            assertEquals(androidx.compose.ui.text.TextRange("Maintainer".length),
                editor.config.getOrNull(SemanticsProperties.TextSelectionRange),
                "Opening an existing tag must place the cursor after its text")
            // ImageComposeScene 1.13 resizes its canvas without updating native layer WindowInfo.
            // Keep the real platform info in sync so Dialogs measure/place in the resized window.
            windowInfo.containerSize = IntSize(320, 240)
            windowInfo.containerDpSize = DpSize(320.dp, 240.dp)
            scene.constraints = Constraints(maxWidth = 320, maxHeight = 240)
            frames()
            val outer = tagged("settings-dialog-compact-scroll")
            val field = scene.nodes().single { it.config.getOrNull(SemanticsActions.SetText) != null }
            val preview = tagged("user-tag-preview")
            val body = scene.nodes().single {
                it.id != outer.id && it.config.getOrNull(SemanticsActions.ScrollBy) != null && includes(it, preview.id)
            }
            fun scrollTo(node: SemanticsNode, fraction: Float) {
                val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
                node.config[SemanticsActions.ScrollBy].action!!.invoke(0f, range.maxValue() * fraction - range.value())
                frames(20)
            }
            fun reveal(node: SemanticsNode, label: String) {
                // Native focus may already have brought the editor into view. Exercise real
                // scrolling rather than assuming the initial offset or an exact title height.
                for (outerFraction in listOf(0f, .25f, .5f, .75f, 1f)) {
                    scrollTo(outer, outerFraction)
                    for (bodyFraction in listOf(0f, .25f, .5f, .75f, 1f)) {
                        scrollTo(body, bodyFraction)
                        if (visible(node)) return
                    }
                }
                assertTrue(visible(node), "$label must be scroll-reachable inside the short dialog: ${node.boundsInWindow}")
            }
            reveal(field, "Tag editor")
            val colorNode = tagged("user-tag-colors")
            // Both halves of the form must be reachable; a clipped plain Column cannot do this.
            reveal(colorNode, "Color choices")
            reveal(preview, "Live preview")
            outer.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 10_000f)
            frames(45)
            val save = scene.nodes().single {
                it.config.getOrNull(SemanticsActions.OnClick) != null &&
                    it.config.getOrNull(SemanticsProperties.Text)?.any { entry -> entry.text == "Set" } == true
            }
            assertTrue(visible(save), "Set action must remain reachable: ${save.boundsInWindow}")
        } finally { scene.close() }
    }

    @Test
    fun shortLargeTextDialogsKeepLazyBodiesFiniteAndAllActionsReachableAcrossResize() = SwingUtilities.invokeAndWait {
        var confirmed = false
        lateinit var windowInfo: WindowInfoImpl
        val scene = ImageComposeScene(320, 800, Density(1f, 2f)) {
            windowInfo = LocalWindowInfo.current as WindowInfoImpl
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                SettingsAlertDialog(
                    onDismissRequest = {},
                    title = { SettingsDialogTitle("Configure category examples") },
                    scrollableContent = true,
                    text = {
                        LazyColumn(Modifier.fillMaxWidth().testTag("bounded-modal-list")) {
                            items(List(20) { "Example story $it" }) { Text(it) }
                        }
                    },
                    dismissButton = { SettingsDialogTextButton(onClick = {}) { Text("Cancel") } },
                    confirmButton = { SettingsDialogTextButton(onClick = { confirmed = true }) { Text("Save changes") } },
                )
            }
        }
        var time = 0L
        fun frames(count: Int = 8) { repeat(count) { scene.render(time.also { time += 16_666_667L }).close() } }
        fun tagged(tag: String) = scene.nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun text(value: String) = scene.nodes().single {
            it.config.getOrNull(SemanticsProperties.Text)?.any { entry -> entry.text == value } == true
        }
        fun assertActionsVisible(height: Int) {
            for (label in listOf("Cancel", "Save changes")) {
                val bounds = text(label).boundsInWindow
                assertTrue(bounds.height > 0 && bounds.top >= 0 && bounds.bottom <= height,
                    "$label must remain reachable in a $height px window: $bounds")
                assertTrue(bounds.left >= 0 && bounds.right <= 320, "$label must wrap within the card")
            }
        }
        try {
            frames()
            windowInfo.containerSize = IntSize(320, 240)
            windowInfo.containerDpSize = DpSize(320.dp, 240.dp)
            scene.constraints = Constraints(maxWidth = 320, maxHeight = 240)
            frames()
            val scroll = tagged("settings-dialog-compact-scroll")
            for (fraction in listOf(0f, .25f, .5f, .75f, 1f)) {
                if (tagged("bounded-modal-list").boundsInWindow.height > 1) break
                val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
                scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, range.maxValue() * fraction - range.value())
                frames(20)
            }
            val body = tagged("bounded-modal-list").boundsInWindow
            assertTrue(body.height in 1f..160f, "The nested list must have a finite, reachable viewport: $body")
            assertTrue(scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 10_000f))
            frames(45)
            assertActionsVisible(240)
            // A temporary larger window must not throw away the compact card's scroll position.
            windowInfo.containerSize = IntSize(320, 760)
            windowInfo.containerDpSize = DpSize(320.dp, 760.dp)
            scene.constraints = Constraints(maxWidth = 320, maxHeight = 760)
            frames()
            windowInfo.containerSize = IntSize(320, 240)
            windowInfo.containerDpSize = DpSize(320.dp, 240.dp)
            scene.constraints = Constraints(maxWidth = 320, maxHeight = 240)
            frames()
            assertActionsVisible(240)
            val action = scene.nodes().single {
                it.config.getOrNull(SemanticsActions.OnClick) != null &&
                    it.config.getOrNull(SemanticsProperties.Text)?.any { entry -> entry.text == "Save changes" } == true
            }
            action.config[SemanticsActions.OnClick].action!!.invoke()
            assertTrue(confirmed)
        } finally { scene.close() }
    }

    @Test
    fun disabledMotionSettlesValidationSizeWithoutADelayedResizeAndCancelStillWorks() = SwingUtilities.invokeAndWait {
        val visible = mutableStateOf(true)
        val noMotion = object : MotionDurationScale { override val scaleFactor = 0f }
        val scene = ImageComposeScene(400, 800, coroutineContext = Dispatchers.Unconfined + noMotion) {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                if (visible.value) AiSummaryTextDialog(
                    title = "Category name", hint = "Name", initialValue = "Short", defaultValue = "",
                    minLines = 1, maxLines = 1, textSizeSp = 16,
                    trimValue = true, allowEmpty = false, showReset = false, asciiInput = false,
                    maxLength = 8, onSave = { null }, onDismiss = { visible.value = false },
                )
            }
        }
        var time = 0L
        fun frames(count: Int = 8) { repeat(count) { scene.render(time.also { time += 16_666_667L }).close() } }
        fun errorBounds(): Rect = scene.nodes().single {
            it.config.getOrNull(SemanticsProperties.Text)?.any { entry -> entry.text == "Use 8 characters or fewer" } == true
        }.boundsInWindow
        try {
            frames()
            val field = scene.nodes().single { it.config.getOrNull(SemanticsActions.SetText) != null }
            field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Too long a name"))
            frames(2)
            val immediate = errorBounds()
            assertTrue(immediate.height > 0)
            frames(45)
            assertEquals(immediate, errorBounds(), "Reduced motion must not leave a delayed dialog-height animation")
            val cancel = scene.nodes().single {
                it.config.getOrNull(SemanticsActions.OnClick) != null &&
                    it.config.getOrNull(SemanticsProperties.Text)?.any { entry -> entry.text == "Cancel" } == true
            }
            cancel.config[SemanticsActions.OnClick].action!!.invoke()
            frames()
            assertFalse(visible.value)
        } finally { scene.close() }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { owner ->
        fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
        descend(owner.rootSemanticsNode)
    }
}
