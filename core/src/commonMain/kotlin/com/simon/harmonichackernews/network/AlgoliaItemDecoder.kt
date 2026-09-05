package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.serialization.JsonObject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonElement

/** Root metadata retained from the original decode; counts include hidden/deleted subtrees. */
class AlgoliaStorySummary internal constructor(
    private val metadata: JsonObject,
    private val descendants: Int,
    val topLevelCommentIds: List<Int> = emptyList(),
) {
    fun encode(fallbackId: Int): String =
        JSONParser.compactAlgoliaStoryFields(metadata, fallbackId, descendants, topLevelCommentIds)
}

internal class AlgoliaItem<T>(val metadata: JsonObject, val children: T)

/** Shares root-field handling between full comment decoding and count-only cache compaction. */
internal class AlgoliaItemSerializer<T>(
    private val childrenSerializer: KSerializer<T>,
    private val emptyChildren: T,
) : KSerializer<AlgoliaItem<T>> {
    private val fields = JSONParser.ALGOLIA_SUMMARY_FIELDS
    override val descriptor = buildClassSerialDescriptor("AlgoliaItem") {
        element("children", childrenSerializer.descriptor, isOptional = true)
        for (field in fields) element(field, JsonElement.serializer().descriptor, isOptional = true)
    }

    override fun deserialize(decoder: Decoder): AlgoliaItem<T> = decoder.decodeStructure(descriptor) {
        val metadata = JsonObject()
        var children = emptyChildren
        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break
                0 -> children = decodeSerializableElement(descriptor, index, childrenSerializer)
                else -> metadata.put(
                    fields[index - 1],
                    decodeSerializableElement(descriptor, index, JsonElement.serializer()),
                )
            }
        }
        AlgoliaItem(metadata, children)
    }

    override fun serialize(encoder: Encoder, value: AlgoliaItem<T>): Unit =
        error("AlgoliaItem is decode-only")
}

// Use a generated recursive descriptor, while decoding each node directly into its count.
// Unknown fields (including comment text) are skipped by kotlinx.serialization.
@Serializable
private class CommentCountShape(val children: List<CommentCountShape> = emptyList())

private object AlgoliaCommentCountSerializer : KSerializer<Int> {
    override val descriptor = CommentCountShape.serializer().descriptor

    override fun deserialize(decoder: Decoder): Int = decoder.decodeStructure(descriptor) {
        var descendants = 0
        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break
                0 -> descendants = decodeSerializableElement(
                    descriptor, index, AlgoliaChildrenCountSerializer,
                )
            }
        }
        1 + descendants
    }

    override fun serialize(encoder: Encoder, value: Int): Unit =
        error("AlgoliaCommentCount is decode-only")
}

/** Accumulates counts as array elements arrive, without retaining a list or node tree. */
internal object AlgoliaChildrenCountSerializer : KSerializer<Int> {
    override val descriptor = ListSerializer(CommentCountShape.serializer()).descriptor

    override fun deserialize(decoder: Decoder): Int = decoder.decodeStructure(descriptor) {
        var count = 0
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            count += decodeSerializableElement(descriptor, index, AlgoliaCommentCountSerializer)
        }
        count
    }

    override fun serialize(encoder: Encoder, value: Int): Unit =
        error("AlgoliaChildrenCount is decode-only")
}
