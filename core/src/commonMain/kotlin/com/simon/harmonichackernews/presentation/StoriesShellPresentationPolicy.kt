package com.simon.harmonichackernews.presentation

data class StoriesShellPresentationInput(
    val searching: Boolean,
    val submittedSearch: Boolean,
    val storyCount: Int,
    val searchLoading: Boolean,
    val loadingFailed: Boolean,
    val notFound: Boolean,
    val rateLimited: Boolean,
    val online: Boolean,
    val bookmarks: Boolean,
    val history: Boolean,
    val userItems: Boolean,
    val userItemsInitialLoadInProgress: Boolean,
    val refreshIndicatorShowing: Boolean,
    val showingCached: Boolean,
    val cacheInProgress: Boolean,
    val visibleStoryCount: Int,
)

data class StoriesShellPresentation(
    val showEmptySearch: Boolean,
    val showEmptySavedList: Boolean,
    val showLoading: Boolean,
    val loadingFailureMessage: String,
    val canCacheStories: Boolean,
)

/** Portable empty/loading/cache affordance decisions for the stories root shell. */
object StoriesShellPresentationPolicy {
    fun present(input: StoriesShellPresentationInput): StoriesShellPresentation {
        val empty = input.storyCount == 0
        val failed = input.loadingFailed || input.notFound
        return StoriesShellPresentation(
            showEmptySearch = input.searching && input.submittedSearch && empty &&
                !input.searchLoading && !failed,
            showEmptySavedList = !input.searching && empty && !failed && (
                input.bookmarks ||
                    input.history ||
                    input.userItems &&
                    !input.userItemsInitialLoadInProgress &&
                    !input.refreshIndicatorShowing
                ),
            showLoading = if (input.searching) {
                input.searchLoading
            } else {
                empty && !failed && !input.bookmarks && !input.history &&
                    (!input.userItems || input.userItemsInitialLoadInProgress)
            },
            loadingFailureMessage = when {
                input.rateLimited -> "Rate limited"
                !input.online -> "No internet connection"
                else -> "Loading failed"
            },
            canCacheStories = input.visibleStoryCount > 0 &&
                !input.showingCached && !input.cacheInProgress && input.online,
        )
    }
}
