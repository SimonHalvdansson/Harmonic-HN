package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.utils.HtmlTextUtils
import kotlinx.serialization.json.JsonPrimitive

data class DiscussionCoverage(val eligible: Int, val included: Int, val shortened: Int, val cached: Boolean)

internal data class DiscussionSummaryInput(val text: String, val coverage: DiscussionCoverage)

private val placeholders = setOf("[deleted]", "[removed]", "[dead]", "[flagged]", "[delayed]")

internal fun PortableCommentItem.mayHaveDiscussionText(): Boolean =
    id > 0 && !text.isNullOrBlank() && text.orEmpty().trim().lowercase() !in placeholders

internal fun prepareDiscussionSummary(
    title: String?,
    body: String?,
    comments: List<PortableCommentItem>,
    cached: Boolean,
    limit: Int,
    checkActive: () -> Unit = {},
): DiscussionSummaryInput? {
    data class Entry(val id: Int, val parent: Int, val text: String) {
        fun encode() = """{"id":$id,"parent_id":$parent,"text":${JsonPrimitive(text)}}"""
    }
    fun clip(text: String, max: Int): String {
        if (text.length <= max) return text
        val end = text.lastIndexOf(' ', max).takeIf { it >= max / 2 } ?: max
        return text.take(if (text[end - 1].isHighSurrogate()) end - 1 else end).trimEnd()
    }
    val seen = mutableSetOf<Int>()
    val entries = comments.asSequence().drop(1)
        .filter { it.mayHaveDiscussionText() }
        .sortedWith(compareBy(PortableCommentItem::sortOrder, PortableCommentItem::id))
        .mapNotNull {
            checkActive()
            val text = HtmlTextUtils.plainText(it.text).trim()
            if (text.isEmpty() || text.lowercase() in placeholders || !seen.add(it.id)) null
            else Entry(it.id, it.parent, text)
        }.toList()
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
        fun takeRanked(entry: Entry, ceiling: Int = limit) {
            checkActive()
            if (entry.id in chosen || (entry.parent in byId && entry.parent !in chosen)) return
            val text = clip(entry.text, (limit / 8).coerceIn(120, 1600))
            val row = entry.copy(text = text).encode()
            if (take(entry.id, row, ceiling) && text != entry.text) shortened++
        }
        // Reserve some room for other top-level viewpoints, then spend the rest in HN rank order.
        val rootCeiling = length + (limit - length) / 3
        entries.asSequence().filter { it.parent !in byId }.forEach { takeRanked(it, rootCeiling) }
        entries.forEach { takeRanked(it) }
    }
    if (chosen.isEmpty()) return null
    // The serialization order remains HN's, not the order of the reservation pass.
    return DiscussionSummaryInput(
        entries.mapNotNull { chosen[it.id] }.joinToString(",", prefix, suffix),
        DiscussionCoverage(entries.size, chosen.size, shortened, cached),
    )
}
