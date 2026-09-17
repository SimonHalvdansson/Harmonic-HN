package com.simon.harmonichackernews.benchmark

import android.os.Bundle
import android.os.Trace
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.data.FileStoryCacheStore
import com.simon.harmonichackernews.data.InMemoryStoryCacheMetadataStore
import com.simon.harmonichackernews.data.PreparedCommentCodec
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryCacheKeys
import com.simon.harmonichackernews.data.StoryCacheRepository
import com.simon.harmonichackernews.network.AlgoliaCommentsParser
import com.simon.harmonichackernews.network.AlgoliaCommentsResponse
import com.simon.harmonichackernews.platform.FileAccessTimeStore
import com.simon.harmonichackernews.presentation.CommentThreadStore
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.utils.CommentSorter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Disk read through initial snapshots, and the extra cost paid when writing network results. */
@RunWith(AndroidJUnit4::class)
class PreparedCommentsBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    private val parser = AlgoliaCommentsParser()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val files = FileStoryCacheStore(
        Path(instrumentation.context.cacheDir.resolve("prepared-comment-benchmark").path),
        FileAccessTimeStore(InMemoryKeyValueStore()),
    )
    private val repository = StoryCacheRepository(files, InMemoryStoryCacheMetadataStore())

    @Test fun rawReadMedium() = read("medium", null)
    @Test fun rawReadLarge() = read("large", null)
    @Test fun protobufReadMedium() = read("medium", PreparedCommentCodec.Encoding.PROTOBUF)
    @Test fun protobufReadLarge() = read("large", PreparedCommentCodec.Encoding.PROTOBUF)
    @Test fun jsonReadMedium() = read("medium", PreparedCommentCodec.Encoding.JSON)
    @Test fun jsonReadLarge() = read("large", PreparedCommentCodec.Encoding.JSON)
    @Test fun rawPrepareAndWriteMedium() = write("medium", null)
    @Test fun rawPrepareAndWriteLarge() = write("large", null)
    @Test fun protobufPrepareAndWriteMedium() = write("medium", PreparedCommentCodec.Encoding.PROTOBUF)
    @Test fun protobufPrepareAndWriteLarge() = write("large", PreparedCommentCodec.Encoding.PROTOBUF)
    @Test fun jsonPrepareAndWriteMedium() = write("medium", PreparedCommentCodec.Encoding.JSON)
    @Test fun jsonPrepareAndWriteLarge() = write("large", PreparedCommentCodec.Encoding.JSON)

    private fun fixture(size: String): String = instrumentation.context
        .createPackageContext(BenchmarkPackageName, 0).assets
        .open("comments_benchmark_fixture_$size.json").bufferedReader().use { it.readText() }

    private fun seed(size: String, encoding: PreparedCommentCodec.Encoding?): Int = runBlocking {
        val raw = fixture(size)
        val prepared = parser.prepare(raw)
        val id = prepared.story.id
        check(repository.storeStory(id, raw, 1_000))
        if (encoding != null) check(files.write(
            StoryCacheKeys.PREPARED_NAMESPACE, "$id.bin", PreparedCommentCodec.encode(prepared, encoding),
        ))
        id
    }

    private fun read(size: String, encoding: PreparedCommentCodec.Encoding?) {
        val id = seed(size, encoding)
        benchmarkRule.measureRepeated {
            runBlocking(Dispatchers.Default) {
                val parsed = if (encoding == null) parser.parse(repository.loadStoryPayload(id))
                else requireNotNull(repository.loadPreparedThread(id)).restore()
                prepareSnapshots(parsed, size)
            }
        }
    }

    private fun write(size: String, encoding: PreparedCommentCodec.Encoding?) {
        val raw = fixture(size)
        benchmarkRule.measureRepeated {
            runBlocking(Dispatchers.Default) {
                val parsed = if (encoding == null) parser.parse(raw) else parser.parsePrepared(raw)
                prepareSnapshots(parsed, size)
                val summary = requireNotNull(parsed.cacheSummary)
                if (encoding == PreparedCommentCodec.Encoding.JSON) {
                    // Keep the same raw/summary write path but select JSON for the sidecar.
                    val rawSummary = summary.withoutPreparedThread()
                    check(repository.storeStory(parsed.id, raw, 1_000, rawSummary))
                    check(files.write(StoryCacheKeys.PREPARED_NAMESPACE, "${parsed.id}.bin",
                        PreparedCommentCodec.encode(requireNotNull(summary.preparedThread), encoding)))
                } else check(repository.storeStory(parsed.id, raw, 1_000, summary))
            }
        }
    }

    private fun prepareSnapshots(parsed: AlgoliaCommentsResponse, size: String) {
        val story = Story()
        parsed.updateStoryInformation(story, 0)
        val store = CommentThreadStore()
        store.reset(story)
        store.replaceParsedComments(story, parsed.comments, CommentSorter.DEFAULT, false)
        check(store.state.value.allComments.size == if (size == "large") 3768 else 700)
    }

    @Test fun reportSizes() = runBlocking {
        for (size in listOf("medium", "large")) {
            val raw = fixture(size)
            val prepared = parser.prepare(raw)
            instrumentation.sendStatus(2, Bundle().apply {
                putString("preparedCacheSizes", "$size raw=${raw.encodeToByteArray().size} " +
                    "protobuf=${PreparedCommentCodec.encode(prepared).size} " +
                    "json=${PreparedCommentCodec.encode(prepared, PreparedCommentCodec.Encoding.JSON).size}")
            })
        }
    }

    /** Separate, untimed workload for all-thread CPU sampling; never use profiled timing results. */
    @Test fun profileLarge() {
        val mode = InstrumentationRegistry.getArguments().getString("prepared.profile.mode") ?: return
        require(mode == "raw" || mode == "protobuf") { "prepared.profile.mode must be raw or protobuf" }
        val id = seed("large", if (mode == "raw") null else PreparedCommentCodec.Encoding.PROTOBUF)
        Thread.sleep(2_000)
        runBlocking(Dispatchers.Default) {
            val end = System.nanoTime() + 15_000_000_000L
            while (System.nanoTime() < end) {
                Trace.beginSection("PreparedCache.$mode")
                try {
                    val parsed = if (mode == "raw") parser.parse(repository.loadStoryPayload(id))
                    else requireNotNull(repository.loadPreparedThread(id)).restore()
                    prepareSnapshots(parsed, "large")
                } finally {
                    Trace.endSection()
                }
            }
        }
    }
}
