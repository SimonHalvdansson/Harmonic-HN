package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscussionSummaryInputTest {
    private fun prepareSummary(
        title: String?,
        body: String?,
        comments: List<PortableCommentItem>,
        cached: Boolean,
        limit: Int,
    ) = prepareDiscussionSummary(title, body, readableDiscussionComments(comments) {}, cached, limit)

    private fun item(id: Int, parent: Int, text: String) =
        PortableCommentItem(CommentSnapshot(id, parentId = parent, text = text), CommentPresentationSnapshot(sortOrder = id))

    private val header = item(1, 0, "Not a comment")

    @Test fun includesNestedRepliesAndNormalizesTextIndependentlyOfUiOrder() {
        val root = item(2, 1, "<p>Two &amp; three</p>")
        val reply = item(3, 2, "Reply \"with quotes\" 😺")
        val placeholder = item(4, 1, "<p>[deleted]</p>")
        val input = assertNotNull(prepareSummary("Post", null,
            listOf(header, reply, root, placeholder, reply), cached = true, limit = 1_000))
        val rows = Json.parseToJsonElement(input.text).jsonObject.getValue("comments").jsonArray
        assertEquals(DiscussionCoverage(2, 2, 0, true), input.coverage)
        assertEquals("Two & three", rows[0].jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals("2", rows[1].jsonObject.getValue("parent_id").jsonPrimitive.content)
        assertEquals("Reply \"with quotes\" 😺", rows[1].jsonObject.getValue("text").jsonPrimitive.content)
        assertFalse(input.text.contains("Not a comment"))
    }

    @Test fun boundedInputKeepsMultipleRootsAndReportsOmissions() {
        val roots = listOf(item(2, 1, "large ".repeat(500)), item(20, 1, "Other branch"))
        val replies = (3..18).map { item(it, 2, "long reply ".repeat(30)) }
        val input = assertNotNull(prepareSummary("Post", null,
            listOf(header, roots[0]) + replies + roots[1], cached = false, limit = 450))
        assertTrue(input.text.length <= 450)
        assertTrue(input.text.contains("Other branch"))
        assertTrue(input.coverage.included < input.coverage.eligible)
        assertTrue(input.coverage.shortened > 0)
        assertEquals(input.coverage.included,
            Json.parseToJsonElement(input.text).jsonObject.getValue("comments").jsonArray.size)
    }

    @Test fun doesNotSendHeaderOrPlaceholdersAlone() {
        assertNull(prepareSummary(null, null, listOf(header, item(2, 1, "[dead]")), false, 1_000))
    }

    @Test fun boundedSelectionPreservesHnRankAndFavorsEarlierReplies() {
        val first = item(2, 1, "First branch")
        val high = item(3, 2, "First reply ".repeat(20))
        val low = item(4, 2, "Later reply ".repeat(20))
        val second = item(5, 1, "Second branch")
        val input = assertNotNull(prepareSummary("Post", null,
            listOf(header, second, low, high, first), false, 300))
        val rows = Json.parseToJsonElement(input.text).jsonObject.getValue("comments").jsonArray
        assertEquals(listOf("2", "3", "5"), rows.map { it.jsonObject.getValue("id").jsonPrimitive.content })
    }
}
