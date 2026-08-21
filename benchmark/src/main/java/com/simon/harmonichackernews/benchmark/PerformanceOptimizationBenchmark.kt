package com.simon.harmonichackernews.benchmark

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.data.SavedItemCodec
import com.simon.harmonichackernews.data.StoryResourceTintRepository
import com.simon.harmonichackernews.network.HtmlDescriptionExtractor
import com.simon.harmonichackernews.network.LinkSummaryParser
import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.network.StoryResourceTintState
import com.simon.harmonichackernews.settings.KeyValueStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Focused device benchmarks for independently profiled production paths. */
@RunWith(AndroidJUnit4::class)
class PerformanceOptimizationBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val metadataHtml = buildString {
        append("<html lang=\"en\"><head>")
        append("<meta property=\"og:title\" content=\"A benchmark article\">")
        append("<meta property=\"og:site_name\" content=\"Example\">")
        append("<meta name=\"author\" content=\"Ada Lovelace\">")
        append("<meta property=\"article:published_time\" content=\"2026-08-21\">")
        append("<meta name=\"description\" content=\"A realistic article description with enough words to pass the quality checks.\">")
        append("<meta property=\"og:image\" content=\"/preview.webp\">")
        append("</head><body>")
        repeat(120) { index ->
            append("<div class=\"navigation-$index\"><span>Unrelated page chrome $index</span></div>")
        }
        append("<article><p>The useful body paragraph is deliberately long enough to be selected as a fallback description.</p></article>")
        append("</body></html>")
    }
    private val fallbackDocument = Ksoup.parse(buildString {
        append("<html><body><main>")
        repeat(80) { index ->
            append("<p>Paragraph $index contains useful explanatory prose, punctuation, and enough words to qualify as meaningful article content.</p>")
        }
        append("</main></body></html>")
    })
    private val savedItems = (1..500).joinToString("-") { id -> "${id}q${1_700_000_000_000L + id}" }
    private val snapshotMap = (1..100).associateWith { id -> "resource-$id" }
    private val snapshotList = (1..30).map { "type-$it" }
    private val tint = StoryResourceTintState(
        sourceUrl = "https://example.com/favicon.png",
        baseColorArgb = 0xfff8f7f4.toInt(),
        paletteConfigKey = "default|100|100|100",
        tintColorArgb = 0xff336699.toInt(),
    )
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val cachedConnectivity = CachedConnectivity(
        online = currentNetworkCapabilities()
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
        unmetered = currentNetworkCapabilities()
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
    )

    @Test
    fun linkSummaryMetadataExtraction() = benchmarkRule.measureRepeated {
        check(
            LinkSummaryParser.extract(
                html = metadataHtml,
                fallbackTitle = "Fallback",
                contentType = "text/html",
                finalUrl = "https://example.com/articles/benchmark",
            ).author == "Ada Lovelace",
        )
    }

    @Test
    fun htmlDescriptionFallbackSelection() = benchmarkRule.measureRepeated {
        check(
            HtmlDescriptionExtractor.chooseDescription(
                metadataDescription = null,
                document = fallbackDocument,
                pageTitle = "Benchmark",
                fallbackTitle = null,
            ).isNotEmpty(),
        )
    }

    @Test
    fun savedItemDecode500() = benchmarkRule.measureRepeated {
        check(SavedItemCodec.decode(savedItems, sortedByCreated = true).size == 500)
    }

    @Test
    fun immutableStateCollectionCopies() = benchmarkRule.measureRepeated {
        check(snapshotMap.toMap().size + snapshotList.toList().size == 130)
    }

    @Test
    fun immutableStateCollectionReuse() = benchmarkRule.measureRepeated {
        check(snapshotMap.size + snapshotList.size == 130)
    }

    @Test
    fun sharedPreferencesTintWrite() {
        val preferences = context.getSharedPreferences(
            "performance_optimization_tint_write",
            Context.MODE_PRIVATE,
        )
        preferences.edit().clear().commit()
        val repository = StoryResourceTintRepository(
            BenchmarkSharedPreferencesStore(preferences),
        )
        benchmarkRule.measureRepeated {
            repository.write(123, StoryResourceTintKind.FAVICON, tint)
        }
    }

    @Test
    fun connectivityManagerQuery() = benchmarkRule.measureRepeated {
        val capabilities = currentNetworkCapabilities()
        check(
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true ||
                capabilities == null ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        )
    }

    @Test
    fun cachedConnectivityRead() = benchmarkRule.measureRepeated {
        check(
            cachedConnectivity.online || !cachedConnectivity.online ||
                cachedConnectivity.unmetered || !cachedConnectivity.unmetered,
        )
    }

    private fun currentNetworkCapabilities(): NetworkCapabilities? =
        connectivityManager.activeNetwork?.let(connectivityManager::getNetworkCapabilities)
}

private class CachedConnectivity(online: Boolean, unmetered: Boolean) {
    @Volatile
    var online = online

    @Volatile
    var unmetered = unmetered
}

private class BenchmarkSharedPreferencesStore(
    private val preferences: SharedPreferences,
) : KeyValueStore {
    override fun clear() = preferences.edit().clear().apply()
    override fun contains(key: String): Boolean = preferences.contains(key)
    override fun keys(): Set<String> = preferences.all.keys
    override fun remove(key: String) = preferences.edit().remove(key).apply()
    override fun getString(key: String, default: String?): String? =
        preferences.getString(key, default)
    override fun putString(key: String, value: String?) {
        preferences.edit().putString(key, value).apply()
    }
    override fun getBoolean(key: String, default: Boolean): Boolean =
        preferences.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }
    override fun getInt(key: String, default: Int): Int = preferences.getInt(key, default)
    override fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }
    override fun getFloat(key: String, default: Float): Float = preferences.getFloat(key, default)
    override fun putFloat(key: String, value: Float) {
        preferences.edit().putFloat(key, value).apply()
    }
    override fun getStringSet(key: String): Set<String> =
        preferences.getStringSet(key, emptySet()).orEmpty()
    override fun putStringSet(key: String, value: Set<String>?) {
        preferences.edit().putStringSet(key, value).apply()
    }

    override fun update(block: KeyValueStore.Editor.() -> Unit) {
        val editor = preferences.edit()
        block(object : KeyValueStore.Editor {
            override fun remove(key: String) {
                editor.remove(key)
            }

            override fun putString(key: String, value: String?) {
                editor.putString(key, value)
            }

            override fun putBoolean(key: String, value: Boolean) {
                editor.putBoolean(key, value)
            }

            override fun putInt(key: String, value: Int) {
                editor.putInt(key, value)
            }

            override fun putLong(key: String, value: Long) {
                editor.putLong(key, value)
            }

            override fun putFloat(key: String, value: Float) {
                editor.putFloat(key, value)
            }

            override fun putStringSet(key: String, value: Set<String>?) {
                editor.putStringSet(key, value)
            }
        })
        editor.apply()
    }
}
