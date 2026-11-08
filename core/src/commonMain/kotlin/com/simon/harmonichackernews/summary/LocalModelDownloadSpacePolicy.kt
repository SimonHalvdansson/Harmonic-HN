package com.simon.harmonichackernews.summary

/** Peak additional allocation for OS downloads delivered as a separate temporary file. */
object LocalModelDownloadSpacePolicy {
    fun backgroundFileBytes(modelBytes: Long, appendOffset: Long, receivedBytes: Long): Long {
        require(modelBytes > 0 && appendOffset in 0 until modelBytes)
        require(receivedBytes in appendOffset..modelBytes)
        val remaining = modelBytes - receivedBytes
        val installationCopy = if (appendOffset > 0) modelBytes - appendOffset else 0L
        // A server may ignore Range and send a full replacement. Retained OS bytes can be
        // reclaimed on restart, but the legacy partial remains until the replacement is ready.
        val replacement = modelBytes - (receivedBytes - appendOffset)
        return maxOf(withBuffer(remaining, installationCopy), replacement)
    }

    fun withBuffer(requiredBytes: Long, bufferBytes: Long): Long {
        require(requiredBytes >= 0 && bufferBytes >= 0)
        return requiredBytes.coerceAtMost(Long.MAX_VALUE - bufferBytes) + bufferBytes
    }

    fun withSafetyBuffer(requiredBytes: Long): Long =
        withBuffer(requiredBytes, LocalModelLifecycle.DEFAULT_STORAGE_BUFFER_BYTES)
}
