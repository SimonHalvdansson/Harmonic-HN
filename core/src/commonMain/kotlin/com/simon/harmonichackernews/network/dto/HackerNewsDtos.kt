package com.simon.harmonichackernews.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire representation of an item returned by the official Hacker News API. */
@Serializable
data class HackerNewsItemDto(
    val id: Int = 0,
    val deleted: Boolean = false,
    val type: String? = null,
    val by: String? = null,
    val time: Int = 0,
    val text: String? = null,
    val dead: Boolean = false,
    val parent: Int = 0,
    val kids: List<Int> = emptyList(),
    val url: String? = null,
    val score: Int = 0,
    val title: String? = null,
    val descendants: Int = 0,
    val parts: List<Int> = emptyList(),
)

@Serializable
data class HackerNewsUserDto(
    val id: String = "",
    val created: Long = 0L,
    val karma: Int = 0,
    val about: String? = null,
    val submitted: List<Int> = emptyList(),
)

/** Wire representation of an Algolia search response. */
@Serializable
data class AlgoliaSearchResponseDto(
    val hits: List<AlgoliaSearchHitDto> = emptyList(),
)

@Serializable
data class AlgoliaSearchHitDto(
    @SerialName("objectID") val objectId: String = "",
    @SerialName("_tags") val tags: List<String> = emptyList(),
    val title: String? = null,
    @SerialName("story_title") val storyTitle: String? = null,
    val points: Int? = null,
    val author: String? = null,
    @SerialName("num_comments") val commentCount: Int? = null,
    @SerialName("created_at_i") val createdAt: Int? = null,
    val url: String? = null,
    @SerialName("story_url") val storyUrl: String? = null,
    @SerialName("story_text") val storyText: String? = null,
    @SerialName("comment_text") val commentText: String? = null,
    @SerialName("story_id") val storyId: Int? = null,
    @SerialName("parent_id") val parentId: Int? = null,
)
