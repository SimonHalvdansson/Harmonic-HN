package com.simon.harmonichackernews.utils

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays

/** Immutable sorted primitive table for allocation-free blocked-host lookups.  */
class AdHostBlocklist private constructor(private val sortedHostHashes: LongArray) {
    val isEmpty: Boolean
        get() = sortedHostHashes.isEmpty()

    fun size(): Int = sortedHostHashes.size

    fun contains(host: String?): Boolean =
        host != null && Arrays.binarySearch(sortedHostHashes, hash(host)) >= 0

    companion object {
        private const val FILE_MAGIC = 0x48414431 // HAD1
        private const val MAX_HOST_COUNT = 1000000
        private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL
        private const val FNV_PRIME = 0x100000001b3L
        private val EMPTY = AdHostBlocklist(LongArray(0))

        fun empty(): AdHostBlocklist = EMPTY

        @Throws(IOException::class)
        fun read(inputStream: InputStream): AdHostBlocklist {
            DataInputStream(BufferedInputStream(inputStream)).use { input ->
                if (input.readInt() != FILE_MAGIC) {
                    throw IOException("Invalid ad host blocklist header")
                }
                val count = input.readInt()
                if (count < 0 || count > MAX_HOST_COUNT) {
                    throw IOException("Invalid ad host blocklist size: $count")
                }

                val encodedHashes = ByteArray(Math.multiplyExact(count, Long.SIZE_BYTES))
                input.readFully(encodedHashes)
                if (input.read() != -1) {
                    throw IOException("Unexpected data after ad host blocklist")
                }

                val hashes = LongArray(count)
                ByteBuffer.wrap(encodedHashes)
                    .order(ByteOrder.BIG_ENDIAN)
                    .asLongBuffer()
                    .get(hashes)
                for (i in 0..<count) {
                    if (i > 0 && hashes[i - 1] >= hashes[i]) {
                        throw IOException("Ad host blocklist is not strictly sorted")
                    }
                }
                return if (count == 0) EMPTY else AdHostBlocklist(hashes)
            }
        }

        private fun hash(host: String): Long {
            var hash = FNV_OFFSET_BASIS
            for (character in host) {
                hash = hash xor character.code.toLong()
                hash *= FNV_PRIME
            }
            return hash
        }
    }
}
