package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.PreparedCommentThread
import com.simon.harmonichackernews.data.Story
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Selects the configured comments source and owns the transport-neutral Algolia-to-official-API
 * fallback policy. Platform shells only decide how to present the result.
 */
class CommentThreadRepository(
    private val algoliaRepository: AlgoliaRepository,
    private val hackerNewsRepository: HackerNewsRepository,
    private val algoliaCommentsParser: AlgoliaCommentsParser = AlgoliaCommentsParser(),
    private val preloads: CommentsPreloadRepository? = null,
    requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val requests = AlgoliaCommentRequests(algoliaRepository, requestDispatcher)

    fun acquireAlgoliaRequest(storyId: Int): AlgoliaCommentRequest =
        preloads?.acquireAlgoliaRequest(storyId) ?: requests.acquire(storyId)

    private val officialLoader = OfficialCommentThreadLoader(hackerNewsRepository)
    private val missingTopLevelComments = MissingTopLevelComments(
        hackerNewsRepository, algoliaCommentsParser, requestDispatcher,
    )

    /** Shared by fresh loads and both ready/in-flight preloads when a discussion is opened. */
    suspend fun reconcileMissingTopLevelComments(
        result: CommentThreadLoadResult.Algolia,
        filteredUsers: Set<String>,
        onPending: suspend () -> Unit = {},
    ): CommentThreadLoadResult.Algolia = missingTopLevelComments.reconcile(
        result,
        result.parsed.cacheSummary?.topLevelCommentIds.orEmpty(),
        filteredUsers,
        onPending,
    )

    suspend fun takePreloadedAlgolia(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
        awaitInFlight: Boolean = true,
    ): PreloadedCommentsThread? = preloads?.takeOrAwait(
        storyId,
        topLevelCommentIds,
        filteredUsers,
        awaitInFlight,
    )

    suspend fun takePreloadedOfficial(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
        awaitInFlight: Boolean = true,
        onProgress: (suspend (CommentThreadLoadResult.Official) -> Unit)? = null,
    ): PreloadedOfficialCommentsThread? = preloads?.takeOfficialOrAwait(
        storyId,
        topLevelCommentIds,
        filteredUsers,
        awaitInFlight,
        onProgress,
    )

    suspend fun load(
        storyId: Int,
        useAlgolia: Boolean,
        filteredUsers: Set<String> = emptySet(),
        topLevelCommentIds: List<Int> = emptyList(),
        cachedThread: PreparedCommentThread? = null,
        onAlgoliaFallback: () -> Unit = {},
        algoliaRequest: AlgoliaCommentRequest? = null,
        reuseDisplay: AlgoliaCommentsDisplayReuse? = null,
        refreshTopLevelOrder: Boolean = false,
        onOfficialProgress: (suspend (CommentThreadLoadResult.Official) -> Unit)? = null,
    ): CommentThreadLoadResult {
        require(storyId > 0) { "A positive Hacker News item ID is required" }

        if (!useAlgolia) {
            return officialLoader.load(storyId, filteredUsers, usedAsFallback = false, onOfficialProgress)
        }

        return try {
            coroutineScope {
                val response = async {
                    if (algoliaRequest != null) {
                        require(algoliaRequest.storyId == storyId)
                        algoliaRequest.await()
                    } else {
                        algoliaRepository.getItemJson(storyId)
                    }
                }
                val orderedIds = if (refreshTopLevelOrder || topLevelCommentIds.isEmpty()) {
                    fetchTopLevelCommentIds(storyId).ifEmpty { topLevelCommentIds }
                } else {
                    topLevelCommentIds
                }
                val responseText = response.await()
                CommentThreadLoadResult.Algolia(
                    responseText,
                    algoliaCommentsParser.parseForDisplay(
                        responseText, orderedIds, filteredUsers, cachedThread, reuseDisplay,
                    ),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error.shouldFallBackToOfficialApi()) {
                onAlgoliaFallback()
                officialLoader.load(storyId, filteredUsers, usedAsFallback = true, onOfficialProgress)
            } else {
                CommentThreadLoadResult.Failure(
                    noInternet = error !is HttpStatusException,
                    source = CommentThreadSource.ALGOLIA,
                    cause = error,
                )
            }
        }
    }

    private suspend fun fetchTopLevelCommentIds(storyId: Int): List<Int> {
        return try {
            hackerNewsRepository.getStory(storyId)?.kids?.toList().orEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun parseAlgolia(
        response: String,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
        cachedThread: PreparedCommentThread? = null,
    ): AlgoliaCommentsResponse = algoliaCommentsParser.parseForDisplay(
        response, topLevelCommentIds, filteredUsers, cachedThread,
    )

    private fun Exception.shouldFallBackToOfficialApi(): Boolean =
        this is HttpRequestTimeoutException ||
            this is HttpStatusException && (statusCode == 404 || statusCode >= 500)
}

/** Loads HN's per-item API with bounded concurrency and publishes settled top-level branches. */
class OfficialCommentThreadLoader(
    private val hackerNewsRepository: HackerNewsRepository,
) {
    // Share the limit across branches and simultaneous loads; release each permit before
    // descending so a wide tree cannot flood the transport or deadlock waiting for children.
    private val requests = Semaphore(WORKER_COUNT)
    suspend fun load(
        storyId: Int,
        filteredUsers: Set<String>,
        usedAsFallback: Boolean,
        onProgress: (suspend (CommentThreadLoadResult.Official) -> Unit)? = null,
    ): CommentThreadLoadResult {
        return try {
            val story = hackerNewsRepository.getStory(storyId)
                ?: return CommentThreadLoadResult.Failure(
                    noInternet = false,
                    source = CommentThreadSource.OFFICIAL,
                )
            val normalizedFilteredUsers = filteredUsers.mapNotNullTo(mutableSetOf()) {
                it.trim().takeIf(String::isNotEmpty)
            }
            val ids = story.kids ?: intArrayOf()
            val roots = MutableList<Branch?>(ids.size) { null }
            val pending = ids.mapIndexed { index, id -> CommentWork(id, 0, roots, index, index) }
            loadCommentForest(story, usedAsFallback, normalizedFilteredUsers, roots, pending, onProgress)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommentThreadLoadResult.Failure(
                noInternet = error !is HttpStatusException,
                source = CommentThreadSource.OFFICIAL,
                cause = error,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun loadCommentForest(
        story: Story,
        usedAsFallback: Boolean,
        filteredUsers: Set<String>,
        roots: List<Branch?>,
        pending: List<CommentWork>,
        onProgress: (suspend (CommentThreadLoadResult.Official) -> Unit)?,
    ): CommentThreadLoadResult.Official = coroutineScope {
        val failed = mutableListOf<CommentWork>()
        val outstanding = IntArray(roots.size)
        pending.forEach { outstanding[it.rootIndex]++ }
        var lastPublication: TimeMark? = null
        var progressPending = false
        if (pending.isEmpty()) return@coroutineScope CommentThreadLoadResult.Official(story, mutableListOf(), usedAsFallback)
        val work = Channel<CommentWork>(Channel.UNLIMITED)
        val completed = Channel<CompletedComment>(WORKER_COUNT)
        // Only these workers suspend in the transport. The coordinator owns the tree and queue,
        // so wide/deep discussions don't create a coroutine and awaitAll list for every branch.
        val workers = List(WORKER_COUNT) {
            launch {
                for (next in work) {
                    val result = try {
                        CompletedComment(next, requests.withPermit { hackerNewsRepository.getComment(next.id) })
                    } catch (error: CancellationException) {
                        // A transport may cancel its own request while the parent is still active.
                        // Propagate that cancellation rather than leave the coordinator waiting.
                        this@coroutineScope.cancel(error)
                        throw error
                    } catch (error: Exception) {
                        val missing = error is HttpStatusException && (error.statusCode == 404 || error.statusCode == 410)
                        CompletedComment(next, null, failed = !missing)
                    }
                    completed.send(result)
                }
            }
        }
        try {
            pending.forEach { work.send(it) }
            var remaining = pending.size
            suspend fun publishProgress() {
                onProgress?.invoke(CommentThreadLoadResult.Official(story, flatten(roots, outstanding), usedAsFallback))
                lastPublication = TimeSource.Monotonic.markNow()
                progressPending = false
            }
            while (remaining > 0) {
                val response = if (progressPending) {
                    val waitMillis = (PROGRESS_INTERVAL_MS - checkNotNull(lastPublication).elapsedNow().inWholeMilliseconds).coerceAtLeast(0)
                    select<CompletedComment?> {
                        onTimeout(waitMillis) { null }
                        completed.onReceive { it }
                    }
                } else completed.receive()
                if (response == null) {
                    publishProgress()
                    continue
                }
                val (next, comment, requestFailed) = response
                remaining--
                outstanding[next.rootIndex]--
                next.destination[next.index] = if (requestFailed) {
                    failed.add(next)
                    Missing(next.id, next.depth)
                } else null
                val author = comment?.by?.trim()
                if (comment != null && author != null && author !in filteredUsers) {
                    comment.expanded = true
                    comment.depth = next.depth
                    val childIds = comment.kidsIds
                    val children = MutableList<Branch?>(childIds?.size ?: 0) { null }
                    next.destination[next.index] = Loaded(comment, children)
                    childIds?.forEachIndexed { index, id ->
                        work.send(CommentWork(id, next.depth + 1, children, index, next.rootIndex))
                        outstanding[next.rootIndex]++
                        remaining++
                    }
                }
                if (onProgress != null && remaining > 0 && outstanding[next.rootIndex] == 0 && roots[next.rootIndex] is Loaded) {
                    if (lastPublication == null) publishProgress() else progressPending = true
                }
            }
        } finally {
            work.close()
            workers.forEach { it.cancel() }
            completed.cancel()
        }
        val gaps = mutableListOf<OfficialBranchGap>()
        val comments = flatten(roots, gaps = gaps)
        val retry = if (failed.isEmpty()) null else OfficialCommentRetry(gaps) {
            loadCommentForest(story, usedAsFallback, filteredUsers, roots, failed, onProgress = null)
        }
        CommentThreadLoadResult.Official(story, comments, usedAsFallback, retry)
    }

    private suspend fun flatten(roots: List<Branch?>, outstanding: IntArray? = null,
        gaps: MutableList<OfficialBranchGap>? = null): MutableList<Comment> {
        val pending = ArrayDeque<Branch>()
        for (index in roots.indices.reversed()) {
            if (outstanding == null || outstanding[index] == 0) roots[index]?.let(pending::addLast)
        }
        val comments = mutableListOf<Comment>()
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            when (val branch = pending.removeLast()) {
                is Missing -> gaps?.add(OfficialBranchGap(branch.id, branch.depth, comments.size))
                is Loaded -> {
                    comments.add(branch.comment)
                    for (index in branch.children.indices.reversed()) branch.children[index]?.let(pending::addLast)
                }
            }
        }
        return comments
    }

    private sealed interface Branch
    private class Loaded(val comment: Comment, val children: List<Branch?>) : Branch
    private class Missing(val id: Int, val depth: Int) : Branch

    private class CommentWork(
        val id: Int,
        val depth: Int,
        val destination: MutableList<Branch?>,
        val index: Int,
        val rootIndex: Int,
    )

    private data class CompletedComment(val work: CommentWork, val comment: Comment?, val failed: Boolean = false)

    private companion object {
        const val WORKER_COUNT = 8
        const val PROGRESS_INTERVAL_MS = 200L
    }
}

/** Retains the positions of missing subtrees so retry can reuse all successful requests. */
class OfficialCommentRetry internal constructor(
    internal val gaps: List<OfficialBranchGap>,
    private val retry: suspend () -> CommentThreadLoadResult.Official,
) {
    val count: Int get() = gaps.size
    private val mutex = Mutex()
    private var completed: CommentThreadLoadResult.Official? = null
    suspend fun load(): CommentThreadLoadResult.Official = mutex.withLock {
        completed ?: retry().also { completed = it }
    }
}

internal data class OfficialBranchGap(val id: Int, val depth: Int, val insertionIndex: Int)

enum class CommentThreadSource {
    ALGOLIA,
    OFFICIAL,
}

sealed interface CommentThreadLoadResult {
    data class Algolia(
        val response: String,
        val parsed: AlgoliaCommentsResponse,
    ) : CommentThreadLoadResult

    data class Official(
        val story: Story,
        val comments: MutableList<Comment>,
        val usedAsFallback: Boolean,
        val retry: OfficialCommentRetry? = null,
    ) : CommentThreadLoadResult

    data class Failure(
        val noInternet: Boolean,
        val source: CommentThreadSource,
        val cause: Throwable? = null,
    ) : CommentThreadLoadResult
}
