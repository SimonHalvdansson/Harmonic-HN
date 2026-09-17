package com.simon.harmonichackernews.utils

import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned AES-GCM envelope shared by the direct Android Keystore stores. */
internal object AesGcmSecretCodec {
    private const val VERSION = 1
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
    private const val IV_BYTES = 12

    fun encrypt(
        plaintext: ByteArray,
        key: SecretKey,
        associatedData: ByteArray,
    ): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        val envelope = ByteBuffer.allocate(2 + iv.size + ciphertext.size)
            .put(VERSION.toByte())
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
        return Base64.getEncoder().withoutPadding().encodeToString(envelope)
    }

    fun decrypt(
        encodedEnvelope: String,
        key: SecretKey,
        associatedData: ByteArray,
    ): ByteArray {
        val envelope = Base64.getDecoder().decode(encodedEnvelope)
        require(envelope.size >= 2 + IV_BYTES + GCM_TAG_BYTES) {
            "Encrypted secret envelope is too short"
        }
        val buffer = ByteBuffer.wrap(envelope)
        require(buffer.get().toInt() and 0xff == VERSION) {
            "Unsupported encrypted secret version"
        }
        val ivSize = buffer.get().toInt() and 0xff
        require(ivSize in IV_BYTES..32 && buffer.remaining() >= ivSize + GCM_TAG_BYTES) {
            "Invalid encrypted secret IV"
        }
        val iv = ByteArray(ivSize).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }

}

/** Small boundary that keeps legacy migration policy independently testable. */
internal interface MigratingSecretDestination {
    val legacyMigrationAllowed: Boolean
    fun read(): ByteArray?
    fun write(value: ByteArray): Boolean
}

internal sealed interface MigratingSecretReadResult {
    data class Success(
        val value: ByteArray?,
        val canDeleteLegacy: Boolean,
        val shouldRetryMigration: Boolean = false,
    ) : MigratingSecretReadResult

    data class Failure(val error: Exception) : MigratingSecretReadResult
}

/**
 * Reads the replacement store first and consults legacy storage only before migration completes.
 * Legacy data is deliberately retained; a failed replacement write must not log an existing user
 * out, and explicit clear uses the destination's migration marker to prevent stale re-import.
 */
internal fun readWithLegacyMigration(
    destination: MigratingSecretDestination,
    onMigrationFailure: (Throwable) -> Unit = {},
    readLegacy: () -> ByteArray?,
): MigratingSecretReadResult {
    val destinationValue = try {
        destination.read()
    } catch (error: Exception) {
        return MigratingSecretReadResult.Failure(error)
    }
    if (destinationValue != null) {
        return MigratingSecretReadResult.Success(
            value = destinationValue,
            canDeleteLegacy = true,
        )
    }

    val migrationAllowed = try {
        destination.legacyMigrationAllowed
    } catch (error: Exception) {
        return MigratingSecretReadResult.Failure(error)
    }
    if (!migrationAllowed) {
        return MigratingSecretReadResult.Success(
            value = null,
            canDeleteLegacy = true,
        )
    }

    val legacy = try {
        readLegacy()
    } catch (error: Exception) {
        return MigratingSecretReadResult.Failure(error)
    } ?: return MigratingSecretReadResult.Success(
        value = null,
        canDeleteLegacy = false,
    )

    val migrated = try {
        destination.write(legacy).also { written ->
            if (!written) {
                onMigrationFailure(IllegalStateException("Replacement secret write was not committed"))
            }
        }
    } catch (error: Exception) {
        onMigrationFailure(error)
        false
    }
    return MigratingSecretReadResult.Success(
        value = legacy,
        canDeleteLegacy = migrated,
        shouldRetryMigration = !migrated,
    )
}
