package com.simon.harmonichackernews.navigation

import com.simon.harmonichackernews.network.AlgoliaCommentRequest
import com.simon.harmonichackernews.network.CommentsPreloadRepository

/** Scene-owned leases acquired before navigation publishes and the feed cancels its preloads. */
class CommentsOpeningRequests(
    private val preloads: CommentsPreloadRepository,
    private val useAlgolia: () -> Boolean,
) : AutoCloseable {
    private val requests = mutableMapOf<Int, AlgoliaCommentRequest>()
    private var closed = false

    fun navigationChanged(state: MainNavigationSnapshot) {
        if (closed) return
        val retained = state.destinationStack.filterIsInstance<MainNavigationEntry.Story>()
            .mapTo(mutableSetOf()) { it.request.serial }
        requests.keys.filter { it !in retained }.forEach { requests.remove(it)?.close() }
        if (state.currentDestination != MainDestination.STORY || !useAlgolia()) return
        val story = state.storyRequest ?: return
        requests.getOrPut(story.serial) { preloads.acquireAlgoliaRequest(story.destination.storyId) }
    }

    /** Borrowed by the initial load; navigation and the presenter may both close it safely. */
    fun requestFor(serial: Int, storyId: Int): AlgoliaCommentRequest? =
        requests[serial]?.takeIf { it.storyId == storyId && !it.isClosed }

    override fun close() {
        closed = true
        requests.values.forEach { it.close() }
        requests.clear()
    }
}
