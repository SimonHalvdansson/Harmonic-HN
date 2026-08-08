package com.simon.harmonichackernews.network

import android.text.TextUtils
import android.util.JsonReader
import android.util.JsonToken
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.utils.StoryUpdate
import com.simon.harmonichackernews.utils.Utils
import java.io.IOException
import java.io.InterruptedIOException
import java.io.StringReader
import java.util.Locale
import kotlin.math.max
import com.simon.harmonichackernews.serialization.JsonArray as JSONArray
import com.simon.harmonichackernews.serialization.JsonException as JSONException
import com.simon.harmonichackernews.serialization.JsonObject as JSONObject

object JSONParser {
    const val ALGOLIA_ERROR_STRING: String = "{\"status\":404,\"error\":\"Not Found\"}"
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
        val parentObject = JSONObject(response)
        val hits = parentObject.getJSONArray("hits")
        val hitCount = hits.length()
        val stories: MutableList<Story> = ArrayList(hitCount)

        for (i in 0..<hitCount) {
            val hit = hits.getJSONObject(i)

            val story = Story()

            val isComment = hit.getJSONArray("_tags").get(0) == "comment"

            story.title = if (isComment) hit.getString("story_title") else hit.optString("title")
            story.score = hit.optInt("points")
            story.by = hit.getString("author")
            story.descendants = hit.optInt("num_comments")
            story.id = hit.getString("objectID").toInt()
            story.time = hit.getInt("created_at_i")
            story.loaded = true
            story.loadingFailed = false
            story.clicked = false

            val url = optStringOrNull(hit, "url")
            if (!url.isNullOrEmpty() && url != JSON_NULL_LITERAL) {
                story.url = url
                story.isLink = true
            } else {
                story.url = "https://news.ycombinator.com/item?id=" + story.id
                story.isLink = false
            }

            val storyText = optStringOrNull(hit, "story_text")
            if (storyText != null && storyText != JSON_NULL_LITERAL) {
                updateStoryText(story, storyText)
            }

            if (isComment) {
                story.isComment = true
                updateStoryText(story, hit.optString("comment_text", ""))
                story.commentMasterTitle = hit.getString("story_title")
                story.commentMasterId = hit.getInt("story_id")
                story.parentId = hit.optInt("parent_id", 0)
                val storyUrl = optStringOrNull(hit, "story_url")
                if (storyUrl != null && storyUrl != JSON_NULL_LITERAL) {
                    story.commentMasterUrl = storyUrl
                    story.isLink = true
                } else {
                    story.isLink = false
                }
                if (!TextUtils.isEmpty(story.title) && story.title == JSON_NULL_LITERAL) {
                    story.title = "Comment by " + story.by
                }
            }

            updateTitleBadgeProperties(story)

            stories.add(story)
        }

