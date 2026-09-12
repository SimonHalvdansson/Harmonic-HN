package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.network.StoryTextProcessor
import com.simon.harmonichackernews.presentation.StoryPlaceholderFactory
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Frozen before kernels paired with the production methods; outputs escape timed blocks. */
@RunWith(AndroidJUnit4::class)
class StoryMicroOptimizationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    private val thirtyIds = (1..30).toList()
    private val fiveHundredIds = (1..500).toList()
    private val clickedIds = (1..500 step 5).toSet()
    private val allClickedIds = fiveHundredIds.toSet()
    private val commentIds = (1..500 step 7).toSet()
    private val retainedStories = fiveHundredIds.map { Story("Retained $it", it, true, false) }
    private val cachedStories = (1..450).associateWith { Story("Cached $it", it, true, false) }
    private val bareUrls = "Read https://example.com/article and http://second.example/path. " +
        "See https:&#x2F;&#47;third.example/path for more."
    private val existingAnchors = """<a href="https://example.com/article">An article</a><A HREF="https://second.example/path">https://second.example/path</A>"""
    private val mixedAnchors = """https://before.example/path <a href="https://example.com/article">An article</a> https://after.example/path."""
    @Volatile private var sink: Any? = null

    @Before
    fun verifyFixtures() {
        for (input in listOf(bareUrls, existingAnchors, mixedAnchors)) {
            check(BeforeStoryTextProcessor.preprocessHtml(input) == StoryTextProcessor.preprocessHtml(input))
        }
        for (ids in listOf(thirtyIds, fiveHundredIds)) {
            for (cache in listOf(emptyMap(), cachedStories)) {
                for (hidden in listOf(false, true)) {
                    val before = beforeCreate(ids, cache, clickedIds, hidden).map {
                        it.toSnapshot() to it.presentationSnapshot()
                    }
                    val after = afterCreate(ids, cache, clickedIds, hidden).map {
                        it.toSnapshot() to it.presentationSnapshot()
                    }
                    check(before == after)
                }
            }
        }
        for (existing in listOf(emptyList(), retainedStories)) {
            val before = beforeReconcile(existing).map { it.toSnapshot() to it.presentationSnapshot() }
            val after = afterReconcile(existing).map { it.toSnapshot() to it.presentationSnapshot() }
            check(before == after)
        }
        check(beforeCreate(fiveHundredIds, cachedStories, allClickedIds, true).isEmpty())
        check(afterCreate(fiveHundredIds, cachedStories, allClickedIds, true).isEmpty())
    }

    @Test fun bareUrlsBefore() = benchmarkRule.measureRepeated {
        sink = BeforeStoryTextProcessor.preprocessHtml(bareUrls)
    }

    @Test fun bareUrlsAfter() = benchmarkRule.measureRepeated {
        sink = StoryTextProcessor.preprocessHtml(bareUrls)
    }

    @Test fun existingAnchorsBefore() = benchmarkRule.measureRepeated {
        sink = BeforeStoryTextProcessor.preprocessHtml(existingAnchors)
    }

    @Test fun existingAnchorsAfter() = benchmarkRule.measureRepeated {
        sink = StoryTextProcessor.preprocessHtml(existingAnchors)
    }

    @Test fun mixedAnchorsBefore() = benchmarkRule.measureRepeated {
        sink = BeforeStoryTextProcessor.preprocessHtml(mixedAnchors)
    }

    @Test fun mixedAnchorsAfter() = benchmarkRule.measureRepeated {
        sink = StoryTextProcessor.preprocessHtml(mixedAnchors)
    }

    @Test fun thirtyPlaceholdersBefore() = benchmarkRule.measureRepeated {
        sink = beforeCreate(thirtyIds)
    }

    @Test fun thirtyPlaceholdersAfter() = benchmarkRule.measureRepeated {
        sink = afterCreate(thirtyIds)
    }

    @Test fun fiveHundredPlaceholdersBefore() = benchmarkRule.measureRepeated {
        sink = beforeCreate(fiveHundredIds)
    }

    @Test fun fiveHundredPlaceholdersAfter() = benchmarkRule.measureRepeated {
        sink = afterCreate(fiveHundredIds)
    }

    @Test fun mostlyCachedPlaceholdersBefore() = benchmarkRule.measureRepeated {
        sink = beforeCreate(fiveHundredIds, cachedStories)
    }

    @Test fun mostlyCachedPlaceholdersAfter() = benchmarkRule.measureRepeated {
        sink = afterCreate(fiveHundredIds, cachedStories)
    }

    @Test fun hiddenPlaceholdersBefore() = benchmarkRule.measureRepeated {
        sink = beforeCreate(fiveHundredIds, cachedStories, allClickedIds, true)
    }

    @Test fun hiddenPlaceholdersAfter() = benchmarkRule.measureRepeated {
        sink = afterCreate(fiveHundredIds, cachedStories, allClickedIds, true)
    }

    @Test fun initialReconcileBefore() = benchmarkRule.measureRepeated {
        sink = beforeReconcile(emptyList())
    }

    @Test fun initialReconcileAfter() = benchmarkRule.measureRepeated {
        sink = afterReconcile(emptyList())
    }

    @Test fun retainedReconcileBefore() = benchmarkRule.measureRepeated {
        sink = beforeReconcile(retainedStories)
    }

    @Test fun retainedReconcileAfter() = benchmarkRule.measureRepeated {
        sink = afterReconcile(retainedStories)
    }

    private fun afterCreate(
        ids: List<Int>,
        cache: Map<Int, Story> = emptyMap(),
        clicked: Set<Int> = clickedIds,
        hidden: Boolean = false,
    ): MutableList<Story> = StoryPlaceholderFactory.create(
        itemIds = ids,
        commentIds = commentIds,
        clickedIds = clicked,
        hideClicked = hidden,
        cachedStories = cache,
    )

    private fun beforeCreate(
        ids: List<Int>,
        cache: Map<Int, Story> = emptyMap(),
        clicked: Set<Int> = clickedIds,
        hidden: Boolean = false,
    ): MutableList<Story> = BeforeStoryPlaceholderFactory.create(
        itemIds = ids,
        commentIds = commentIds,
        clickedIds = clicked,
        hideClicked = hidden,
        cachedStories = cache,
    )

    private fun afterReconcile(existing: List<Story>): MutableList<Story> =
        StoryPlaceholderFactory.reconcile(
            existingStories = existing,
            itemIds = fiveHundredIds,
            commentIds = commentIds,
            clickedIds = clickedIds,
        )

    private fun beforeReconcile(existing: List<Story>): MutableList<Story> =
        BeforeStoryPlaceholderFactory.reconcile(
            existingStories = existing,
            itemIds = fiveHundredIds,
            commentIds = commentIds,
            clickedIds = clickedIds,
        )
}

