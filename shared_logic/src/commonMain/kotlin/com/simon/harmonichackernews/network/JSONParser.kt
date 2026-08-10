package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import com.simon.harmonichackernews.network.dto.applyTo
import com.simon.harmonichackernews.network.dto.toComment
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import com.simon.harmonichackernews.serialization.JsonArray as JSONArray
import com.simon.harmonichackernews.serialization.JsonException as JSONException
import com.simon.harmonichackernews.serialization.JsonObject as JSONObject

object JSONParser {
    const val ALGOLIA_ERROR_STRING: String = "{\"status\":404,\"error\":\"Not Found\"}"
    private val ALGOLIA_JSON = Json { ignoreUnknownKeys = true }
    private const val JSON_NULL_LITERAL = "null"
    private val PDF_SUFFIXES = arrayOf<String>(" [pdf]", "[pdf]", " (pdf)", "(pdf)")
    private val VIDEO_SUFFIXES = arrayOf<String>(" [video]", "[video]", " (video)", "(video)")
    private const val CACHED_STORY_SUMMARY_VERSION = 1
    private const val KEY_PREVIEW_IMAGE_URL = "preview_image_url"
    private const val KEY_PREVIEW_IMAGE_URL_LOADED = "preview_image_url_loaded"
    private const val KEY_PREVIEW_IMAGE_LOAD_FAILED = "preview_image_load_failed"
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

    private fun hasOnlyTwoTopLevelFields(jsonObject: JSONObject): Boolean {
        return jsonObject.length() == 2
    }

