package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.platform.Crc32
import com.simon.harmonichackernews.platform.KotlinCrc32
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf

/** FlatBuffers production storage, with legacy Protobuf/JSON readers and a CRC-32 envelope. */
@OptIn(ExperimentalSerializationApi::class)
object PreparedCommentCodec {
    private const val ENVELOPE_SIZE = 5
    // These IDs are persisted. Never derive them from enum order or reuse a retired ID.
    enum class Encoding(val wireId: Int) { PROTOBUF(0), JSON(1), FLATBUFFERS(2) }
    private val json = Json

    fun encode(
        thread: PreparedCommentThread,
        encoding: Encoding = Encoding.FLATBUFFERS,
        crc32: Crc32 = KotlinCrc32,
    ): ByteArray {
        require(thread.sourceDigest.length == 64) { "Prepared cache requires a source digest" }
        val payload = when (encoding) {
            Encoding.PROTOBUF -> ProtoBuf.encodeToByteArray(PreparedCommentThread.serializer(), thread)
            Encoding.JSON -> json.encodeToString(PreparedCommentThread.serializer(), thread).encodeToByteArray()
            Encoding.FLATBUFFERS -> PreparedCommentFlatBuffer.encode(thread)
        }
        val checksum = crc32.compute(payload, 0)
        return ByteArray(payload.size + ENVELOPE_SIZE).also { result ->
            result[0] = encoding.wireId.toByte()
            repeat(4) { result[it + 1] = (checksum ushr (it * 8)).toByte() }
            payload.copyInto(result, ENVELOPE_SIZE)
        }
    }

    fun decode(bytes: ByteArray, crc32: Crc32 = KotlinCrc32): PreparedCommentThread? = runCatching {
        if (bytes.size < ENVELOPE_SIZE) return null
        var checksum = 0
        repeat(4) { checksum = checksum or ((bytes[it + 1].toInt() and 255) shl (it * 8)) }
        if (checksum != crc32.compute(bytes, ENVELOPE_SIZE)) return null
        val decoded = when (bytes[0].toInt()) {
            Encoding.PROTOBUF.wireId -> ProtoBuf.decodeFromByteArray(
                PreparedCommentThread.serializer(), bytes.copyOfRange(ENVELOPE_SIZE, bytes.size),
            )
            Encoding.JSON.wireId -> json.decodeFromString(
                PreparedCommentThread.serializer(), bytes.decodeToString(startIndex = ENVELOPE_SIZE),
            )
            Encoding.FLATBUFFERS.wireId -> PreparedCommentFlatBuffer.decode(bytes, ENVELOPE_SIZE)
            else -> return null
        }
        decoded.takeIf { it.isCompatible() }
    }.getOrNull()
}
