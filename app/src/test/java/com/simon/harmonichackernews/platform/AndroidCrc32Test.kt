package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.data.PreparedCommentCodec
import com.simon.harmonichackernews.network.AlgoliaCommentsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidCrc32Test {
    @Test fun matchesIeeeCheckValueAndPortableImplementationAcrossBulkSizes() {
        assertEquals(0xcbf43926.toInt(), AndroidCrc32.compute("123456789".encodeToByteArray(), 0))
        for (size in listOf(0, 1, 7, 8, 255, 256, 65_535, 65_536, 65_537, 1_392_893)) {
            val bytes = ByteArray(size) { (it * 31 + it / 256).toByte() }
            for (start in listOf(0, minOf(5, size), size)) {
                assertEquals("size=$size start=$start", KotlinCrc32.compute(bytes, start), AndroidCrc32.compute(bytes, start))
            }
        }
    }

    @Test fun cacheBytesRemainCompatibleAndCorruptionIsRejected() = runBlocking {
        val prepared = AlgoliaCommentsParser().prepare(
            """{"id":42,"title":"Cache compatibility","children":[
                {"id":7,"parent_id":42,"author":"alice","text":"Hello 世界","created_at_i":123}
            ]}""",
        )
        for (encoding in PreparedCommentCodec.Encoding.entries) {
            val portable = PreparedCommentCodec.encode(prepared, encoding)
            val platform = PreparedCommentCodec.encode(prepared, encoding, AndroidCrc32)
            assertArrayEquals(portable, platform)
            assertEquals(prepared, PreparedCommentCodec.decode(portable, AndroidCrc32))
            assertEquals(prepared, PreparedCommentCodec.decode(platform))
            platform[platform.lastIndex] = (platform.last().toInt() xor 1).toByte()
            assertNull(PreparedCommentCodec.decode(platform, AndroidCrc32))
        }
    }
}
