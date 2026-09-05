package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.PreparedCommentThread
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.utils.HackerNewsLinks
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.overwriteWith
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerialName

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
    val cacheSummary: AlgoliaStorySummary? = null,
) {
    fun updateStoryInformation(story: Story, oldCommentCount: Int): Boolean {
        cacheSummary?.topLevelCommentIds?.takeIf { it.isNotEmpty() }?.let {
            story.kids = it.toIntArray()
        }
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
    suspend fun parsePrepared(
        response: String,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): AlgoliaCommentsResponse = withContext(parsingDispatcher) {
        prepare(response, topLevelCommentIds).restore(topLevelCommentIds, filteredUsers)
    }

    /** Prepare neutral content once; user filters are applied only when restoring presentation. */
    suspend fun prepare(
        response: String,
        topLevelCommentIds: List<Int> = emptyList(),
    ): PreparedCommentThread = withContext(parsingDispatcher) {
        // Keep canonical Algolia root order, allowing later live rankings to reorder whole subtrees.
        PreparedCommentThread.fromParsed(response, parse(response), topLevelCommentIds)
    }

    // Decode normal string fields directly. Unusual scalar types retain the legacy coercions
    // through a second decode from the original input, never from a partly consumed decoder.
    private val fastJson = Json(json) {
        isLenient = false
        serializersModule = json.serializersModule.overwriteWith(SerializersModule {
            contextual(String::class, NullableDefaultStringSerializer)
        })
    }
    private val flexibleJson = Json(json) {
        serializersModule = json.serializersModule.overwriteWith(SerializersModule {
            contextual(String::class, FlexibleStringSerializer)
        })
    }

    suspend fun parse(
        response: String?,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): AlgoliaCommentsResponse = withContext(parsingDispatcher) {
        val (item, payload) = try {
            val item = try {
                fastJson.decodeFromString(itemSerializer, response.orEmpty())
            } catch (_: IllegalArgumentException) {
                flexibleJson.decodeFromString(itemSerializer, response.orEmpty())
            }
            item to json.decodeFromJsonElement<AlgoliaCommentsPayload>(item.metadata.toJsonElement())
        } catch (error: SerializationException) {
            throw ApiDecodingException("Invalid Algolia comments JSON", error)
        } catch (error: IllegalArgumentException) {
            throw ApiDecodingException("Invalid Algolia comments JSON", error)
        }

        val normalizedFilteredUsers = buildSet(filteredUsers.size) {
            for (user in filteredUsers) {
                val trimmed = user.trim()
                if (trimmed.isNotEmpty()) add(trimmed.lowercase())
            }
        }
        val topLevelComments = if (topLevelCommentIds.isNotEmpty() && item.children.size > 1) {
            val priorityById = mutableMapOf<Int, Int>()
            topLevelCommentIds.forEachIndexed { index, id ->
                if (id !in priorityById) priorityById[id] = index
            }
            item.children.sortedBy { node ->
                priorityById[node.id] ?: topLevelCommentIds.size
            }
        } else item.children

        val descendants = item.children.sumOf { 1 + it.descendants }
        val flattenedComments = ArrayList<Comment>(descendants)
        for (node in topLevelComments) {
            appendComment(node, 0, normalizedFilteredUsers, coroutineContext, flattenedComments)
        }
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
            cacheSummary = AlgoliaStorySummary(item.metadata, descendants, topLevelCommentIds.toList()),
        )
    }

    private fun appendComment(
        payload: AlgoliaCommentPayload,
        depth: Int,
        filteredUsers: Set<String>,
        context: CoroutineContext,
        destination: MutableList<Comment>,
    ) {
        context.ensureActive()
        val rawText = payload.text.trim()
        val author = payload.author.trim()
        if (rawText.isEmpty() || rawText.equals(JSON_NULL_LITERAL, ignoreCase = true)) return
        if (filteredUsers.isNotEmpty() && author.lowercase() in filteredUsers) return

        destination.add(Comment().also { comment ->
            comment.id = payload.id
            comment.parent = payload.parentId
            comment.by = author
            comment.text = StoryTextProcessor.preprocessHtml(rawText)
            comment.time = payload.createdAt
            comment.expanded = true
            comment.depth = depth
            comment.children = payload.children.size
        })
        val children = if (payload.children.size > 1) {
            payload.children.sortedByDescending { it.children.size }
        } else payload.children
        for (child in children) appendComment(child, depth + 1, filteredUsers, context, destination)
    }

    private companion object {
        const val JSON_NULL_LITERAL = "null"
        val itemSerializer = AlgoliaItemSerializer(ListSerializer(AlgoliaCommentPayload.serializer()), emptyList())
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
)

@Serializable
private data class AlgoliaCommentPayload(
    @Contextual
    val text: String = "",
    @Contextual
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
) {
    val descendants: Int = children.sumOf { 1 + it.descendants }
}

private object NullableDefaultStringSerializer : KSerializer<String> {
    private val nullableSerializer = String.serializer().nullable
    override val descriptor = nullableSerializer.descriptor
    override fun deserialize(decoder: Decoder): String =
        decoder.decodeSerializableValue(nullableSerializer).orEmpty()
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

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
        // Ordinary integers need no second JSON lexer. Preserve the existing exponent/decimal
        // handling below, and the stricter conversion of quoted numeric strings.
        val integer = primitive.content.toIntOrNull()
        if (primitive.isString) return integer ?: 0
        if (integer != null) return integer
        primitive.intOrNull?.let { return it }
        val doubleValue = primitive.doubleOrNull ?: return 0
        val intValue = doubleValue.toInt()
        return if (intValue.toDouble() == doubleValue) intValue else 0
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}
