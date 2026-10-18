package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.LazyListState
import com.fleeksoft.ksoup.Ksoup
import androidx.compose.ui.text.AnnotatedString
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.PortableCommentThreadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import kotlin.math.min

/** Parse-heavy, style-neutral comment content retained across lazy-list disposal and re-entry. */
@Immutable
internal data class CommentRenderModel(
    val references: CollectedReferenceLinks.Result?,
    val contentBlocks: List<CollectedReferenceLinks.ContentBlock>,
)

/**
 * UI-thread LRU. Entries are keyed by comment identity and source content, which acts as the
 * content revision for the legacy mutable comment model.
 */
internal object CommentRenderModelCache {
    private const val MAX_ENTRIES = 192
    private const val MAX_CACHEABLE_SOURCE_CHARS = 64 * 1024
    private const val MAX_TOTAL_WEIGHTED_CHARS = 1024 * 1024
    private val entries = CommentContentLruEntries<Key, CommentRenderModel>()
    private var totalWeightedChars = 0

    fun get(
        commentId: Int,
        expandedHtml: String?,
        collectLinks: Boolean,
    ): CommentRenderModel {
        val source = expandedHtml.orEmpty()
        val key = Key(commentId, source, collectLinks)
        entries[key]?.let { cached ->
            return cached
        }

        val model = prepare(expandedHtml, collectLinks)
        install(commentId, source, collectLinks, model)
        return model
    }

    fun peek(commentId: Int, source: String, collectLinks: Boolean): CommentRenderModel? =
        entries.peek(Key(commentId, source, collectLinks))

    fun prepare(expandedHtml: String?, collectLinks: Boolean): CommentRenderModel {
        val references = if (collectLinks) CollectedReferenceLinks.parse(expandedHtml) else null
        val blocks = references
            ?.takeIf(CollectedReferenceLinks.Result::hasLinks)
            ?.contentBlocks
            ?: listOf(CollectedReferenceLinks.ContentBlock.text(expandedHtml))
        return CommentRenderModel(references, blocks)
    }

    fun install(commentId: Int, source: String, collectLinks: Boolean, model: CommentRenderModel) {
        val key = Key(commentId, source, collectLinks)
        if (key in entries) return
        removePriorRevisions(commentId, source)
        if (source.length <= MAX_CACHEABLE_SOURCE_CHARS) remember(key, model)
    }

    private fun remember(key: Key, model: CommentRenderModel) {
        val weight = key.source.length * 2
        while (entries.isNotEmpty() &&
            (entries.size >= MAX_ENTRIES || totalWeightedChars + weight > MAX_TOTAL_WEIGHTED_CHARS)
        ) {
            remove(entries.oldestKey())
        }
        entries.install(key, model)
        totalWeightedChars += weight
    }

    private fun removePriorRevisions(commentId: Int, source: String) {
        entries.keys.filter { it.commentId == commentId && it.source != source }.forEach(::remove)
    }

    private fun remove(key: Key) {
        if (entries.remove(key) != null) totalWeightedChars -= key.source.length * 2
    }

    internal fun clearForTest() {
        entries.clear()
        totalWeightedChars = 0
    }

    internal fun entryCountForTest(): Int = entries.size

    private data class Key(
        val commentId: Int,
        val source: String,
        val collectLinks: Boolean,
    )
}

internal fun collapsedCommentPreview(
    commentId: Int,
    rawHtml: String?,
    needed: Boolean,
    parse: (String) -> String = { html -> Ksoup.parse(html).text() },
): String? = if (needed) {
    CommentCollapsedPreviewCache.get(commentId, rawHtml.orEmpty(), parse)
} else {
    null
}

private object CommentCollapsedPreviewCache {
    private const val MAX_ENTRIES = 192
    private const val MAX_TOTAL_KEY_CHARS = 48 * 1024
    private val entries = CommentContentLruEntries<PreviewKey, String>()
    private var totalKeyChars = 0

    fun get(commentId: Int, rawHtml: String, parse: (String) -> String): String {
        val sourcePrefix = rawHtml.take(240)
        val key = PreviewKey(commentId, sourcePrefix)
        entries[key]?.let { cached ->
            return cached
        }
        entries.keys.filter { it.commentId == commentId }.forEach(::remove)
        val preview = parse(sourcePrefix).replace('\n', ' ').take(120)
        while (entries.isNotEmpty() &&
            (entries.size >= MAX_ENTRIES || totalKeyChars + sourcePrefix.length > MAX_TOTAL_KEY_CHARS)
        ) {
            remove(entries.oldestKey())
        }
        entries.install(key, preview)
        totalKeyChars += sourcePrefix.length
        return preview
    }

    private fun remove(key: PreviewKey) {
        if (entries.remove(key) != null) totalKeyChars -= key.sourcePrefix.length
    }

    private data class PreviewKey(val commentId: Int, val sourcePrefix: String)
}

/** UI-owned cache of immutable text; pure worker preparation never accesses these maps. */
internal object CommentHtmlTextCache {
    private const val MAX_ENTRIES = 384
    private const val MAX_CACHEABLE_SOURCE_CHARS = 64 * 1024
    private const val MAX_TOTAL_WEIGHTED_CHARS = 2 * 1024 * 1024
    private val entries = CommentContentLruEntries<String, PreparedCommentHtml>()
    private var totalWeightedChars = 0

