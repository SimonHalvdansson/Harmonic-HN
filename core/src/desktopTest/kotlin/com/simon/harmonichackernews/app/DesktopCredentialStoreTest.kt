package com.simon.harmonichackernews.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopCredentialStoreTest {
    @Test
    fun macNativeAdapterPreservesSecretBytesAndClearsTemporaryArrays() {
        var written: ByteArray? = null
        var read: ByteArray? = null
        val api = object : MacKeychainApi {
            override fun read(service: String, account: String): ByteArray {
                assertEquals("test-service", service)
                assertEquals("test-id", account)
                return "Unicode café\n".encodeToByteArray().also { read = it }
            }
            override fun write(service: String, account: String, value: ByteArray): Boolean {
                assertEquals("test-service", service)
                assertEquals("test-id", account)
                assertEquals("Unicode café\n", value.decodeToString())
                written = value
                return true
            }
            override fun remove(service: String, account: String) = true
        }
        val store = MacOsKeychainCredentialStore("test-service", api)
        assertTrue(store.write("test-id", "Unicode café\n"))
        assertTrue(written!!.all { it == 0.toByte() })
        assertEquals("Unicode café\n", store.read("test-id"))
        assertTrue(read!!.all { it == 0.toByte() })
    }

    @Test
    fun macNativeKeychainRoundTripsWithAnIsolatedServiceIdentity() {
        if (!System.getProperty("os.name").contains("mac", ignoreCase = true)) return
        val store = MacOsKeychainCredentialStore("com.simon.harmonichackernews.desktop.test.${System.nanoTime()}")
        val id = "native-round-trip"
        try {
            assertNull(store.read(id))
            assertTrue(store.write(id, "Unicode café\n"))
            assertEquals("Unicode café\n", store.read(id))
            assertTrue(store.write(id, "Updated value"))
            assertEquals("Updated value", store.read(id))
            assertTrue(store.write(id, ""))
            assertEquals("", store.read(id))
            assertTrue(store.remove(id))
            assertNull(store.read(id))
            assertTrue(store.remove(id))
        } finally { store.remove(id) }
    }

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
