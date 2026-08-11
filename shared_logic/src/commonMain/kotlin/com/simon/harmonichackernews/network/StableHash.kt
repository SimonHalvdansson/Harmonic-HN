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
        val source = value.encodeToByteArray()
        val paddedSize = ((source.size + 9 + 63) / 64) * 64
        val message = ByteArray(paddedSize)
        source.copyInto(message)
        message[source.size] = 0x80.toByte()
        val bitLength = source.size.toLong() * 8L
        for (index in 0 until 8) {
            message[paddedSize - 1 - index] = (bitLength ushr (index * 8)).toByte()
        }

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
        for (chunkStart in message.indices step 64) {
            for (index in 0 until 16) {
                val offset = chunkStart + index * 4
                words[index] =
                    ((message[offset].toInt() and 0xff) shl 24) or
                    ((message[offset + 1].toInt() and 0xff) shl 16) or
                    ((message[offset + 2].toInt() and 0xff) shl 8) or
                    (message[offset + 3].toInt() and 0xff)
            }
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

        val result = CharArray(hash.size * 8)
        hash.forEachIndexed { wordIndex, word ->
            for (nibble in 0 until 8) {
                result[wordIndex * 8 + nibble] =
                    hexDigits[(word ushr ((7 - nibble) * 4)) and 0x0f]
            }
        }
        return result.concatToString()
    }

    private fun Int.rotateRight(bitCount: Int): Int =
        (this ushr bitCount) or (this shl (32 - bitCount))
}
