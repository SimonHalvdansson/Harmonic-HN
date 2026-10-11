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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AskTypographyTest {
    @Test
    fun sentQuestionAndAnswerFollowCommentSizeAndLivePreferenceChanges() = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("AskTypographyTest")
        bootstrap.app.settings.setDebugBoolean(DebugBooleanPreference.MOCK_AI_ANSWERS, true)
        val scheduler = TestCoroutineScheduler()
        val dispatcher = UnconfinedTestDispatcher(scheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        bootstrap.scene.navigation.openStory(StoryRoute(42))
        val binding = CommentsFeatureBinding.create(bootstrap.app, bootstrap.scene,
            checkNotNull(bootstrap.scene.navigation.state.value.storyRequest), scope)
        val size = mutableFloatStateOf(15f)
        val settings = CommentDisplaySettings.from(bootstrap.app.userSettings.comments, false, false, false, false)
            .copy(font = "devicedefault")
        val subject = AskSource.Post(
            StoryListItemSnapshot(StorySnapshot(42, title = "Test post"), StoryPresentationSnapshot()),
            "Test summary", "Test article",
        )
        val scene = ImageComposeScene(400, 1000, Density(1f), coroutineContext = dispatcher) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                    AskSurface(binding.controller, subject, Rect.Zero, 1f, rememberGraphicsLayer(),
                        settings.copy(preferredTextSize = size.floatValue), Color.White, {})
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
        fun layouts(text: String): List<TextLayoutResult> = allNodes().flatMap { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.filter { text in it.layoutInput.text.text }
        }
        try {
            frames(50)
            val question = "Explain the main idea in simpler terms"
            val questionButton = allNodes().first {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == question } == true &&
                    it.config.getOrNull(SemanticsActions.OnClick) != null
            }
            assertTrue(questionButton.config[SemanticsActions.OnClick].action!!.invoke())
            frames(400)
            for (preferredSize in listOf(15f, 18f, 12f, 15f)) {
                size.floatValue = preferredSize
                frames(5)
                for (text in listOf(question, "A useful way to understand technical language")) {
                    val actual = layouts(text)
                    assertTrue(actual.isNotEmpty(), "Missing rendered text: $text")
                    actual.forEach { layout ->
                        assertEquals(preferredSize.sp, layout.layoutInput.style.fontSize, text)
                        assertEquals((preferredSize + 2f).sp, layout.layoutInput.style.lineHeight, text)
                    }
                }
            }
        } finally {
            scene.close()
            binding.close()
            scope.cancel()
            bootstrap.close()
        }
    }
}
