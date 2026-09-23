package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Story
import kotlinx.serialization.json.Json
import com.simon.harmonichackernews.serialization.JsonArray as JSONArray
import com.simon.harmonichackernews.serialization.JsonException as JSONException
import com.simon.harmonichackernews.serialization.JsonObject as JSONObject
import com.simon.harmonichackernews.utils.HackerNewsLinks

object JSONParser {
    const val ALGOLIA_ERROR_STRING: String = "{\"status\":404,\"error\":\"Not Found\"}"
    private val ALGOLIA_JSON = Json { ignoreUnknownKeys = true }
    private const val JSON_NULL_LITERAL = "null"
    private const val CACHED_STORY_SUMMARY_VERSION = 1
    private const val KEY_PREVIEW_IMAGE_URL = "preview_image_url"
    private const val KEY_PREVIEW_IMAGE_URL_LOADED = "preview_image_url_loaded"
    private const val KEY_PREVIEW_IMAGE_TINT_COLOR = "preview_image_tint_color"
    private const val KEY_PREVIEW_IMAGE_TINT_COLOR_LOADED = "preview_image_tint_color_loaded"
    private const val KEY_PREVIEW_IMAGE_TINT_SOURCE_URL = "preview_image_tint_source_url"
    private const val KEY_PREVIEW_IMAGE_TINT_BASE_COLOR = "preview_image_tint_base_color"
    private const val KEY_PREVIEW_IMAGE_TINT_MODE = "preview_image_tint_mode"
    private const val KEY_FAVICON_TINT_COLOR = "favicon_tint_color"
    private const val KEY_FAVICON_TINT_COLOR_LOADED = "favicon_tint_color_loaded"
    private const val KEY_FAVICON_TINT_SOURCE_URL = "favicon_tint_source_url"
    private const val KEY_FAVICON_TINT_BASE_COLOR = "favicon_tint_base_color"
    private const val KEY_FAVICON_TINT_MODE = "favicon_tint_mode"

    internal val ALGOLIA_SUMMARY_FIELDS = listOf(
        "id", "type", "title", "author", "points", "created_at_i", "url", "text",
        "story_id", "parent_id", "story_title", "story_url",
        KEY_PREVIEW_IMAGE_URL, KEY_PREVIEW_IMAGE_URL_LOADED,
        KEY_PREVIEW_IMAGE_TINT_COLOR_LOADED, KEY_PREVIEW_IMAGE_TINT_COLOR,
        KEY_PREVIEW_IMAGE_TINT_SOURCE_URL, KEY_PREVIEW_IMAGE_TINT_BASE_COLOR,
        KEY_PREVIEW_IMAGE_TINT_MODE, KEY_FAVICON_TINT_COLOR_LOADED, KEY_FAVICON_TINT_COLOR,
        KEY_FAVICON_TINT_SOURCE_URL, KEY_FAVICON_TINT_BASE_COLOR, KEY_FAVICON_TINT_MODE,
    )
    private val summarySerializer by lazy {
        AlgoliaItemSerializer(AlgoliaChildrenCountSerializer, 0)
    }

    fun updateTitleBadgeProperties(story: Story?) {
        StoryTextProcessor.applyTitleBadges(story)
    }

    fun compactAlgoliaStoryResponse(response: String?, fallbackId: Int): String? {
        if (response.isNullOrEmpty()
            || JSON_NULL_LITERAL == response
            || ALGOLIA_ERROR_STRING == response
        ) {
            return null
        }

        try {
            val item = ALGOLIA_JSON.decodeFromString(summarySerializer, response)
            return compactAlgoliaStoryFields(item.metadata, fallbackId, item.children)
        } catch (_: IllegalArgumentException) {
            // Retain legacy handling of unusual children values (nulls, primitives, etc.).
        }
        return try {
            val item = JSONObject(response)
            compactAlgoliaStoryFields(item, fallbackId, countAlgoliaComments(item.optJSONArray("children")))
        } catch (_: JSONException) {
            null
        }
    }