    @Throws(JSONException::class)
    fun updateStoryWithHNJson(response: String?, story: Story, isHistory: Boolean): Boolean {
        if (response.isNullOrBlank() || JSON_NULL_LITERAL == response) return false
        return try {
            val item = ALGOLIA_JSON.decodeFromString<HackerNewsItemDto>(response)
            if (item.type != "comment" && item.title == null) return false
            item.applyTo(story, preserveTime = isHistory)
        } catch (_: SerializationException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    @Throws(JSONException::class)
    fun updateCommentMasterStoryWithHNJson(story: Story?, response: String?): Boolean {
        if (story == null || response.isNullOrEmpty() || JSON_NULL_LITERAL == response) {
            return false
        }

        val jsonObject = JSONObject(response)
        if (hasOnlyTwoTopLevelFields(jsonObject) || "comment" == jsonObject.optString("type", "")) {
            return false
        }

        val id = jsonObject.optInt("id", 0)
        if (id <= 0) {
            return false
        }

        story.commentMasterId = id
        story.commentMasterTitle = jsonObject.optString("title", story.commentMasterTitle)
        story.commentMasterBy = jsonObject.optString("by", story.commentMasterBy)
        story.commentMasterScore = jsonObject.optInt("score", story.commentMasterScore)
        story.commentMasterTime = jsonObject.optInt("time", story.commentMasterTime)
        story.commentMasterDescendants =
            jsonObject.optInt("descendants", story.commentMasterDescendants)
        val url = optStringOrNull(jsonObject, "url")
        if (url != null) {
            story.commentMasterUrl = url
        } else {
            story.commentMasterUrl = "https://news.ycombinator.com/item?id=$id"
        }
        story.commentMasterLoaded = true
        return true
    }

    @Throws(JSONException::class)
    fun updateStoryWithHNCommentJson(jsonObject: JSONObject, story: Story): Boolean {
        if (jsonObject.has("deleted") && jsonObject.getBoolean("deleted")) {
            return false
        }

        val by = jsonObject.getString("by")
        val id = jsonObject.getInt("id")
        val time = jsonObject.getInt("time")

        // setting the score to -1 means it doesn't get shown
        story.update(
            by,
            id,
            0,
            time,
            "Comment by " + by
        )

        story.isComment = true
        story.parentId = jsonObject.optInt("parent", 0)

        if (jsonObject.has("kids")) {
            val kidsJsonArray = jsonObject.getJSONArray("kids")
            val kidCount = kidsJsonArray.length()
            story.descendants = kidCount
            val kids = IntArray(kidCount)

            for (i in 0..<kidCount) {
                kids[i] = kidsJsonArray.getInt(i)
            }

            story.kids = kids
        } else {
            story.descendants = 0
        }

        story.url = "https://news.ycombinator.com/item?id=" + story.id
        story.isLink = false
        val text = optStringOrNull(jsonObject, "text")
        if (text != null) {
            updateStoryText(story, text)
        }

        story.loaded = true
        story.loadingFailed = false

        return true
    }

    fun updateTitleBadgeProperties(story: Story?) {
        StoryTextProcessor.applyTitleBadges(story)
    }

    fun updateStoryWithAlgoliaResponse(story: Story, response: String) {
        try {
            val item = JSONObject(response)

            // count children in one go
            val children = item.optJSONArray("children")
            story.descendants = (if (children == null) 0 else children.length())

            // timestamp, title, author, score—all with a single lookup each
            story.time = item.optInt("created_at_i", story.time)
            story.title = item.optString("title", story.title)
            story.score = item.optInt("points", story.score)
            story.by = item.optString("author", story.by)

            // pull url once, trim it, then check for empty or literal "null"
            val rawUrl = item.optString("url", "").trim { it <= ' ' }
            val hasValidUrl =
                !rawUrl.isEmpty() && !rawUrl.equals(JSON_NULL_LITERAL, ignoreCase = true)
            story.isLink = hasValidUrl

            // only set story.url once
            if (hasValidUrl) {
                story.url = rawUrl
            } else {
                story.url = "https://news.ycombinator.com/item?id=" + story.id
            }

            updateTitleBadgeProperties(story)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun compactAlgoliaStoryResponse(response: String?, fallbackId: Int): String? {
        if (response.isNullOrEmpty()
            || JSON_NULL_LITERAL == response
            || ALGOLIA_ERROR_STRING == response
        ) {
            return null
        }

        try {
            val item = JSONObject(response)
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
            summary.put("descendants", countAlgoliaComments(item.optJSONArray("children")))
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
        } catch (e: JSONException) {
            return null
        }
    }

    fun updateCachedStorySummaryPreviewState(response: String?, story: Story?): String? {
        if (story == null || response.isNullOrEmpty() || JSON_NULL_LITERAL == response) {
            return null
        }

        try {
            val summary = JSONObject(response)
            if (summary.optInt("id", story.id) != story.id) {
                return null
            }

            if (story.previewImageUrlLoaded || !story.previewImageUrl.isNullOrEmpty()) {
                summary.put(KEY_PREVIEW_IMAGE_URL_LOADED, true)
                if (story.previewImageUrl.isNullOrEmpty()) {
                    summary.remove(KEY_PREVIEW_IMAGE_URL)
                } else {
                    summary.put(KEY_PREVIEW_IMAGE_URL, story.previewImageUrl)
                }
                summary.put(KEY_PREVIEW_IMAGE_LOAD_FAILED, story.previewImageLoadFailed)
            }

            if (story.previewImageTintColorLoaded && !story.previewImageTintSourceUrl.isNullOrEmpty()) {
                summary.put(KEY_PREVIEW_IMAGE_TINT_COLOR_LOADED, true)
                summary.put(KEY_PREVIEW_IMAGE_TINT_COLOR, story.previewImageTintColor)
                summary.put(KEY_PREVIEW_IMAGE_TINT_SOURCE_URL, story.previewImageTintSourceUrl)
                summary.put(KEY_PREVIEW_IMAGE_TINT_BASE_COLOR, story.previewImageTintBaseColor)
                if (story.previewImageTintMode.isNullOrEmpty()) {
                    summary.remove(KEY_PREVIEW_IMAGE_TINT_MODE)
                } else {
                    summary.put(KEY_PREVIEW_IMAGE_TINT_MODE, story.previewImageTintMode)
                }
            } else {
                summary.remove(KEY_PREVIEW_IMAGE_TINT_COLOR_LOADED)
                summary.remove(KEY_PREVIEW_IMAGE_TINT_COLOR)
                summary.remove(KEY_PREVIEW_IMAGE_TINT_SOURCE_URL)
                summary.remove(KEY_PREVIEW_IMAGE_TINT_BASE_COLOR)
                summary.remove(KEY_PREVIEW_IMAGE_TINT_MODE)
            }

            if (story.faviconTintColorLoaded && !story.faviconTintSourceUrl.isNullOrEmpty()) {
                summary.put(KEY_FAVICON_TINT_COLOR_LOADED, true)
                summary.put(KEY_FAVICON_TINT_COLOR, story.faviconTintColor)
                summary.put(KEY_FAVICON_TINT_SOURCE_URL, story.faviconTintSourceUrl)
                summary.put(KEY_FAVICON_TINT_BASE_COLOR, story.faviconTintBaseColor)
                if (story.faviconTintMode.isNullOrEmpty()) {
                    summary.remove(KEY_FAVICON_TINT_MODE)
                } else {
                    summary.put(KEY_FAVICON_TINT_MODE, story.faviconTintMode)
                }
            } else {
                summary.remove(KEY_FAVICON_TINT_COLOR_LOADED)
                summary.remove(KEY_FAVICON_TINT_COLOR)
                summary.remove(KEY_FAVICON_TINT_SOURCE_URL)
                summary.remove(KEY_FAVICON_TINT_BASE_COLOR)
                summary.remove(KEY_FAVICON_TINT_MODE)
            }

            return summary.toString()
        } catch (e: JSONException) {
            return null
        }
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
            story.time = item.optInt("created_at_i", item.optInt("time", story.time))
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
                story.commentMasterId = item.optInt("story_id", 0)
                story.commentMasterTitle = item.optString("story_title", "")
                story.commentMasterUrl = item.optString("story_url", "")
                val urlId = if (story.commentMasterId > 0) story.commentMasterId else story.id
                story.url = "https://news.ycombinator.com/item?id=$urlId"
            } else {
                story.isComment = false
                story.title = item.optString("title", story.title)
                val rawUrl = item.optString("url", "").trim { it <= ' ' }
                val hasValidUrl =
                    !rawUrl.isEmpty() && !rawUrl.equals(JSON_NULL_LITERAL, ignoreCase = true)
                story.isLink = hasValidUrl
                story.url =
                    if (hasValidUrl) rawUrl else "https://news.ycombinator.com/item?id=" + story.id
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
        copyBoolean(source, destination, KEY_PREVIEW_IMAGE_LOAD_FAILED)
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
        val previewImageUrlLoaded =
            item.optBoolean(KEY_PREVIEW_IMAGE_URL_LOADED, hasPreviewImageUrl)
        if (previewImageUrlLoaded) {
            val previewImageUrl = item.optString(KEY_PREVIEW_IMAGE_URL, "").trim { it <= ' ' }
            story.previewImageUrl =
                if (previewImageUrl.isEmpty()) null else previewImageUrl
            story.previewImageUrlLoaded = true
            story.previewImageLoadFailed = item.optBoolean(
                KEY_PREVIEW_IMAGE_LOAD_FAILED,
                story.previewImageUrl.isNullOrEmpty()
            )
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

    // Official HN API parsing methods for fallback
    fun updateStoryWithOfficialHNResponse(story: Story, response: String?): Boolean {
        try {
            if (response.isNullOrEmpty() || JSON_NULL_LITERAL == response) {
                return false
            }
            return ALGOLIA_JSON.decodeFromString<HackerNewsItemDto>(response.orEmpty()).applyTo(story)
        } catch (e: SerializationException) {
            e.printStackTrace()
            return false
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            return false
        }
    }

    @Throws(JSONException::class)
    fun parseOfficialHNCommentResponse(response: String): Comment? {
        return try {
            ALGOLIA_JSON.decodeFromString<HackerNewsItemDto>(response).toComment()
        } catch (error: SerializationException) {
            throw JSONException("Invalid Hacker News comment JSON", error)
        } catch (error: IllegalArgumentException) {
            throw JSONException("Invalid Hacker News comment JSON", error)
        }
    }

}
