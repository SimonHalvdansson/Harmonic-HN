package com.simon.harmonichackernews

import android.net.Uri
import android.text.TextUtils
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.utils.SearchRelevanceUtils
import java.util.Locale

internal class StorySearchController {
    internal fun interface StoryFilter {
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
        this.isOnlyClicked = false
    }

    fun toggleOnlyClicked() {
        this.isOnlyClicked = !this.isOnlyClicked
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
        val currentTime = (System.currentTimeMillis() / 1000).toInt()
        if (storyType == StoryType.LAST_24_HOURS) {
            return currentTime - 60 * 60 * 24
        } else if (storyType == StoryType.LAST_48_HOURS) {
            return currentTime - 60 * 60 * 48
        } else if (storyType == StoryType.LAST_WEEK) {
            return currentTime - 60 * 60 * 24 * 7
        }

        return currentTime
    }

    fun buildTopStoriesUrl(startTime: Int, hitsPerPage: Int): String {
        return Uri.parse("https://hn.algolia.com/api/v1/search")
            .buildUpon()
            .appendQueryParameter("tags", "story")
            .appendQueryParameter("numericFilters", "created_at_i>" + startTime)
            .appendQueryParameter("hitsPerPage", hitsPerPage.toString())
            .build()
            .toString()
    }

    fun buildSearchUrl(query: String?, hitsPerPage: Int): String {
        val endpoint = if (sortIndex == 0)
            "https://hn.algolia.com/api/v1/search"
        else
            "https://hn.algolia.com/api/v1/search_by_date"
        val builder = Uri.parse(endpoint).buildUpon()
            .appendQueryParameter("query", query)
            .appendQueryParameter("tags", "story")
            .appendQueryParameter("hitsPerPage", hitsPerPage.toString())
            .appendQueryParameter("typoTolerance", "min")

        val numericFilters: MutableList<String> = ArrayList()
        val days: Int = SEARCH_DATE_RANGE_DAYS[dateRangeIndex]
        if (days > 0) {
            val startTime = (System.currentTimeMillis() / 1000L) - (days * 24L * 60L * 60L)
            numericFilters.add("created_at_i>=" + startTime)
        }

        val minimumPoints: Int = SEARCH_MINIMUM_POINTS[minimumPointsIndex]
        if (minimumPoints > 0) {
            numericFilters.add("points>=" + minimumPoints)
        }

        val minimumComments: Int = SEARCH_MINIMUM_COMMENTS[minimumCommentsIndex]
        if (minimumComments > 0) {
            numericFilters.add("num_comments>=" + minimumComments)
        }

        if (!numericFilters.isEmpty()) {
            builder.appendQueryParameter("numericFilters", TextUtils.join(",", numericFilters))
        }

        return builder.build().toString()
    }

    fun canLoadMoreResults(rawParsedStoryCount: Int, hitsPerPage: Int): Boolean {
        return rawParsedStoryCount >= hitsPerPage
    }

    fun normalizeQuery(query: String?): String {
        return if (query == null) "" else query.trim { it <= ' ' }.lowercase(Locale.getDefault())
    }

    fun shouldIncludeOnlyClickedStory(
        story: Story,
        normalizedQuery: String,
        storyFilter: StoryFilter
    ): Boolean {
        val title = story.title
        if (title == null || !title.lowercase(Locale.getDefault())
                .contains(normalizedQuery)
        ) {
            return false
        }

        val minimumTime = this.minimumTimeSeconds
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

    fun sortOnlyClickedResultsIfNeeded(stories: MutableList<Story>, query: String?) {
        if (sortIndex == 0) {
            SearchRelevanceUtils.sortStoriesByRelevance(stories, query)
        }
    }

    private val minimumTimeSeconds: Int
        get() {
            val days: Int = SEARCH_DATE_RANGE_DAYS[dateRangeIndex]
            if (days <= 0) {
                return 0
            }

            return ((System.currentTimeMillis() / 1000L) - (days * 24L * 60L * 60L)).toInt()
        }

    companion object {
        const val ALGOLIA_HITS_INCREMENT: Int = 200

        val sortLabels: Array<String> = arrayOf("Relevance", "Newest")
        val dateRangeLabels: Array<String> =
            arrayOf("All time", "Past day", "Past week", "Past month", "Past year")
        private val SEARCH_DATE_RANGE_DAYS = intArrayOf(0, 1, 7, 30, 365)
        val minimumPointsLabels: Array<String> =
            arrayOf("Any points", "5+ points", "25+ points", "100+ points")
        private val SEARCH_MINIMUM_POINTS = intArrayOf(0, 5, 25, 100)
        val minimumCommentsLabels: Array<String> =
            arrayOf("Any comments", "5+ comments", "25+ comments", "100+ comments")
        private val SEARCH_MINIMUM_COMMENTS = intArrayOf(0, 5, 25, 100)
    }
}
