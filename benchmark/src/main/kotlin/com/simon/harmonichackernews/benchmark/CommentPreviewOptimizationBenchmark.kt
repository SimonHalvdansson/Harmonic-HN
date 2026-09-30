package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.*
import com.simon.harmonichackernews.network.*
import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Identical local workloads for before/after CPU and allocation comparisons on Android. */
@RunWith(AndroidJUnit4::class)
class CommentPreviewOptimizationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var sink: Any? = null

    @Test fun referenceLinksTypical() = referenceLinks(6)
    @Test fun referenceLinksLong() = referenceLinks(60)

    private fun referenceLinks(paragraphs: Int) {
        val html = buildString {
            append("<p>Discussion with <a href='https://example.com/inline'>an inline link</a>.</p>")
            repeat(paragraphs) { append("<p>Paragraph $it has <b>formatted prose</b>, whitespace &amp; context.</p>") }
            append("<p>[1] <a href='https://example.com/source'>Source</a></p>")
            append("<p>[2] <a href='https://example.org/other'>Other source</a></p>")
            append("<p>[3] An explanatory footnote remains visible.</p>")
        }
        check(CollectedReferenceLinks.parse(html).links.size == 2)
        benchmarkRule.measureRepeated { sink = CollectedReferenceLinks.parse(html) }
    }

    @Test fun previewTint250() = previewTint(250)
    @Test fun previewTint1000() = previewTint(1000)

    private fun previewTint(count: Int) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val service = object : StoryPreviewResourceService {
            override suspend fun readCached(request: StoryPreviewResourceRequest): CachedStoryPreviewResource = error("Unused")
            override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent = error("Unused")
        }
        val runtime = StoryPreviewResourceRuntime(scope, service)
        val url = "https://example.com/page"
        val tint = StoryResourceTintState("https://example.com/icon", 0, "default", 0)
        repeat(count) { runtime.recordTint(it + 1, url, StoryResourceTintKind.FAVICON, tint) }
        var color = 0
        try {
            benchmarkRule.measureRepeated {
                runtime.recordTint(count / 2, url, StoryResourceTintKind.FAVICON, tint.copy(tintColorArgb = ++color))
                sink = runtime.states.value
            }
        } finally {
            runtime.dispose()
            scope.cancel()
        }
    }

    @Test fun cachedOpenAndUnchangedRevalidation3767() {
        val response = InstrumentationRegistry.getInstrumentation().context
            .createPackageContext(BenchmarkPackageName, 0).assets
            .open("comments_benchmark_fixture_large.json").bufferedReader().use { it.readText() }
        // Keep all pure preparation on the measured thread so allocation counts include it.
        val parser = AlgoliaCommentsParser(parsingDispatcher = Dispatchers.Unconfined)
        val prepared = runBlocking { parser.prepare(response) }
        val roots = prepared.comments.filter { it.depth == 0 }.map { it.id }
        val cached = prepared.copy(rankedIds = roots)
        val algolia = object : AlgoliaRepository {
            override suspend fun getItemJson(id: Int) = response
            override suspend fun getSubmissions(userName: String, pageSize: Int, type: AlgoliaSubmissionType,
                cursor: AlgoliaSubmissionsCursor): AlgoliaSubmissionsPage = error("Unused")
            override suspend fun search(url: String): AlgoliaSearchPage = error("Unused")
        }
        val official = object : HackerNewsRepository {
            override suspend fun getStory(id: Int): Story? = error("Unused")
            override suspend fun getComment(id: Int): Comment? = error("Unused")
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unused")
        }
        benchmarkRule.measureRepeated {
            val scope = runWithTimingDisabled { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }
            val story = runWithTimingDisabled {
                Story("Cached", prepared.story.id, true, false).apply { kids = roots.toIntArray() }
            }
            val presenter = runWithTimingDisabled {
                CommentsPresenter(scope, CommentsSessionState(),
                    CommentThreadRepository(algolia, official, parser, requestDispatcher = Dispatchers.Unconfined),
                    object : PollOptionsLoader {
                        override suspend fun findOptionIds(storyId: Int): IntArray = error("Unused")
                        override fun placeholders(optionIds: IntArray): List<PollOption> = error("Unused")
                        override fun loadOptions(optionIds: IntArray): kotlinx.coroutines.flow.Flow<PollOption> = error("Unused")
                    },
                    SavedItemActionUseCase(SavedItemsRepository(InMemoryKeyValueStore()), { 0L },
                        { _, _ -> error("Unused") }, { _, _ -> error("Unused") }),
                    object : HackerNewsVotingService {
                        override suspend fun vote(itemId: String, direction: String): HackerNewsActionResult = error("Unused")
                    }, threadPreparationDispatcher = Dispatchers.Unconfined,
                ).also { it.thread.reset(story) }
            }
            runBlocking {
                val completed = async(start = CoroutineStart.UNDISPATCHED) {
                    presenter.effects.filterIsInstance<CommentsPresenterEffect.ThreadApplied>().first { it.networkCompleted }
                }
                presenter.dispatch(CommentsAction.LoadThread(
                    story, true, emptySet(), "Default", false, null, false,
                    loadPreparedThread = { cached },
                ))
                check(!completed.await().contentApplied)
                sink = presenter.thread.state.value
            }
            runWithTimingDisabled {
                check(presenter.thread.state.value.allComments.size == 3768)
                scope.cancel()
            }
        }
    }
}
