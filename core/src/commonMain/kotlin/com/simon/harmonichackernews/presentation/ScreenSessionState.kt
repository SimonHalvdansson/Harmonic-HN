package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.CommentsScrollProgress
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform-neutral story-screen session state.
 *
 * Android lifecycle holders retain this object, but no Android type crosses the boundary.
 */
class StoriesSessionState {
    var initialized: Boolean = false
    val mainStoryList = StoryListStore()
    val searchStoryList = StoryListStore()
    val mainStories: List<Story> = mainStoryList.stories
    val searchStories: List<Story> = searchStoryList.stories
    val bookmarkStories = mutableListOf<Story>()
    val userItemListStories = mutableListOf<Story>()
    val userItemListCommentIds = mutableSetOf<Int>()

    var mainStoryType: StoryType = StoryType.TOP_STORIES
    var searchStoryType: StoryType = StoryType.TOP_STORIES

    var searching: Boolean = false
    var lastSearch: String = ""
    var lastLoaded: Long = 0
    var showRefreshPrompt: Boolean = false
    var userItemListFilter: Int = 1
    var frontPageDayUtcMillis: Long = -1L
    var scrapedFrontpageNextPageUrl: String? = null

    var searchOptions: StorySearchOptions = StorySearchOptions()

    private val pendingPreviewVoteIds = linkedSetOf<Int>()
    private val pendingPreviewFavoriteIds = linkedSetOf<Int>()
    private val mutablePreviewActionState = MutableStateFlow(StoriesPreviewActionState())
    internal val previewActionState: StateFlow<StoriesPreviewActionState> =
        mutablePreviewActionState.asStateFlow()

    internal fun beginPreviewAction(storyId: Int, action: StoryPreviewActionKind): Boolean {
        val started = when (action) {
            StoryPreviewActionKind.Vote -> pendingPreviewVoteIds.add(storyId)
            StoryPreviewActionKind.Favorite -> pendingPreviewFavoriteIds.add(storyId)
            StoryPreviewActionKind.Read, StoryPreviewActionKind.Bookmark -> true
        }
        if (started) publishPreviewActionState()
        return started
    }

    internal fun finishPreviewAction(storyId: Int, action: StoryPreviewActionKind) {
        when (action) {
            StoryPreviewActionKind.Vote -> pendingPreviewVoteIds.remove(storyId)
            StoryPreviewActionKind.Favorite -> pendingPreviewFavoriteIds.remove(storyId)
            StoryPreviewActionKind.Read, StoryPreviewActionKind.Bookmark -> Unit
        }
        publishPreviewActionState()
    }

    private fun publishPreviewActionState() {
        mutablePreviewActionState.value = StoriesPreviewActionState(
            voteLoadingIds = pendingPreviewVoteIds.toSet(),
            favoriteLoadingIds = pendingPreviewFavoriteIds.toSet(),
        )
    }
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
    var showRefreshPrompt: Boolean = false
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
