package com.simon.harmonichackernews

import androidx.annotation.Nullable
import androidx.lifecycle.ViewModel
import com.simon.harmonichackernews.data.Story
/** Keeps the in-memory story screen state while its activity is recreated.  */
class StoriesViewModel : ViewModel() {
    var state: State? = null

    class State {
        val mainStories: ArrayList<Story> = ArrayList()
        val searchStories: ArrayList<Story> = ArrayList()
        val bookmarkStories: ArrayList<Story> = ArrayList()
        val userItemListStories: ArrayList<Story> = ArrayList()
        val userItemListCommentIds: MutableSet<Int> = HashSet()

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
}
