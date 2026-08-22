package com.simon.harmonichackernews.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopCredentialStoreTest {
    @Test
    fun unsupportedPlatformsFailClosedWithoutAFileStore() {
        val store = desktopCredentialStore(osName = "unsupported")

        assertNull(store.read("secret"))
        assertFalse(store.write("secret", "value"))
        assertTrue(store.remove("secret"))
    }

    @Test
    fun windowsCredentialManagerRoundTripsWithoutTheFileStore() {
        if (!System.getProperty("os.name").contains("win", ignoreCase = true)) return
        val service = "com.simon.harmonichackernews.desktop.test.${System.nanoTime()}"
        val store = WindowsCredentialStore(service)
        val id = "round-trip"
        val value = "temporary-test-value-${System.nanoTime()}"

        try {
            assertNull(store.read(id))
            assertTrue(store.write(id, value))
            assertEquals(value, store.read(id))
            assertTrue(store.remove(id))
            assertNull(store.read(id))
        } finally {
            store.remove(id)
        }
    }
}
