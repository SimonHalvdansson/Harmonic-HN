import com.simon.harmonichackernews.network.NitterInstance
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.StoredSettingsMutator
import com.simon.harmonichackernews.settings.StoredUserSettings
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.util.Locale

/** Production before/after jars; run with run-json-access.py --harness <this file>. */
object StartupSettingsBenchmark {
    // Include strings loaded from storage, not just an interned constant.
    private val defaults = Array(64) { String("https://nitter.net".toCharArray()) }
    private val custom = Array(64) { i -> "https://nitter$i.example.org" }
    private val stores = Array(2) { i ->
        InMemoryKeyValueStore().apply {
            if (i == 1) putString("pref_nitter_instance_url", custom[0])
        }
    }
    private val readers = Array(2) { i ->
        StoredUserSettings(stores[i], stores[i].changes, { "material_light" }, false, true)
    }
    private val repositories = Array(2) { i ->
        AppSettingsRepository(readers[i], StoredSettingsMutator(stores[i]))
    }
    @Volatile private var objectSink: Any? = null
    @Volatile private var sink = 0L
    private val cases = arrayOf(
        "defaultUrl", "customUrl", "readingDefaults", "readingCustom",
        "snapshotDefaults", "snapshotCustom",
    )

    init {
        val inputs = arrayOf(
            defaults[0], " https://nitter.net/ ", "HTTPS://NITTER.NET", "https://nitter.net:443",
            custom[0], "http://localhost:8080/", "https://nitter.net.evil.org",
            "https://nitter.net/path", "https://user:password@nitter.net", "https://",
            "https://nitter.net?x=1", "https://nitter.net#fragment", "https://nitter.net:99999",
            "https://bad host.org", "https://nitter.net\\path", "ftp://nitter.net", "",
        )
        for (value in inputs) {
            val expected = NitterInstance.normalize(value) ?: NitterInstance.DEFAULT_URL
            check(expected == NitterInstance.effectiveUrl(value)) { "Normalization changed: $value" }
        }
        for (i in readers.indices) {
            val expected = if (i == 0) NitterInstance.DEFAULT_URL else custom[0]
            check(expected == repositories[i].snapshot().reading.nitterInstanceUrl) { "Settings fixture failed" }
        }
    }

    private fun run(name: String, operations: Int) {
        var checksum = 0L
        when (name) {
            "defaultUrl", "customUrl" -> {
                val values = if (name == "defaultUrl") defaults else custom
                for (i in 0 until operations) {
                    val value = NitterInstance.effectiveUrl(values[i and 63])
                    objectSink = value
                    checksum += value.length
                }
            }
            "readingDefaults", "readingCustom" -> {
                val reader = readers[if (name == "readingDefaults") 0 else 1]
                for (i in 0 until operations) {
                    val value = reader.reading
                    objectSink = value
                    checksum += value.nitterInstanceUrl.length
                }
            }
            "snapshotDefaults", "snapshotCustom" -> {
                val repository = repositories[if (name == "snapshotDefaults") 0 else 1]
                for (i in 0 until operations) {
                    val value = repository.snapshot()
                    objectSink = value
                    checksum += value.reading.nitterInstanceUrl.length
                }
            }
            else -> error("Unknown case: $name")
        }
        sink = checksum
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun main(args: Array<String>) {
        Locale.setDefault(Locale.ROOT)
        val allocation = ManagementFactory.getThreadMXBean() as ThreadMXBean
        allocation.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
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
