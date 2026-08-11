package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SavedItemsRepositoryTest {
    @Test
    fun legacyEncodedValuesRemainReadableAndMalformedEntriesAreIgnored() {
        val store = TestKeyValueStore(
            mapOf(SavedItemKeys.BOOKMARKS to "10q100-broken-20q200-30qbad"),
        )
        val repository = SavedItemsRepository(store)

        assertEquals(
            listOf(TimestampedItem(10, 100), TimestampedItem(20, 200)),
            repository.loadItems(SavedItemSource.BOOKMARKS),
        )
    }

    @Test
    fun membershipMutationsAreIdempotentAndPreserveExistingDates() {
        val repository = SavedItemsRepository(TestKeyValueStore())

        assertTrue(repository.setMembership(SavedItemSource.FAVORITES, 7, true, 100))
        assertFalse(repository.setMembership(SavedItemSource.FAVORITES, 7, true, 999))
        assertEquals(
            listOf(TimestampedItem(7, 100)),
            repository.loadItems(SavedItemSource.FAVORITES),
        )
        assertTrue(repository.setMembership(SavedItemSource.FAVORITES, 7, false, 999))
        assertFalse(repository.setMembership(SavedItemSource.FAVORITES, 7, false, 999))
    }

    @Test
    fun snapshotsNormalizeDuplicatesAndRestrictCommentIdsToSavedItems() {
        val snapshot = SavedItemSnapshots.normalize(
            itemIds = listOf(2, 5, 2, 3),
            commentIds = listOf(2, 4, 5),
        )

        assertEquals(listOf(5, 3, 2), snapshot.itemIds)
        assertEquals(setOf(2, 5), snapshot.commentIds)
    }

    @Test
    fun bookmarksRejectASeparateCommentMembershipStore() {
        val repository = SavedItemsRepository(TestKeyValueStore())

        assertFailsWith<IllegalArgumentException> {
            repository.saveCommentIds(SavedItemSource.BOOKMARKS, setOf(1))
        }
    }
}
