package com.simon.harmonichackernews.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf

/** ProtoBuf production storage; JSON is retained for benchmark comparisons. Both validate a checksum. */
@OptIn(ExperimentalSerializationApi::class)
object PreparedCommentCodec {
    enum class Encoding { PROTOBUF, JSON }
    private val json = Json
    private val crcTable = IntArray(256) { value ->
        var crc = value
        repeat(8) { crc = (crc ushr 1) xor (if (crc and 1 != 0) 0xedb88320.toInt() else 0) }
        crc
    }

    fun encode(thread: PreparedCommentThread, encoding: Encoding = Encoding.PROTOBUF): ByteArray {
        val payload = when (encoding) {
            Encoding.PROTOBUF -> ProtoBuf.encodeToByteArray(PreparedCommentThread.serializer(), thread)
            Encoding.JSON -> json.encodeToString(PreparedCommentThread.serializer(), thread).encodeToByteArray()
        }
        val checksum = crc32(payload, 0)
        return ByteArray(payload.size + 5).also { result ->
            result[0] = encoding.ordinal.toByte()
            repeat(4) { result[it + 1] = (checksum ushr (it * 8)).toByte() }
            payload.copyInto(result, 5)
        }
    }

    fun decode(bytes: ByteArray): PreparedCommentThread? = runCatching {
        if (bytes.size < 5) return null
        var checksum = 0
        repeat(4) { checksum = checksum or ((bytes[it + 1].toInt() and 255) shl (it * 8)) }
        if (checksum != crc32(bytes, 5)) return null
        val payload = bytes.copyOfRange(5, bytes.size)
        val decoded = when (bytes[0].toInt()) {
            Encoding.PROTOBUF.ordinal -> ProtoBuf.decodeFromByteArray(PreparedCommentThread.serializer(), payload)
            Encoding.JSON.ordinal -> json.decodeFromString(PreparedCommentThread.serializer(), payload.decodeToString())
            else -> return null
        }
        decoded.takeIf { it.isCompatible() }
    }.getOrNull()

    private fun crc32(bytes: ByteArray, start: Int): Int {
        var crc = -1
        for (index in start until bytes.size) {
            crc = (crc ushr 8) xor crcTable[(crc xor bytes[index].toInt()) and 255]
        }
        return crc.inv()
    }
}