// Snapshot of the pre-change production implementation for repeatable comparisons.
private object BeforeStoryTextProcessor {
    private val anchorPattern = Regex("(?is)<a\\b[^>]*>.*?</a>")
    private val urlPattern = Regex(
        "(https?:(?:/{1}|(?:&#x2F;)|(?:&#47;))" +
            "(?:/{1}|(?:&#x2F;)|(?:&#47;))" +
            "(?=[^\\s<>\"]*\\.)[^\\s<>\"]+)",
    )
    private const val TRAILING_PUNCTUATION = ".,;:!?)"
    private val pdfSuffixes = arrayOf(" [pdf]", "[pdf]", " (pdf)", "(pdf)")
    private val videoSuffixes = arrayOf(" [video]", "[video]", " (video)", "(video)")

    fun preprocessHtml(input: String?): String? {
        if (input.isNullOrEmpty()) return input
        var processed = linkify(input)
        if (processed.contains("code>")) {
            processed = processed.replace("<pre><code>", "<pre><small>")
                .replace("</code></pre>", "</small></pre>")
                .replace("<code>", "<pre><small>")
                .replace("</code>", "</small></pre>")
        }
        if (processed.contains("pre>")) {
            if (processed.contains("<pre>")) processed = escapePreBlockWhitespace(processed)
            processed = processed.replace("<pre>", "<div><tt>")
                .replace("</pre>", "</tt></div>")
        }
        return processed
    }

    fun applyTitleBadges(story: Story?) {
        val title = story?.title?.takeUnless(String::isEmpty) ?: return
        val url = story.url?.takeUnless(String::isEmpty) ?: return
        story.pdfTitle = null
        story.videoTitle = null

        val mayHaveSuffix = title.last() == ']' || title.last() == ')'
        val pdfTitle = if (mayHaveSuffix) stripSuffix(title, pdfSuffixes) else null
        when {
            url.endsWith(".pdf", ignoreCase = true) -> story.pdfTitle = pdfTitle ?: title
            pdfTitle != null -> story.pdfTitle = pdfTitle
            mayHaveSuffix -> story.videoTitle = stripSuffix(title, videoSuffixes)
        }
    }

    private fun linkify(input: String): String {
        // Both supported URL schemes share this prefix; ordinary text needs only one scan.
        if (!input.contains("http")) return input
        val output = StringBuilder(input.length)
        var endOfPreviousAnchor = 0
        anchorPattern.findAll(input).forEach { anchor ->
            output.append(linkifySegment(input.substring(endOfPreviousAnchor, anchor.range.first)))
            output.append(input, anchor.range.first, anchor.range.last + 1)
            endOfPreviousAnchor = anchor.range.last + 1
        }
        output.append(linkifySegment(input.substring(endOfPreviousAnchor)))
        return output.toString()
    }

