import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.*
import com.simon.harmonichackernews.network.*
import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.settings.*
import com.simon.harmonichackernews.ui.stories.StoriesScreenController
import com.sun.management.ThreadMXBean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * Opt-in comparison harness for run-json-access.py --harness PerformanceReviewBenchmark.kt.
 * Requires the UI desktop runtime classpath as well as core. Run from the repository root.
 * Counts use a deterministic cache service; no live network or device state is touched.
 * Reflection selects the equivalent production scheduling path on the pre-change binary.
 */
object PerformanceReviewBenchmark {
    private val allocations = (ManagementFactory.getThreadMXBean() as ThreadMXBean).apply {
        isThreadAllocatedMemoryEnabled = true
    }
    @Volatile private var sink: Any? = null
    private val cases = listOf("toggle500", "toggle3767", "feed500", "previewState2000",
        "previewWindow500", "previewCacheOverflow", "refresh3767")
    private fun allocated() = allocations.getThreadAllocatedBytes(Thread.currentThread().threadId())

    private interface Case : AutoCloseable {
        fun run()
        fun extra(): String = ""
        override fun close() = Unit
    }

    private fun story(id: Int) = Story("Story $id", id, true, false).apply {
        url = "https://example.com/$id"; loaded = true; isLink = true
    }
    private fun comments(count: Int) = List(count) { index -> Comment().apply {
        id = index + 1
        val position = index % 10
        parent = if (position == 0) -1 else index - position + 1
        depth = if (position == 0) 0 else 1
        by = "reader"; text = "Comment $index"; expanded = true; time = index
    } }
    private fun controller(): StoriesScreenController {
        val listener = Proxy.newProxyInstance(StoriesScreenController.Listener::class.java.classLoader,
            arrayOf(StoriesScreenController.Listener::class.java)) { _, _, _ -> null }
            as StoriesScreenController.Listener
        val saved = object : SavedItemStateReader {
            override fun isBookmarked(itemId: Int) = false
            override fun isFavorited(itemId: Int) = false
            override fun isUpvoted(itemId: Int, isComment: Boolean) = false
        }
        return StoriesScreenController.create(96, saved, listener)
    }
    private val service = object : StoryPreviewResourceService {
        override suspend fun readCached(request: StoryPreviewResourceRequest) =
            CachedStoryPreviewResource(true, "https://example.com/image", LinkSummary(description = "cached"))
        override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent = error("Unexpected network")
    }
    private fun create(name: String): Case = when {
        name.startsWith("toggle") -> object : Case {
            val count = name.removePrefix("toggle").toInt()
            val store = CommentThreadStore().apply {
                reset(story(10_000))
                appendLoadedComments(story(10_000), comments(count), "Default", false)
            }
            override fun run() { store.toggleExpanded(1); sink = store.state.value }
        }
        name == "feed500" -> object : Case {
            val store = StoryListStore().apply { replace((1..500).map(::story)) }
            val controller = controller().apply {
                updateContent(StoriesState(mainList = store.state.value))
                (1..250).forEach { updateStoryItemHeight(it, 96) }
            }
            var read = false
            override fun run() {
                read = !read
                store.markRead(250, read)
                controller.updateContent(StoriesState(mainList = store.state.value))
                sink = controller.mainStories
            }
        }
        name == "previewState2000" -> object : Case {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val runtime = StoryPreviewResourceRuntime(scope, service)
            val retain = runtime.javaClass.methods.firstOrNull { it.name == "retainStories" }
            val controller = controller()
            val list = (1901..2000).map { StoryListItemSnapshot(story(it).toSnapshot(), story(it).presentationSnapshot()) }
            var tint = 0
            init {
                for (batch in 0 until 20) {
                    val ids = (batch * 100 + 1..batch * 100 + 100).toSet()
                    retain?.invoke(runtime, ids)
                    ids.forEach { runtime.request(StoryPreviewResourceRequest(it, "https://example.com/$it", true, true)) }
                }
                controller.updateContent(StoriesState(mainList = PortableStoryListState(items = list), previewResources = runtime.states.value))
                list.forEach { controller.previewResource(it.id) }
            }
            override fun run() {
                runtime.recordTint(1950, "https://example.com/1950", StoryResourceTintKind.FAVICON,
                    StoryResourceTintState("icon", 0, "default", tint++))
                controller.updateContent(StoriesState(mainList = PortableStoryListState(items = list), previewResources = runtime.states.value))
                sink = controller.previewResource(1950)
            }
            override fun extra() = ",\"retainedEntries\":${runtime.states.value.size}"
            override fun close() { runtime.dispose(); scope.cancel() }
        }
        name == "previewWindow500" -> object : Case {
            val stories = (1..500).map(::story)
            val settings = StoryDisplaySettings(true, false, true, true, false, true,
                StoryPreviewMode.OFF, false, false, 16f, true, false, false, DisplayStyle.RAISED,
                false, "default", true, 0, "", "default", 16f)
            val open = StoryListResourceRuntime::class.java.methods.firstOrNull { it.name == "openDialog" }
            var requests = 0
            override fun run() {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
                val runtime = StoryListResourceRuntime(scope, service, settings)
                if (open != null) open.invoke(runtime, stories, 250)
                else stories.forEach(runtime::requestForDialog)
                requests = runtime.states().size
                sink = runtime.states()
                runtime.dispose(); scope.cancel()
            }
            override fun extra() = ",\"requestsPerOpen\":$requests"
        }
        name == "previewCacheOverflow" -> object : Case {
            var fetches = 0
            override fun run() = runBlocking {
                val coordinator = PreviewContentCoordinator(this)
                suspend fun load(id: Int) = coordinator.load("page$id", true, false) {
                    fetches++; LinkSummary(imageUrl = "image$id", description = "summary$id")
                }
                for (id in 0 until 300) load(id)
                fetches = 0
                load(300)
                for (id in 1 until 300) sink = load(id)
            }
            override fun extra() = ",\"fetchesAfterPriming\":$fetches"
        }
        name == "refresh3767" -> RefreshCase()
        else -> error(name)
    }

