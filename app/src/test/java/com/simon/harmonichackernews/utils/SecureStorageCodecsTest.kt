package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.platform.HackerNewsAccountPayloadCodec
import java.util.Base64
import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureStorageCodecsTest {
    @Test
    fun aesGcmEnvelopeRoundTripsAndAuthenticatesMetadata() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val plaintext = "sensitive value".encodeToByteArray()
        val associatedData = "account-v2".encodeToByteArray()

        val encrypted = AesGcmSecretCodec.encrypt(plaintext, key, associatedData)

        assertFalse(encrypted.contains("sensitive value"))
        assertArrayEquals(
            plaintext,
            AesGcmSecretCodec.decrypt(encrypted, key, associatedData),
        )
        assertThrows(Exception::class.java) {
            AesGcmSecretCodec.decrypt(encrypted, key, "different-store".encodeToByteArray())
        }
    }

    @Test
    fun aesGcmEnvelopeRejectsTampering() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val associatedData = "account-v2".encodeToByteArray()
        val envelope = Base64.getDecoder().decode(
            AesGcmSecretCodec.encrypt("secret".encodeToByteArray(), key, associatedData),
        )
        envelope[envelope.lastIndex] = (envelope.last().toInt() xor 1).toByte()
        val tampered = Base64.getEncoder().withoutPadding().encodeToString(envelope)

        assertThrows(Exception::class.java) {
            AesGcmSecretCodec.decrypt(tampered, key, associatedData)
        }
    }

    @Test
    fun accountPayloadRoundTripsUnicodeAndDelimiters() {
        val account = HackerNewsAccount(" häcker:user ", "pāss:word\u0000value")

        assertEquals(
            account,
            HackerNewsAccountPayloadCodec.decode(HackerNewsAccountPayloadCodec.encode(account)),
        )
    }

    @Test
    fun legacyValueSurvivesReplacementWriteFailure() {
        val destination = MemoryMigrationDestination(writeFailure = true)
        val legacy = "existing login".encodeToByteArray()

        val resolved = readWithLegacyMigration(destination) { legacy }

        assertTrue(resolved is MigratingSecretReadResult.Success)
        resolved as MigratingSecretReadResult.Success
        assertArrayEquals(legacy, resolved.value)
        assertFalse(resolved.canDeleteLegacy)
        assertTrue(resolved.shouldRetryMigration)
        assertTrue(destination.legacyMigrationAllowed)
    }

    @Test
    fun completedMigrationNeverReimportsAStaleLegacyValue() {
        val destination = MemoryMigrationDestination(
            initialValue = null,
            migrationComplete = true,
        )
        var legacyRead = false

        val resolved = readWithLegacyMigration(destination) {
            legacyRead = true
            "stale login".encodeToByteArray()
        }

        assertTrue(resolved is MigratingSecretReadResult.Success)
        resolved as MigratingSecretReadResult.Success
        assertEquals(null, resolved.value)
        assertTrue(resolved.canDeleteLegacy)
        assertFalse(resolved.shouldRetryMigration)
        assertFalse(legacyRead)
    }

    @Test
    fun destinationReadFailureIsDistinctFromAnEmptySecret() {
        val destination = MemoryMigrationDestination(readFailure = true)

        val resolved = readWithLegacyMigration(destination) {
            "fallback".encodeToByteArray()
        }

        assertTrue(resolved is MigratingSecretReadResult.Failure)
    }

    private class MemoryMigrationDestination(
        initialValue: ByteArray? = null,
        private var migrationComplete: Boolean = false,
        private val writeFailure: Boolean = false,
        private val readFailure: Boolean = false,
    ) : MigratingSecretDestination {
        private var value = initialValue

        override val legacyMigrationAllowed: Boolean
            get() = value == null && !migrationComplete

        override fun read(): ByteArray? {
            if (readFailure) error("Secure storage is temporarily unavailable")
            return value
        }

        override fun write(value: ByteArray): Boolean {
            if (writeFailure) return false
            this.value = value
            migrationComplete = true
            return true
        }
    }
}
