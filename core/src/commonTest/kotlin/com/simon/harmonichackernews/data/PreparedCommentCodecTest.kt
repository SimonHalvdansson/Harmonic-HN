package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.platform.KotlinCrc32
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreparedCommentCodecTest {
    private val thread = PreparedCommentThread(
        1, 1, "a".repeat(64),
        PreparedCommentStory(42, "Golden 世界 🚀", 123, 1700000000, "story", "Zoë", 0, 0,
            "", "https://example.com/é", "Header\u0000body"),
        listOf(
            PreparedCommentRecord(7, 42, "alice", 100, "<p>世界 🚀</p>", null, 0, 1, 2),
            PreparedCommentRecord(8, 7, "", 200, "", "", 1, 0, 2),
        ),
        """{"id":42,"descendants":2}""", listOf(7, 7, 0),
    )

    @Test fun readsFrozenLegacyAndIndependentFlatcFilesOnEveryPlatform() {
        for (fixture in listOf(PreparedCommentWireFixtures.PROTOBUF,
            PreparedCommentWireFixtures.JSON, PreparedCommentWireFixtures.FLATBUFFERS)) {
            assertEquals(thread, PreparedCommentCodec.decode(Base64.decode(fixture)))
        }
    }

    @Test fun defaultsToFlatBuffersWithoutChangingLegacyFormatIds() {
        assertEquals(0, PreparedCommentCodec.Encoding.PROTOBUF.wireId)
        assertEquals(1, PreparedCommentCodec.Encoding.JSON.wireId)
        assertEquals(2, PreparedCommentCodec.Encoding.FLATBUFFERS.wireId)
        val bytes = PreparedCommentCodec.encode(thread)
        assertEquals(2, bytes[0].toInt())
        assertEquals("HNPC", bytes.copyOfRange(9, 13).decodeToString())
        for (encoding in PreparedCommentCodec.Encoding.entries) {
            assertEquals(thread, PreparedCommentCodec.decode(PreparedCommentCodec.encode(thread, encoding)))
        }
    }

    @Test fun preservesIntegerExtremesEmptyVectorsAndOptionalText() {
        val values = thread.copy(
            story = thread.story.copy(points = Int.MIN_VALUE, createdAtEpochSeconds = Int.MAX_VALUE),
            comments = emptyList(), rankedIds = emptyList(),
        )
        assertEquals(values, PreparedCommentCodec.decode(PreparedCommentCodec.encode(values)))
        val decoded = assertNotNull(PreparedCommentCodec.decode(PreparedCommentCodec.encode(thread)))
        assertNull(decoded.comments[0].expandedHtml)
        assertEquals("", decoded.comments[1].expandedHtml)
    }

    @Test fun validatedDecodeCanRestoreAndCopiesStillValidateTheirOwnStructure() = runTest {
        val decoded = assertNotNull(PreparedCommentCodec.decode(PreparedCommentCodec.encode(thread)))
        assertEquals(listOf(7, 8), decoded.restore().comments.map { it.id })
        val invalidCopy = decoded.copy(comments = listOf(decoded.comments.first()))
        assertFailsWith<IllegalArgumentException> { invalidCopy.restore() }
        assertNull(PreparedCommentCodec.decode(PreparedCommentCodec.encode(invalidCopy)))
    }

    @Test fun rejectsUnknownTagsAndIdentifiers() {
        val unknown = PreparedCommentCodec.encode(thread).also { it[0] = 99 }
        assertNull(PreparedCommentCodec.decode(unknown))
        rejectMutation { it[9] = 'X'.code.toByte() }
    }

    @Test fun rejectsOutOfBoundsOffsetsCountsAndMissingRequiredFieldsWithValidChecksums() {
        rejectMutation { putInt(it, 5, Int.MAX_VALUE) }
        rejectMutation { putInt(it, root(it), Int.MIN_VALUE) }
        rejectMutation { putInt(it, field(it, root(it), 3), -1) }
        rejectMutation { putInt(it, vector(it, 4), Int.MAX_VALUE) }
        rejectMutation { putInt(it, vector(it, 4), -1) }
        rejectMutation { putInt(it, vector(it, 6), Int.MAX_VALUE) }
        rejectMutation { putInt(it, vector(it, 4) + 4, Int.MAX_VALUE) }
        rejectMutation {
            val root = root(it)
            val vtable = root - int(it, root)
            it[vtable + 4 + 3 * 2] = 0
            it[vtable + 5 + 3 * 2] = 0
        }
        rejectMutation {
            val digest = field(it, root(it), 2).let { offset -> offset + int(it, offset) }
            putInt(it, digest, Int.MAX_VALUE)
        }
        rejectMutation {
            val digest = field(it, root(it), 2).let { offset -> offset + int(it, offset) }
            it[digest + 4 + 64] = 1 // Required NUL terminator.
        }
    }

    @Test fun verifierRejectsHighBitFieldOffsetsBeforeCallingNativeAccessors() {
        val encoded = PreparedCommentCodec.encode(thread)
        val payload = encoded.copyOfRange(5, encoded.size).copyOf(encoded.size + 65536)
        val root = int(payload, 0)
        val vtable = root - int(payload, root)
        payload[vtable + 2] = 0xff.toByte()
        payload[vtable + 3] = 0xff.toByte() // Large object size, still inside the extended buffer.
        payload[vtable + 4] = 0
        payload[vtable + 5] = 0x80.toByte() // Signed accessor would read before the table.
        assertFailsWith<IllegalArgumentException> { PreparedCommentFlatBufferVerifier(payload).verify() }
    }

    @Test fun truncatedPayloadsAndRandomMutationsCannotEscapeAsUnvalidatedContent() {
        val bytes = PreparedCommentCodec.encode(thread)
        for (length in 5 until bytes.size / 2) {
            val truncated = bytes.copyOf(length)
            checksum(truncated)
            assertNull(PreparedCommentCodec.decode(truncated))
        }
        val random = Random(327)
        repeat(500) {
            val mutated = bytes.copyOf()
            val position = random.nextInt(5, mutated.size)
            mutated[position] = random.nextInt(256).toByte()
            checksum(mutated)
            // Some changes affect only content or unused padding and remain valid. Others must
            // return null, without throwing or allocating based on an unchecked length/offset.
            PreparedCommentCodec.decode(mutated)?.let { decoded ->
                assertTrue(decoded.isCompatible())
                assertNotNull(PreparedCommentCodec.decode(PreparedCommentCodec.encode(decoded)))
            }
        }
    }

    private fun rejectMutation(mutate: (ByteArray) -> Unit) {
        val bytes = PreparedCommentCodec.encode(thread)
        mutate(bytes)
        checksum(bytes)
        assertNull(PreparedCommentCodec.decode(bytes))
    }

    private fun checksum(bytes: ByteArray) = putInt(bytes, 1, KotlinCrc32.compute(bytes, 5))
    private fun root(bytes: ByteArray) = 5 + int(bytes, 5)
    private fun field(bytes: ByteArray, table: Int, id: Int): Int {
        val entry = table - int(bytes, table) + 4 + id * 2
        return table + (bytes[entry].toInt() and 255) + ((bytes[entry + 1].toInt() and 255) shl 8)
    }
    private fun vector(bytes: ByteArray, id: Int): Int = field(bytes, root(bytes), id)
        .let { it + int(bytes, it) }
    private fun int(bytes: ByteArray, offset: Int): Int = (0..3).fold(0) { value, i ->
        value or ((bytes[offset + i].toInt() and 255) shl (i * 8))
    }
    private fun putInt(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }
}
