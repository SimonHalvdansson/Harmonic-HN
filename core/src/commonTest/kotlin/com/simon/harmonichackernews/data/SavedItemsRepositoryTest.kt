package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.settings.TestKeyValueStore
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    fun membershipCachePreservesDuplicateRemovalAndItemOrder() {
        val repository = SavedItemsRepository(
            TestKeyValueStore(
                mapOf(SavedItemKeys.BOOKMARKS to "3q30-1q10-3q20-2q40"),
            ),
        )

        assertTrue(repository.contains(SavedItemSource.BOOKMARKS, 3))
        assertTrue(repository.setMembership(SavedItemSource.BOOKMARKS, 3, false, 50))
        assertTrue(repository.contains(SavedItemSource.BOOKMARKS, 3))
        assertEquals(
            listOf(
                TimestampedItem(1, 10),
                TimestampedItem(3, 20),
                TimestampedItem(2, 40),
            ),
            repository.loadItems(SavedItemSource.BOOKMARKS),
        )

        assertTrue(repository.setMembership(SavedItemSource.BOOKMARKS, 3, false, 60))
        assertFalse(repository.contains(SavedItemSource.BOOKMARKS, 3))
        assertEquals(
            listOf(TimestampedItem(1, 10), TimestampedItem(2, 40)),
            repository.loadItems(SavedItemSource.BOOKMARKS),
        )
    }

    @Test
    fun directSavesPreserveListSemanticsAndSnapshotsRefreshCachedMembership() {
        val repository = SavedItemsRepository(
            TestKeyValueStore(mapOf(SavedItemKeys.FAVORITES to "1q10")),
        )

        assertTrue(repository.contains(SavedItemSource.FAVORITES, 1))
        val saved = listOf(
            TimestampedItem(4, 40),
            TimestampedItem(2, 20),
            TimestampedItem(4, 10),
        )
        repository.saveItems(SavedItemSource.FAVORITES, saved)

        assertFalse(repository.contains(SavedItemSource.FAVORITES, 1))
        assertTrue(repository.contains(SavedItemSource.FAVORITES, 4))
        assertEquals(saved, repository.loadItems(SavedItemSource.FAVORITES))

        repository.saveSnapshot(
            source = SavedItemSource.FAVORITES,
            snapshot = SavedItemSnapshot(itemIds = listOf(8, 6, 8), commentIds = setOf(6)),
            createdAtMillis = 100,
        )

        assertFalse(repository.contains(SavedItemSource.FAVORITES, 4))
        assertTrue(repository.contains(SavedItemSource.FAVORITES, 8))
        assertTrue(repository.contains(SavedItemSource.FAVORITES, 6))
        assertEquals(
            listOf(TimestampedItem(8, 100), TimestampedItem(6, 99)),
            repository.loadItems(SavedItemSource.FAVORITES),
        )
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

    @Test
    fun concurrentAtomicMembershipChangesDoNotLoseItems() = runTest {
        val repository = SavedItemsRepository(TestKeyValueStore())

        coroutineScope {
            repeat(100) { id ->
                launch(Dispatchers.Default) {
                    repository.setMembershipAtomic(
                        SavedItemSource.BOOKMARKS,
                        id = id + 1,
                        present = true,
                        createdAtMillis = id.toLong(),
                    )
                }
            }
        }

        assertEquals((1..100).toSet(), repository.loadItems(SavedItemSource.BOOKMARKS).map { it.id }.toSet())
    }

    @Test
    fun atomicSnapshotUsesOneKeyValueBatch() = runTest {
        val store = RecordingUpdateStore()
        val repository = SavedItemsRepository(store)

        repository.saveSnapshotAtomic(
            SavedItemSource.FAVORITES,
            SavedItemSnapshot(listOf(9, 7), setOf(7)),
            200,
        )

        assertEquals(1, store.updateCount)
    }

    private class RecordingUpdateStore : KeyValueStore {
        private val delegate = TestKeyValueStore()
        var updateCount = 0
            private set

        override fun clear() = delegate.clear()
        override fun contains(key: String) = delegate.contains(key)
        override fun remove(key: String) = delegate.remove(key)
        override fun getString(key: String, default: String?) = delegate.getString(key, default)
        override fun putString(key: String, value: String?) = delegate.putString(key, value)
        override fun getBoolean(key: String, default: Boolean) = delegate.getBoolean(key, default)
        override fun putBoolean(key: String, value: Boolean) = delegate.putBoolean(key, value)
        override fun getInt(key: String, default: Int) = delegate.getInt(key, default)
        override fun putInt(key: String, value: Int) = delegate.putInt(key, value)
        override fun getFloat(key: String, default: Float) = delegate.getFloat(key, default)
        override fun putFloat(key: String, value: Float) = delegate.putFloat(key, value)
        override fun getStringSet(key: String) = delegate.getStringSet(key)
        override fun putStringSet(key: String, value: Set<String>?) = delegate.putStringSet(key, value)

        override fun update(block: KeyValueStore.Editor.() -> Unit) {
            updateCount++
            delegate.update(block)
        }
    }
}
