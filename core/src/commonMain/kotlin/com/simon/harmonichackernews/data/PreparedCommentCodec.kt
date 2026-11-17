package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.platform.Crc32
import com.simon.harmonichackernews.platform.KotlinCrc32
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf

/** FlatBuffers production storage, with legacy Protobuf/JSON readers and a CRC-32 envelope. */
@OptIn(ExperimentalSerializationApi::class)
object PreparedCommentCodec {
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
        return ByteArray(payload.size + 5).also { result ->
            result[0] = encoding.wireId.toByte()
            repeat(4) { result[it + 1] = (checksum ushr (it * 8)).toByte() }
            payload.copyInto(result, 5)
        }
    }

    fun decode(bytes: ByteArray, crc32: Crc32 = KotlinCrc32): PreparedCommentThread? = runCatching {
        if (bytes.size < 5) return null
        var checksum = 0
        repeat(4) { checksum = checksum or ((bytes[it + 1].toInt() and 255) shl (it * 8)) }
        if (checksum != crc32.compute(bytes, 5)) return null
        val payload = bytes.copyOfRange(5, bytes.size)
        val decoded = when (bytes[0].toInt()) {
            Encoding.PROTOBUF.wireId -> ProtoBuf.decodeFromByteArray(PreparedCommentThread.serializer(), payload)
            Encoding.JSON.wireId -> json.decodeFromString(PreparedCommentThread.serializer(), payload.decodeToString())
            Encoding.FLATBUFFERS.wireId -> PreparedCommentFlatBuffer.decode(payload)
            else -> return null
        }
        decoded.takeIf { it.isCompatible() }
    }.getOrNull()
}
