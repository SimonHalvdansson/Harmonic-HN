package com.simon.harmonichackernews.debug

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.utils.HackerNewsLinks

/** A deterministic nested Algolia response for exercising cached comments without a network. */
object DebugCachedPostFixture {
    const val storyId: Int = 900_000_001

    val payload: String = """
        {
          "id": 900000001,
          "title": "A cached post for offline UI testing",
          "author": "offline_author",
          "points": 123,
          "created_at_i": 1776345600,
          "type": "story",
          "text": "<p>This post is written into the local cache by the Debug settings screen. It is safe to use when testing the comments UI without an internet connection.</p>",
          "children": [
            {
              "id": 900000011,
              "parent_id": 900000001,
              "author": "cache_reader",
              "created_at_i": 1776349200,
              "text": "<p>This is a cached top-level comment. It should be available immediately when the post opens.</p>",
              "children": [
                {
                  "id": 900000012,
                  "parent_id": 900000011,
                  "author": "offline_reply",
                  "created_at_i": 1776352800,
                  "text": "<p>And this is a nested reply, also loaded entirely from the same cached payload.</p>",
                  "children": []
                }
              ]
            },
            {
              "id": 900000021,
              "parent_id": 900000001,
              "author": "layout_tester",
              "created_at_i": 1776356400,
              "text": "<p>This longer comment gives the list something substantial to render. It includes <a href=\"https://example.com/cached\">a harmless example link</a>, enough text to wrap across several lines, and a second sentence so spacing and actions can be inspected in a realistic-looking thread.</p>",
              "children": [
                {
                  "id": 900000022,
                  "parent_id": 900000021,
                  "author": "reply_checker",
                  "created_at_i": 1776360000,
                  "text": "<p>The reply is cached too.</p>",
                  "children": []
                },
                {
                  "id": 900000023,
                  "parent_id": 900000021,
                  "author": "another_reply",
                  "created_at_i": 1776363600,
                  "text": "<p>Multiple replies make the nesting and collapse controls easier to exercise.</p>",
                  "children": []
                }
              ]
            },
            {
              "id": 900000031,
              "parent_id": 900000001,
              "author": "quiet_reader",
              "created_at_i": 1776367200,
              "text": "<p>A final top-level comment keeps the fixture useful for sorting and filtering checks.</p>",
              "children": []
            }
          ]
        }
    """.trimIndent()

    suspend fun seed(storeStory: suspend (Int, String) -> Boolean): Boolean =
        storeStory(storyId, payload)

    fun story(): Story = Story().apply {
        id = storyId
        title = "A cached post for offline UI testing"
        by = "offline_author"
        score = 123
        time = 1776345600
        descendants = 6
        text = "<p>This post is written into the local cache by the Debug settings screen. It is safe to use when testing the comments UI without an internet connection.</p>"
        url = HackerNewsLinks.itemUrl(storyId)
        isLink = false
        loaded = true
    }
}
