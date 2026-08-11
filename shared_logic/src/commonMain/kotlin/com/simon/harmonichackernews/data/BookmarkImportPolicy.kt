package com.simon.harmonichackernews.data

data class BookmarkImportResult(
    val items: List<TimestampedItem>,
    val importedCount: Int,
)

/** Platform-neutral overwrite/merge policy for bookmark document imports. */
object BookmarkImportPolicy {
    fun apply(
        content: String,
        current: List<TimestampedItem>,
        overwrite: Boolean,
    ): BookmarkImportResult? {
        val imported = SavedItemCodec.decode(content, sortedByCreated = true)
        if (imported.isEmpty()) return null
        if (overwrite) return BookmarkImportResult(imported, imported.size)

        val currentIds = current.mapTo(mutableSetOf(), TimestampedItem::id)
        val additions = imported.filter { currentIds.add(it.id) }
        return BookmarkImportResult(
            items = current.toList() + additions,
            importedCount = additions.size,
        )
    }
}