    internal fun compactAlgoliaStoryFields(
        item: JSONObject,
        fallbackId: Int,
        descendants: Int,
        topLevelCommentIds: List<Int> = emptyList(),
    ): String {
        val summary = JSONObject()
        var id = item.optInt("id", fallbackId)
        if (id <= 0) {
            id = fallbackId
        }

        summary.put("cache_version", CACHED_STORY_SUMMARY_VERSION)
        summary.put("id", id)
        summary.put("type", item.optString("type", "story"))
        summary.put("title", item.optString("title", ""))
        summary.put("author", item.optString("author", ""))
        summary.put("points", item.optInt("points", 0))
        summary.put("created_at_i", item.optInt("created_at_i", 0))
        summary.put("descendants", descendants)
        if (topLevelCommentIds.isNotEmpty()) {
            summary.put("kids", JSONArray().apply { topLevelCommentIds.forEach { put(it) } })
        }
        putNonNullString(summary, "url", item.optString("url", ""))
        putNonNullString(summary, "text", item.optString("text", ""))

        if (item.has("story_id")) {
            summary.put("story_id", item.optInt("story_id", 0))
        }
        if (item.has("parent_id")) {
            summary.put("parent_id", item.optInt("parent_id", 0))
        }
        putNonNullString(summary, "story_title", item.optString("story_title", ""))
        putNonNullString(summary, "story_url", item.optString("story_url", ""))
        copyPreviewImageSummaryFields(item, summary)
        copyFaviconTintSummaryFields(item, summary)

        return summary.toString()
    }

    fun updateStoryWithCachedStorySummary(story: Story?, response: String?): Boolean {
        if (story == null || response.isNullOrEmpty() || JSON_NULL_LITERAL == response) {
            return false
        }

        try {
            val item = JSONObject(response)
            val id = item.optInt("id", story.id)
            if (id <= 0) {
                return false
            }

            story.id = id
            // A live feed's ordering wins over older cached metadata. Legacy summaries simply
            // omit kids; those threads can still open immediately in cached Algolia order.
            if (story.kids?.isNotEmpty() != true) {
                item.optJSONArray("kids")?.let { ids ->
                    story.kids = IntArray(ids.length()) { ids.getInt(it) }
                }
            }
            story.createdAtEpochSeconds = item.optInt("created_at_i", item.optInt("time", story.createdAtEpochSeconds))
            story.score = item.optInt("points", item.optInt("score", story.score))
            story.by = item.optString("author", item.optString("by", story.by))
            story.descendants = if (item.has("descendants"))
                item.optInt("descendants", story.descendants)
            else
                countAlgoliaComments(item.optJSONArray("children"))

            val type = item.optString("type", "")
            if ("comment" == type) {
                story.isComment = true
                story.title = "Comment by " + story.by
                story.isLink = false
                story.parentId = item.optInt("parent_id", 0)
                story.rootStoryId = item.optInt("story_id", 0)
                story.rootStoryTitle = item.optString("story_title", "")
                story.rootStoryUrl = item.optString("story_url", "")
                val urlId = if (story.rootStoryId > 0) story.rootStoryId else story.id
                story.url = HackerNewsLinks.itemUrl(urlId)
            } else {
                story.isComment = false
                story.title = item.optString("title", story.title)
                val rawUrl = item.optString("url", "").trim { it <= ' ' }
                val hasValidUrl =
                    !rawUrl.isEmpty() && !rawUrl.equals(JSON_NULL_LITERAL, ignoreCase = true)
                story.isLink = hasValidUrl
                story.url =
                    if (hasValidUrl) rawUrl else HackerNewsLinks.itemUrl(story.id)
                story.isJob = "job" == type
            }

            val text = optStringOrNull(item, "text")
            if (text != null) {
                updateStoryText(story, text)
            }

            applyPreviewImageSummaryFields(story, item)
            applyFaviconTintSummaryFields(story, item)
            updateTitleBadgeProperties(story)
            story.loaded = true
            story.loadingFailed = false
            return !story.title.isNullOrEmpty()
        } catch (e: JSONException) {
            return false
        }
    }

    @Throws(JSONException::class)
    private fun putNonNullString(`object`: JSONObject, key: String, value: String?) {
        if (!value.isNullOrEmpty() && !JSON_NULL_LITERAL.equals(value, ignoreCase = true)) {
            `object`.put(key, value)
        }
    }

    @Throws(JSONException::class)
    private fun copyPreviewImageSummaryFields(source: JSONObject, destination: JSONObject) {
        copyString(source, destination, KEY_PREVIEW_IMAGE_URL)
        copyBoolean(source, destination, KEY_PREVIEW_IMAGE_URL_LOADED)
        copyBoolean(source, destination, KEY_PREVIEW_IMAGE_TINT_COLOR_LOADED)
        copyInt(source, destination, KEY_PREVIEW_IMAGE_TINT_COLOR)
        copyString(source, destination, KEY_PREVIEW_IMAGE_TINT_SOURCE_URL)
        copyInt(source, destination, KEY_PREVIEW_IMAGE_TINT_BASE_COLOR)
        copyString(source, destination, KEY_PREVIEW_IMAGE_TINT_MODE)
    }

