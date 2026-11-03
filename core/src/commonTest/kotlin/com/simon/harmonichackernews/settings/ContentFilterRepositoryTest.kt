package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentFilterRepositoryTest {
    @Test
    fun distinctCaseIdentitiesCoexistAndExternalWritesStayVisible() {
        val store = TestKeyValueStore()
        val first = ContentFilterRepository(store)
        val second = ContentFilterRepository(store)
        assertFalse(first.containsUser("Alice"))
        second.addUser(" Alice ")
        assertTrue(first.containsUser("Alice"))
        assertFalse(first.containsUser("alice"))
        second.addUser("alice")
        assertEquals(setOf("Alice", "alice"), first.load().users)
        second.removeUser("Alice")
        assertFalse(first.containsUser("Alice"))
        assertTrue(first.containsUser("alice"))
        store.clear()
        assertFalse(first.containsUser("alice"))
    }

    @Test
    fun exactUserLookupMatchesLoadedFiltersWithoutUnicodeCaseFolding() {
        val store = TestKeyValueStore()
        val repository = ContentFilterRepository(store)
        val usernames = listOf("Alice", "alice", " BOB ", "İ", "i", "i\u0307", "Σ", "ς", "missing", "", null)
        for (serialized in listOf(null, "", ", ,", " Alice,BOB,alice, İ,Σ,ς,, ")) {
            store.putString(ContentFilterKeys.EXACT_USERS, serialized)
            val users = repository.load().users
            for (username in usernames) {
                assertEquals(username?.trim() in users, repository.containsUser(username))
            }
        }
    }

    @Test
    fun releasedUsernamesUseStoredSpellingWithoutReviewAndDoNotReviveAfterRemoval() {
        val original = " alice,BOB,alice "
        val store = TestKeyValueStore(mapOf(ContentFilterKeys.USERS to original,
            ContentFilterKeys.EXACT_USERS to "Alice"))
        val repository = ContentFilterRepository(store)
        assertEquals(setOf("Alice", "alice", "BOB"), repository.load().users)
        assertEquals(emptyList(), repository.legacyUsers())
        assertTrue(repository.containsUser("alice"))
        assertEquals(original, store.getString(ContentFilterKeys.USERS))
        repository.removeUser("alice")
        assertEquals(setOf("Alice", "BOB"), ContentFilterRepository(store).load().users)
    }

    @Test
    fun previouslyDiscardedLegacyBlocksStayRemoved() {
        val store = TestKeyValueStore(mapOf(ContentFilterKeys.USERS to "alice"))
        store.putStringSet(ContentFilterKeys.REVIEWED_LEGACY_USERS, setOf("alice"))
        assertEquals(emptySet(), ContentFilterRepository(store).load().users)
    }

    @Test
    fun editorsPreserveExactIdentityAndArchiveWhileTitleAndDomainListsStayTrimmed() {
        val store = TestKeyValueStore(mapOf(ContentFilterKeys.USERS to "olduser"))
        val repository = ContentFilterRepository(store)
        repository.setItems(ContentFilterType.STORY_TITLE, listOf(" Kotlin ", "Kotlin", ""))
        repository.setItems(ContentFilterType.DOMAIN, listOf(" example.com ", "news.test"))
        repository.setItems(ContentFilterType.USER, listOf(" Alice ", "alice", "Alice", "BOB"))
        assertEquals(listOf("Kotlin"), repository.items(ContentFilterType.STORY_TITLE))
        assertEquals(listOf("example.com", "news.test"), repository.items(ContentFilterType.DOMAIN))
        assertEquals(listOf("Alice", "alice", "BOB"), repository.items(ContentFilterType.USER))
        assertEquals("Alice,alice,BOB", store.getString(ContentFilterKeys.EXACT_USERS))
        assertEquals("olduser", store.getString(ContentFilterKeys.USERS))
    }

    @Test
    fun profileToggleAffectsOnlyItsExactUsernameAndKeepsDisplayCapitalization() {
        val repository = ContentFilterRepository(TestKeyValueStore())
        repository.addUser("alice")
        val blocked = repository.toggleUser(" Alice ")
        assertEquals(true, blocked?.blocked)
        assertEquals(true, blocked?.dismissProfile)
        assertEquals("You will no longer see posts or comments from Alice", blocked?.message)
        assertEquals(setOf("alice", "Alice"), repository.load().users)
        val unblocked = repository.toggleUser("Alice")
        assertEquals(false, unblocked?.blocked)
        assertEquals(false, unblocked?.dismissProfile)
        assertEquals("Unblocked Alice", unblocked?.message)
        assertEquals(setOf("alice"), repository.load().users)
    }

    @Test
    fun invalidReviewNeverConsumesOrRebindsLegacyData() {
        val repository = ContentFilterRepository(TestKeyValueStore(mapOf(ContentFilterKeys.USERS to "alice")))
        assertFalse(repository.resolveLegacyUser("alice", " "))
        assertFalse(repository.resolveLegacyUser("alice", "Alice,Bob"))
        assertFalse(repository.resolveLegacyUser("missing", "Alice"))
        assertFalse(repository.discardLegacyUser("missing"))
        assertFalse(repository.addUser(" "))
        assertFalse(repository.removeUser(null))
        assertEquals(emptyList(), repository.legacyUsers())
        assertEquals(setOf("alice"), repository.load().users)
    }
}
