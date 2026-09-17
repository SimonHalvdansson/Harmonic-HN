package com.simon.harmonichackernews

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.utils.SearchRelevanceUtils
import io.ktor.http.URLBuilder
import kotlin.time.Clock

class StorySearchController(
    private val clock: Clock = Clock.System,
) {
    fun interface StoryFilter {
        fun shouldFilterStory(story: Story): Boolean
    }

    var sortIndex: Int = 0
    var dateRangeIndex: Int = 0
    var minimumPointsIndex: Int = 0
    var minimumCommentsIndex: Int = 0
    var isOnlyClicked: Boolean = false
        private set

    fun resetOptions() {
        sortIndex = 0
        dateRangeIndex = 0
        minimumPointsIndex = 0
        minimumCommentsIndex = 0
        isOnlyClicked = false
    }

    fun toggleOnlyClicked() {
        isOnlyClicked = !isOnlyClicked
    }

    val sortLabel: String
        get() = sortLabels[sortIndex]

    val dateRangeLabel: String
        get() = dateRangeLabels[dateRangeIndex]

    val minimumPointsLabel: String
        get() = minimumPointsLabels[minimumPointsIndex]

    val minimumCommentsLabel: String
        get() = minimumCommentsLabels[minimumCommentsIndex]

    fun getCurrentTopStoriesStartTime(storyType: StoryType?): Int {
        val currentTime = nowEpochSeconds.toInt()
        return when (storyType) {
            StoryType.LAST_24_HOURS -> currentTime - 60 * 60 * 24
            StoryType.LAST_48_HOURS -> currentTime - 60 * 60 * 48
            StoryType.LAST_WEEK -> currentTime - 60 * 60 * 24 * 7
            else -> currentTime
        }
    }

    fun buildTopStoriesUrl(startTime: Int, hitsPerPage: Int): String {
        return URLBuilder("https://hn.algolia.com/api/v1/search").apply {
            parameters.append("tags", "story")
            parameters.append("numericFilters", "created_at_i>$startTime")
            parameters.append("hitsPerPage", hitsPerPage.toString())
        }.buildString()
    }

    fun buildSearchUrl(query: String?, hitsPerPage: Int): String {
        val endpoint = if (sortIndex == 0)
            "https://hn.algolia.com/api/v1/search"
        else
            "https://hn.algolia.com/api/v1/search_by_date"
        val builder = URLBuilder(endpoint).apply {
            parameters.append("query", query.orEmpty())
            parameters.append("tags", "story")
            parameters.append("hitsPerPage", hitsPerPage.toString())
            parameters.append("typoTolerance", "min")
        }

        val numericFilters = ArrayList<String>()
        val days = SEARCH_DATE_RANGE_DAYS[dateRangeIndex]
        if (days > 0) {
            val startTime = nowEpochSeconds - (days * 24L * 60L * 60L)
            numericFilters.add("created_at_i>=$startTime")
        }

        val minimumPoints = SEARCH_MINIMUM_POINTS[minimumPointsIndex]
        if (minimumPoints > 0) {
            numericFilters.add("points>=$minimumPoints")
        }

        val minimumComments = SEARCH_MINIMUM_COMMENTS[minimumCommentsIndex]
        if (minimumComments > 0) {
            numericFilters.add("num_comments>=$minimumComments")
        }

        if (numericFilters.isNotEmpty()) {
            builder.parameters.append("numericFilters", numericFilters.joinToString(","))
        }

        return builder.buildString()
    }

    fun canLoadMoreResults(rawParsedStoryCount: Int, hitsPerPage: Int): Boolean {
        return rawParsedStoryCount >= hitsPerPage
    }

    fun normalizeQuery(query: String?): String {
        return query.orEmpty().trim { it <= ' ' }.lowercase()
    }

    fun shouldIncludeOnlyClickedStory(
        story: Story,
        normalizedQuery: String,
        storyFilter: StoryFilter
    ): Boolean {
        if (story.title?.lowercase()?.contains(normalizedQuery) != true) {
            return false
        }

        val minimumTime = minimumTimeSeconds
        if (minimumTime > 0 && story.time < minimumTime) {
            return false
        }

        val minimumPoints: Int = SEARCH_MINIMUM_POINTS[minimumPointsIndex]
        if (minimumPoints > 0 && story.score < minimumPoints) {
            return false
        }

        val minimumComments: Int = SEARCH_MINIMUM_COMMENTS[minimumCommentsIndex]
        if (minimumComments > 0 && story.descendants < minimumComments) {
            return false
        }

        return !storyFilter.shouldFilterStory(story)
    }

    fun sortOnlyClickedResults(stories: MutableList<Story>, query: String?) {
        if (sortIndex == 0) {
            SearchRelevanceUtils.sortStoriesByRelevance(stories, query)
        } else {
            stories.sortByDescending(Story::time)
        }
    }

    private val minimumTimeSeconds: Int
        get() {
            val days: Int = SEARCH_DATE_RANGE_DAYS[dateRangeIndex]
            if (days <= 0) {
                return 0
            }

            return (nowEpochSeconds - (days * 24L * 60L * 60L)).toInt()
        }

    private val nowEpochSeconds: Long
        get() = clock.now().epochSeconds

    companion object {
        const val ALGOLIA_HITS_INCREMENT: Int = 200

        val sortLabels: List<String> = listOf("Relevance", "Newest")
        val dateRangeLabels: List<String> =
            listOf("All time", "Past day", "Past week", "Past month", "Past year")
        private val SEARCH_DATE_RANGE_DAYS = intArrayOf(0, 1, 7, 30, 365)
        val minimumPointsLabels: List<String> =
            listOf("Any points", "5+ points", "25+ points", "100+ points")
        private val SEARCH_MINIMUM_POINTS = intArrayOf(0, 5, 25, 100)
        val minimumCommentsLabels: List<String> =
            listOf("Any comments", "5+ comments", "25+ comments", "100+ comments")
        private val SEARCH_MINIMUM_COMMENTS = intArrayOf(0, 5, 25, 100)
    }
}
