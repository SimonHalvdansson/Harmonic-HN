package com.simon.harmonichackernews.platform

/** IEEE CRC-32, compatible with existing prepared-cache checksums. */
fun interface Crc32 {
    /** Checksums bytes from [start] (in `0..bytes.size`) to the end, returning the 32 result bits. */
    fun compute(bytes: ByteArray, start: Int): Int
}

/** Portable fallback for hosts without a platform checksum adapter. */
object KotlinCrc32 : Crc32 {
    private val table = IntArray(256) { value ->
        var crc = value
        repeat(8) { crc = (crc ushr 1) xor (if (crc and 1 != 0) 0xedb88320.toInt() else 0) }
        crc
    }

    override fun compute(bytes: ByteArray, start: Int): Int {
        require(start in 0..bytes.size)
        var crc = -1
        for (index in start until bytes.size) {
            crc = (crc ushr 8) xor table[(crc xor bytes[index].toInt()) and 255]
        }
        return crc.inv()
    }
}
