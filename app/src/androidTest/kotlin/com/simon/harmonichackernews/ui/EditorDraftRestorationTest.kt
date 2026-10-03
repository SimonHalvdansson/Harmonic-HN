package com.simon.harmonichackernews.ui

import android.os.Bundle
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real Android editor, its activity saved-state Bundle, and actual activity recreation. */
@RunWith(AndroidJUnit4::class)
class EditorDraftRestorationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun closeTestEditor() {
        compose.runOnIdle { compose.activity.navigationController.closeEditor() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("compose_editor_container").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun backGestureHidesKeyboardBeforeAskingToDiscardPost() {
        openEditor(EditorType.POST)
        val fields = listOf(
            "compose_editor_title" to "Ask HN: keyboard back regression",
            "compose_editor_url" to "https://example.com/draft",
            "compose_editor_text" to "Keep this draft when hiding the keyboard.",
        )
        fields.forEach { (tag, draft) ->
            compose.onNodeWithTag(tag).performTextReplacement(draft)
        }
        fields.forEach { (tag, _) ->
            compose.onNodeWithTag(tag).performClick()
            awaitKeyboard(visible = true)
            swipeBack()
            awaitKeyboard(visible = false)
            compose.onNodeWithText("Discard post?").assertDoesNotExist()
            fields.forEach { (field, draft) -> assertFieldText(field, draft) }
        }

        // With the keyboard already hidden, the same gesture must protect the draft.
        swipeBack()
        awaitDiscardDialog()
        compose.onNodeWithText("Cancel", substring = false).performClick()
        fields.forEach { (tag, draft) -> assertFieldText(tag, draft) }
        discardEditor()
    }

    private fun awaitKeyboard(visible: Boolean) {
        compose.waitUntil(10_000) {
            compose.runOnIdle {
                val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                insets != null && insets.isVisible(WindowInsetsCompat.Type.ime()) == visible &&
                    (visible || insets.getInsets(WindowInsetsCompat.Type.ime()).bottom == 0)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun backGestureHidesKeyboardBeforeClosingEmptyPost() {
        openEditor(EditorType.POST)
        compose.onNodeWithTag("compose_editor_title").performClick()
        awaitKeyboard(visible = true)
        swipeBack()
        awaitKeyboard(visible = false)
        compose.onNodeWithTag("compose_editor_container").assertExists()
        compose.onNodeWithText("Discard post?").assertDoesNotExist()
        swipeBack()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("compose_editor_container").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun backKeyHidesKeyboardBeforeAskingToDiscardReply() {
        openEditor(EditorType.COMMENT_REPLY)
        val draft = "Keep this reply when hiding the keyboard."
        compose.onNodeWithTag(COMMENT).performTextReplacement(draft)
        compose.onNodeWithTag(COMMENT).performClick()
        awaitKeyboard(visible = true)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitKeyboard(visible = false)
        compose.onNodeWithText("Discard comment?").assertDoesNotExist()
        assertFieldText(COMMENT, draft)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitDiscardDialog()
        compose.onNodeWithText("Cancel", substring = false).performClick()
        assertFieldText(COMMENT, draft)
        discardEditor()
    }

    private fun swipeBack() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val bounds = compose.runOnIdle { compose.activity.window.decorView.let { it.width to it.height } }
        val downTime = SystemClock.uptimeMillis()
        for (step in 0..20) {
            val action = when (step) {
                0 -> MotionEvent.ACTION_DOWN
                20 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action,
                1f + bounds.first * 0.45f * step / 20, bounds.second * 0.5f, 0,
            ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
                assertTrue("System back gesture must be injected", automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
            SystemClock.sleep(16)
        }
        compose.waitForIdle()
    }

    @Test
    fun incompletePostCannotCloseWithoutAnExplicitDiscard() {
        val cases = listOf(
            "compose_editor_text" to "Body without a title",
            "compose_editor_title" to "Title without a URL or body",
            "compose_editor_url" to "https://example.com/unfinished",
            "compose_editor_title" to "x".repeat(81),
        )
        for ((tag, draft) in cases) {
            openEditor(EditorType.POST)
            compose.onNodeWithTag(tag).performTextReplacement(draft)
            compose.onNodeWithContentDescription("Close").performClick()
            awaitDiscardDialog()
            compose.onNodeWithText("Cancel", substring = false).performClick()
            assertFieldText(tag, draft)
            // System Back uses the same dirty-draft guard as the close button.
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            awaitDiscardDialog()
            compose.onNodeWithText("Cancel", substring = false).performClick()
            assertFieldText(tag, draft)
            discardEditor()
        }
    }

    @Test
    fun ordinaryReplyRestoresTextAndSelectionWithoutCreatingDraftFiles() {
        val originalFiles = draftFiles()
        openEditor(EditorType.COMMENT_REPLY)
        val text = "A normal reply with a useful point."
        val selection = TextRange(2, 14)
        compose.onNodeWithTag(COMMENT).performTextReplacement(text)
        compose.onNodeWithTag(COMMENT).performTextInputSelection(selection)
        assertField(COMMENT, text, selection)

        assertSmallSavedState()
        backgroundAndRecreate()

        compose.waitUntil(10_000) { compose.onAllNodesWithTag(COMMENT).fetchSemanticsNodes().isNotEmpty() }
        assertField(COMMENT, text, selection)
        assertEquals(originalFiles, draftFiles())
        discardEditor()
    }

    @Test
    fun formattingCanBeUndoneAndRedoneWithTheKeyboard() {
        openEditor(EditorType.COMMENT_REPLY)
        val text = "A useful reply"
        val field = compose.onNodeWithTag(COMMENT)
        field.performTextReplacement(text)
        field.performTextInputSelection(TextRange(2, 8))
        compose.onNodeWithContentDescription("Italic").performClick()
        assertFieldText(COMMENT, "A *useful* reply")
        field.performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Z)
            keyUp(Key.CtrlLeft)
        }
        assertField(COMMENT, text, TextRange(2, 8))
        field.performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            pressKey(Key.Z)
            keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        assertFieldText(COMMENT, "A *useful* reply")
        discardEditor()
    }

    @Test
    fun largeReplyRestoresInFullAfterBackgroundingAndRecreationAndCleansUpOnDiscard() {
        val originalFiles = draftFiles()
        openEditor(EditorType.COMMENT_REPLY)
        val text = "Draft recovery ø🙂 with line breaks.\n".repeat(20_000)
        val selection = TextRange(450_000, 450_012)
        compose.onNodeWithTag(COMMENT).performTextReplacement(text)
        compose.onNodeWithTag(COMMENT).performTextInputSelection(selection)
        assertField(COMMENT, text, selection)

        assertSmallSavedState()
        compose.waitUntil(10_000) { (draftFiles() - originalFiles).size == 1 }
        backgroundAndRecreate()

        assertField(COMMENT, text, selection)
        compose.waitUntil(10_000) { (draftFiles() - originalFiles).size == 1 }
        discardEditor()
        compose.waitUntil(10_000) { draftFiles() == originalFiles }
    }

    @Test
    fun postFieldsRestoreIndependentlyWhenBodySpillsToDisk() {
        val originalFiles = draftFiles()
        openEditor(EditorType.POST)
        val title = "Ask HN: draft restoration"
        val url = "https://example.com/draft"
        val text = "Long post body ø🙂.\n".repeat(35_000)
        compose.onNodeWithTag("compose_editor_title").performTextReplacement(title)
        compose.onNodeWithTag("compose_editor_url").performTextReplacement(url)
        compose.onNodeWithTag("compose_editor_text").performTextReplacement(text)

        assertSmallSavedState()
        backgroundAndRecreate()

        assertFieldText("compose_editor_title", title)
        assertFieldText("compose_editor_url", url)
        assertFieldText("compose_editor_text", text)
        compose.waitUntil(10_000) { (draftFiles() - originalFiles).size == 1 }
        discardEditor()
        compose.waitUntil(10_000) { draftFiles() == originalFiles }
    }

    private fun openEditor(type: EditorType) {
        compose.runOnIdle {
            compose.activity.navigationController.apply {
                dismissWelcomeDialog()
                dismissChangelogDialog()
                openEditor(EditorDestination(type = type, itemId = 1, parentText = "Parent comment", postTitle = "Draft test"))
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("compose_editor_container").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertSmallSavedState() {
        val state = Bundle()
        compose.runOnIdle {
            InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(compose.activity, state)
        }
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(state)
            Log.i("EditorDraftTest", "Complete activity saved-state bytes: ${parcel.dataSize()}")
            assertTrue("Saved state should stay comfortably below Binder's limit", parcel.dataSize() < 256 * 1024)
        } finally {
            parcel.recycle()
        }
    }

    private fun backgroundAndRecreate() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("compose_editor_container").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertField(tag: String, text: String, selection: TextRange) {
        assertFieldText(tag, text)
        assertEquals(selection, compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
    }

    private fun assertFieldText(tag: String, text: String) {
        // OutlinedTextField also exposes its label/counter as Text; compare only the editable value.
        val actual = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertTrue("Full draft must match: expected ${text.length} characters, restored ${actual.length}", text == actual)
    }

    private fun discardEditor() {
        compose.onNodeWithContentDescription("Close").performClick()
        awaitDiscardDialog()
        compose.onNodeWithText("Discard", substring = false).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("compose_editor_container").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun awaitDiscardDialog() {
        // The native IME animation runs outside Compose's test clock. The dialog deliberately
        // waits for it to finish before taking window focus.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Discard", substring = false).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun draftFiles(): Set<String> = File(
        InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,
        "editor-drafts",
    ).walkTopDown().filter { it.isFile }.map { it.absolutePath }.toSet()

    private companion object {
        const val COMMENT = "compose_editor_comment"
    }
}
