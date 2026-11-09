package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlin.test.*

class AccountPresentationTest {
    private val now = 1_800_000_000_000L

    @Test fun lastCheckedUsesAgoExceptForJustNowAndToleratesAFreshCheck() {
        assertEquals("Not checked yet", accountLastCheckedLabel(0, now))
        assertEquals("Last checked just now", accountLastCheckedLabel(now, now))
        assertEquals("Last checked just now", accountLastCheckedLabel(now + 500, now))
        assertEquals("Last checked just now", accountLastCheckedLabel(now - 59_000, now))
        assertEquals("Last checked 1m ago", accountLastCheckedLabel(now - 60_000, now))
        assertEquals("Last checked 2m ago", accountLastCheckedLabel(now - 120_000, now))
    }

    @Test fun debugIdentityPersistsCaseAndTrimmingAndCanReturnToRealAccount() {
        val store = TestKeyValueStore()
        val identity = DebugAccountIdentity(store, enabled = true)
        identity.setUsername(" Alice ")
        val first = identity.state.value
        assertEquals("Alice", first.username)
        assertEquals("Alice", DebugAccountIdentity(store, true).state.value.username)
        identity.setUsername("Bob")
        identity.setUsername("Alice")
        assertNotSame(first, identity.state.value)
        assertTrue(identity.state.value.revision > first.revision)
        identity.setUsername(null)
        assertNull(DebugAccountIdentity(store, true).state.value.username)
    }

    @Test fun releaseHostIgnoresPersistedDebugIdentityAndCannotEnableIt() {
        val store = TestKeyValueStore()
        DebugAccountIdentity(store, true).setUsername("Alice")
        val release = DebugAccountIdentity(store, false)
        assertNull(release.state.value.username)
        release.setUsername("Bob")
        assertNull(release.state.value.username)
        assertEquals("Alice", DebugAccountIdentity(store, true).state.value.username)
    }
}
