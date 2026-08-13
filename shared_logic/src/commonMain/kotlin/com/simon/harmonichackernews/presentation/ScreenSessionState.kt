package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StorySearchController
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.CommentsScrollProgress
import com.simon.harmonichackernews.data.Story

/**
 * Platform-neutral story-screen session state.
 *
 * Android lifecycle holders retain this object, but no Android type crosses the boundary.
 */
class StoriesSessionState {
    var initialized: Boolean = false
    val mainStoryList = StoryListStore()
    val searchStoryList = StoryListStore()
    val mainStories: MutableList<Story> = mainStoryList.stories
    val searchStories: MutableList<Story> = searchStoryList.stories
    val bookmarkStories = mutableListOf<Story>()
    val userItemListStories = mutableListOf<Story>()
    val userItemListCommentIds = mutableSetOf<Int>()

    var mainStoryType: StoryType = StoryType.TOP_STORIES
    var searchStoryType: StoryType = StoryType.TOP_STORIES

    var searching: Boolean = false
    var lastSearch: String = ""
    var mainAlgoliaHitsPerPage: Int = StorySearchController.ALGOLIA_HITS_INCREMENT
    var searchAlgoliaHitsPerPage: Int = StorySearchController.ALGOLIA_HITS_INCREMENT
    var mainLastAlgoliaTopStoriesStartTime: Int = 0
    var searchLastAlgoliaTopStoriesStartTime: Int = 0

    var lastLoaded: Long = 0
    var updateButtonShowing: Boolean = false
    var userItemListFilter: Int = 1
    var frontPageDayUtcMillis: Long = -1L
    var scrapedFrontpageNextPageUrl: String? = null

    var searchSortIndex: Int = 0
    var searchDateRangeIndex: Int = 0
    var searchMinimumPointsIndex: Int = 0
    var searchMinimumCommentsIndex: Int = 0
    var searchOnlyClicked: Boolean = false
}

/** Canonical non-visual state for a comments session. */
class CommentsSessionState(
    val scrollProgress: CommentsScrollProgress = CommentsScrollProgress(),
) {
    var initialized: Boolean = false
    val commentThread = CommentThreadStore()
    var story: Story? = null
    var showWebsite: Boolean = false
    var commentsLoaded: Boolean = false
    var refreshInProgress: Boolean = false
    var loadingFailed: Boolean = false
    var loadingFailedServerError: Boolean = false
    var showUpdate: Boolean = false
    var storyVoteLoading: Boolean = false
    var storyFavoriteLoading: Boolean = false
    var scrollToCommentId: Int = -1
    var lastLoaded: Long = 0
    var hostRestoration: CommentsHostRestoration = CommentsHostRestoration()
}

/** Canonical state for a submissions session, including its visible list position. */
class SubmissionsSessionState(
    val submissions: SubmissionsStore,
) {
    var initialized: Boolean = false
    var firstVisibleStoryPosition: Int = 0
    var firstVisibleStoryTop: Int = 0
    var appBarCollapsed: Boolean = false
}

enum class SubmissionFilter {
    STORIES,
    BOTH,
    COMMENTS,
}
