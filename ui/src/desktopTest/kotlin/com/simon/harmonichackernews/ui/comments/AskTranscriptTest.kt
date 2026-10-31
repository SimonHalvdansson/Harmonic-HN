package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.settings.DebugBooleanPreference
import com.simon.harmonichackernews.summary.AskSource
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AskTranscriptTest {
    @Test
    fun longConversationFollowsOutputUntilTheUserScrollsBack() = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("AskTranscriptTest")
        bootstrap.app.settings.setDebugBoolean(DebugBooleanPreference.MOCK_AI_ANSWERS, true)
        val scheduler = TestCoroutineScheduler()
        val dispatcher = UnconfinedTestDispatcher(scheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        bootstrap.scene.navigation.openStory(StoryRoute(42))
        val binding = CommentsFeatureBinding.create(bootstrap.app, bootstrap.scene,
            checkNotNull(bootstrap.scene.navigation.state.value.storyRequest), scope)
        val textSize = mutableFloatStateOf(15f)
        val settings = CommentDisplaySettings.from(bootstrap.app.userSettings.comments, false, false, false, false)
            .copy(font = "devicedefault")
        val subject = AskSource.Post(
            StoryListItemSnapshot(StorySnapshot(42, title = "Test post"), StoryPresentationSnapshot()),
            "Test summary", "Test article",
        )
        val scene = ImageComposeScene(400, 800, Density(1f), coroutineContext = dispatcher) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                    AskSurface(binding.controller, subject, Rect.Zero, { 1f }, rememberGraphicsLayer(),
                        settings.copy(preferredTextSize = textSize.floatValue), Color.White, {})
                }
            }
        }
        fun frames(count: Int) = repeat(count) {
            scheduler.advanceTimeBy(16)
            scheduler.runCurrent()
            scene.render(scheduler.currentTime * 1_000_000).close()
        }
        fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        fun allNodes() = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
        fun listNode() = allNodes().first { it.config.getOrNull(SemanticsActions.ScrollToIndex) != null }
        fun hasText(text: String) = allNodes().any { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
        }
        fun send(question: String) {
            val field = allNodes().first { it.config.getOrNull(SemanticsActions.SetText) != null }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(question)))
            frames(2)
            val send = allNodes().first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Send question") == true
            }
            assertTrue(send.config[SemanticsActions.OnClick].action!!.invoke())
        }
        fun assertAtEnd() {
            val range = listNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertEquals(range.maxValue(), range.value(), 0.1f, "New output should remain above the composer")
        }
        try {
            frames(10)
            repeat(4) { index ->
                send("Conversation question $index")
                frames(400)
                assertAtEnd()
            }
            assertTrue(hasText("Conversation question 3"))
            assertFalse(hasText("Conversation question 0"), "Old turns should leave the composed viewport")

            // Wrapping changes must also keep a followed conversation at its end.
            textSize.floatValue = 18f
            frames(10)
            assertAtEnd()

            send("Conversation question 4")
            frames(20)
            assertTrue(listNode().config[SemanticsActions.ScrollBy].action!!.invoke(0f, -10_000f))
            frames(80)
            val readingPosition = listNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue(hasText("Conversation question 0"))
            frames(320)
            assertEquals(readingPosition, listNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.1f,
                "Streaming must not pull the user away from earlier turns")
            assertFalse(hasText("Conversation question 4"))

            send("Conversation question 5")
            frames(400)
            assertAtEnd()
            assertTrue(hasText("Conversation question 5"))
        } finally {
            scene.close()
            binding.close()
            scope.cancel()
            bootstrap.close()
        }
    }
}
