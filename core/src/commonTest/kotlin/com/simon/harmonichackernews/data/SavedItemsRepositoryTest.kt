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
    fun remoteMembershipIsAccountScopedWhileBookmarksRemainLocal() = runTest {
        val store = TestKeyValueStore(mapOf(SavedItemKeys.FAVORITES to "999q1"))
        var account: String? = "alice"
        val repository = SavedItemsRepository(store).also { it.bindAccountScope { account } }
        assertFalse(repository.contains(SavedItemSource.FAVORITES, 999))
        repository.setMembership(SavedItemSource.BOOKMARKS, 7, true, 10)
        repository.saveSnapshotAtomic(SavedItemSource.FAVORITES, SavedItemSnapshot(listOf(1), setOf(1)), 10)
        repository.saveSnapshotAtomic(SavedItemSource.UPVOTED, SavedItemSnapshot(listOf(2), setOf(2)), 10)

        account = "bob"
        assertEquals(emptyList(), repository.loadItems(SavedItemSource.FAVORITES))
        assertEquals(emptySet(), repository.loadCommentIds(SavedItemSource.UPVOTED))
        assertTrue(repository.contains(SavedItemSource.BOOKMARKS, 7))
        repository.saveSnapshotAtomic(SavedItemSource.FAVORITES, SavedItemSnapshot(listOf(3), setOf(3)), 20)

        account = null
        assertEquals(emptyList(), repository.loadItems(SavedItemSource.FAVORITES))
        account = "alice"
        assertEquals(SavedItemSnapshot(listOf(1), setOf(1)), repository.loadSnapshot(SavedItemSource.FAVORITES))
        assertEquals(SavedItemSnapshot(listOf(2), setOf(2)), repository.loadSnapshot(SavedItemSource.UPVOTED))
        val reopened = SavedItemsRepository(store).also { it.bindAccountScope { account } }
        assertEquals(repository.loadSnapshot(SavedItemSource.FAVORITES), reopened.loadSnapshot(SavedItemSource.FAVORITES))
        account = "bob"
        assertEquals(SavedItemSnapshot(listOf(3), setOf(3)), reopened.loadSnapshot(SavedItemSource.FAVORITES))
    }

    @Test
    fun oldSnapshotCannotOverwriteAnAccountAfterSwitchingAwayAndBack() = runTest {
        var account = "alice"
        val repository = SavedItemsRepository(TestKeyValueStore()).also { it.bindAccountScope { account } }
        val initialRevision = repository.currentAccountRevision
        account = "bob"
        repository.refreshAccountScope()
        account = "alice"
        repository.saveSnapshotAtomic(SavedItemSource.UPVOTED, SavedItemSnapshot(listOf(9), emptySet()), 10)
        assertFalse(repository.saveSnapshotIfAccountCurrent(
            SavedItemSource.UPVOTED, SavedItemSnapshot(listOf(1), emptySet()), 20, initialRevision,
        ))
        assertEquals(listOf(9), repository.loadSnapshot(SavedItemSource.UPVOTED).itemIds)
    }

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
    fun legacyBookmarkDuplicatesLoadOnceWithNewestDatesAndCanBeRemovedInOneAction() {
        val repository = SavedItemsRepository(
            TestKeyValueStore(
                mapOf(SavedItemKeys.BOOKMARKS to "3q20-1q10-3q30-2q40"),
            ),
        )

        assertTrue(repository.contains(SavedItemSource.BOOKMARKS, 3))
        assertEquals(
            listOf(
                TimestampedItem(3, 30),
                TimestampedItem(1, 10),
                TimestampedItem(2, 40),
            ),
            repository.loadItems(SavedItemSource.BOOKMARKS),
        )

        assertEquals(
            listOf(TimestampedItem(2, 40), TimestampedItem(3, 30), TimestampedItem(1, 10)),
            repository.loadItems(SavedItemSource.BOOKMARKS, sortedByCreated = true),
        )

        assertTrue(repository.setMembership(SavedItemSource.BOOKMARKS, 3, false, 50))
        assertFalse(repository.contains(SavedItemSource.BOOKMARKS, 3))
        assertFalse(repository.setMembership(SavedItemSource.BOOKMARKS, 3, false, 60))
        assertEquals(
            listOf(TimestampedItem(1, 10), TimestampedItem(2, 40)),
            repository.loadItems(SavedItemSource.BOOKMARKS),
        )
    }

    @Test
    fun bookmarkSavesPersistAndPublishUniqueIdsWithNewestDates() = runTest {
        val store = TestKeyValueStore()
        val repository = SavedItemsRepository(store)
        val change = async(start = CoroutineStart.UNDISPATCHED) { repository.changes.first() }

        repository.saveItemsAtomic(
            SavedItemSource.BOOKMARKS,
            listOf(TimestampedItem(3, 10), TimestampedItem(2, 20), TimestampedItem(3, 30)),
        )

        val expected = listOf(TimestampedItem(3, 30), TimestampedItem(2, 20))
        assertEquals(expected, repository.loadItems(SavedItemSource.BOOKMARKS))
        assertEquals(expected, repository.loadItems(SavedItemSource.BOOKMARKS, sortedByCreated = true))
        assertEquals(expected, SavedItemsRepository(store).loadItems(SavedItemSource.BOOKMARKS))
        assertEquals("3q30-2q20", store.getString(SavedItemKeys.BOOKMARKS))
        assertEquals(SavedItemsChange(SavedItemSource.BOOKMARKS, listOf(3, 2), emptySet()), change.await())
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
