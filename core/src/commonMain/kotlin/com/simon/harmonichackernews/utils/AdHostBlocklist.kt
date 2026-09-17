package com.simon.harmonichackernews.utils

/** Immutable sorted primitive table for allocation-free blocked-host lookups. */
class AdHostBlocklist private constructor(private val sortedHostHashes: LongArray) {
    val isEmpty: Boolean
        get() = sortedHostHashes.isEmpty()

    val size: Int
        get() = sortedHostHashes.size

    fun contains(host: String?): Boolean {
        if (host == null) return false
        val target = hash(host)
        var low = 0
        var high = sortedHostHashes.lastIndex
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val comparison = sortedHostHashes[middle].compareTo(target)
            when {
                comparison < 0 -> low = middle + 1
                comparison > 0 -> high = middle - 1
                else -> return true
            }
        }
        return false
    }

    companion object {
        private const val FILE_MAGIC = 0x48414431 // HAD1
        private const val HEADER_SIZE = 8
        private const val LONG_BYTES = 8
        private const val MAX_HOST_COUNT = 1_000_000
        private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL
        private const val FNV_PRIME = 0x100000001b3L
        private val EMPTY = AdHostBlocklist(LongArray(0))

        fun empty(): AdHostBlocklist = EMPTY

        fun decode(bytes: ByteArray): AdHostBlocklist {
            require(bytes.size >= HEADER_SIZE) { "Truncated ad host blocklist header" }
            require(readInt(bytes, 0) == FILE_MAGIC) { "Invalid ad host blocklist header" }

            val count = readInt(bytes, 4)
            require(count in 0..MAX_HOST_COUNT) { "Invalid ad host blocklist size: $count" }
            val expectedSize = HEADER_SIZE.toLong() + count.toLong() * LONG_BYTES
            require(bytes.size.toLong() == expectedSize) {
                if (bytes.size.toLong() < expectedSize) {
                    "Truncated ad host blocklist"
                } else {
                    "Unexpected data after ad host blocklist"
                }
            }

            if (count == 0) return EMPTY
            val hashes = LongArray(count)
            var offset = HEADER_SIZE
            for (index in 0 until count) {
                val hash = readLong(bytes, offset)
                require(index == 0 || hashes[index - 1] < hash) {
                    "Ad host blocklist is not strictly sorted"
                }
                hashes[index] = hash
                offset += LONG_BYTES
            }
            return AdHostBlocklist(hashes)
        }

        private fun readInt(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 0xff) shl 24) or
                ((bytes[offset + 1].toInt() and 0xff) shl 16) or
                ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                (bytes[offset + 3].toInt() and 0xff)

        private fun readLong(bytes: ByteArray, offset: Int): Long {
            var value = 0L
            for (index in 0 until LONG_BYTES) {
                value = (value shl 8) or (bytes[offset + index].toLong() and 0xffL)
            }
            return value
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
