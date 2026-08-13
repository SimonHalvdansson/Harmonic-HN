package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.utils.HackerNewsLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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
import kotlin.coroutines.coroutineContext

/** Platform-neutral result of parsing an Algolia item and its comment tree. */
data class AlgoliaCommentsResponse(
    val comments: MutableList<Comment> = mutableListOf(),
    val title: String = "",
    val points: Int = 0,
    val createdAtEpochSeconds: Int = 0,
    val type: String = "",
    val author: String = "",
    val storyId: Int = 0,
    val parentId: Int = 0,
    val storyTitle: String = "",
    val url: String = "",
    val text: String = "",
    val id: Int = 0,
) {
    fun updateStoryInformation(story: Story, oldCommentCount: Int): Boolean {
        val changed = story.title.isNullOrEmpty() ||
            title != story.title ||
            points != story.score ||
            oldCommentCount != comments.size + 1

        story.time = createdAtEpochSeconds
        if (type == "comment") {
            story.title = "Comment by $author"
            story.isLink = false
            story.url = HackerNewsLinks.itemUrl(storyId)
            story.isComment = true
            story.parentId = parentId
            story.commentMasterId = storyId
            story.commentMasterTitle = storyTitle
        } else {
            story.title = title
            story.isLink = url.isNotEmpty() && url != JSON_NULL_LITERAL
            story.url = if (story.isLink) url else HackerNewsLinks.itemUrl(story.id)
            StoryTextProcessor.applyTitleBadges(story)
        }

        if (text.isNotEmpty() && text != JSON_NULL_LITERAL) {
            story.text = StoryTextProcessor.preprocessHtml(text)
        }
        story.descendants = comments.size
        story.id = id
        story.score = points
        story.by = author
        story.loaded = true
        return changed
    }

    private companion object {
        const val JSON_NULL_LITERAL = "null"
    }
}

/**
 * Parses and prepares Algolia's nested comment response without Android types.
 * The returned shared comments are flattened in display order for direct screen consumption.
 */
class AlgoliaCommentsParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val parsingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun parse(
        response: String?,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): AlgoliaCommentsResponse = withContext(parsingDispatcher) {
        val payload = try {
            json.decodeFromString<AlgoliaCommentsPayload>(response.orEmpty())
        } catch (error: SerializationException) {
            throw ApiDecodingException("Invalid Algolia comments JSON", error)
        } catch (error: IllegalArgumentException) {
            throw ApiDecodingException("Invalid Algolia comments JSON", error)
        }

        val normalizedFilteredUsers = filteredUsers
            .asSequence()
            .map { user -> user.trim() }
            .filter { user -> user.isNotEmpty() }
            .map { user -> user.lowercase() }
            .toSet()
        val topLevelComments = payload.children.mapNotNull { child ->
            parseComment(child, depth = 0, normalizedFilteredUsers)
        }.toMutableList()

        if (topLevelCommentIds.isNotEmpty() && topLevelComments.size > 1) {
            val priorityById = mutableMapOf<Int, Int>()
            topLevelCommentIds.forEachIndexed { index, id ->
                if (id !in priorityById) priorityById[id] = index
            }
            topLevelComments.sortBy { node ->
                priorityById[node.comment.id] ?: topLevelCommentIds.size
            }
        }

        val flattenedComments = mutableListOf<Comment>()
        topLevelComments.forEach { node -> flatten(node, flattenedComments) }
        AlgoliaCommentsResponse(
            comments = flattenedComments,
            title = payload.title,
            points = payload.points,
            createdAtEpochSeconds = payload.createdAt,
            type = payload.type,
            author = payload.author,
            storyId = payload.storyId,
            parentId = payload.parentId,
            storyTitle = payload.storyTitle,
            url = payload.url,
            text = payload.text,
            id = payload.id,
        )
    }

    private suspend fun parseComment(
        payload: AlgoliaCommentPayload,
        depth: Int,
        filteredUsers: Set<String>,
    ): ParsedCommentNode? {
        coroutineContext.ensureActive()
        val rawText = payload.text.trim()
        val author = payload.author.trim()
        val childNodes = payload.children.mapNotNull { child ->
            parseComment(child, depth + 1, filteredUsers)
        }.sortedByDescending { node -> node.comment.children }
        coroutineContext.ensureActive()

        if (rawText.isEmpty() || rawText.equals(JSON_NULL_LITERAL, ignoreCase = true)) return null
        if (author.lowercase() in filteredUsers) return null

        return ParsedCommentNode(
            comment = Comment().also { comment ->
                comment.id = payload.id
                comment.parent = payload.parentId
                comment.by = author
                comment.text = StoryTextProcessor.preprocessHtml(rawText)
                comment.time = payload.createdAt
                comment.expanded = true
                comment.depth = depth
                comment.children = payload.children.size
            },
            children = childNodes,
        )
    }

    private fun flatten(node: ParsedCommentNode, destination: MutableList<Comment>) {
        destination += node.comment
        node.children.forEach { child -> flatten(child, destination) }
    }

    private data class ParsedCommentNode(
        val comment: Comment,
        val children: List<ParsedCommentNode>,
    )

    private companion object {
        const val JSON_NULL_LITERAL = "null"
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
        return if (primitive.isString || primitive.doubleOrNull != null) primitive.content else ""
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

private object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return 0
        if (primitive.isString) return primitive.content.toIntOrNull() ?: 0
        primitive.intOrNull?.let { return it }
        val doubleValue = primitive.doubleOrNull ?: return 0
        val intValue = doubleValue.toInt()
        return if (intValue.toDouble() == doubleValue) intValue else 0
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}
