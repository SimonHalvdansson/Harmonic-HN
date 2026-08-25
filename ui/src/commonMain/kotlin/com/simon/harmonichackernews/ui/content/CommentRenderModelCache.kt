package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Immutable
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
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

        val references = if (collectLinks) CollectedReferenceLinks.parse(expandedHtml) else null
        val blocks = references
            ?.takeIf(CollectedReferenceLinks.Result::hasLinks)
            ?.contentBlocks
            ?: listOf(CollectedReferenceLinks.ContentBlock.text(expandedHtml))
        val model = CommentRenderModel(references, blocks)
        removePriorRevisions(commentId, source)
        if (source.length <= MAX_CACHEABLE_SOURCE_CHARS) remember(key, model)
        return model
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

/** Parsed DOMs are style-neutral; link color and callbacks are applied when the text is built. */
internal object CommentHtmlDocumentCache {
    private const val MAX_ENTRIES = 384
    private const val MAX_CACHEABLE_SOURCE_CHARS = 64 * 1024
    private const val MAX_TOTAL_WEIGHTED_CHARS = 2 * 1024 * 1024
    private val entries = mutableMapOf<String, Document>()
    private val order = ArrayDeque<String>()
    private var totalWeightedChars = 0

    fun get(html: String): Document {
        entries[html]?.let { cached ->
            order.remove(html)
            order.addLast(html)
            return cached
        }
        val document = Ksoup.parse(preserveLegacyCommentParagraphSpacing(html))
        if (html.length > MAX_CACHEABLE_SOURCE_CHARS) return document
        val weight = min(Int.MAX_VALUE / 2, html.length * 3)
        while (order.isNotEmpty() &&
            (order.size >= MAX_ENTRIES || totalWeightedChars + weight > MAX_TOTAL_WEIGHTED_CHARS)
        ) {
            remove(order.first())
        }
        entries[html] = document
        order.addLast(html)
        totalWeightedChars += weight
        return document
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
