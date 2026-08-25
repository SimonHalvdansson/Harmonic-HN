package com.simon.harmonichackernews.network

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AlgoliaCommentsParserTest {
    @Test
    fun blankAndNullParentsDiscardTheirSubtrees() = runTest {
        val parsed = AlgoliaCommentsParser().parse(
            """
            {
              "id": 100,
              "children": [
                {
                  "id": 1,
                  "author": "blank-parent",
                  "text": "   ",
                  "children": [
                    {"id": 2, "parent_id": 1, "author": "kept", "text": "discarded child"}
                  ]
                },
                {
                  "id": 3,
                  "author": "null-parent",
                  "text": null,
                  "children": [
                    {"id": 4, "parent_id": 3, "author": "kept", "text": "discarded child"}
                  ]
                },
                {
                  "id": 5,
                  "author": "null-literal-parent",
                  "text": "NULL",
                  "children": [
                    {"id": 6, "parent_id": 5, "author": "kept", "text": "discarded child"}
                  ]
                },
                {
                  "id": 7,
                  "author": "kept",
                  "text": "accepted parent",
                  "children": [
                    {"id": 8, "parent_id": 7, "author": "kept", "text": "accepted child"}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf(7, 8), parsed.comments.map { it.id })
        assertEquals(listOf(0, 1), parsed.comments.map { it.depth })
        assertEquals(1, parsed.comments.first().children)
    }

    @Test
    fun filteredParentDiscardsSubtreeWhileEmptyFiltersKeepAuthors() = runTest {
        val response =
            """
            {
              "id": 100,
              "children": [
                {
                  "id": 1,
                  "author": " SpAmMeR ",
                  "text": "filtered parent",
                  "children": [
                    {"id": 2, "parent_id": 1, "author": "kept", "text": "discarded child"}
                  ]
                },
                {"id": 3, "author": "MiXeDCase", "text": "accepted"}
              ]
            }
            """.trimIndent()

        val filtered = AlgoliaCommentsParser().parse(
            response,
            filteredUsers = setOf(" spammer ", "SPAMMER", "", "   "),
        )
        assertEquals(listOf(3), filtered.comments.map { it.id })
        assertEquals("MiXeDCase", filtered.comments.single().by)

        val unfiltered = AlgoliaCommentsParser().parse(response)
        assertEquals(listOf(1, 2, 3), unfiltered.comments.map { it.id })
        assertEquals("SpAmMeR", unfiltered.comments.first().by)
    }
}
