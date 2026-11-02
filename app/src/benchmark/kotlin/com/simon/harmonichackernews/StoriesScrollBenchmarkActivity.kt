package com.simon.harmonichackernews

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Debug
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.ui.session.HarmonicSceneViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONObject

/** Fixed input, real Stories store/screen, and local images; absent from distributable builds. */
@OptIn(coil3.annotation.DelicateCoilApi::class)
class StoriesScrollBenchmarkActivity : MainActivity() {
    private var originalLoader: ImageLoader? = null
    private var fixtureLoader: ImageLoader? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.action) {
            "com.simon.harmonichackernews.action.BENCHMARK_OPEN_LINKED_COMMENTS" -> {
                val storyId = intent.getIntExtra("benchmark_story_id", 0)
                require(storyId > 0)
                navigationController.openLinkedStory(StoryDestination(storyId))
            }
            "com.simon.harmonichackernews.action.BENCHMARK_APPEARANCE" -> navigationController.openSettings("appearance")
            "com.simon.harmonichackernews.action.BENCHMARK_RETURN_STORIES" -> navigationController.navigationState.returnToStories()
            "com.simon.harmonichackernews.action.BENCHMARK_COLOR_SCHEME" -> {
                val scheme = requireNotNull(intent.getStringExtra("benchmark_color_scheme"))
                require(scheme in com.simon.harmonichackernews.settings.ColorSchemePreferences.values)
                Log.i("BenchmarkMemory", "Previous scheme: ${harmonicAppComposition.userSettings.appearance.colorSchemes.light}")
                harmonicAppComposition.settings.setColorScheme(scheme)
            }
            "com.simon.harmonichackernews.action.BENCHMARK_MEMORY_SNAPSHOT" -> {
                val label = requireNotNull(intent.getStringExtra("benchmark_memory_label"))
                require(label.matches(Regex("[a-z0-9_]+")))
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        System.gc()
                        delay(500)
                        System.gc()
                        val directory = File(getExternalFilesDir(null), "memory-snapshots").apply { mkdirs() }
                        Debug.dumpHprofData(File(directory, "$label.hprof").absolutePath)
                        val runtime = Runtime.getRuntime()
                        File(directory, "$label.json").writeText(JSONObject().apply {
                            put("javaUsedBytes", runtime.totalMemory() - runtime.freeMemory())
                            put("javaHeapBytes", runtime.totalMemory())
                            put("nativeAllocatedBytes", Debug.getNativeHeapAllocatedSize())
                        }.toString())
                        Log.i("BenchmarkMemory", "Snapshot ready: $label")
                    }
                }
            }
            else -> return
        }
        setIntent(Intent(this, StoriesScrollBenchmarkActivity::class.java))
    }

    override fun prepareScene() {
        val session = ViewModelProvider(this, HarmonicSceneViewModel.factory(harmonicAppComposition))[
            HarmonicSceneViewModel::class.java
        ].scene.sessions.stories
        val run = intent.getIntExtra("stories_run", 0).coerceIn(0, 100_000)
        val cached = intent.getBooleanExtra("stories_cached", false)
        val image = assets.open("stories_scroll_preview.webp").use {
            checkNotNull(BitmapFactory.decodeStream(it)).asImage()
        }
        originalLoader = SingletonImageLoader.get(this)
        fixtureLoader = ImageLoader.Builder(this).components {
            add(Interceptor { chain ->
                // Give first-arrival fades/palettes the same timing in every build. The actual
                // Compose image rendering and background palette extraction are unchanged.
                delay(40)
                SuccessResult(image, chain.request, DataSource.NETWORK)
            })
        }.build().also(SingletonImageLoader::setUnsafe)
        val titles = listOf(
            "A small database built for predictable performance",
            "What happens when you follow a packet across the internet",
            "Show HN: A tiny compiler that fits in your head",
            "Photographs of the night sky from a backyard telescope",
            "The engineering behind a century-old railway bridge",
            "Why text rendering is harder than it first appears",
        )
        val stories = List(160) { index ->
            Story().apply {
                id = 1_000_000_000 + run * 1_000 + index
                title = "${index + 1}. ${titles[index % titles.size]}"
                by = "fixture_author"
                score = 23 + index * 7
                descendants = 0 // No unrelated discussion preloads for synthetic IDs.
                createdAtEpochSeconds = 1_700_000_000
                url = if (index == 0 && intent.getBooleanExtra("profile_reading", false)) {
                    "http://127.0.0.1:18765/article"
                } else "https://stories-scroll.invalid/$index"
                loaded = true
                isLink = true
                previewImageUrl = "https://stories-scroll.invalid/image/$index.webp"
                previewImageUrlResolved = true
                linkSummaryDescription = "A repeatable story summary with enough text to wrap across multiple lines."
                linkSummaryLoaded = true
                if (cached) {
                    // Persisted palettes are tested separately from first-arrival animations.
                    previewImageTintColorLoaded = true
                    previewImageTintColor = 0xffd9e3df.toInt()
                    previewImageTintSourceUrl = previewImageUrl
                    previewImageTintBaseColor = com.simon.harmonichackernews.ui.theme.previewTintBaseColor(
                        this@StoriesScrollBenchmarkActivity,
                    )
                    previewImageTintMode = com.simon.harmonichackernews.settings.StoryPreviewTintState.storedMode(
                        harmonicAppComposition.userSettings.story.paletteTintConfigKey,
                    )
                }
            }
        }
        session.mainStoryList.replace(stories)
        session.mainStoryList.markLoadedThrough(stories.lastIndex)
        session.initialized = true
        session.lastLoaded = System.currentTimeMillis()
        session.searching = false
        if (intent.getBooleanExtra("stories_updates", false)) {
            lifecycleScope.launch {
                delay(1_000)
                // Exercise the same immutable-list publication as arriving/refreshed story
                // metadata, without introducing network timing into a scrolling comparison.
                repeat(160) { index ->
                    delay(120)
                    val story = stories[index % stories.size]
                    story.score++
                    session.mainStoryList.contentChanged(story)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        originalLoader?.let(SingletonImageLoader::setUnsafe)
        fixtureLoader?.shutdown()
    }
}
