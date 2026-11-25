package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.utils.HtmlTextUtils
import kotlinx.serialization.json.JsonPrimitive
import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import okio.use

data class DiscussionCoverage(val eligible: Int, val included: Int, val shortened: Int, val cached: Boolean)

internal data class DiscussionSummaryInput(val text: String, val coverage: DiscussionCoverage)

private val placeholders = setOf("[deleted]", "[removed]", "[dead]", "[flagged]", "[delayed]")

internal fun PortableCommentItem.mayHaveDiscussionText(): Boolean =
    id > 0 && !text.isNullOrBlank() && text.orEmpty().trim().lowercase() !in placeholders

internal data class DiscussionComment(val id: Int, val parent: Int, val text: String) {
    fun encode() = """{"id":$id,"parent_id":$parent,"text":${JsonPrimitive(text)}}"""
}

/** Hash the readable thread in prompt order without retaining entire threads across visits. */
internal fun discussionSourceDigest(
    title: String?, body: String?, entries: List<DiscussionComment>, checkActive: () -> Unit,
): String {
    val digest = HashingSink.sha256(blackholeSink())
    digest.buffer().use { sink ->
        sink.writeUtf8(JsonPrimitive(title).toString()).writeByte(10)
        sink.writeUtf8(JsonPrimitive(body).toString()).writeByte(10)
        for (entry in entries) {
            checkActive()
            sink.writeUtf8(entry.encode()).writeByte(10)
        }
    }
    return digest.hash.hex()
}

internal fun readableDiscussionComments(
    comments: List<PortableCommentItem>,
    checkActive: () -> Unit,
): List<DiscussionComment> {
    val seen = mutableSetOf<Int>()
    return comments.asSequence().drop(1)
        .filter { it.mayHaveDiscussionText() }
        .sortedWith(compareBy(PortableCommentItem::sortOrder, PortableCommentItem::id))
        .mapNotNull {
            checkActive()
            val text = HtmlTextUtils.plainText(it.text).trim()
            if (text.isEmpty() || text.lowercase() in placeholders || !seen.add(it.id)) null
            else DiscussionComment(it.id, it.parent, text)
        }.toList()
}

internal fun prepareDiscussionSummary(
    title: String?,
    body: String?,
    entries: List<DiscussionComment>,
    cached: Boolean,
    limit: Int,
    checkActive: () -> Unit = {},
): DiscussionSummaryInput? {
    fun clip(text: String, max: Int): String {
        if (text.length <= max) return text
        val end = text.lastIndexOf(' ', max).takeIf { it >= max / 2 } ?: max
        return text.take(if (text[end - 1].isHighSurrogate()) end - 1 else end).trimEnd()
    }
    if (entries.isEmpty()) return null
    val prefix = """{"post_title":${JsonPrimitive(clip(HtmlTextUtils.plainText(title), 240))},"post_body":${JsonPrimitive(clip(HtmlTextUtils.plainText(body), 400))},"comments":["""
    val suffix = "]}"
    val chosen = linkedMapOf<Int, String>()
    var length = prefix.length + suffix.length
    fun take(id: Int, row: String, ceiling: Int = limit): Boolean {
        val added = row.length + if (chosen.isEmpty()) 0 else 1
        if (added > ceiling - length) return false
        length += added
        chosen[id] = row
        return true
    }
    var shortened = 0
    if (!entries.all { checkActive(); it.text.length <= limit && take(it.id, it.encode()) }) {
        chosen.clear()
        length = prefix.length + suffix.length
        val byId = entries.associateBy { it.id }
        fun takeRanked(entry: DiscussionComment, ceiling: Int = limit) {
            checkActive()
            if (entry.id in chosen || (entry.parent in byId && entry.parent !in chosen)) return
            val text = clip(entry.text, (limit / 8).coerceIn(120, 1600))
            val row = entry.copy(text = text).encode()
            if (take(entry.id, row, ceiling) && text != entry.text) shortened++
        }
        // Reserve some room for other top-level viewpoints, then spend the rest in the loaded thread order.
        val rootCeiling = length + (limit - length) / 3
        entries.asSequence().filter { it.parent !in byId }.forEach { takeRanked(it, rootCeiling) }
        entries.forEach { takeRanked(it) }
    }
    if (chosen.isEmpty()) return null
    // Serialize the selected rows in their original thread order.
    return DiscussionSummaryInput(
        entries.mapNotNull { chosen[it.id] }.joinToString(",", prefix, suffix),
        DiscussionCoverage(entries.size, chosen.size, shortened, cached),
    )
}
