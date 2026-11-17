package com.simon.harmonichackernews.data

/** Bounds checks for prepared_comments.fbs. Unknown appended fields remain forward-compatible. */
internal class PreparedCommentFlatBufferVerifier(private val bytes: ByteArray, private val payloadOffset: Int = 0) {
    private val payloadSize = bytes.size - payloadOffset
    // A valid writer stores each referenced string/table in the payload. Bound repeated references
    // too, so a tiny forged vector cannot expand one large string into unbounded allocations.
    private var remainingReferencedBytes = payloadSize.toLong() * 4

    fun verify() {
        range(0, 8)
        require(byte(4) == 'H'.code.toByte() && byte(5) == 'N'.code.toByte() &&
            byte(6) == 'P'.code.toByte() && byte(7) == 'C'.code.toByte())
        val root = table(target(0))
        root.scalar(0)
        root.scalar(1)
        root.string(2)
        val story = table(root.reference(3))
        for (field in STORY_SCALARS) story.scalar(field)
        for (field in STORY_STRINGS) story.string(field)
        val comments = vector(root.reference(4))
        for (index in 0 until comments.count) {
            val comment = table(target(comments.start + index * 4))
            for (field in COMMENT_SCALARS) comment.scalar(field)
            comment.string(2)
            comment.string(4)
            comment.string(5, required = false)
        }
        root.string(5)
        vector(root.reference(6))
    }

    private fun table(position: Int): Table {
        range(position, 4)
        require(position >= 8 && position % 4 == 0)
        // Vtable deduplication can produce a negative distance: use signed, wide arithmetic.
        val vtable = position.toLong() - int(position)
        require(vtable in 8L..(payloadSize - 4).toLong())
        val start = vtable.toInt()
        val length = ushort(start)
        val objectSize = ushort(start + 2)
        // The pinned KMP accessors interpret vtable lengths/field offsets as signed Shorts.
        require(start % 2 == 0 && length in 4..Short.MAX_VALUE && length % 2 == 0 && objectSize >= 4)
        range(start, length)
        range(position, objectSize)
        spend(objectSize)
        return Table(position, start, length, objectSize)
    }

    private inner class Table(
        val position: Int,
        val vtable: Int,
        val vtableSize: Int,
        val objectSize: Int,
    ) {
        private fun field(id: Int): Int {
            val entry = 4 + id * 2
            if (entry >= vtableSize) return 0
            val offset = ushort(vtable + entry)
            if (offset == 0) return 0
            require(offset in 4..Short.MAX_VALUE && offset <= objectSize - 4)
            val field = position + offset
            require(field % 4 == 0)
            return field
        }

        fun scalar(id: Int) { field(id) }

        fun reference(id: Int): Int = field(id).also { require(it != 0) }.let(::target)

        fun string(id: Int, required: Boolean = true): Int {
            val field = field(id)
            if (field == 0) {
                require(!required)
                return 0
            }
            val position = target(field)
            val length = int(position)
            require(length >= 0 && length < payloadSize - position - 4)
            require(byte(position + 4 + length) == 0.toByte())
            spend(length)
            return length
        }
    }

    private data class Vector(val start: Int, val count: Int)

    private fun vector(position: Int): Vector {
        val count = int(position)
        require(count >= 0 && count <= (payloadSize - position - 4) / 4)
        spend(count * 4)
        return Vector(position + 4, count)
    }

    private fun target(offset: Int): Int {
        val relative = int(offset)
        require(relative > 0 && relative <= payloadSize - offset - 4)
        val position = offset + relative
        require(position % 4 == 0)
        return position
    }

    private fun int(offset: Int): Int {
        range(offset, 4)
        return (byte(offset).toInt() and 255) or
            ((byte(offset + 1).toInt() and 255) shl 8) or
            ((byte(offset + 2).toInt() and 255) shl 16) or
            (byte(offset + 3).toInt() shl 24)
    }

    private fun ushort(offset: Int): Int =
        (byte(offset).toInt() and 255) or ((byte(offset + 1).toInt() and 255) shl 8)

    private fun byte(offset: Int): Byte = bytes[payloadOffset + offset]

    private fun range(offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= payloadSize - length)
    }

    private fun spend(size: Int) {
        remainingReferencedBytes -= size
        require(remainingReferencedBytes >= 0)
    }

    private companion object {
        val STORY_SCALARS = intArrayOf(0, 2, 3, 6, 7)
        val STORY_STRINGS = intArrayOf(1, 4, 5, 8, 9, 10)
        val COMMENT_SCALARS = intArrayOf(0, 1, 3, 6, 7, 8)
    }
}
