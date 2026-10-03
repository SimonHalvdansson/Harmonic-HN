package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class UserTagsRepositoryTest {
    @Test
    fun repeatedRepositoryLookupsObserveExternalEditsAndMalformedData() {
        val store = TestKeyValueStore()
        val repository = UserTagsRepository(store)
        val otherRepository = UserTagsRepository(store)
        val usernames = listOf(" Alice ", "BOB", "İ", "i\u0307", "Σ", "ς", "missing", "", null)
        for (serialized in listOf(
            """{" Alice ":"first","ALICE":"last","Bob":42,"İ":"dotted","Σ":"sigma","ς":"final"}""",
            """{"ALICE":null,"Bob":{"tag":true}}""",
            "invalid", "[]", "", null,
        )) {
            store.putString(UserTagKeys.TAGS, serialized)
            repeat(2) {
                for (username in usernames) {
                    assertEquals(UserTagCodec.tagFor(serialized, username), repository.tagFor(username))
                }
            }
        }
        otherRepository.setTag("Alice", "new")
        assertEquals("new", repository.tagFor("alice"))
        repository.setTag("Alice", "updated")
        assertEquals("updated", repository.tagFor("alice"))
        otherRepository.setTag("Alice", "")
        assertEquals("", repository.tagFor("alice"))
    }

    @Test
    fun lookupPreservesLastNormalizedMatchAndJsonValueConversions() {
        val serialized = """{" Alice ":"first","ALICE":"last","Bob":42,"null":null,"object":{"a":1},"array":[1,2],"İ":"dotted","Σ":"sigma","ς":"final"}"""
        val decoded = UserTagCodec.decode(serialized, normalizeUsernames = true)
        for (username in listOf(" alice ", "BOB", "null", "object", "array", "İ", "i", "i\u0307", "Σ", "ς", "missing")) {
            assertEquals(decoded[username.trim().lowercase()].orEmpty(), UserTagCodec.tagFor(serialized, username))
        }
        assertEquals("last", UserTagCodec.tagFor(serialized, "Alice"))
        assertEquals("", UserTagCodec.tagFor("""{"alice":"first","ALICE":null}""", "Alice"))
    }

    @Test
    fun invalidOrEmptyTagDataAndUsernamesReturnEmpty() {
        for (serialized in listOf(null, "", "not json", "[]", """{"alice":"tag",""")) {
            assertEquals("", UserTagCodec.tagFor(serialized, "alice"))
        }
        for (username in listOf(null, "", " \t\n")) {
            assertEquals("", UserTagCodec.tagFor("""{"":"empty","alice":"tag"}""", username))
        }
    }

    @Test
    fun storesOriginalUsernameButLooksUpCaseInsensitively() {
        val repository = UserTagsRepository(TestKeyValueStore())

        repository.setTag("Alice", "maintainer")

        assertEquals("maintainer", repository.tagFor("alice"))
        assertEquals(mapOf("Alice" to "maintainer"), repository.tags(normalizeUsernames = false))
        assertEquals(mapOf("alice" to "maintainer"), repository.tags())
    }

    @Test
    fun blankTagRemovesExistingEntry() {
        val repository = UserTagsRepository(TestKeyValueStore())
        repository.setTag("Alice", "maintainer")

        repository.setTag("ALICE", "  ")

        assertEquals(emptyMap(), repository.tags())
    }
}
