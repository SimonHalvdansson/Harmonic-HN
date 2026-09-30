package com.simon.harmonichackernews.benchmark

import android.os.Bundle
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.MainNavigationSnapshot
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.network.AlgoliaCommentsParser
import com.simon.harmonichackernews.network.CommentThreadLoadResult
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.OfficialCommentThreadLoader
import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.utils.CommentSorter
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Repeatable production-path comparisons; all content is local and no user data is modified. */
@RunWith(AndroidJUnit4::class)
class RetentionAndPreparationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val parser = AlgoliaCommentsParser()
    private var sink: Any? = null

    private fun fixture(): String = instrumentation.context
        .createPackageContext(BenchmarkPackageName, 0).assets
        .open("comments_benchmark_fixture_large.json").bufferedReader().use { it.readText() }

    @Test fun freshCommentPreparationLarge() {
        val raw = fixture()
        benchmarkRule.measureRepeated {
            sink = runBlocking { parser.parseForDisplay(raw) }
        }
    }

    @Test fun officialWideThread() {
        // 128 roots with eight children each. Suspension exercises the request queue without
        // network latency. The production loader must preserve preorder, regardless of scheduling.
        val repository = object : HackerNewsRepository {
            override suspend fun getStory(id: Int) = Story().apply {
                this.id = id
                kids = IntArray(128) { it + 1 }
            }
            override suspend fun getStoryIds(type: StoryType) = emptyList<Int>()
            override suspend fun getComment(id: Int): Comment {
                yield()
                return Comment().apply {
                    this.id = id
                    by = "reader"
                    text = "A local comment for allocation and scheduling measurements."
                    if (id <= 128) kidsIds = IntArray(8) { 1000 + (id - 1) * 8 + it }
                    else parent = (id - 1000) / 8 + 1
                }
            }
        }
        val loader = OfficialCommentThreadLoader(repository)
        benchmarkRule.measureRepeated {
            val result = runBlocking { loader.load(9999, emptySet(), false) }
            check(result is CommentThreadLoadResult.Official && result.comments.size == 1152)
            sink = result
        }
    }

    @Test fun repeatedReaderThemeCommand() {
        val driver = RecordingDriver()
        val controller = WebContentController(WebContentService().createRuntime(), driver)
        // Same-size ASCII payload as the bundled JetBrains Mono pair; no font decoding is timed.
        val theme = ReaderModeTheme(true, "#fff", "#000", "#000", "#333", "#00f", "#aaa",
            "#eee", fontFaceCss = "a".repeat(460188), font = "jetbrainsmono", fontSizePx = 16)
        controller.evaluateReaderMode("/* installed reader */", theme, false) {}
        benchmarkRule.measureRepeated {
            controller.evaluateReaderMode("/* installed reader */", theme, false) {}
        }
        instrumentation.sendStatus(2, Bundle().apply {
            putString("readerCommandCharacters", driver.characters.toString())
        })
    }

    @Test fun closedThreadRetention() {
        val registry = ScreenSessionRegistry()
        val references = runBlocking { retainThread(registry, fixture()) }
        // Reflection lets the identical benchmark source run against the pre-optimization build.
        registry.javaClass.methods.firstOrNull {
            it.name == "navigationChanged" && it.parameterTypes.contentEquals(arrayOf(MainNavigationSnapshot::class.java))
        }?.invoke(registry, MainNavigationStore().state.value)
        repeat(5) {
            Runtime.getRuntime().gc()
            Thread.sleep(100)
        }
        val retained = references.count { it.get() != null }
        sink = registry // Keep the scene alive through the reachability measurement.
        instrumentation.sendStatus(2, Bundle().apply {
            putString("closedThreadRetainedComments", "$retained/${references.size}")
        })
    }

    private suspend fun retainThread(registry: ScreenSessionRegistry, raw: String): List<WeakReference<Comment>> {
        val parsed = parser.parseForDisplay(raw)
        val session = registry.commentsStateFor(1, parsed.id)
        val story = Story().apply { id = parsed.id }
        session.commentThread.reset(story)
        session.commentThread.replaceParsedComments(story, parsed.comments, CommentSorter.DEFAULT, false)
        return parsed.comments.map(::WeakReference)
    }

    private class RecordingDriver : WebContentDriver {
        override val state = MutableStateFlow(WebContentDriverState())
        var characters = 0
        override fun load(url: String) = Unit
        override fun reload() = Unit
        override fun goBack() = false
        override fun readPageText(onResult: (String?) -> Unit) = onResult(null)
        override fun evaluateJavaScript(script: String, onResult: (String?) -> Unit) {
            characters = script.length
            onResult("\"disabled\"")
        }
    }
}
