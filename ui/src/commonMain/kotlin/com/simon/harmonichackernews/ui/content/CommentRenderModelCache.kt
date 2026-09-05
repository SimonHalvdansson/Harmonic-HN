package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.LazyListState
import com.fleeksoft.ksoup.Ksoup
import androidx.compose.ui.text.AnnotatedString
import com.simon.harmonichackernews.presentation.PortableCommentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.coroutines.coroutineContext
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
    private val entries = mutableMapOf<Key, CommentRenderModel>()
    private val order = ArrayDeque<Key>()
    private var totalWeightedChars = 0

    fun get(
        commentId: Int,
        expandedHtml: String?,
        collectLinks: Boolean,
    ): CommentRenderModel {
        val source = expandedHtml.orEmpty()
        val key = Key(commentId, source, collectLinks)
        entries[key]?.let { cached ->
            order.remove(key)
            order.addLast(key)
            return cached
        }

        val model = prepare(expandedHtml, collectLinks)
        install(commentId, source, collectLinks, model)
        return model
    }

    fun peek(commentId: Int, source: String, collectLinks: Boolean): CommentRenderModel? =
        entries[Key(commentId, source, collectLinks)]

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
        while (order.isNotEmpty() &&
            (order.size >= MAX_ENTRIES || totalWeightedChars + weight > MAX_TOTAL_WEIGHTED_CHARS)
        ) {
            remove(order.first())
        }
        entries[key] = model
        order.addLast(key)
        totalWeightedChars += weight
    }

    private fun removePriorRevisions(commentId: Int, source: String) {
        entries.keys.filter { it.commentId == commentId && it.source != source }.forEach(::remove)
    }

    private fun remove(key: Key) {
        if (entries.remove(key) != null) totalWeightedChars -= key.source.length * 2
        order.remove(key)
    }

    internal fun clearForTest() {
        entries.clear()
        order.clear()
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
    private val entries = mutableMapOf<PreviewKey, String>()
    private val order = ArrayDeque<PreviewKey>()
    private var totalKeyChars = 0

    fun get(commentId: Int, rawHtml: String, parse: (String) -> String): String {
        val sourcePrefix = rawHtml.take(240)
        val key = PreviewKey(commentId, sourcePrefix)
        entries[key]?.let { cached ->
            order.remove(key)
            order.addLast(key)
            return cached
        }
        entries.keys.filter { it.commentId == commentId }.forEach(::remove)
        val preview = parse(sourcePrefix).replace('\n', ' ').take(120)
        while (order.isNotEmpty() &&
            (order.size >= MAX_ENTRIES || totalKeyChars + sourcePrefix.length > MAX_TOTAL_KEY_CHARS)
        ) {
            remove(order.first())
        }
        entries[key] = preview
        order.addLast(key)
        totalKeyChars += sourcePrefix.length
        return preview
    }

    private fun remove(key: PreviewKey) {
        if (entries.remove(key) != null) totalKeyChars -= key.sourcePrefix.length
        order.remove(key)
    }

    private data class PreviewKey(val commentId: Int, val sourcePrefix: String)
}

/** UI-owned cache of immutable text; pure worker preparation never accesses these maps. */
internal object CommentHtmlTextCache {
    private const val MAX_ENTRIES = 384
    private const val MAX_CACHEABLE_SOURCE_CHARS = 64 * 1024
    private const val MAX_TOTAL_WEIGHTED_CHARS = 2 * 1024 * 1024
    private val entries = mutableMapOf<String, AnnotatedString>()
    private val order = ArrayDeque<String>()
    private var totalWeightedChars = 0

    fun get(html: String): AnnotatedString {
        entries[html]?.let { cached ->
            order.remove(html)
            order.addLast(html)
            return cached
        }
        val text = prepareCommentHtml(html)
        install(html, text)
        return text
    }

    fun contains(html: String): Boolean = html in entries

    fun install(html: String, text: AnnotatedString) {
        if (html in entries || html.length > MAX_CACHEABLE_SOURCE_CHARS) return
        val weight = min(Int.MAX_VALUE / 2, html.length * 3)
        while (order.isNotEmpty() &&
            (order.size >= MAX_ENTRIES || totalWeightedChars + weight > MAX_TOTAL_WEIGHTED_CHARS)
        ) {
            remove(order.first())
        }
        entries[html] = text
        order.addLast(html)
        totalWeightedChars += weight
    }

    private fun remove(html: String) {
        if (entries.remove(html) != null) {
            totalWeightedChars -= min(Int.MAX_VALUE / 2, html.length * 3)
        }
        order.remove(html)
    }

    internal fun clearForTest() {
        entries.clear()
        order.clear()
        totalWeightedChars = 0
    }

    internal fun entryCountForTest(): Int = entries.size
}

/** Called from a UI coroutine. Only detached preparation runs on Default; installation stays here. */
internal suspend fun prefetchCommentRenderModels(comments: List<PortableCommentItem>, collectLinks: Boolean) {
    for (comment in comments) {
        coroutineContext.ensureActive()
        val source = comment.expandedAnchorText.orEmpty()
        val cached = CommentRenderModelCache.peek(comment.id, source, collectLinks)
        val model = cached ?: withContext(Dispatchers.Default) {
            CommentRenderModelCache.prepare(source, collectLinks)
        }
        // Read the mutable caches on their owning UI thread, then pass only strings to the worker.
        val missing = model.contentBlocks.mapNotNull { it.bodyHtml }
            .distinct().filterNot(CommentHtmlTextCache::contains)
        if (missing.isEmpty()) {
            CommentRenderModelCache.install(comment.id, source, collectLinks, model)
            continue
        }
        val prepared = withContext(Dispatchers.Default) {
            missing.associateWith { html ->
                coroutineContext.ensureActive()
                prepareCommentHtml(html)
            }
        }
        CommentRenderModelCache.install(comment.id, source, collectLinks, model)
        prepared.forEach { (html, text) -> CommentHtmlTextCache.install(html, text) }
    }
}

@Composable
internal fun PrefetchCommentContent(
    listState: LazyListState,
    comments: List<PortableCommentItem>,
    collectLinks: Boolean,
    headerItems: Int = 0,
) {
    LaunchedEffect(listState, comments, collectLinks) {
        snapshotFlow {
            val items = listState.layoutInfo.visibleItemsInfo
            (items.firstOrNull()?.index ?: 0) to (items.lastOrNull()?.index ?: 0)
        }.distinctUntilChanged().collectLatest { (first, last) ->
            val start = (first - headerItems - 8).coerceIn(0, comments.size)
            val end = (last - headerItems + 25).coerceIn(start, comments.size)
            prefetchCommentRenderModels(comments.subList(start, end), collectLinks)
        }
    }
}