    fun get(
        html: String,
        highlightQuotes: Boolean = true,
        retainQuoteDecoration: Boolean = false,
    ): AnnotatedString = getPrepared(html).text(highlightQuotes, retainQuoteDecoration)

    fun getPrepared(html: String): PreparedCommentHtml {
        entries[html]?.let { return it }
        val prepared = prepareCommentHtmlContent(html)
        install(html, prepared)
        return prepared
    }

    fun contains(html: String): Boolean = html in entries

    fun install(html: String, text: PreparedCommentHtml) {
        if (html in entries || html.length > MAX_CACHEABLE_SOURCE_CHARS) return
        val weight = text.weightedChars(html.length)
        while (entries.isNotEmpty() &&
            (entries.size >= MAX_ENTRIES || totalWeightedChars + weight > MAX_TOTAL_WEIGHTED_CHARS)
        ) {
            remove(entries.oldestKey())
        }
        entries.install(html, text)
        totalWeightedChars += weight
    }

    private fun remove(html: String) {
        val removed = entries.remove(html) ?: return
        totalWeightedChars -= removed.weightedChars(html.length)
    }

    internal fun clearForTest() {
        entries.clear()
        totalWeightedChars = 0
    }

    internal fun entryCountForTest(): Int = entries.size
}

/** Called from a UI coroutine. Only detached preparation runs on Default; installation stays here. */
internal suspend fun prefetchCommentRenderModels(
    comments: List<PortableCommentItem>,
    collectLinks: Boolean,
    prepareHtml: (String) -> PreparedCommentHtml = ::prepareCommentHtmlContent,
) {
    for (comment in comments) {
        currentCoroutineContext().ensureActive()
        // Snapshot cache lookups on their owner. Workers receive immutable models and strings only.
        val source = comment.expandedAnchorText.orEmpty()
        val cached = CommentRenderModelCache.peek(comment.id, source, collectLinks)
        val missing = cached?.contentBlocks?.mapNotNull { it.bodyHtml }
            ?.distinct()?.filterNot(CommentHtmlTextCache::contains)
        if (cached != null && missing.isNullOrEmpty()) continue
        val prepared = withContext(Dispatchers.Default) {
            try {
                val model = cached ?: CommentRenderModelCache.prepare(source, collectLinks)
                val missingTexts = missing ?: model.contentBlocks.mapNotNull { it.bodyHtml }.distinct()
                model to missingTexts.associateWith { html ->
                    coroutineContext.ensureActive()
                    prepareHtml(html)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A failed comment must not prevent other rows from preparing or install a
                // partial model. Successful earlier comments are already available to rendering.
                null
            }
        } ?: continue
        CommentRenderModelCache.install(comment.id, source, collectLinks, prepared.first)
        prepared.second.forEach { (html, text) -> CommentHtmlTextCache.install(html, text) }
    }
}

/** Finish the current comment, then switch to the newest viewport without queuing old windows. */
internal suspend fun prefetchCommentWindows(
    windows: Flow<List<PortableCommentItem>>,
    prepare: suspend (PortableCommentItem) -> Unit,
) = coroutineScope {
    val requests = Channel<List<PortableCommentItem>>(Channel.CONFLATED)
    launch {
        try {
            windows.collect { requests.send(it) }
        } finally {
            requests.close()
        }
    }
    var pending = emptyList<PortableCommentItem>()
    var index = 0
    while (true) {
        requests.tryReceive().getOrNull()?.let {
            pending = it
            index = 0
        }
        if (index == pending.size) {
            pending = requests.receiveCatching().getOrNull() ?: break
            index = 0
        } else {
            prepare(pending[index++])
        }
    }
}

/** Small first viewport only; do not delay presentation to render an entire discussion. */
suspend fun prepareInitialCommentContent(
    thread: PortableCommentThreadState,
    collectLinks: Boolean,
    targetCommentId: Int = 0,
) {
    val visible = thread.visibleComments
    val start = visible.indexOfFirst { it.comment.id == targetCommentId }.coerceAtLeast(0)
    var remainingChars = 32 * 1024
    val initial = visible.subList(start, min(start + 8, visible.size)).map { it.comment }.takeWhile {
        remainingChars -= it.expandedAnchorText.orEmpty().length
        remainingChars >= 0
    }
    prefetchCommentRenderModels(initial, collectLinks)
}

@Composable
internal fun PrefetchCommentContent(
    listState: LazyListState,
    comments: List<PortableCommentItem>,
    collectLinks: Boolean,
    headerItems: Int = 0,
) {
    LaunchedEffect(listState, comments, collectLinks) {
        val windows = snapshotFlow {
            val items = listState.layoutInfo.visibleItemsInfo
            (items.firstOrNull()?.index ?: 0) to (items.lastOrNull()?.index ?: 0)
        }.distinctUntilChanged().map { (first, last) ->
            val start = (first - headerItems - 8).coerceIn(0, comments.size)
            val end = (last - headerItems + 25).coerceIn(start, comments.size)
            val visibleStart = (first - headerItems).coerceIn(start, end)
            // Visible/upcoming rows take priority over the small retained window behind them.
            comments.subList(visibleStart, end) + comments.subList(start, visibleStart).asReversed()
        }
        prefetchCommentWindows(windows) { comment ->
            prefetchCommentRenderModels(listOf(comment), collectLinks)
        }
    }
}
