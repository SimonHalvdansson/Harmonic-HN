package com.simon.harmonichackernews.network

import android.text.TextUtils
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.dto.AlgoliaSearchResponseDto
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import com.simon.harmonichackernews.network.dto.applyTo
import com.simon.harmonichackernews.network.dto.toComment
import com.simon.harmonichackernews.network.dto.toStory
import com.simon.harmonichackernews.utils.StoryUpdate
import com.simon.harmonichackernews.utils.Utils
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Locale
import kotlin.math.max
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
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
    fun algoliaJsonToStories(response: String): MutableList<Story> {
        return try {
            ALGOLIA_JSON.decodeFromString<AlgoliaSearchResponseDto>(response)
                .hits
                .mapNotNull { it.toStory() }
                .toMutableList()
        } catch (error: SerializationException) {
            throw JSONException("Invalid Algolia search JSON", error)
        } catch (error: IllegalArgumentException) {
            throw JSONException("Invalid Algolia search JSON", error)
        }
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
        if (story == null || TextUtils.isEmpty(response) || JSON_NULL_LITERAL == response) {
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
            story.commentMasterUrl = "https://news.ycombinator.com/item?id=" + id
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
        if (story == null || TextUtils.isEmpty(story.url) || TextUtils.isEmpty(story.title)) {
            return
        }

        story.pdfTitle = null
        story.videoTitle = null

        val title = story.title ?: return
        val url = story.url ?: return
        val lastTitleCharacter = title.last()
        val mayHaveTitleSuffix = lastTitleCharacter == ']' || lastTitleCharacter == ')'
        val pdfTitle = if (mayHaveTitleSuffix)
            stripTitleSuffixOrNull(title, PDF_SUFFIXES)
        else
            null

        if (endsWithIgnoreCase(url, ".pdf")) {
            story.pdfTitle = pdfTitle ?: title
        } else if (pdfTitle != null) {
            story.pdfTitle = pdfTitle
        } else if (mayHaveTitleSuffix) {
            story.videoTitle = stripTitleSuffixOrNull(title, VIDEO_SUFFIXES)
        }
    }

    private fun stripTitleSuffixOrNull(title: String, suffixes: Array<String>): String? {
        for (suffix in suffixes) {
            if (endsWithIgnoreCase(title, suffix)) {
                return title.substring(0, title.length - suffix.length)
            }
        }
        return null
    }

    private fun endsWithIgnoreCase(value: String, suffix: String): Boolean {
        return value.length >= suffix.length
                && value.regionMatches(
            value.length - suffix.length,
            suffix,
            0,
            suffix.length,
            ignoreCase = true
        )
    }

    @Throws(IOException::class)
    fun parseAlgoliaCommentsResponse(
        response: String?,
        prioTop: IntArray?,
        filteredUsers: Set<String>?
    ): AlgoliaCommentsResponse {
        try {
            val payload = ALGOLIA_JSON.decodeFromString<AlgoliaCommentsPayload>(response.orEmpty())
            val result = AlgoliaCommentsResponse().apply {
                title = payload.title
                points = payload.points
                createdAt = payload.createdAt
                type = payload.type
                author = payload.author
                storyId = payload.storyId
                parentId = payload.parentId
                storyTitle = payload.storyTitle
                url = payload.url
                text = payload.text
                id = payload.id
            }
            val activeFilteredUsers = filteredUsers?.takeUnless(Set<String>::isEmpty)
            val topLevelComments = payload.children
                .mapNotNull { parseAlgoliaComment(it, 0, activeFilteredUsers) }
                .toMutableList()

            if (prioTop != null && prioTop.isNotEmpty() && topLevelComments.size > 1) {
                sortTopLevelComments(topLevelComments, prioTop)
            }

            flattenComments(topLevelComments, result.comments)
            return result
        } catch (error: SerializationException) {
            throw IOException("Invalid Algolia comments JSON", error)
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid Algolia comments JSON", error)
        }
    }

    private fun parseAlgoliaComment(
        payload: AlgoliaCommentPayload,
        depth: Int,
        filteredUsers: Set<String>?
    ): Comment? {
        throwIfInterrupted()
        val rawText = payload.text.trim { it <= ' ' }
        val author = payload.author.trim { it <= ' ' }
        val childCount = payload.children.size
        val childComments = payload.children
            .mapNotNull { parseAlgoliaComment(it, depth + 1, filteredUsers) }
            .toMutableList()
        throwIfInterrupted()

        if (rawText.isEmpty() || JSON_NULL_LITERAL.equals(rawText, ignoreCase = true)) {
            return null
        }
        if (filteredUsers != null && filteredUsers.contains(author.lowercase(Locale.getDefault()))) {
            return null
        }

        val comment = Comment()
        comment.depth = depth
        comment.parent = payload.parentId
        comment.expanded = true
        comment.by = author
        comment.text = preprocessHtml(rawText)
        comment.time = payload.createdAt
        comment.id = payload.id
        comment.children = childCount
        comment.childComments = childComments

        if (comment.childComments.isNotEmpty()) {
            comment.childComments.sortWith(compareByDescending { it.children })
        }

        return comment
    }

    @Throws(InterruptedIOException::class)
    private fun throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw InterruptedIOException("Comment parsing was cancelled")
        }
    }

    @Serializable
    private data class AlgoliaCommentsPayload(
        @Serializable(with = FlexibleStringSerializer::class)
        val title: String = "",
        @Serializable(with = FlexibleIntSerializer::class)
        val points: Int = 0,
        @SerialName("created_at_i")
        @Serializable(with = FlexibleIntSerializer::class)
        val createdAt: Int = 0,
        @Serializable(with = FlexibleStringSerializer::class)
        val type: String = "",
        @Serializable(with = FlexibleStringSerializer::class)
        val author: String = "",
        @SerialName("story_id")
        @Serializable(with = FlexibleIntSerializer::class)
        val storyId: Int = 0,
        @SerialName("parent_id")
        @Serializable(with = FlexibleIntSerializer::class)
        val parentId: Int = 0,
        @SerialName("story_title")
        @Serializable(with = FlexibleStringSerializer::class)
        val storyTitle: String = "",
        @Serializable(with = FlexibleStringSerializer::class)
        val url: String = "",
        @Serializable(with = FlexibleStringSerializer::class)
        val text: String = "",
        @Serializable(with = FlexibleIntSerializer::class)
        val id: Int = 0,
        val children: List<AlgoliaCommentPayload> = emptyList(),
    )

    @Serializable
    private data class AlgoliaCommentPayload(
        @Serializable(with = FlexibleStringSerializer::class)
        val text: String = "",
        @Serializable(with = FlexibleStringSerializer::class)
        val author: String = "",
        @SerialName("parent_id")
        @Serializable(with = FlexibleIntSerializer::class)
        val parentId: Int = 0,
        @SerialName("created_at_i")
        @Serializable(with = FlexibleIntSerializer::class)
        val createdAt: Int = 0,
        @Serializable(with = FlexibleIntSerializer::class)
        val id: Int = 0,
        val children: List<AlgoliaCommentPayload> = emptyList(),
    )

    private object FlexibleStringSerializer : KSerializer<String> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): String {
            val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
            val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return ""
            return if (primitive.isString || primitive.doubleOrNull != null) {
                primitive.content
            } else {
                ""
            }
        }

        override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    }

    private object FlexibleIntSerializer : KSerializer<Int> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

        override fun deserialize(decoder: Decoder): Int {
            val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
            val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return 0
            if (primitive.isString) {
                return primitive.content.toIntOrNull() ?: 0
            }
            primitive.intOrNull?.let { return it }
            val doubleValue = primitive.doubleOrNull ?: return 0
            val intValue = doubleValue.toInt()
            return if (intValue.toDouble() == doubleValue) intValue else 0
        }

        override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
    }

    private fun sortTopLevelComments(comments: MutableList<Comment>, prioTop: IntArray) {
        val mapCapacity = max(16, (prioTop.size / 0.75f).toInt() + 1)
        val priorityById: MutableMap<Int, Int> = HashMap(mapCapacity)
        for (i in prioTop.indices) {
            priorityById.putIfAbsent(prioTop[i], i)
        }
        comments.sortBy { priorityById.getOrDefault(it.id, prioTop.size) }
    }

    private fun flattenComments(source: MutableList<Comment>, destination: MutableList<Comment>) {
        val sourceSize = source.size
        for (i in 0..<sourceSize) {
            val comment = source[i]
            destination.add(comment)
            if (comment.childComments.isNotEmpty()) {
                flattenComments(comment.childComments, destination)
                comment.childComments = mutableListOf()
            }
        }
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
        if (TextUtils.isEmpty(response)
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
        if (story == null || TextUtils.isEmpty(response) || JSON_NULL_LITERAL == response) {
            return null
        }

        try {
            val summary = JSONObject(response)
            if (summary.optInt("id", story.id) != story.id) {
                return null
            }

            if (story.previewImageUrlLoaded || !TextUtils.isEmpty(story.previewImageUrl)) {
                summary.put(KEY_PREVIEW_IMAGE_URL_LOADED, true)
                if (TextUtils.isEmpty(story.previewImageUrl)) {
                    summary.remove(KEY_PREVIEW_IMAGE_URL)
                } else {
                    summary.put(KEY_PREVIEW_IMAGE_URL, story.previewImageUrl)
                }
                summary.put(KEY_PREVIEW_IMAGE_LOAD_FAILED, story.previewImageLoadFailed)
            }

            if (story.previewImageTintColorLoaded && !TextUtils.isEmpty(story.previewImageTintSourceUrl)) {
                summary.put(KEY_PREVIEW_IMAGE_TINT_COLOR_LOADED, true)
                summary.put(KEY_PREVIEW_IMAGE_TINT_COLOR, story.previewImageTintColor)
                summary.put(KEY_PREVIEW_IMAGE_TINT_SOURCE_URL, story.previewImageTintSourceUrl)
                summary.put(KEY_PREVIEW_IMAGE_TINT_BASE_COLOR, story.previewImageTintBaseColor)
                if (TextUtils.isEmpty(story.previewImageTintMode)) {
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

            if (story.faviconTintColorLoaded && !TextUtils.isEmpty(story.faviconTintSourceUrl)) {
                summary.put(KEY_FAVICON_TINT_COLOR_LOADED, true)
                summary.put(KEY_FAVICON_TINT_COLOR, story.faviconTintColor)
                summary.put(KEY_FAVICON_TINT_SOURCE_URL, story.faviconTintSourceUrl)
                summary.put(KEY_FAVICON_TINT_BASE_COLOR, story.faviconTintBaseColor)
                if (TextUtils.isEmpty(story.faviconTintMode)) {
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
        if (story == null || TextUtils.isEmpty(response) || JSON_NULL_LITERAL == response) {
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
                story.url = "https://news.ycombinator.com/item?id=" + urlId
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
            return !TextUtils.isEmpty(story.title)
        } catch (e: JSONException) {
            return false
        }
    }

    @Throws(JSONException::class)
    private fun putNonNullString(`object`: JSONObject, key: String, value: String?) {
        if (!TextUtils.isEmpty(value) && !JSON_NULL_LITERAL.equals(value, ignoreCase = true)) {
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
                if (TextUtils.isEmpty(previewImageUrl)) null else previewImageUrl
            story.previewImageUrlLoaded = true
            story.previewImageUrlNeedsRefresh = true
            story.previewImageLoadFailed = item.optBoolean(
                KEY_PREVIEW_IMAGE_LOAD_FAILED,
                TextUtils.isEmpty(story.previewImageUrl)
            )
        }

        if (item.optBoolean(KEY_PREVIEW_IMAGE_TINT_COLOR_LOADED, false)
            && !TextUtils.isEmpty(story.previewImageUrl)
        ) {
            val tintSourceUrl =
                item.optString(KEY_PREVIEW_IMAGE_TINT_SOURCE_URL, story.previewImageUrl)
            story.previewImageTintColor =
                item.optInt(KEY_PREVIEW_IMAGE_TINT_COLOR, story.previewImageTintColor)
            story.previewImageTintColorLoaded = !TextUtils.isEmpty(tintSourceUrl)
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
        if (TextUtils.isEmpty(tintSourceUrl)) {
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
        val text = preprocessHtml(rawText)
        if (!TextUtils.equals(story.text, text)) {
            story.spannedText = null
            story.collectedReferenceLinksSource = null
            story.collectedReferenceLinks = null
            story.collectedReferenceLinksSpannedText = null
        }
        story.text = text
    }

    fun preprocessHtml(input: String?): String? {
        if (input.isNullOrEmpty()) {
            return input
        }
        // Linkify first, so we don't have to deal with &nbsp; from escapePreBlockWhitespace
        var processed = Utils.linkify(input) ?: return null

        // Standardize code blocks: handle <pre><code> first, then standalone <code>
        if (processed.contains("code>")) {
            processed = processed.replace("<pre><code>", "<pre><small>")
                .replace("</code></pre>", "</small></pre>")
                .replace("<code>", "<pre><small>")
                .replace("</code>", "</small></pre>")
        }

        if (processed.contains("<pre>")) {
            processed = escapePreBlockWhitespace(processed)
        }
        if (processed.contains("pre>")) {
            processed = processed.replace("<pre>", "<div><tt>")
                .replace("</pre>", "</tt></div>")
        }

        return processed
    }

    private fun escapePreBlockWhitespace(input: String): String {
        val inputLength = input.length
        val output = StringBuilder(inputLength)
        var insidePreBlock = false

        var i = 0
        while (i < inputLength) {
            val current = input.get(i)
            if (current == '<' && input.startsWith("<pre>", i)) {
                insidePreBlock = true
                output.append("<pre>")
                i += "<pre>".length - 1
            } else if (current == '<' && input.startsWith("</pre>", i)) {
                insidePreBlock = false
                output.append("</pre>")
                i += "</pre>".length - 1
            } else {
                if (insidePreBlock && current == ' ') {
                    output.append("&nbsp;")
                } else if (insidePreBlock && current == '\n') {
                    output.append("<br>")
                } else {
                    output.append(current)
                }
            }
            i++
        }

        return output.toString()
    }

    // Official HN API parsing methods for fallback
    fun updateStoryWithOfficialHNResponse(story: Story, response: String?): Boolean {
        try {
            if (TextUtils.isEmpty(response) || JSON_NULL_LITERAL == response) {
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

    class AlgoliaCommentsResponse {
        val comments: MutableList<Comment> = ArrayList()
        var title = ""
        var points = 0
        var createdAt = 0
        var type = ""
        var author = ""
        var storyId = 0
        var parentId = 0
        var storyTitle = ""
        var url = ""
        var text = ""
        var id = 0

        fun updateStoryInformation(
            story: Story,
            forceRefresh: Boolean,
            oldCommentCount: Int
        ): Boolean {
            val oldFormattedTime = story.timeFormatted
            val newCommentCount = comments.size + 1
            val changed: Boolean

            if (TextUtils.isEmpty(story.title)) {
                changed = true
            } else {
                changed =
                    ((title != story.title) || points != story.score || (oldFormattedTime != story.timeFormatted) || oldCommentCount != newCommentCount)
            }

            story.time = createdAt

            if ("comment" == type) {
                story.title = "Comment by " + author
                story.isLink = false
                story.url = "https://news.ycombinator.com/item?id=" + storyId
                story.isComment = true
                story.parentId = parentId
                story.commentMasterId = storyId
                story.commentMasterTitle = storyTitle
            } else {
                story.title = title
                story.isLink = !TextUtils.isEmpty(url) && JSON_NULL_LITERAL != url

                if (story.isLink) {
                    story.url = url
                } else {
                    story.url = "https://news.ycombinator.com/item?id=" + story.id
                }

                updateTitleBadgeProperties(story)
            }

            if (!TextUtils.isEmpty(text) && JSON_NULL_LITERAL != text) {
                updateStoryText(story, text)
            }

            story.descendants = comments.size
            story.id = id
            story.score = points
            story.by = author
            story.loaded = true

            if (forceRefresh) {
                StoryUpdate.updateStory(story)
            }

            return changed
        }
    }
}
