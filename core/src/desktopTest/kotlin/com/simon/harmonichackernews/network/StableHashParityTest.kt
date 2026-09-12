package com.simon.harmonichackernews.network

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class StableHashParityTest {
    @Test
    fun commentFixturesMatchPlatformSha256() {
        for (suffix in listOf("", "_medium", "_large")) {
            val path = Path.of("../app/src/benchmark/assets/comments_benchmark_fixture$suffix.json")
            assertMatchesPlatform(Files.readString(path), path.fileName.toString())
        }
    }

    @Test
    fun everyUtf16CodeUnitMatchesPlatformEncoding() {
        // Separators keep surrogate code units unpaired, covering every malformed code unit too.
        val value = buildString {
            for (code in 0..0xffff) {
                append(code.toChar())
                append('x')
            }
        }
        assertMatchesPlatform(value, "All UTF-16 code units")
    }

    @Test
    fun randomizedUtf16AndSupplementaryTextMatchPlatformSha256() {
        val random = Random(256)
        repeat(200) { sample ->
            val value = buildString {
                repeat(random.nextInt(20_000)) {
                    when (random.nextInt(4)) {
                        0 -> append(random.nextInt(128).toChar())
                        1 -> append(random.nextInt(0x10000).toChar())
                        2 -> {
                            append(random.nextInt(0xd800, 0xdc00).toChar())
                            append(random.nextInt(0xdc00, 0xe000).toChar())
                        }
                        else -> append(random.nextInt(0xd800, 0xe000).toChar())
                    }
                }
            }
            assertMatchesPlatform(value, "Random sample $sample")
        }
    }

    private fun assertMatchesPlatform(value: String, label: String) {
        // This is the exact encoding used by the original hasher, with an independent digest.
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(expected, StableHash.sha256Hex(value), label)
    }
}
