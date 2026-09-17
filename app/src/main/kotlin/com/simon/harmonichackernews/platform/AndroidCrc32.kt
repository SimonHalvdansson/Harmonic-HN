package com.simon.harmonichackernews.platform

import java.util.zip.CRC32

internal object AndroidCrc32 : Crc32 {
    override fun compute(bytes: ByteArray, start: Int): Int {
        require(start in 0..bytes.size)
        // Each call owns its mutable accumulator; reads and writes may run concurrently.
        return CRC32().run {
            update(bytes, start, bytes.size - start)
            value.toInt()
        }
    }
}
