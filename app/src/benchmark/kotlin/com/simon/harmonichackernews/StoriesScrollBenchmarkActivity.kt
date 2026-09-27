package com.simon.harmonichackernews

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModelProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.delay

/** Fixed input, real Stories store/screen, and local images; absent from distributable builds. */
@OptIn(coil3.annotation.DelicateCoilApi::class)
class StoriesScrollBenchmarkActivity : MainActivity() {
    private var originalLoader: ImageLoader? = null
    private var fixtureLoader: ImageLoader? = null

    override fun prepareScene() {
        val session = ViewModelProvider(this)[HarmonicSceneViewModel::class.java].scene.sessions.stories
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
                url = "https://stories-scroll.invalid/$index"
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
    }

    override fun onDestroy() {
        super.onDestroy()
        originalLoader?.let(SingletonImageLoader::setUnsafe)
        fixtureLoader?.shutdown()
    }
}
