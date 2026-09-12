package com.simon.harmonichackernews.network

/** Stable SHA-256 identifiers without a JVM or native crypto dependency. */
object StableHash {
    private val roundConstants = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )
    private val hexDigits = "0123456789abcdef".toCharArray()

    fun sha256Hex(value: String): String {
        val hash = intArrayOf(
            0x6a09e667,
            0xbb67ae85.toInt(),
            0x3c6ef372,
            0xa54ff53a.toInt(),
            0x510e527f,
            0x9b05688c.toInt(),
            0x1f83d9ab,
            0x5be0cd19,
        )
        val words = IntArray(64)
        // Encode directly into a bounded workspace instead of copying the entire response.
        // Leave room for a complete UTF-8 character and the final SHA-256 padding blocks.
        val message = ByteArray(minOf(value.length.toLong() * 3 + 128, 4096L).toInt())
        var charIndex = 0
        var size = 0
        var byteCount = 0L
        while (true) {
            // ASCII code units are already UTF-8 bytes. Feed complete blocks straight into
            // the schedule, avoiding a copy through the encoding buffer for typical JSON.
            if (size == 0 && value.length - charIndex >= 64) {
                var combined = 0
                for (index in 0 until 16) {
                    val start = charIndex + index * 4
                    val a = value[start].code
                    val b = value[start + 1].code
                    val c = value[start + 2].code
                    val d = value[start + 3].code
                    combined = combined or a or b or c or d
                    words[index] = (a shl 24) or (b shl 16) or (c shl 8) or d
                }
                if (combined < 0x80) {
                    charIndex += 64
                    byteCount += 64
                    compressWords(hash, words)
                    continue
                }
            }
            val carriedBytes = size
            while (charIndex < value.length && size <= message.size - 128) {
                val character = value[charIndex++].code
                when {
                    character < 0x80 -> message[size++] = character.toByte()
                    character < 0x800 -> {
                        message[size++] = (0xc0 or (character ushr 6)).toByte()
                        message[size++] = (0x80 or (character and 0x3f)).toByte()
                    }
                    character !in 0xd800..0xdfff -> {
                        message[size++] = (0xe0 or (character ushr 12)).toByte()
                        message[size++] = (0x80 or ((character ushr 6) and 0x3f)).toByte()
                        message[size++] = (0x80 or (character and 0x3f)).toByte()
                    }
                    character <= 0xdbff && charIndex < value.length &&
                        value[charIndex].code in 0xdc00..0xdfff -> {
                        val codePoint = 0x10000 + ((character - 0xd800) shl 10) +
                            (value[charIndex++].code - 0xdc00)
                        message[size++] = (0xf0 or (codePoint ushr 18)).toByte()
                        message[size++] = (0x80 or ((codePoint ushr 12) and 0x3f)).toByte()
                        message[size++] = (0x80 or ((codePoint ushr 6) and 0x3f)).toByte()
                        message[size++] = (0x80 or (codePoint and 0x3f)).toByte()
                    }
                    else -> {
                        // Match this platform's encodeToByteArray replacement for unpaired
                        // surrogates; JVM and Native need not choose the same replacement bytes.
                        malformedUtf8.copyInto(message, size)
                        size += malformedUtf8.size
                    }
                }
            }
            byteCount += size - carriedBytes
            val finished = charIndex == value.length
            if (finished) {
                val paddedSize = (size + 9 + 63) / 64 * 64
                message[size] = 0x80.toByte()
                message.fill(0, size + 1, paddedSize)
                val bitLength = byteCount * 8L
                for (index in 0 until 8) {
                    message[paddedSize - 1 - index] = (bitLength ushr (index * 8)).toByte()
                }
                size = paddedSize
            }
            val completeBytes = size / 64 * 64
            var offset = 0
            while (offset < completeBytes) {
                compress(message, offset, hash, words)
                offset += 64
            }
            if (finished) break
            message.copyInto(message, 0, completeBytes, size)
            size -= completeBytes
        }

        val result = CharArray(hash.size * 8)
        hash.forEachIndexed { wordIndex, word ->
            for (nibble in 0 until 8) {
                result[wordIndex * 8 + nibble] =
                    hexDigits[(word ushr ((7 - nibble) * 4)) and 0x0f]
            }
        }
        return result.concatToString()
    }

    private val malformedUtf8 = "\uD800".encodeToByteArray()

    // Keep block compression in the caller's hot loop; a separate call regresses JVM throughput.
    @Suppress("NOTHING_TO_INLINE")
    private inline fun compress(message: ByteArray, chunkStart: Int, hash: IntArray, words: IntArray) {
        for (index in 0 until 16) {
            val offset = chunkStart + index * 4
            words[index] =
                ((message[offset].toInt() and 0xff) shl 24) or
                ((message[offset + 1].toInt() and 0xff) shl 16) or
                ((message[offset + 2].toInt() and 0xff) shl 8) or
                (message[offset + 3].toInt() and 0xff)
        }
        compressWords(hash, words)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun compressWords(hash: IntArray, words: IntArray) {
        for (index in 16 until 64) {
            val previous15 = words[index - 15]
            val previous2 = words[index - 2]
            val sigma0 = previous15.rotateRight(7) xor previous15.rotateRight(18) xor
                (previous15 ushr 3)
            val sigma1 = previous2.rotateRight(17) xor previous2.rotateRight(19) xor
                (previous2 ushr 10)
            words[index] = words[index - 16] + sigma0 + words[index - 7] + sigma1
        }

        var a = hash[0]
        var b = hash[1]
        var c = hash[2]
        var d = hash[3]
        var e = hash[4]
        var f = hash[5]
        var g = hash[6]
        var h = hash[7]
        for (index in 0 until 64) {
            val sum1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choice = (e and f) xor (e.inv() and g)
            val temp1 = h + sum1 + choice + roundConstants[index] + words[index]
            val sum0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = sum0 + majority
            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }
        hash[0] += a
        hash[1] += b
        hash[2] += c
        hash[3] += d
        hash[4] += e
        hash[5] += f
        hash[6] += g
        hash[7] += h
    }

    private fun Int.rotateRight(bitCount: Int): Int =
        (this ushr bitCount) or (this shl (32 - bitCount))
}
