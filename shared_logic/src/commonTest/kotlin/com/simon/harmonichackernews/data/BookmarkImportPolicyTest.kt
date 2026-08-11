package com.simon.harmonichackernews.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookmarkImportPolicyTest {
    @Test
    fun overwriteUsesValidImportedItemsInNewestFirstOrder() {
        val result = requireNotNull(
            BookmarkImportPolicy.apply(
                content = "1q100-invalid-2q300-3q200",
                current = listOf(TimestampedItem(9, 900)),
                overwrite = true,
            ),
        )

        assertEquals(listOf(2, 3, 1), result.items.map(TimestampedItem::id))
        assertEquals(3, result.importedCount)
    }

    @Test
    fun mergePreservesCurrentOrderAndOnlyAddsNewIds() {
        val result = requireNotNull(
            BookmarkImportPolicy.apply(
                content = "2q500-3q400-3q300-1q200-4q100",
                current = listOf(TimestampedItem(1, 10), TimestampedItem(2, 20)),
                overwrite = false,
            ),
        )

        assertEquals(listOf(1, 2, 3, 4), result.items.map(TimestampedItem::id))
        assertEquals(2, result.importedCount)
    }

    @Test
    fun emptyOrInvalidDocumentsAreRejectedWithoutChangingState() {
        assertNull(
            BookmarkImportPolicy.apply(
                content = "not-a-bookmark",
                current = listOf(TimestampedItem(1, 10)),
                overwrite = false,
            ),
        )
    }
}
