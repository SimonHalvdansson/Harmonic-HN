package com.simon.harmonichackernews.ui.comments

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentsHeaderStatusTest {
    @Test
    fun uncachedInitialLoadShowsLoading() {
        assertTrue(
            shouldShowCommentsHeaderLoading(
                loadingFailed = false,
                pullToRefreshInProgress = false,
                headerRefreshInProgress = false,
                commentsLoaded = false,
                initialThreadCached = false,
            ),
        )
    }

    @Test
    fun cachedInitialLoadAndBackgroundRefreshesDoNotShowLoading() {
        assertFalse(
            shouldShowCommentsHeaderLoading(
                loadingFailed = false,
                pullToRefreshInProgress = false,
                headerRefreshInProgress = false,
                commentsLoaded = false,
                initialThreadCached = true,
            ),
        )
        assertFalse(
            shouldShowCommentsHeaderLoading(
                loadingFailed = false,
                pullToRefreshInProgress = false,
                headerRefreshInProgress = false,
                commentsLoaded = true,
                initialThreadCached = false,
            ),
        )
    }

    @Test
    fun userRequestedHeaderRefreshShowsLoadingForCachedAndLoadedThreads() {
        assertTrue(
            shouldShowCommentsHeaderLoading(
                loadingFailed = false,
                pullToRefreshInProgress = false,
                headerRefreshInProgress = true,
                commentsLoaded = true,
                initialThreadCached = true,
            ),
        )
    }

    @Test
    fun pullToRefreshUsesOnlyItsOwnIndicator() {
        assertFalse(
            shouldShowCommentsHeaderLoading(
                loadingFailed = false,
                pullToRefreshInProgress = true,
                headerRefreshInProgress = false,
                commentsLoaded = true,
                initialThreadCached = false,
            ),
        )
    }
}
