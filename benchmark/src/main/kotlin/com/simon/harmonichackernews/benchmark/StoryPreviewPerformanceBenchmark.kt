package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.runtime.mutableFloatStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.ItemTimeFormatter
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.ArgbColor
import com.simon.harmonichackernews.presentation.StoriesInteractionStore
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.utils.DomainNamePolicy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device microbenchmarks for the non-rendering work removed from story-preview interactions. */
@RunWith(AndroidJUnit4::class)
class StoryPreviewPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val stories = (1..100).map { id ->
        StoryListItemSnapshot(
            story = StorySnapshot(id = id, title = "Story $id"),
            presentation = StoryPresentationSnapshot(loaded = true),
        )
    }
    private val colors = List(stories.size) { index -> 0xff000000.toInt() or index }
    private val legacyPagingStore = StoriesInteractionStore(defaultStoryHeightPx = 360).apply {
        check(showStoryPreview(stories, colors, openedStoryId = 50))
    }
    private val lowerAlpha = mutableFloatStateOf(0f)
    private val upperAlpha = mutableFloatStateOf(1f)
    private var pagingOffset = 0f
    private val previewUrl = "https://www.example.com/articles/compose-performance"
    private val cachedMeta = previewMeta()
    private val resourceState = StoryPreviewResourceState(
        storyId = 50,
        pageUrl = previewUrl,
        imageUrlResolved = true,
        imageUrl = "https://www.example.com/preview.webp",
        imageLoaded = true,
    )
    private var resourceStates = stories.associate { story ->
        story.id to resourceState.copy(storyId = story.id)
    }

    @Test
    fun legacyWholeSnapshotPagingUpdate() = benchmarkRule.measureRepeated {
        pagingOffset = (pagingOffset + 0.013f) % 1f
        legacyPagingStore.updateStoryPreviewPagePosition(49, 50, pagingOffset)
        benchmarkSink = legacyPagingStore.state.storyPagingAlphas.size
    }

    @Test
    fun rowLocalPagingUpdate() = benchmarkRule.measureRepeated {
        pagingOffset = (pagingOffset + 0.013f) % 1f
        lowerAlpha.floatValue = pagingOffset
        upperAlpha.floatValue = 1f - pagingOffset
        benchmarkSink = if (lowerAlpha.floatValue < upperAlpha.floatValue) 1 else 2
    }

    @Test
    fun recomputePreviewMeta() = benchmarkRule.measureRepeated {
        benchmarkSink = previewMeta().hashCode()
    }

    @Test
    fun reusePreviewMeta() = benchmarkRule.measureRepeated {
        benchmarkSink = cachedMeta.hashCode()
    }

    @Test
    fun legacyPreviewColorCopies() = benchmarkRule.measureRepeated {
        val controllerArray = colors.toIntArray()
        val controllerList = controllerArray.toList()
        val wrapped = controllerList.map(::ArgbColor)
        val stored = wrapped.toList()
        val rendered = stored.map(ArgbColor::value)
        benchmarkSink = rendered.last()
    }

    @Test
    fun directPreviewColorPath() = benchmarkRule.measureRepeated {
        val wrapped = colors.map(::ArgbColor)
        val stored = wrapped.toList()
        benchmarkSink = stored.last().value
    }

    @Test
    fun duplicateResourcePublication() = benchmarkRule.measureRepeated {
        resourceStates = resourceStates + (resourceState.storyId to resourceState)
        benchmarkSink = resourceStates.size
    }

    @Test
    fun skipIdenticalResourcePublication() = benchmarkRule.measureRepeated {
        if (resourceStates[resourceState.storyId] != resourceState) {
            resourceStates = resourceStates + (resourceState.storyId to resourceState)
        }
        benchmarkSink = resourceStates.size
    }

    private fun previewMeta(): String {
        val domain = DomainNamePolicy.fromUrl(previewUrl) ?: previewUrl
        return buildString {
            append(412)
            append(" points • ")
            append(domain)
            append(" • ")
            append(ItemTimeFormatter.format(1_771_600_000, 1_771_686_400_000L))
        }
    }
}

@Volatile
private var benchmarkSink: Int = 0