    /** Times owner-dispatcher runnable execution, excluding worker time and queue waits. */
    private class OwnerDispatcher : CoroutineDispatcher(), AutoCloseable {
        val executor = Executors.newSingleThreadExecutor()
        var enabled = false
        var nanos = 0L
        var bytes = 0L
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            executor.execute {
                val recording = enabled
                val start = if (recording) System.nanoTime() else 0L
                val before = if (recording) allocated() else 0L
                try { block.run() } finally {
                    if (recording) {
                        nanos += System.nanoTime() - start
                        bytes += allocated() - before
                    }
                }
            }
        }
        fun start() { executor.submit { nanos = 0; bytes = 0; enabled = true }.get() }
        fun stop(): Pair<Long, Long> = executor.submit<Pair<Long, Long>> {
            enabled = false; nanos to bytes
        }.get()
        override fun close() { executor.shutdown() }
    }

    private class RefreshCase : Case {
        val owner = OwnerDispatcher()
        val scope = CoroutineScope(SupervisorJob() + owner)
        val response = File("app/src/benchmark/assets/comments_benchmark_fixture_large.json").readText()
        val algolia = object : AlgoliaRepository {
            override suspend fun getSubmissions(userName: String, pageSize: Int, type: AlgoliaSubmissionType,
                cursor: AlgoliaSubmissionsCursor): AlgoliaSubmissionsPage = error("Unused")
            override suspend fun search(url: String): AlgoliaSearchPage = error("Unused")
            override suspend fun getItemJson(id: Int) = response
        }
        val unusedOfficial = object : HackerNewsRepository {
            override suspend fun getStory(id: Int): Story? = error("Unused")
            override suspend fun getComment(id: Int): Comment? = error("Unused")
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unused")
        }
        val preloads = CommentsPreloadRepository(algolia, nowMillis = { 0L })
        val parsed = runBlocking { AlgoliaCommentsParser().parseForDisplay(response) }
        val story = story(41002195).apply { kids = parsed.comments.filter { it.depth == 0 }.map { it.id }.toIntArray() }
        val presenter = CommentsPresenter(scope, CommentsSessionState(),
            CommentThreadRepository(algolia, unusedOfficial, preloads = preloads),
            object : PollOptionsLoader {
                override suspend fun findOptionIds(storyId: Int): IntArray = error("Unused")
                override fun placeholders(optionIds: IntArray): List<PollOption> = error("Unused")
                override fun loadOptions(optionIds: IntArray): kotlinx.coroutines.flow.Flow<PollOption> = error("Unused")
            },
            SavedItemActionUseCase(SavedItemsRepository(InMemoryKeyValueStore()), { 0L },
                { _, _ -> error("Unused") }, { _, _ -> error("Unused") }),
            object : HackerNewsVotingService {
                override suspend fun vote(itemId: String, direction: String): HackerNewsActionResult = error("Unused")
            }, threadPreparationDispatcher = Dispatchers.Default)
        var ownerNanos = 0L
        var ownerBytes = 0L
        var wallNanos = 0L
        init {
            runBlocking { withContext(owner) {
                presenter.thread.reset(story)
                presenter.thread.appendLoadedComments(story, parsed.comments, "Default", false)
            } }
        }
        override fun run() = runBlocking {
            // Supply fresh prepared network data through the real repository handoff, outside
            // measurement. Measure refresh application, not parsing/network/cache I/O.
            preloads.preload(story.id, story.kids!!.toList())
            val complete = scope.async(start = CoroutineStart.UNDISPATCHED) {
                presenter.effects.filterIsInstance<CommentsPresenterEffect.ThreadApplied>().first()
            }
            owner.start()
            val started = System.nanoTime()
            withContext(owner) { presenter.dispatch(CommentsAction.LoadThread(story, true, emptySet(),
                "Default", false, null, false)) }
            withTimeout(30_000) { complete.await() }
            val result = owner.stop()
            wallNanos = System.nanoTime() - started
            ownerNanos = result.first
            ownerBytes = result.second
            sink = presenter.thread.state.value
        }
        override fun close() { scope.cancel(); owner.stop(); owner.close() }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val rotation = args[0].toInt()
        val selected = args.getOrNull(1)?.split(',') ?: cases
        for (index in selected.indices) {
            val name = selected[(index + rotation) % selected.size]
            create(name).use { case ->
                val deadline = System.nanoTime() + 1_000_000_000L
                do { case.run() } while (System.nanoTime() < deadline)
                val calibration = System.nanoTime()
                repeat(8) { case.run() }
                val count = (200_000_000L * 8 / (System.nanoTime() - calibration)).coerceIn(1, 100_000).toInt()
                val times = DoubleArray(7)
                val bytes = DoubleArray(7)
                val walls = DoubleArray(7)
                for (sample in times.indices) {
                    val before = allocated()
                    val start = System.nanoTime()
                    var ownerTime = 0L; var ownerBytes = 0L; var wall = 0L
                    repeat(count) {
                        case.run()
                        if (case is RefreshCase) {
                            ownerTime += case.ownerNanos; ownerBytes += case.ownerBytes; wall += case.wallNanos
                        }
                    }
                    times[sample] = (if (case is RefreshCase) ownerTime else System.nanoTime() - start) / count.toDouble()
                    bytes[sample] = (if (case is RefreshCase) ownerBytes else allocated() - before) / count.toDouble()
                    walls[sample] = wall / count.toDouble()
                }
                val extra = if (case is RefreshCase) ",\"wallNsPerOp\":${walls.contentToString()}" else case.extra()
                println("""{"case":"$name","operationsPerSample":$count,"nsPerOp":${times.contentToString()},"bytesPerOp":${bytes.contentToString()}$extra}""")
            }
        }
    }
}
