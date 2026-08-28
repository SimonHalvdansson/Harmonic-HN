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
                commentsLoaded = false,
                initialThreadCached = false,
            ),
        )
    }

    @Test
    fun cachedInitialLoadAndRefreshesDoNotShowLoading() {
        assertFalse(
            shouldShowCommentsHeaderLoading(
                loadingFailed = false,
                pullToRefreshInProgress = false,
                commentsLoaded = false,
                initialThreadCached = true,
            ),
        )
        assertFalse(
            shouldShowCommentsHeaderLoading(
                loadingFailed = false,
                pullToRefreshInProgress = false,
                commentsLoaded = true,
                initialThreadCached = false,
            ),
        )
    }
}
