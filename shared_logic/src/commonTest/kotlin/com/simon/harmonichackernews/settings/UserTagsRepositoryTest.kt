package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class UserTagsRepositoryTest {
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
