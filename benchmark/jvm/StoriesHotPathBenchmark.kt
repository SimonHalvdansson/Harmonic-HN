import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.CachedStoryPreviewResource
import com.simon.harmonichackernews.network.LinkSummary
import com.simon.harmonichackernews.network.PreviewContent
import com.simon.harmonichackernews.network.StoryPreviewResourceRequest
import com.simon.harmonichackernews.network.StoryPreviewResourceRuntime
import com.simon.harmonichackernews.network.StoryPreviewResourceService
import com.simon.harmonichackernews.presentation.HiddenStoryRows
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Alternating isolated-JVM comparison using run-json-access.py --harness this file. */
object StoriesHotPathBenchmark {
    @Volatile private var sink: Any? = null
    private val allocations = (ManagementFactory.getThreadMXBean() as ThreadMXBean).apply {
        isThreadAllocatedMemoryEnabled = true
    }
    private interface Case : AutoCloseable {
        fun run()
        override fun close() = Unit
    }

    private fun create(name: String): Case = when (name) {
        "hidden500" -> object : Case {
            val filter = HiddenStoryRows()
            val hidden = (1..500 step 20).toSet()
            val rows = filter.apply((1..500).map { Story("Story $it", it, true, false) }, hidden)
            override fun run() { sink = filter.apply(rows, hidden) }
        }
        "resolvedPreview30" -> object : Case {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val runtime = StoryPreviewResourceRuntime(scope, object : StoryPreviewResourceService {
                override suspend fun readCached(request: StoryPreviewResourceRequest) =
                    CachedStoryPreviewResource(true, "https://example.com/image", LinkSummary(description = "cached"))
                override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent = error("No network")
            })
            val requests = (1..30).map { StoryPreviewResourceRequest(it, "https://example.com/$it", true, true) }
            init { requests.forEach(runtime::request) }
            override fun run() { requests.forEach { sink = runtime.request(it) } }
            override fun close() { runtime.dispose(); scope.cancel() }
        }
        else -> error("Unknown case: $name")
    }

    @JvmStatic fun main(args: Array<String>) {
        val cases = args.getOrNull(1)?.split(',') ?: listOf("hidden500", "resolvedPreview30")
        val rotation = args[0].toInt()
        for (index in cases.indices) {
            val name = cases[(index + rotation) % cases.size]
            create(name).use { case ->
                val warmupUntil = System.nanoTime() + 1_000_000_000
                do { case.run() } while (System.nanoTime() < warmupUntil)
                val calibration = System.nanoTime()
                repeat(100) { case.run() }
                val count = (200_000_000L * 100 / (System.nanoTime() - calibration)).coerceIn(1, 100_000).toInt()
                val times = DoubleArray(7)
                val bytes = DoubleArray(7)
                for (sample in times.indices) {
                    val allocatedBefore = allocations.getThreadAllocatedBytes(Thread.currentThread().threadId())
                    val start = System.nanoTime()
                    repeat(count) { case.run() }
                    times[sample] = (System.nanoTime() - start) / count.toDouble()
                    bytes[sample] = (allocations.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocatedBefore) / count.toDouble()
                }
                println("""{"case":"$name","operationsPerSample":$count,"nsPerOp":${times.contentToString()},"bytesPerOp":${bytes.contentToString()}}""")
            }
        }
    }
}