    private fun linkifySegment(segment: String): String = urlPattern.replace(segment) { match ->
        val url = match.value
        var end = url.length
        while (end > 0 && url[end - 1] in TRAILING_PUNCTUATION) end--
        if (end > 0 && url[end - 1] == ')') {
            val core = url.substring(0, end)
            if (core.count { it == ')' } > core.count { it == '(' }) end--
        }
        val core = url.substring(0, end)
            .replace("&#x2F;", "/")
            .replace("&#47;", "/")
        "<a href=\"$core\">$core</a>${url.substring(end)}"
    }

    private fun escapePreBlockWhitespace(input: String): String = buildString(input.length) {
        var inPre = false
        var index = 0
        while (index < input.length) {
            when {
                input.startsWith("<pre>", index) -> {
                    inPre = true
                    append("<pre>")
                    index += 5
                }
                input.startsWith("</pre>", index) -> {
                    inPre = false
                    append("</pre>")
                    index += 6
                }
                inPre && input[index] == ' ' -> {
                    append("&nbsp;")
                    index++
                }
                inPre && input[index] == '\n' -> {
                    append("<br>")
                    index++
                }
                else -> append(input[index++])
            }
        }
    }

    private fun stripSuffix(title: String, suffixes: Array<String>): String? =
        suffixes.firstOrNull { title.endsWith(it, ignoreCase = true) }
            ?.let { title.dropLast(it.length) }
}

// Exact original factory, including parameter and callback defaults.
private object BeforeStoryPlaceholderFactory {
    fun create(
        itemIds: List<Int>,
        commentIds: Set<Int> = emptySet(),
        clickedIds: Set<Int> = emptySet(),
        hideClicked: Boolean = false,
        hydrateCachedStory: (Story) -> Boolean = { false },
        shouldHideHydratedStory: (Story) -> Boolean = { false },
        cachedStories: Map<Int, Story> = emptyMap(),
    ): MutableList<Story> = itemIds.mapNotNullTo(mutableListOf()) { id ->
        if (hideClicked && id in clickedIds) return@mapNotNullTo null
        (cachedStories[id] ?: Story("Loading...", id, false, id in clickedIds)).also { story ->
            story.clicked = id in clickedIds
            story.isComment = id in commentIds
            if ((id in cachedStories || hydrateCachedStory(story)) && shouldHideHydratedStory(story)) {
                return@mapNotNullTo null
            }
        }
    }

    fun createNew(
        existingStories: List<Story>,
        itemIds: List<Int>,
        commentIds: Set<Int> = emptySet(),
        clickedIds: Set<Int> = emptySet(),
        hideClicked: Boolean = false,
        hydrateCachedStory: (Story) -> Boolean = { false },
        shouldHideHydratedStory: (Story) -> Boolean = { false },
        cachedStories: Map<Int, Story> = emptyMap(),
    ): MutableList<Story> {
        val existingIds = existingStories.mapTo(mutableSetOf(), Story::id)
        return create(
            itemIds = itemIds.filterNot(existingIds::contains),
            commentIds = commentIds,
            clickedIds = clickedIds,
            hideClicked = hideClicked,
            hydrateCachedStory = hydrateCachedStory,
            shouldHideHydratedStory = shouldHideHydratedStory,
            cachedStories = cachedStories,
        )
    }

    /**
     * Reorders a refreshed feed while retaining the complete live objects for IDs already shown.
     * New IDs still follow the normal cache hydration and visibility policies.
     */
    fun reconcile(
        existingStories: List<Story>,
        itemIds: List<Int>,
        commentIds: Set<Int> = emptySet(),
        clickedIds: Set<Int> = emptySet(),
        hideClicked: Boolean = false,
        hydrateCachedStory: (Story) -> Boolean = { false },
        shouldHideHydratedStory: (Story) -> Boolean = { false },
        cachedStories: Map<Int, Story> = emptyMap(),
    ): MutableList<Story> {
        val existingById = existingStories.associateBy(Story::id)
        return itemIds.mapNotNullTo(mutableListOf()) { id ->
            if (hideClicked && id in clickedIds) return@mapNotNullTo null
            existingById[id]?.also { story ->
                story.isComment = id in commentIds
            } ?: (cachedStories[id] ?: Story("Loading...", id, false, id in clickedIds)).also { story ->
                story.clicked = id in clickedIds
                story.isComment = id in commentIds
                if ((id in cachedStories || hydrateCachedStory(story)) && shouldHideHydratedStory(story)) {
                    return@mapNotNullTo null
                }
            }
        }
    }
}
