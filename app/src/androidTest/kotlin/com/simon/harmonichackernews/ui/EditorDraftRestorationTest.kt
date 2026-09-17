package com.simon.harmonichackernews.ui

import android.os.Bundle
import android.os.Parcel
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
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

        assertField(COMMENT, text, selection)
        assertEquals(originalFiles, draftFiles())
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
        assertEquals(1, (draftFiles() - originalFiles).size)
        backgroundAndRecreate()

        assertField(COMMENT, text, selection)
        assertEquals(1, (draftFiles() - originalFiles).size)
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
        assertEquals(1, (draftFiles() - originalFiles).size)
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
        compose.onNodeWithText("Discard", substring = false).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("compose_editor_container").fetchSemanticsNodes().isEmpty()
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
