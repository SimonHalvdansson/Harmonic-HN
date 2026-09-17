package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentFilterRepositoryTest {
    @Test
    fun filtersAreTrimmedAndUsernamesAreCaseInsensitive() {
        val repository = ContentFilterRepository(
            TestKeyValueStore(
                mapOf(
                    ContentFilterKeys.WORDS to " Kotlin,  Compose ,",
                    ContentFilterKeys.DOMAINS to "example.com, news.test ",
                    ContentFilterKeys.USERS to " Alice,BOB ",
                ),
            ),
        )

        val filters = repository.load()
        assertEquals(listOf("Kotlin", "Compose"), filters.words)
        assertEquals(listOf("example.com", "news.test"), filters.domains)
        assertEquals(setOf("alice", "bob"), filters.users)
        assertTrue(repository.containsUser(" ALICE "))
    }

    @Test
    fun userMutationsPreserveTheExistingPreferenceFormat() {
        val repository = ContentFilterRepository(
            TestKeyValueStore(mapOf(ContentFilterKeys.USERS to "alice")),
        )

        assertTrue(repository.addUser(" Bob "))
        assertEquals(setOf("alice", "bob"), repository.load().users)
        assertTrue(repository.removeUser("ALICE"))
        assertEquals(setOf("bob"), repository.load().users)
    }

    @Test
    fun blankUsernamesAreRejectedWithoutChangingState() {
        val repository = ContentFilterRepository(TestKeyValueStore())

        assertFalse(repository.addUser("  "))
        assertFalse(repository.removeUser(null))
        assertFalse(repository.containsUser(""))
        assertEquals(emptySet(), repository.load().users)
    }

    @Test
    fun typedEditorsNormalizeListsAndProfileTogglePresentation() {
        val repository = ContentFilterRepository(TestKeyValueStore())

        repository.setItems(ContentFilterType.STORY_TITLE, listOf(" Kotlin ", "Kotlin", ""))
        repository.setItems(ContentFilterType.USER, listOf(" Alice ", "BOB"))

        assertEquals(listOf("Kotlin"), repository.items(ContentFilterType.STORY_TITLE))
        assertEquals(setOf("alice", "bob"), repository.items(ContentFilterType.USER).toSet())

        val unblocked = repository.toggleUser("ALICE")
        assertEquals(false, unblocked?.blocked)
        assertEquals(false, unblocked?.dismissProfile)
        assertEquals("Unblocked alice", unblocked?.message)

        val blocked = repository.toggleUser("Charlie")
        assertEquals(true, blocked?.blocked)
        assertEquals(true, blocked?.dismissProfile)
        assertTrue(repository.containsUser("charlie"))
    }
}