        return stories
    }

    @Throws(JSONException::class)
    fun updateStoryWithHNJson(response: String?, story: Story, isHistory: Boolean): Boolean {
        if (TextUtils.isEmpty(response) || JSON_NULL_LITERAL == response) {
            return false
        }

        val jsonObject = JSONObject(response)

        if (hasOnlyTwoTopLevelFields(jsonObject)) {
            return false
        }

        val by = optStringOrNull(jsonObject, "by")
        if (by == null) {
            return false
        }

        val type = optStringOrNull(jsonObject, "type")
        if ("comment" == type) {
            return updateStoryWithHNCommentJson(jsonObject, story)
        }

        story.update(
            by,
            jsonObject.getInt("id"),
            jsonObject.getInt("score"),
            if (isHistory) story.time else jsonObject.getInt("time"),
            jsonObject.getString("title")
        )

        if (jsonObject.has("descendants")) {
            story.descendants = jsonObject.getInt("descendants")
        } else {
            story.descendants = 0
        }

        if ("job" == type) {
            story.isJob = true
        }

        if ("poll" == type && jsonObject.has("parts")) {
            val pollOptionsJson = jsonObject.getJSONArray("parts")
            val pollOptionCount = pollOptionsJson.length()
            val pollOptions = IntArray(pollOptionCount)
            for (i in 0..<pollOptionCount) {
                pollOptions[i] = pollOptionsJson.getInt(i)
            }

            story.pollOptions = pollOptions
        }

        if (jsonObject.has("kids")) {
            val kidsJsonArray = jsonObject.getJSONArray("kids")
            val kidCount = kidsJsonArray.length()
            val kids = IntArray(kidCount)

            for (i in 0..<kidCount) {
                kids[i] = kidsJsonArray.getInt(i)
            }

            story.kids = kids
        }

        val url = optStringOrNull(jsonObject, "url")
        if (url != null) {
            story.url = url
            story.isLink = true
        } else {
            story.url = "https://news.ycombinator.com/item?id=" + story.id
            story.isLink = false
        }

        val text = optStringOrNull(jsonObject, "text")
        if (text != null) {
            updateStoryText(story, text)
        }

        updateTitleBadgeProperties(story)

        story.loaded = true
        story.loadingFailed = false

        return true
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
        val reader = JsonReader(StringReader(response))
        val result = AlgoliaCommentsResponse()
        val topLevelComments = mutableListOf<Comment>()
        val activeFilteredUsers = if (!filteredUsers.isNullOrEmpty())
            filteredUsers
        else
            null

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                "title" -> result.title = nextStringOrDefault(reader, result.title)
                "points" -> result.points = nextIntOrDefault(reader, result.points)
                "created_at_i" -> result.createdAt = nextIntOrDefault(reader, result.createdAt)
                "type" -> result.type = nextStringOrDefault(reader, result.type)
                "author" -> result.author = nextStringOrDefault(reader, result.author)
                "story_id" -> result.storyId = nextIntOrDefault(reader, result.storyId)
                "parent_id" -> result.parentId = nextIntOrDefault(reader, result.parentId)
                "story_title" -> result.storyTitle = nextStringOrDefault(reader, result.storyTitle)
                "url" -> result.url = nextStringOrDefault(reader, result.url)
                "text" -> result.text = nextStringOrDefault(reader, result.text)
                "id" -> result.id = nextIntOrDefault(reader, result.id)
                "children" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        val comment = parseAlgoliaComment(reader, 0, activeFilteredUsers)
                        if (comment != null) {
                            topLevelComments.add(comment)
                        }
                    }
                    reader.endArray()
                }

                else -> reader.skipValue()
            }
        }
        reader.endObject()
        reader.close()

        if (prioTop != null && prioTop.isNotEmpty() && topLevelComments.size > 1) {
            sortTopLevelComments(topLevelComments, prioTop)
        }

        flattenComments(topLevelComments, result.comments)
        return result
    }

    @Throws(IOException::class)
    private fun parseAlgoliaComment(
        reader: JsonReader,
        depth: Int,
        filteredUsers: Set<String>?
    ): Comment? {
        throwIfInterrupted()
        var rawText = ""
        var author = ""
        var parentId = 0
        var createdAt = 0
        var id = 0
        var childCount = 0
        var childComments: MutableList<Comment>? = null

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                "text" -> rawText = nextStringOrDefault(reader, "").trim { it <= ' ' }
                "author" -> author = nextStringOrDefault(reader, "").trim { it <= ' ' }
                "parent_id" -> parentId = nextIntOrDefault(reader, parentId)
                "created_at_i" -> createdAt = nextIntOrDefault(reader, createdAt)
                "id" -> id = nextIntOrDefault(reader, id)
                "children" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        childCount++
                        val childComment = parseAlgoliaComment(reader, depth + 1, filteredUsers)
                        if (childComment != null) {
                            if (childComments == null) {
                                childComments = ArrayList()
                            }
                            childComments.add(childComment)
                        }
                    }
                    reader.endArray()
                }

                else -> reader.skipValue()
            }
        }
        reader.endObject()
        throwIfInterrupted()

        if (rawText.isEmpty() || JSON_NULL_LITERAL.equals(rawText, ignoreCase = true)) {
            return null
        }
        if (filteredUsers != null && filteredUsers.contains(author.lowercase(Locale.getDefault()))) {
            return null
        }

        val comment = Comment()
        comment.depth = depth
        comment.parent = parentId
        comment.expanded = true
        comment.by = author
        comment.text = preprocessHtml(rawText)
        comment.time = createdAt
        comment.id = id
        comment.children = childCount
        comment.childComments = childComments ?: mutableListOf()

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

    @Throws(IOException::class)
    private fun nextStringOrDefault(reader: JsonReader, defaultValue: String): String {
        val token = reader.peek()
        if (token == JsonToken.NULL) {
            reader.nextNull()
            return defaultValue
        }
        if (token == JsonToken.STRING || token == JsonToken.NUMBER) {
            return reader.nextString()
        }
        reader.skipValue()
        return defaultValue
    }

    @Throws(IOException::class)
    private fun nextIntOrDefault(reader: JsonReader, defaultValue: Int): Int {
        val token = reader.peek()
        if (token == JsonToken.NULL) {
            reader.nextNull()
            return defaultValue
        }
        if (token == JsonToken.NUMBER) {
            return reader.nextInt()
        }
        if (token == JsonToken.STRING) {
            try {
                return reader.nextString().toInt()
            } catch (ignored: NumberFormatException) {
                return defaultValue
            }
        }
        reader.skipValue()
        return defaultValue
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

    private fun updateStoryText(story: Story, rawText: String?) {
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

            val jsonObject = JSONObject(response)


            // Check if this is a valid story response
            val by = optStringOrNull(jsonObject, "by")
            if (by == null || hasOnlyTwoTopLevelFields(jsonObject)) {
                return false
            }

            story.by = by
            story.id = jsonObject.optInt("id", story.id)
            story.score = jsonObject.optInt("score", 0)
            story.time = jsonObject.optInt("time", story.time)
            story.title = jsonObject.optString("title", story.title)
            story.descendants = jsonObject.optInt("descendants", 0)

            val type = optStringOrNull(jsonObject, "type")
            if ("comment" == type) {
                story.isComment = true
                story.parentId = jsonObject.optInt("parent", 0)
                if (TextUtils.isEmpty(story.title)) {
                    story.title = "Comment by " + story.by
                }
            }

            //if a story is dead, it might not have a title. Right now we only do the fallback if
            // the story is dead (can it have no title for another reason? The example post was
            // "flagged" but the JSON said dead=true so let's go with that). If more cases show up,
            //let's add those in then
            if (TextUtils.isEmpty(story.title) && jsonObject.optBoolean("dead", false)) {
                story.title = "[deleted]"
            }

            if ("job" == type) {
                story.isJob = true
            }

            if ("poll" == type && jsonObject.has("parts")) {
                val pollOptionsJson = jsonObject.getJSONArray("parts")
                val pollOptionCount = pollOptionsJson.length()
                val pollOptions = IntArray(pollOptionCount)
                for (i in 0..<pollOptionCount) {
                    pollOptions[i] = pollOptionsJson.getInt(i)
                }

                story.pollOptions = pollOptions
            }

            if (jsonObject.has("kids")) {
                val kidsArray = jsonObject.getJSONArray("kids")
                val kidCount = kidsArray.length()
                val kids = IntArray(kidCount)
                for (i in 0..<kidCount) {
                    kids[i] = kidsArray.getInt(i)
                }
                story.kids = kids
            }

            val url = optStringOrNull(jsonObject, "url")
            if (url != null) {
                story.url = url
                story.isLink = true
            } else {
                story.url = "https://news.ycombinator.com/item?id=" + story.id
                story.isLink = false
            }

            val text = optStringOrNull(jsonObject, "text")
            if (text != null) {
                updateStoryText(story, text)
            }

            updateTitleBadgeProperties(story)

            story.loaded = true
            story.loadingFailed = false

            return true
        } catch (e: JSONException) {
            e.printStackTrace()
            return false
        }
    }

    @Throws(JSONException::class)
    fun parseOfficialHNCommentResponse(response: String): Comment? {
        val jsonObject = JSONObject(response)

        // Check if this is a deleted comment
        if (jsonObject.has("deleted") && jsonObject.getBoolean("deleted")) {
            return null
        }

        val comment = Comment()
        comment.id = jsonObject.getInt("id")
        comment.by = jsonObject.optString("by", "")
        comment.time = jsonObject.getInt("time")
        comment.parent = jsonObject.optInt("parent", 0)
        comment.expanded = true

        val text = optStringOrNull(jsonObject, "text")
        comment.text = if (text == null) "" else preprocessHtml(text)

        if (jsonObject.has("kids")) {
            val kidsArray = jsonObject.getJSONArray("kids")
            val kidCount = kidsArray.length()
            comment.children = kidCount
            // Store kids for later loading
            val kidsIds = IntArray(kidCount)
            for (i in 0..<kidCount) {
                kidsIds[i] = kidsArray.getInt(i)
            }
            comment.kidsIds = kidsIds
        } else {
            comment.children = 0
            comment.kidsIds = null
        }

        return comment
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
