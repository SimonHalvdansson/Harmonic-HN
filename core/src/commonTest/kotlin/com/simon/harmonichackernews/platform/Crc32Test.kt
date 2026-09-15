package com.simon.harmonichackernews.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class Crc32Test {
    @Test fun ieeeCheckValueAndEmptyInput() {
        assertEquals(0xcbf43926.toInt(), KotlinCrc32.compute("123456789".encodeToByteArray(), 0))
        assertEquals(0, KotlinCrc32.compute(byteArrayOf(), 0))
    }

    @Test fun excludesTheCacheHeaderAndSupportsEmptyPayloads() {
        val bytes = "12345123456789".encodeToByteArray()
        assertEquals(0xcbf43926.toInt(), KotlinCrc32.compute(bytes, 5))
        assertEquals(0, KotlinCrc32.compute(bytes, bytes.size))
    }
}
