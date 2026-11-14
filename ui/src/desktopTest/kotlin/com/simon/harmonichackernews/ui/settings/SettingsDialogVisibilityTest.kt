@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class SettingsDialogVisibilityTest {
    @Test
    fun inactiveSettingsHideTheirModalAndRestoreItsDraftOnReturn() = SwingUtilities.invokeAndWait {
        var visible by mutableStateOf(true)
        var dismissals = 0
        val scene = ImageComposeScene(400, 800) {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            val draft = rememberTextFieldState("Original")
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalSettingsDialogsVisible provides visible) {
                    SettingsAlertDialog(onDismissRequest = { dismissals++ }, confirmButton = {}, text = {
                        BasicTextField(draft, Modifier.testTag("retained-draft"))
                    })
                }
            }
        }
        var time = 0L
        fun frames() { repeat(15) { scene.render(time.also { time += 16_666_667L }).close() } }
        fun SemanticsNode.nodes(): List<SemanticsNode> = listOf(this) + children.flatMap { it.nodes() }
        fun field(): SemanticsNode? = scene.semanticsOwners.flatMap { it.rootSemanticsNode.nodes() }
            .firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == "retained-draft" }
        try {
            frames()
            assertNotNull(field()).config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Unfinished setting"))
            frames()
            visible = false
            frames()
            assertNull(field(), "An inactive screen must not leave a modal over a notification destination")
            assertEquals(0, dismissals, "Hiding the modal must not discard its owner's edit state")
            visible = true
            frames()
            assertEquals("Unfinished setting", assertNotNull(field()).config[SemanticsProperties.EditableText].text)
        } finally { scene.close() }
    }
}
