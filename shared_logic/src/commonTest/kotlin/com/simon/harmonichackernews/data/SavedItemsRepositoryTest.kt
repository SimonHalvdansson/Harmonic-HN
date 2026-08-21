package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SavedItemsRepositoryTest {
    @Test
    fun codecPreservesNumericLimitsAndRejectsOverflowOrDuplicateSeparators() {
        assertEquals(
            listOf(TimestampedItem(Int.MAX_VALUE, Long.MAX_VALUE)),
            SavedItemCodec.decode("${Int.MAX_VALUE}q${Long.MAX_VALUE}"),
        )
        assertEquals(
            emptyList(),
            SavedItemCodec.decode("2147483648q1-1q9223372036854775808-1q2q3"),
        )
    }

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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun atomicMembershipMutationPublishesPersistedState() = runTest {
        val repository = SavedItemsRepository(TestKeyValueStore())
        val change = async(start = CoroutineStart.UNDISPATCHED) { repository.changes.first() }

        assertTrue(
            repository.setMembershipAtomic(
                SavedItemSource.BOOKMARKS,
                id = 42,
                present = true,
                createdAtMillis = 100,
            ),
        )

        assertEquals(
            SavedItemsChange(SavedItemSource.BOOKMARKS, listOf(42), emptySet()),
            change.await(),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun atomicSnapshotPublishesOnlyTheCompleteSnapshot() = runTest {
        val repository = SavedItemsRepository(TestKeyValueStore())
        val change = async(start = CoroutineStart.UNDISPATCHED) { repository.changes.first() }
        val snapshot = SavedItemSnapshot(listOf(9, 7), setOf(7))

        repository.saveSnapshotAtomic(SavedItemSource.FAVORITES, snapshot, 200)

        assertEquals(
            SavedItemsChange(SavedItemSource.FAVORITES, listOf(9, 7), setOf(7)),
            change.await(),
        )
    }
}
