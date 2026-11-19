package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.network.RichLinkPreviewUrls
import com.simon.harmonichackernews.presentation.detectUserProfileLinks
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Baselines from 1bfa9b10 share the input, runtime, dispatch and fixture setup with production. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ProfileAndPreviewAllocationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var sink: Any? = null

    // 218 characters, five accepted URLs and one excluded email address.
    private val biography = "I write about compilers at https://reader.example.com/notes. " +
        "Projects: https://github.com/example/compiler, and https://example.org/docs/a_(b). " +
        "More at www.example.dev and reader.blog/about; contact reader@example.com."
    // A separately labelled stress case: 796 characters, including 120 unmatched closing brackets.
    private val bracketTail = "Reference: https://example.com/docs/" + "section-".repeat(80) + ")]}".repeat(40)
    private val storyUrls = List(30) { index ->
        "https://publication$index.example.com/2026/11/articles/topic-$index/details?source=hn"
    }
    private val mixedUrls = listOf(
        "https://github.com/JetBrains/kotlin",
        "https://huggingface.co/example/model",
        "https://www.npmjs.com/package/@example/library",
        "https://pypi.org/project/example",
        "https://crates.io/crates/example",
        "https://pkg.go.dev/example.org/module/package",
        "https://formulae.brew.sh/formula/example",
        "https://gitlab.com/example/project",
        "https://openrouter.ai/example/model",
        "https://stackoverflow.com/questions/123/example",
        "https://arxiv.org/abs/2401.12345",
        "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
        "https://example.statuspage.io/incidents/abc123",
        "https://doi.org/10.1000/example",
        "https://earthquake.usgs.gov/earthquakes/eventpage/us12345678",
        "https://example.substack.com/p/article",
        "https://mastodon.social/@example/123456789",
        "https://bsky.app/profile/example.com/post/abc123",
        "https://old.reddit.com/r/kotlin/comments/abc123/example",
        "https://ordinary.example.com/articles/topic/details",
    )

    @Before fun verifyEquivalence() {
        for (text in listOf(biography, bracketTail)) {
            check(legacyProfileLinks(text) == detectUserProfileLinks(text))
        }
        for (url in storyUrls + mixedUrls) {
            check(LegacyLinkPreviewProviders.type(url) == RichLinkPreviewUrls.type(url)) { url }
        }
    }

    @Test fun biographyFiveLinksBefore() = benchmarkRule.measureRepeated {
        sink = legacyProfileLinks(biography)
    }
    @Test fun biographyFiveLinksOptimized() = benchmarkRule.measureRepeated {
        sink = detectUserProfileLinks(biography)
    }
    @Test fun biographyLongBracketTailBefore() = benchmarkRule.measureRepeated {
        sink = legacyProfileLinks(bracketTail)
    }
    @Test fun biographyLongBracketTailOptimized() = benchmarkRule.measureRepeated {
        sink = detectUserProfileLinks(bracketTail)
    }
    @Test fun previewThirtyOrdinaryUrlsBefore() = benchmarkRule.measureRepeated {
        for (url in storyUrls) sink = LegacyLinkPreviewProviders.type(url)
    }
    @Test fun previewThirtyOrdinaryUrlsOptimized() = benchmarkRule.measureRepeated {
        for (url in storyUrls) sink = RichLinkPreviewUrls.type(url)
    }
    @Test fun previewTwentyMixedUrlsBefore() = benchmarkRule.measureRepeated {
        for (url in mixedUrls) sink = LegacyLinkPreviewProviders.type(url)
    }
    @Test fun previewTwentyMixedUrlsOptimized() = benchmarkRule.measureRepeated {
        for (url in mixedUrls) sink = RichLinkPreviewUrls.type(url)
    }
}
