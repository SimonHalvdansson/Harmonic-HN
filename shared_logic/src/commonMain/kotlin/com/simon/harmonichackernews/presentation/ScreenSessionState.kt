package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story

/**
 * Platform-neutral story-screen session state.
 *
 * Android lifecycle holders retain this object, but no Android type crosses the boundary.
 */
class StoriesSessionState {
    val mainStories = mutableListOf<Story>()
    val searchStories = mutableListOf<Story>()
    val bookmarkStories = mutableListOf<Story>()
    val userItemListStories = mutableListOf<Story>()
    val userItemListCommentIds = mutableSetOf<Int>()

    var mainTypeLabel: String? = null
    var searchTypeLabel: String? = null
    var mainVisibleStoryCount: Int = 0
    var searchVisibleStoryCount: Int = 0
    var mainShowLoadMoreButton: Boolean = false
    var searchShowLoadMoreButton: Boolean = false

    var searching: Boolean = false
    var lastSearch: String = ""
    var mainLoadedTo: Int = 0
    var searchLoadedTo: Int = 0
    var mainShowingCached: Boolean = false
    var searchShowingCached: Boolean = false
    var mainLoadingFailed: Boolean = false
    var mainLoadingFailedServerError: Boolean = false
    var mainLoadingFailedRateLimited: Boolean = false
    var searchLoadingFailed: Boolean = false
    var searchLoadingFailedServerError: Boolean = false
    var searchLoadingFailedRateLimited: Boolean = false
    var mainAlgoliaHitsPerPage: Int = 0
    var searchAlgoliaHitsPerPage: Int = 0
    var mainLastAlgoliaTopStoriesStartTime: Int = 0
    var searchLastAlgoliaTopStoriesStartTime: Int = 0

    var lastLoaded: Long = 0
    var updateButtonShowing: Boolean = false
    var userItemListFilter: Int = 0
    var frontPageDayUtcMillis: Long = -1L
    var scrapedFrontpageNextPageUrl: String? = null

    var mainFirstVisiblePosition: Int = -1
    var mainFirstVisibleTop: Int = 0
    var searchFirstVisiblePosition: Int = -1
    var searchFirstVisibleTop: Int = 0
    var appBarCollapsed: Boolean = false

    var searchSortIndex: Int = 0
    var searchDateRangeIndex: Int = 0
    var searchMinimumPointsIndex: Int = 0
    var searchMinimumCommentsIndex: Int = 0
    var searchOnlyClicked: Boolean = false
}

/** Canonical non-visual state for a comments session. */
class CommentsSessionState {
    var story: Story? = null
    var comments: MutableList<Comment>? = null
    var allComments: MutableList<Comment>? = null
    var showWebsite: Boolean = false
    var commentsLoaded: Boolean = false
    var refreshInProgress: Boolean = false
    var loadingFailed: Boolean = false
    var loadingFailedServerError: Boolean = false
    var showUpdate: Boolean = false
    var storyVoteLoading: Boolean = false
    var storyFavoriteLoading: Boolean = false
    var scrollToCommentId: Int = -1
    var commentsByOpFilterActive: Boolean = false
    var currentCommentSorting: String? = null
    var lastLoaded: Long = 0
}

enum class SubmissionFilter {
    STORIES,
    BOTH,
    COMMENTS,
}
