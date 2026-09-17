import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.JSONParser
import com.simon.harmonichackernews.serialization.JsonArray
import com.simon.harmonichackernews.serialization.JsonObject
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.util.Locale

/** Standalone, opt-in JVM microbenchmark. Run through run-json-access.py. */
object JsonAccessBenchmark {
    private val unusualNumbers = arrayOf(
        "1e2", "1.5", "null", "\"+42\"", "\"٤٢\"", "true", "{}", "9223372036854775808",
    )
    private val objects = Array(64) { i ->
        JsonObject("""{"id":${43000000 + i},"time":${1700000000L + i},"value":{"id":$i}}""")
    }
    private val arrays = Array(64) { i -> JsonArray("[${43000000 + i}]") }
    private val fallbacks = Array(64) { i -> JsonObject("""{"value":${unusualNumbers[i and 7]}}""") }
    private val summaries = Array(64) { i ->
        """{"cache_version":1,"id":${43000000 + i},"type":"story","title":"A cached story $i","author":"reader","points":237,"created_at_i":1700000000,"descendants":81,"kids":[43000100,43000101,43000102],"url":"https://example.com/article"}"""
    }
    @Volatile private var sink = 0L
    @Volatile private var objectSink: Any? = null
    private val cases = arrayOf(
        "objectGetInteger", "arrayGetInteger", "objectOptInt", "objectOptLong",
        "objectMismatch", "arrayMismatch", "objectMatch", "cachedStory",
        "objectOptIntFallback", "objectOptLongFallback",
    )

    private fun run(name: String, operations: Int): Long {
        var checksum = 0L
        when (name) {
            "objectGetInteger" -> for (i in 0 until operations) {
                val value = objects[i and 63].get("id")
                objectSink = value // Make boxed results escape, as the public Any? API permits.
                checksum += value as Long
            }
            "arrayGetInteger" -> for (i in 0 until operations) {
                val value = arrays[i and 63].get(0)
                objectSink = value
                checksum += value as Long
            }
            "objectOptInt" -> for (i in 0 until operations) {
                checksum += objects[i and 63].optInt("id", 0)
            }
            "objectOptLong" -> for (i in 0 until operations) {
                checksum += objects[i and 63].optLong("time", 0L)
            }
            "objectOptIntFallback" -> for (i in 0 until operations) {
                checksum += fallbacks[i and 63].optInt("value", 0)
            }
            "objectOptLongFallback" -> for (i in 0 until operations) {
                checksum += fallbacks[i and 63].optLong("value", 0L)
            }
            "objectMismatch" -> for (i in 0 until operations) {
                if (objects[i and 63].optJSONObject("id") == null) checksum++
            }
            "arrayMismatch" -> for (i in 0 until operations) {
                if (objects[i and 63].optJSONArray("id") == null) checksum++
            }
            "objectMatch" -> for (i in 0 until operations) {
                val value = objects[i and 63].optJSONObject("value")
                objectSink = value
                checksum += checkNotNull(value).length()
            }
            "cachedStory" -> for (i in 0 until operations) {
                val story = Story()
                check(JSONParser.updateStoryWithCachedStorySummary(story, summaries[i and 63])) {
                    "Fixture failed to parse"
                }
                objectSink = story
                checksum += story.id + story.score + checkNotNull(story.kids).size
            }
            else -> error("Unknown case: $name")
        }
        sink = checksum
        return checksum
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun main(args: Array<String>) {
        Locale.setDefault(Locale.ROOT)
        val allocation = ManagementFactory.getThreadMXBean() as ThreadMXBean
        allocation.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
        // Each process rotates case order to reduce systematic order effects.
        val rotation = args[0].toInt()
        val selectedCases = args.getOrNull(1)?.split(',') ?: cases.toList()
        for (index in selectedCases.indices) {
            val name = selectedCases[(index + rotation) % selectedCases.size]
            val deadline = System.nanoTime() + 1_000_000_000L
            do { run(name, 1024) } while (System.nanoTime() < deadline)
            var start = System.nanoTime()
            run(name, 8192)
            val elapsed = System.nanoTime() - start
            val operations = (200_000_000L * 8192 / elapsed.coerceAtLeast(1))
                .coerceIn(1024, 50_000_000).toInt()
            val times = DoubleArray(7)
            val bytes = DoubleArray(7)
            for (sample in times.indices) {
                val beforeBytes = allocation.getThreadAllocatedBytes(thread)
                start = System.nanoTime()
                run(name, operations)
                times[sample] = (System.nanoTime() - start) / operations.toDouble()
                bytes[sample] = (allocation.getThreadAllocatedBytes(thread) - beforeBytes) / operations.toDouble()
            }
            println("""{"case":"$name","operationsPerSample":$operations,"nsPerOp":${times.contentToString()},"bytesPerOp":${bytes.contentToString()}}""")
        }
    }
}
