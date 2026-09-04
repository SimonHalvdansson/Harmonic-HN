package com.simon.harmonichackernews.network

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class AlgoliaCommentsParserTest {
    @Test
    fun normalStringsAndNullsKeepQuotedNumbersDistinctFromNumbers() = runTest {
        val response = """{"children":[
          {"id":"1e2","text":"quoted exponent"},
          {"id":"1.0","text":"quoted decimal"},
          {"id":"123","text":"quoted integer"},
          {"id":1e2,"text":"exponent"},
          {"id":1.0,"text":"decimal"},
          {"id":null,"author":null,"text":"null fields"},
          {"id":7,"text":null}
        ]}"""
        val parsed = AlgoliaCommentsParser().parse(response)
        assertEquals(listOf(0, 0, 123, 100, 1, 0), parsed.comments.map { it.id })
        assertEquals("", parsed.comments.last().by)
    }

    @Test
    fun orderingUsesRawChildCountsAndStableTies() = runTest {
        val response = """{"children":[
          {"id":1,"text":"one","children":[
            {"id":11,"text":"first tie","children":[{"text":null}]},
            {"id":12,"text":"largest","children":[{"text":null},{"text":null}]},
            {"id":13,"text":"second tie","children":[{"text":null}]}
          ]},
          {"id":2,"text":"two"},{"id":3,"text":"three"},{"id":4,"text":"four"}
        ]}"""
        val parsed = AlgoliaCommentsParser().parse(response, listOf(2, 1, 2))
        assertEquals(listOf(2, 1, 12, 11, 13, 3, 4), parsed.comments.map { it.id })
        assertEquals(listOf(0, 0, 1, 1, 1, 0, 0), parsed.comments.map { it.depth })
        assertEquals(listOf(0, 3, 2, 1, 1, 0, 0), parsed.comments.map { it.children })
    }

    @Test
    fun unusualScalarsKeepLegacyCoercions() = runTest {
        val response = """{"points":2.0,"title":123,"children":[
          {"id":"42","parent_id":1e2,"created_at_i":3.0,"author":123,"text":456},
          {"id":"1e2","parent_id":1.5,"created_at_i":2147483648,"author":false,"text":"kept"},
          {"id":null,"parent_id":null,"created_at_i":null,"author":null,"text":"null fields"},
          {"text":{},"children":[{"text":"hidden"}]},
          {"text":false},{"text":null}
        ]}"""
        val parsed = AlgoliaCommentsParser().parse(response)
        assertEquals("123", parsed.title)
        assertEquals(2, parsed.points)
        assertEquals(listOf(42, 0, 0), parsed.comments.map { it.id })
        assertEquals(listOf(100, 0, 0), parsed.comments.map { it.parent })
        assertEquals(listOf(3, 0, 0), parsed.comments.map { it.time })
        assertEquals(listOf("123", "", ""), parsed.comments.map { it.by })
        assertEquals(listOf("456", "kept", "null fields"), parsed.comments.map { it.text })
    }

    @Test
    fun nullChildrenStillFailRatherThanSilentlyBecomingEmpty() = runTest {
        assertFailsWith<ApiDecodingException> {
            AlgoliaCommentsParser().parse("""{"children":[{"text":"parent","children":null}]}""")
        }
    }

    @Test
    fun reusedSummaryPreservesMetadataAndCountsFilteredSubtrees() = runTest {
        val response = """{
          "id":100,"title":true,"type":null,"points":"3.5","story_id":null,
          "story_url":"https://example.com/story","text":"<p>original</p>",
          "preview_image_url":"https://example.com/image","preview_image_url_loaded":true,
          "preview_image_tint_color":123,"preview_image_tint_color_loaded":true,
          "preview_image_tint_source_url":"source","preview_image_tint_base_color":456,
          "preview_image_tint_mode":"mode","favicon_tint_color":789,
          "favicon_tint_color_loaded":true,"favicon_tint_source_url":"favicon",
          "favicon_tint_base_color":321,"favicon_tint_mode":"favicon-mode",
          "children":[{"author":"blocked","text":"parent","children":[{"text":"child"}]},
                      {"text":null,"children":[{"text":"also hidden"}]},{"text":"visible"}]
        }"""
        val parsed = AlgoliaCommentsParser().parse(response, filteredUsers = setOf("blocked"))
        assertEquals(1, parsed.comments.size)
        val reused = Json.parseToJsonElement(assertNotNull(parsed.cacheSummary).encode(100)).jsonObject
        val standalone = Json.parseToJsonElement(assertNotNull(JSONParser.compactAlgoliaStoryResponse(response, 100)))
        assertEquals(standalone, reused)
        assertEquals("5", reused["descendants"].toString())
        assertEquals("\"true\"", reused["title"].toString())
        assertEquals("3", reused["points"].toString())
        assertEquals("\"story\"", reused["type"].toString())
        assertEquals("\"favicon-mode\"", reused["favicon_tint_mode"].toString())
    }

    @Test
    fun summaryRetainsPermissiveCountingForNonObjectChildren() {
        val response = """{"children":[null,1,{"children":[false,{},null]},{"children":null}]}"""
        val summary = Json.parseToJsonElement(assertNotNull(JSONParser.compactAlgoliaStoryResponse(response, 100))).jsonObject
        assertEquals("7", summary["descendants"].toString())
        assertEquals("100", summary["id"].toString())
    }

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
