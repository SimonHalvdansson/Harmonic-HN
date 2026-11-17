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
import com.simon.harmonichackernews.platform.Crc32
import com.simon.harmonichackernews.presentation.CommentThreadStore
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.utils.CommentSorter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.CRC32

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
    private val crc32 = Crc32 { bytes, start ->
        CRC32().apply { update(bytes, start, bytes.size - start) }.value.toInt()
    }
    private val repository = StoryCacheRepository(files, InMemoryStoryCacheMetadataStore(), crc32 = crc32)

    @Test fun rawReadMedium() = read("medium", null)
    @Test fun rawReadLarge() = read("large", null)
    @Test fun protobufReadMedium() = read("medium", PreparedCommentCodec.Encoding.PROTOBUF)
    @Test fun protobufReadLarge() = read("large", PreparedCommentCodec.Encoding.PROTOBUF)
    @Test fun protobufReadSmall() = read("small", PreparedCommentCodec.Encoding.PROTOBUF)
    @Test fun protobufRead2000() = read("2000", PreparedCommentCodec.Encoding.PROTOBUF)
    @Test fun flatbuffersReadSmall() = read("small", PreparedCommentCodec.Encoding.FLATBUFFERS)
    @Test fun flatbuffersReadMedium() = read("medium", PreparedCommentCodec.Encoding.FLATBUFFERS)
    @Test fun flatbuffersRead2000() = read("2000", PreparedCommentCodec.Encoding.FLATBUFFERS)
    @Test fun flatbuffersReadLarge() = read("large", PreparedCommentCodec.Encoding.FLATBUFFERS)
    @Test fun jsonReadMedium() = read("medium", PreparedCommentCodec.Encoding.JSON)
    @Test fun jsonReadLarge() = read("large", PreparedCommentCodec.Encoding.JSON)
    @Test fun rawPrepareAndWriteMedium() = write("medium", null)
    @Test fun rawPrepareAndWriteLarge() = write("large", null)
    @Test fun protobufPrepareAndWriteMedium() = write("medium", PreparedCommentCodec.Encoding.PROTOBUF)
    @Test fun protobufPrepareAndWriteLarge() = write("large", PreparedCommentCodec.Encoding.PROTOBUF)
    @Test fun flatbuffersPrepareAndWriteMedium() = write("medium", PreparedCommentCodec.Encoding.FLATBUFFERS)
    @Test fun flatbuffersPrepareAndWriteLarge() = write("large", PreparedCommentCodec.Encoding.FLATBUFFERS)
    @Test fun jsonPrepareAndWriteMedium() = write("medium", PreparedCommentCodec.Encoding.JSON)
    @Test fun jsonPrepareAndWriteLarge() = write("large", PreparedCommentCodec.Encoding.JSON)

    private fun fixture(size: String): String {
        val suffix = when (size) { "small" -> ""; "2000" -> "_large"; else -> "_$size" }
        val raw = instrumentation.context.createPackageContext(BenchmarkPackageName, 0).assets
            .open("comments_benchmark_fixture$suffix.json").bufferedReader().use { it.readText() }
        if (size != "2000") return raw
        // Derive a fixed real-content prefix outside timing; avoid another duplicated 1 MB asset.
        val root = JSONObject(raw)
        var remaining = 2000
        fun trim(node: JSONObject) {
            val children = node.optJSONArray("children") ?: JSONArray()
            val kept = JSONArray()
            for (index in 0 until children.length()) {
                if (remaining == 0) break
                val child = children.getJSONObject(index)
                val text = child.optString("text").trim()
                if (text.isEmpty() || text == "null") continue
                remaining--
                trim(child)
                kept.put(child)
            }
            node.put("children", kept)
        }
        trim(root)
        check(remaining == 0)
        return root.put("descendants", 2000).toString()
    }

    private fun seed(size: String, encoding: PreparedCommentCodec.Encoding?): Int = runBlocking {
        val raw = fixture(size)
        val prepared = parser.prepare(raw)
        val id = prepared.story.id
        check(repository.storeStory(id, raw, 1_000))
        if (encoding != null) check(files.write(
            StoryCacheKeys.PREPARED_NAMESPACE, "$id.bin", PreparedCommentCodec.encode(prepared, encoding, crc32),
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
                if (encoding != null) {
                    // Keep the same raw/summary write path while selecting the sidecar encoding.
                    val rawSummary = summary.withoutPreparedThread()
                    check(repository.storeStory(parsed.id, raw, 1_000, rawSummary))
                    check(files.write(StoryCacheKeys.PREPARED_NAMESPACE, "${parsed.id}.bin",
                        PreparedCommentCodec.encode(requireNotNull(summary.preparedThread), encoding, crc32)))
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
        val expected = when (size) { "small" -> 29; "medium" -> 700; "2000" -> 2001; else -> 3768 }
        check(store.state.value.allComments.size == expected)
    }

    @Test fun reportSizes() = runBlocking {
        for (size in listOf("small", "medium", "2000", "large")) {
            val raw = fixture(size)
            val prepared = parser.prepare(raw)
            instrumentation.sendStatus(2, Bundle().apply {
                putString("preparedCacheSizes", "$size raw=${raw.encodeToByteArray().size} " +
                    "protobuf=${PreparedCommentCodec.encode(prepared, PreparedCommentCodec.Encoding.PROTOBUF).size} " +
                    "flatbuffers=${PreparedCommentCodec.encode(prepared).size} " +
                    "json=${PreparedCommentCodec.encode(prepared, PreparedCommentCodec.Encoding.JSON).size}")
            })
        }
    }

    /** Separate, untimed workload for all-thread CPU sampling; never use profiled timing results. */
    @Test fun profileLarge() {
        val mode = InstrumentationRegistry.getArguments().getString("prepared.profile.mode") ?: return
        val encoding = when (mode) {
            "raw" -> null
            "protobuf" -> PreparedCommentCodec.Encoding.PROTOBUF
            "flatbuffers" -> PreparedCommentCodec.Encoding.FLATBUFFERS
            else -> error("prepared.profile.mode must be raw, protobuf or flatbuffers")
        }
        val id = seed("large", encoding)
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
