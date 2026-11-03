@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.app.*
import com.simon.harmonichackernews.data.*
import com.simon.harmonichackernews.network.*
import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.comments.*
import com.simon.harmonichackernews.ui.theme.*
import io.ktor.client.engine.okhttp.OkHttp
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual comment-detail/post-summary -> Ask flows inside the production Android sheet. */
@RunWith(AndroidJUnit4::class)
class AskSheetGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun commentAskHeaderTranscriptAndComposerCannotDragTheSheet() = verify(fromComment = true)
    @Test fun postAskHeaderTranscriptAndComposerCannotDragTheSheet() = verify(fromComment = false)

    private fun verify(fromComment: Boolean) {
        val production = runBlocking { (compose.activity.application as HarmonicApplication).awaitStartup() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val network = NetworkGraphFactory.create(NetworkGraphEnvironment(scope, "AskSheetGestureTest", engine = { OkHttp.create() }))
        val preferences = InMemoryKeyValueStore().apply { putBoolean("pref_debug_mock_ai_answers", true) }
        val app = HarmonicAppComposition(network, production.platform, HarmonicHostConfiguration.inMemory(
            metadata = AppMetadata(debug = true), currentMinutesFromMidnight = { 0 }, systemDark = { false },
            settingsStore = preferences, settingsChanges = preferences.changes))
        val scene = app.createScene()
        val story = StoryListItemSnapshot(StorySnapshot(42, title = "Ask sheet fixture", url = "https://example.com/article"),
            StoryPresentationSnapshot(loaded = true, aiSummaryText = "A summary of the article.", summaryGeneratedSuccessfully = true))
        val comment = PortableCommentItem(CommentSnapshot(43, author = "testauthor", text = "A comment about this article.",
            expandedAnchorText = "A comment about this article."), CommentPresentationSnapshot(expanded = true))
        val listener = Proxy.newProxyInstance(CommentsScreenController.Listener::class.java.classLoader,
            arrayOf(CommentsScreenController.Listener::class.java)) { _, _, _ -> null } as CommentsScreenController.Listener
        val controller = CommentsScreenController.create(shouldSmoothScroll = { true }, story = story,
            showWebsite = false, accountUser = null, savedItemState = object : SavedItemStateReader {
                override fun isBookmarked(itemId: Int) = false
                override fun isFavorited(itemId: Int) = false
                override fun isUpvoted(itemId: Int, isComment: Boolean) = false
            }, listener = listener)
        val settings = CommentDisplaySettings.from(app.userSettings.comments, false, false, false, true).copy(canAsk = true)
        controller.updateContent(CommentsScreenState(story = story, integratedWebView = true, comments = listOf(comment), displaySettings = settings))
        try {
            compose.setContent {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(app, scene)) {
                    HarmonicTheme(palette.colorScheme, palette.dark) {
                        CommentsScaffold(controller, reserveUpButtonInset = false, retainedContent = {
                            Box(Modifier.fillMaxSize()) {
                                LazyColumn(Modifier.fillMaxSize().testTag("sheet-comments")) {
                                    item { Text("Original comments header", Modifier.height(100.dp)) }
                                    item { StoryAiSummary(story, settings, {}, onAsk = controller::openPostAsk) }
                                    items(30) { Text("Comment $it", Modifier.height(60.dp)) }
                                }
                                CommentActionOverlay(controller, settings, false, true, TextStyle.Default, {})
                            }
                        })
                    }
                }
            }
            if (fromComment) compose.runOnIdle { controller.showCommentActions(comment) }
            if (fromComment) compose.onAllNodesWithText("Ask", substring = false).onLast().performClick()
            else compose.onNodeWithText("Ask", substring = false).performClick()
            val back = compose.onNodeWithContentDescription(if (fromComment) "Back to comment" else "Back to summary")
            back.assertIsDisplayed()
            compose.waitForIdle()
            evidencePause()
            for (target in listOf(back, compose.onNodeWithTag("ask-transcript"), compose.onNode(hasSetTextAction()))) {
                target.performTouchInput {
                    val start = Offset(center.x, height.toFloat().coerceAtMost(120f) / 2)
                    swipe(start, start + Offset(0f, 300f), durationMillis = 600)
                }
                compose.runOnIdle {
                    assertTrue(controller.askOpen)
                    assertEquals("Ask gestures must leave the sheet expanded", 1f, controller.sheetSlideOffset, .001f)
                }
                evidencePause()
            }
            if (InstrumentationRegistry.getArguments().getString("harmonicCaptureEvidence") == "true") {
                val directory = File(compose.activity.getExternalFilesDir(null), "ask-regression").apply { mkdirs() }
                compose.onRoot().captureToImage().asAndroidBitmap().useBitmap { bitmap ->
                    File(directory, if (fromComment) "comment-ask.png" else "post-ask.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
            back.performClick()
            compose.waitForIdle()
            if (fromComment) {
                compose.runOnIdle { controller.requestDismissCommentActions() }
                compose.waitUntil(5_000) { controller.commentActionOverlay == null }
            }
            compose.waitUntil(5_000) { !controller.sheetGesturesBlocked }
            compose.onNodeWithText("Original comments header").performTouchInput {
                swipe(center, center + Offset(0f, 400f), durationMillis = 600)
            }
            compose.runOnIdle { assertTrue("Normal dragging resumes after Ask and its parent close", controller.sheetSlideOffset < .99f) }
        } finally { network.close(); scope.cancel() }
    }

    private fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }

    // The test clock advances animations much faster than real time. Give a screen recording
    // enough presented frames to show each gesture when evidence capture is explicitly enabled.
    private fun evidencePause() {
        if (InstrumentationRegistry.getArguments().getString("harmonicCaptureEvidence") == "true") Thread.sleep(1_000)
    }
}
