package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.*
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
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AskQuestionMorphTest {
    @Test fun starterAndFollowupQuestionsMoveContinuouslyIntoSentBubbles() = checkMorph(1f)
    @Test fun fastMockRepliesDoNotSnapTheDestinationDuringASlowMorph() = checkMorph(5f)

    private fun checkMorph(motionScale: Float) = SwingUtilities.invokeAndWait {
        val app = DesktopHarmonicAppBootstrap.inMemory("AskQuestionMorph")
        app.app.settings.setDebugBoolean(DebugBooleanPreference.MOCK_AI_ANSWERS, true)
        val scheduler = TestCoroutineScheduler()
        val dispatcher = UnconfinedTestDispatcher(scheduler)
        val clock = dispatcher + object : MotionDurationScale { override val scaleFactor = motionScale }
        val scope = CoroutineScope(SupervisorJob() + clock)
        app.scene.navigation.openStory(StoryRoute(42))
        val binding = CommentsFeatureBinding.create(app.app, app.scene,
            checkNotNull(app.scene.navigation.state.value.storyRequest), scope)
        val settings = CommentDisplaySettings.from(app.app.userSettings.comments, false, false, false, false)
            .copy(font = "devicedefault")
        val subject = AskSource.Post(StoryListItemSnapshot(StorySnapshot(42, title = "Test post"),
            StoryPresentationSnapshot()), "Test summary", "Test article")
        val scene = ImageComposeScene(400, 1000, Density(1f), coroutineContext = clock) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(app.app, app.scene)) {
                    val layer = rememberGraphicsLayer()
                    val opening = remember { PostAskState(subject, layer, { Rect.Zero }, { Color.White }) }
                    SideEffect(opening) { binding.controller.openPostAsk(opening) }
                    AskSurface(binding.controller, subject, Rect.Zero, { 1f }, layer, settings, Color.White, {})
                }
            }
        }
        fun frame(name: String? = null) {
            scheduler.advanceTimeBy(16)
            scheduler.runCurrent()
            scene.render(scheduler.currentTime * 1_000_000).use { image ->
                System.getenv("HARMONIC_QUESTION_MORPH_FRAMES")?.let { folder ->
                    if (name != null) image.encodeToData()!!.use { data ->
                        File(folder).mkdirs()
                        File(folder, "${motionScale.toInt()}-$name.png").writeBytes(data.bytes)
                    }
                }
            }
        }
        fun nodes(): List<SemanticsNode> {
            fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        }
        fun tagged(tag: String) = nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        try {
            repeat(220) { frame() }
            for ((index, question) in listOf("What are the practical implications?",
                "What evidence would help evaluate this claim?").withIndex()) {
                val source = tagged("ask-suggestion-$question")
                val start = source.boundsInRoot
                assertTrue(source.config[SemanticsActions.OnClick].action!!.invoke())
                val bounds = mutableListOf<Rect>()
                repeat((50 * motionScale).toInt()) { tick ->
                    frame("$index-${tick.toString().padStart(2, '0')}")
                    nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == "ask-sent-question-$index" }
                        ?.let {
                            val bubble = it.boundsInRoot
                            if (bubble.height > 1f && bubble.width > 1f) bounds += bubble
                            // The first turn stays in place while the follow-up also scrolls the transcript.
                            if (motionScale == 1f && index == 0 && tick >= 12) scene.render(scheduler.currentTime * 1_000_000).use { image ->
                                val pixel = image.toComposeImageBitmap().toPixelMap()[
                                    bubble.center.x.toInt().coerceIn(0, 399),
                                    (bubble.top + 4).toInt().coerceIn(0, 999),
                                ]
                                assertTrue(pixel.red < 0.98f || pixel.green < 0.98f || pixel.blue < 0.98f,
                                    "The sent bubble surface must remain visible during its handoff (frame $tick)")
                            }
                        }
                }
                assertTrue(bounds.distinct().size >= 8, "Sent question must move through intermediate bounds: $bounds")
                assertTrue(bounds.first().top < start.bottom + 5f, "The bubble should begin at the selected suggestion")
                if (motionScale > 1f) bounds.drop(20).zipWithNext().forEach { (before, after) ->
                    assertTrue(kotlin.math.abs(after.top - before.top) < 35f,
                        "Fast replies must not snap the moving question: $before -> $after")
                }
                repeat(420) { frame() }
                assertEquals(1, nodes().count { it.config.getOrNull(SemanticsProperties.TestTag) == "ask-sent-question-$index" })
                assertFalse(nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == "ask-suggestion-$question" })
            }
        } finally { scene.close(); binding.close(); scope.cancel(); app.close() }
    }
}
