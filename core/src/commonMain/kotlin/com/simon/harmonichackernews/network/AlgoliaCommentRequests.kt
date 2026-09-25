package com.simon.harmonichackernews.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** A reference to immutable response bytes. Its owner must close it, including on cancellation. */
class AlgoliaCommentRequest internal constructor(
    val storyId: Int,
    response: Deferred<String>,
    release: () -> Unit,
) : AutoCloseable {
    private class Reference(val response: Deferred<String>, val release: () -> Unit)
    private val reference = MutableStateFlow<Reference?>(Reference(response, release))
    private val closed = CompletableDeferred<Unit>()
    val isClosed: Boolean get() = reference.value == null

    fun invokeOnClose(action: () -> Unit): DisposableHandle = closed.invokeOnCompletion { action() }

    suspend fun await(): String {
        val current = reference.value ?: throw CancellationException("Comment request released")
        val response = current.response.await()
        if (reference.value !== current) throw CancellationException("Comment request released")
        return response
    }

    override fun close() {
        reference.getAndUpdate { null }?.let {
            it.release()
            closed.complete(Unit)
        }
    }

    internal companion object {
        fun completed(storyId: Int, response: String) =
            AlgoliaCommentRequest(storyId, CompletableDeferred(response)) {}
    }
}

/**
 * Shares downloads across feed preloads and navigation, independently of parsing and UI lifetime.
 * Each transfer is owned by its leases; releasing the last one cancels it and discards its bytes.
 * Acquisition is synchronous so navigation can retain a transfer before deactivating the feed.
 */
internal class AlgoliaCommentRequests(
    private val algolia: AlgoliaRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private class Transfer(val scope: CoroutineScope, val response: Deferred<String>)
    private data class Entry(val transfer: Transfer, val references: Int)
    private val entries = MutableStateFlow<Map<Int, Entry>>(emptyMap())

    fun acquire(storyId: Int): AlgoliaCommentRequest {
        require(storyId > 0)
        while (true) {
            val current = entries.value
            val existing = current[storyId]
            val transfer = existing?.transfer ?: run {
                val scope = CoroutineScope(SupervisorJob() + dispatcher)
                Transfer(scope, scope.async(start = CoroutineStart.LAZY) { algolia.getItemJson(storyId) })
            }
            if (entries.compareAndSet(current, current + (storyId to Entry(transfer, (existing?.references ?: 0) + 1)))) {
                transfer.response.start()
                return AlgoliaCommentRequest(storyId, transfer.response) { release(storyId, transfer) }
            }
            if (existing == null) transfer.scope.cancel()
        }
    }

    private fun release(storyId: Int, transfer: Transfer) {
        while (true) {
            val current = entries.value
            val entry = current[storyId]?.takeIf { it.transfer === transfer } ?: return
            val remaining = entry.references - 1
            val next = if (remaining == 0) current - storyId else current + (storyId to entry.copy(references = remaining))
            if (entries.compareAndSet(current, next)) {
                if (remaining == 0) transfer.scope.cancel()
                return
            }
        }
    }
}
