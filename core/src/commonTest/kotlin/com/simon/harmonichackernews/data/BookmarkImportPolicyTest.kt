package com.simon.harmonichackernews.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookmarkImportPolicyTest {
    @Test
    fun malformedSegmentsCannotCauseRepeatedScansOfTheRemainingDocument() {
        val input = "bad-".repeat(131_072) + "42q123"
        val result = BookmarkImportPolicy.apply(input, emptyList(), overwrite = true)
        assertNull(result)
        assertEquals(listOf(TimestampedItem(42, 123)), SavedItemCodec.decode(input))
    }

    @Test
    fun overwriteDeduplicatesIdsAndKeepsTheirNewestTimestamp() {
        val result = requireNotNull(
            BookmarkImportPolicy.apply(
                content = "1q100-2q300-1q400-3q200-2q50-1q400",
                current = listOf(TimestampedItem(9, 900)),
                overwrite = true,
            ),
        )

        assertEquals(
            listOf(TimestampedItem(1, 400), TimestampedItem(2, 300), TimestampedItem(3, 200)),
            result.items,
        )
        assertEquals(3, result.importedCount)
    }

    @Test
    fun mergeRecoversExistingDuplicatesWithoutReplacingExistingBookmarksWithImportedDates() {
        val result = requireNotNull(
            BookmarkImportPolicy.apply(
                content = "1q900-3q100-3q200",
                current = listOf(TimestampedItem(2, 20), TimestampedItem(1, 10), TimestampedItem(2, 30)),
                overwrite = false,
            ),
        )

        assertEquals(
            listOf(TimestampedItem(2, 30), TimestampedItem(1, 10), TimestampedItem(3, 200)),
            result.items,
        )
        assertEquals(1, result.importedCount)
    }

    @Test
    fun overwriteUsesValidImportedItemsInNewestFirstOrder() {
        val result = requireNotNull(
            BookmarkImportPolicy.apply(
                content = "1q100-2q300-3q200",
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
        val current = listOf(TimestampedItem(1, 10))
        for (overwrite in listOf(false, true)) {
            for (content in listOf("", "not-a-bookmark", "0q0", "-1q123", "1q-123",
                "1q100-invalid-2q300", "1q100-0q0", "1q100--2q300", "1q100-",
                "2147483648q1", "1q9223372036854775808", "1q2q3")) {
                assertNull(BookmarkImportPolicy.apply(content, current, overwrite), content)
                assertEquals(listOf(TimestampedItem(1, 10)), current)
            }
        }
    }

    @Test
    fun exportedNumericLimitsAndZeroTimestampRemainImportable() {
        val items = listOf(TimestampedItem(Int.MAX_VALUE, Long.MAX_VALUE), TimestampedItem(1, 0))
        assertEquals(items, BookmarkImportPolicy.apply(SavedItemCodec.encode(items), emptyList(), true)?.items)
    }
}
