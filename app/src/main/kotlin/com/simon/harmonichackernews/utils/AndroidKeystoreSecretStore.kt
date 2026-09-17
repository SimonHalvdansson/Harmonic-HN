package com.simon.harmonichackernews.utils

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Single-record encrypted storage backed directly by Android Keystore.
 *
 * Callers are responsible for invoking every method off the main thread. The regular preference
 * file contains only an authenticated ciphertext envelope; the non-exportable AES key remains in
 * Android Keystore.
 */
internal class AndroidKeystoreSecretStore(
    context: Context,
    preferencesName: String,
    private val preferenceKey: String,
    private val keyAlias: String,
) : MigratingSecretDestination {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )
    private val associatedData = buildString {
        append(context.applicationContext.packageName)
        append(':')
        append(preferencesName)
        append(':')
        append(preferenceKey)
    }.encodeToByteArray()

    @Volatile
    private var cachedKey: SecretKey? = null

    override val legacyMigrationAllowed: Boolean
        @Synchronized get() = !preferences.contains(preferenceKey) &&
            !preferences.getBoolean(MIGRATION_COMPLETE_KEY, false)

    @Synchronized
    override fun read(): ByteArray? {
        val envelope = preferences.getString(preferenceKey, null) ?: return null
        return AesGcmSecretCodec.decrypt(envelope, secretKey(), associatedData)
    }

    @SuppressLint("ApplySharedPref")
    @Synchronized
    override fun write(value: ByteArray): Boolean {
        val previous = preferenceSnapshot()
        val key = secretKey()
        val envelope = AesGcmSecretCodec.encrypt(value, key, associatedData)
        check(AesGcmSecretCodec.decrypt(envelope, key, associatedData).contentEquals(value)) {
            "Encrypted secret verification failed"
        }
        val committed = preferences.edit()
            .putString(preferenceKey, envelope)
            .putBoolean(MIGRATION_COMPLETE_KEY, true)
            .commit()
        if (!committed) restorePreferenceSnapshot(previous)
        return committed
    }

    @SuppressLint("ApplySharedPref")
    @Synchronized
    fun clear(): Boolean {
        val previous = preferenceSnapshot()
        val committed = preferences.edit()
            .remove(preferenceKey)
            .putBoolean(MIGRATION_COMPLETE_KEY, true)
            .commit()
        if (!committed) restorePreferenceSnapshot(previous)
        return committed
    }

    /** Clears the ciphertext first, then removes the key so a later write can recover cleanly. */
    @Synchronized
    fun reset(): Boolean {
        if (!clear()) return false
        try {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
                load(null)
                if (containsAlias(keyAlias)) deleteEntry(keyAlias)
            }
        } finally {
            cachedKey = null
        }
        return true
    }

    private fun preferenceSnapshot() = PreferenceSnapshot(
        value = preferences.getString(preferenceKey, null),
        migrationMarkerPresent = preferences.contains(MIGRATION_COMPLETE_KEY),
        migrationComplete = preferences.getBoolean(MIGRATION_COMPLETE_KEY, false),
    )

    private fun restorePreferenceSnapshot(snapshot: PreferenceSnapshot) {
        preferences.edit {
            if (snapshot.value == null) remove(preferenceKey)
            else putString(preferenceKey, snapshot.value)
            if (snapshot.migrationMarkerPresent) {
                putBoolean(MIGRATION_COMPLETE_KEY, snapshot.migrationComplete)
            } else {
                remove(MIGRATION_COMPLETE_KEY)
            }
        }
    }

    private fun secretKey(): SecretKey {
        cachedKey?.let { return it }
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null)
        if (existing != null) {
            require(existing is SecretKey) { "Keystore alias does not contain a secret key" }
            return existing.also { cachedKey = it }
        }

        val generated = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
        cachedKey = generated
        return generated
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val MIGRATION_COMPLETE_KEY = "legacy_migration_complete"
    }

    private data class PreferenceSnapshot(
        val value: String?,
        val migrationMarkerPresent: Boolean,
        val migrationComplete: Boolean,
    )
}