    @Throws(JSONException::class)
    private fun copyFaviconTintSummaryFields(source: JSONObject, destination: JSONObject) {
        copyBoolean(source, destination, KEY_FAVICON_TINT_COLOR_LOADED)
        copyInt(source, destination, KEY_FAVICON_TINT_COLOR)
        copyString(source, destination, KEY_FAVICON_TINT_SOURCE_URL)
        copyInt(source, destination, KEY_FAVICON_TINT_BASE_COLOR)
        copyString(source, destination, KEY_FAVICON_TINT_MODE)
    }

    private fun applyPreviewImageSummaryFields(story: Story, item: JSONObject) {
        val hasPreviewImageUrl = item.has(KEY_PREVIEW_IMAGE_URL)
        val previewImageUrlResolved =
            item.optBoolean(KEY_PREVIEW_IMAGE_URL_LOADED, hasPreviewImageUrl)
        if (previewImageUrlResolved) {
            val previewImageUrl = item.optString(KEY_PREVIEW_IMAGE_URL, "").trim { it <= ' ' }
            story.previewImageUrl =
                if (previewImageUrl.isEmpty()) null else previewImageUrl
            story.previewImageUrlResolved = true
        }

        if (item.optBoolean(KEY_PREVIEW_IMAGE_TINT_COLOR_LOADED, false)
            && !story.previewImageUrl.isNullOrEmpty()
        ) {
            val tintSourceUrl =
                item.optString(KEY_PREVIEW_IMAGE_TINT_SOURCE_URL, story.previewImageUrl)
            story.previewImageTintColor =
                item.optInt(KEY_PREVIEW_IMAGE_TINT_COLOR, story.previewImageTintColor)
            story.previewImageTintColorLoaded = !tintSourceUrl.isNullOrEmpty()
            story.previewImageTintSourceUrl = tintSourceUrl
            story.previewImageTintBaseColor =
                item.optInt(KEY_PREVIEW_IMAGE_TINT_BASE_COLOR, story.previewImageTintBaseColor)
            story.previewImageTintMode =
                item.optString(KEY_PREVIEW_IMAGE_TINT_MODE, story.previewImageTintMode)
        }
    }

    @Throws(JSONException::class)
    private fun applyFaviconTintSummaryFields(story: Story, item: JSONObject) {
        if (!item.optBoolean(KEY_FAVICON_TINT_COLOR_LOADED, false)) {
            return
        }

        val tintSourceUrl = optStringOrNull(item, KEY_FAVICON_TINT_SOURCE_URL)
        if (tintSourceUrl.isNullOrEmpty()) {
            return
        }

        story.faviconTintColor = item.optInt(KEY_FAVICON_TINT_COLOR, story.faviconTintColor)
        story.faviconTintColorLoaded = true
        story.faviconTintSourceUrl = tintSourceUrl
        story.faviconTintBaseColor =
            item.optInt(KEY_FAVICON_TINT_BASE_COLOR, story.faviconTintBaseColor)
        story.faviconTintMode = item.optString(KEY_FAVICON_TINT_MODE, story.faviconTintMode)
    }

    @Throws(JSONException::class)
    private fun optStringOrNull(`object`: JSONObject, key: String): String? {
        val value = `object`.opt(key)
        if (value == null) {
            return null
        }

        return value.toString()
    }

    @Throws(JSONException::class)
    private fun copyString(source: JSONObject, destination: JSONObject, key: String) {
        val value = optStringOrNull(source, key)
        if (value != null) {
            putNonNullString(destination, key, value)
        }
    }

    @Throws(JSONException::class)
    private fun copyBoolean(source: JSONObject, destination: JSONObject, key: String) {
        if (source.has(key)) {
            destination.put(key, source.optBoolean(key, false))
        }
    }

    @Throws(JSONException::class)
    private fun copyInt(source: JSONObject, destination: JSONObject, key: String) {
        if (source.has(key)) {
            destination.put(key, source.optInt(key, 0))
        }
    }

    @Throws(JSONException::class)
    private fun countAlgoliaComments(children: JSONArray?): Int {
        if (children == null) {
            return 0
        }

        val childCount = children.length()
        var count = childCount
        for (i in 0..<childCount) {
            val child = children.optJSONObject(i)
            if (child == null) {
                continue
            }
            count += countAlgoliaComments(child.optJSONArray("children"))
        }
        return count
    }

    internal fun updateStoryText(story: Story, rawText: String?) {
        story.text = preprocessHtml(rawText)
    }

    fun preprocessHtml(input: String?): String? = StoryTextProcessor.preprocessHtml(input)
}
